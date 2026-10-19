package com.c520.shengdewoxin

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 背景執行緒池（網路、查聯絡人、讀 App 清單）。 */
object Bg {
    val pool: ExecutorService = Executors.newCachedThreadPool()
}

/** 呼叫 AI 把一句話變成動作。只用標準庫 HttpURLConnection＋org.json，不加套件。 */
object Llm {
    private const val TAG = "Llm"

    /** detail：服務商自己的錯誤說明（英文），設定頁測試時顯示，看得出真正原因。 */
    class Result(val text: String?, val error: String?, val detail: String? = null)

    // 上一輪對話（3 分鐘內有效），讓「那明天呢」這種追問接得上
    @Volatile private var last: Triple<String, String, Long>? = null

    // 每個模型能接受的加速等級（見 body）。模型不認得的參數會回 400，就降一級重送，成功後記住
    private val levels = ConcurrentHashMap<String, Int>()

    fun remember(question: String, answer: String) {
        last = Triple(question, answer, System.currentTimeMillis())
    }

    /** 開始聽的同時先連上 AI 主機（TLS 握手），講完送出時可省 0.2~0.3 秒。 */
    fun warm(c: Context) {
        val p = Prefs.provider(c)
        if (Prefs.key(c, p).isEmpty()) return
        val url = Prefs.endpoint(c, p)
        if (!url.startsWith("https://")) return
        Bg.pool.execute {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.responseCode
                (conn.errorStream ?: runCatching { conn.inputStream }.getOrNull())?.close()
            } catch (e: Exception) {
                // 暖機失敗不影響正式請求
            }
        }
    }

    /** 同步呼叫，只能在背景執行緒用。history＝false 時不帶上一輪對話（情境儲存時翻譯指令用）。 */
    fun ask(c: Context, text: String, history: Boolean = true): Result {
        val p = Prefs.provider(c)
        val key = Prefs.key(c, p)
        if (key.isEmpty()) return Result(null, "還沒設定 AI 金鑰，請到聲得我心設定填寫")
        val url = Prefs.endpoint(c, p)
        if (!url.startsWith("https://")) return Result(null, "AI 網址要用 https:// 開頭")
        val model = Prefs.model(c, p)
        val sys = system(Prefs.nickname(c))
        val msgs = JSONArray()
        if (!p.anthropic) msgs.put(JSONObject().put("role", "system").put("content", sys))
        last?.takeIf { history && System.currentTimeMillis() - it.third < 180_000 }?.let {
            msgs.put(JSONObject().put("role", "user").put("content", it.first))
            msgs.put(JSONObject().put("role", "assistant").put("content", it.second))
        }
        msgs.put(JSONObject().put("role", "user").put("content", text))
        val id = "${p.name}/$model"
        var level = levels[id] ?: top(p)
        return try {
            val t0 = System.currentTimeMillis()
            var (code, resp) = post(p, url, key, body(p, model, sys, msgs, level))
            while (code == 400 && level > 0) {
                Log.w(TAG, "$model 不接受第 $level 級加速參數：${detail(resp)}")
                level--
                val r = post(p, url, key, body(p, model, sys, msgs, level))
                code = r.first
                resp = r.second
            }
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code ${resp.take(300)}")
                return Result(null, httpError(code), detail(resp))
            }
            levels[id] = level
            val j = JSONObject(resp)
            val out = if (p.anthropic) {
                // 回應前面可能有 thinking 區塊，只取 text
                val arr = j.getJSONArray("content")
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filter { it.optString("type") == "text" }
                    .joinToString("") { it.optString("text") }
            } else {
                j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            }
            Log.i(TAG, "HTTP 200 $model 加速等級 $level，${System.currentTimeMillis() - t0} ms")
            if (out.isBlank()) Result(null, "AI 沒有回應") else Result(out, null)
        } catch (e: SocketTimeoutException) {
            Result(null, "AI 回應逾時")
        } catch (e: UnknownHostException) {
            Result(null, "連不上網路")
        } catch (e: Exception) {
            Log.w(TAG, "ask failed", e)
            Result(null, "AI 連線失敗")
        }
    }

    private fun top(p: Provider) = when (p) {
        Provider.CLAUDE -> 2
        Provider.DEEPSEEK, Provider.OPENAI -> 1
        Provider.CUSTOM -> 0
    }

    /**
     * 請求內容。新模型預設會先「思考」再回答，關掉才快；等級越高，送的加速參數越多：
     * Claude 2＝關思考＋effort low（Haiku／Sonnet 5.5）、1＝只設 effort low（不能關思考的 Opus 5.5 等）、0＝都不送（舊模型）；
     * DeepSeek／OpenAI 1＝關思考／推理＋只回 JSON、0＝都不送。
     * temperature 一律不送：Claude 5.5 這一代送了會回 400。
     */
    private fun body(p: Provider, model: String, sys: String, msgs: JSONArray, level: Int): JSONObject {
        val b = JSONObject().put("model", model).put("messages", msgs)
        when (p) {
            Provider.CLAUDE -> {
                // 思考也算在 max_tokens 裡，上限放寬避免答案被截掉
                b.put("system", sys).put("max_tokens", 2048)
                if (level >= 2) b.put("thinking", JSONObject().put("type", "disabled"))
                if (level >= 1) b.put("output_config", JSONObject().put("effort", "low"))
            }
            Provider.DEEPSEEK -> if (level >= 1) {
                b.put("thinking", JSONObject().put("type", "disabled"))
                b.put("response_format", JSONObject().put("type", "json_object"))
            }
            Provider.OPENAI -> if (level >= 1) {
                b.put("reasoning_effort", if (Regex("^gpt-5(-|$)").containsMatchIn(model)) "minimal" else "none")
                b.put("response_format", JSONObject().put("type", "json_object"))
            }
            Provider.CUSTOM -> {}
        }
        return b
    }

    private fun post(p: Provider, url: String, key: String, body: JSONObject): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 15000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (p.anthropic) {
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
        } else {
            conn.setRequestProperty("Authorization", "Bearer $key")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return code to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    /** 服務商的錯誤說明（Anthropic、OpenAI 都放在 error.message）。 */
    private fun detail(resp: String): String? =
        runCatching { JSONObject(resp).getJSONObject("error").optString("message") }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.take(200)

    private fun httpError(code: Int) = when (code) {
        400 -> "AI 請求被拒絕"
        401, 403 -> "AI 金鑰無效或沒有權限"
        404 -> "找不到這個模型或網址"
        429 -> "AI 用量超過上限，稍後再試"
        in 500..599 -> "AI 服務暫時有問題"
        else -> "AI 錯誤（$code）"
    }

    private fun system(nickname: String): String {
        // 稱呼是使用者自己填的，拿掉換行與引號再放進提示詞
        val nick = nickname.replace(Regex("[\\r\\n「」\"]"), "").take(Prefs.NICK_MAX)
        val callMe = if (nick.isEmpty()) "" else "6. 使用者希望你稱呼他「$nick」：打招呼、閒聊、回答問題時自然地稱呼（不用每句都叫）。"
        val now = ZonedDateTime.now()
        val wd = "一二三四五六日"[now.dayOfWeek.value - 1]
        val ts = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return """
你是 Android 手機上的語音助理「聲得我心」。把使用者說的一句話，變成要在手機上執行的動作。
只輸出一個 JSON 物件，不要 markdown，不要其他文字。
現在時間：$ts（星期$wd），時區 ${now.zone.id}。
格式：{"say":"要念給使用者聽的話","actions":[{"t":"動作種類", 其他參數}]}
動作種類與參數：
- alarm {"h":0-23,"m":0-59,"label":"可省略"}：設鬧鐘（24 小時制）
- timer {"sec":秒數,"label":"可省略"}：計時、幾分鐘後提醒
- calendar {"title":"事項","start":"YYYY-MM-DDTHH:mm","min":分鐘數}：指定日期時間的提醒或行程（有說日期、或超過 24 小時就用這個）
- open_app {"names":["最可能的 App 名稱","別名或英文名"]}
- navigate {"q":"目的地"}
- call {"name":"聯絡人名稱或電話號碼"}
- flashlight {"on":true 或 false}
- volume {"stream":"media|ring|alarm","level":0-100}，或 {"stream":"media","delta":1 或 -1}，或 {"stream":"media","mute":true 或 false}
- ringer {"mode":"normal|vibrate|silent"}：手機的響鈴、震動、靜音模式（「取消靜音」用 normal）
- brightness {"level":0-100} 或 {"delta":1 或 -1}
- dnd {"on":true 或 false}：勿擾模式
- media {"cmd":"play|pause|next|prev"}：控制正在播的音樂
- play {"q":"歌名、歌手或歌單","app":"spotify|youtube music|youtube（可省略）"}：播指定的歌或歌手；「我喜歡的歌、我的最愛」用 {"liked":true}；沒指定要聽什麼就用 media play
- settings {"page":"wifi|bluetooth|internet|display|sound|battery|location|nfc|apps|main"}：打開設定頁（Wi-Fi、藍牙不能直接切換，用這個）
- search {"q":"關鍵字"}：上網搜尋
- weather {"day":0 今天、1 明天、2 後天,"place":"地點","lat":緯度,"lon":經度}：沒說地點就不要給 place、lat、lon
規則：
1. 能用動作完成的就給動作，say 用一句很短的話說明，例如「好，早上7點叫你」。
2. 一般問題、知識、閒聊：actions 給 []，直接在 say 用口語回答，60 字以內。
3. 需要即時資訊（新聞、股價、比分、營業時間）就用 search。
4. 聽不清楚或缺少必要資訊：actions 給 []，say 簡短反問。
5. 一句話有好幾件事就給好幾個動作。
$callMe
""".trim()
    }
}
