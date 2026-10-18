package com.c520.shengdewoxin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 情境：說出觸發語句 → 先做設定類動作（音量、靜音、勿擾…）→ 念回應 → 再做開 App、放音樂這類動作。
 * 「要做的事」存使用者寫的句子（跟平常說的一樣），儲存時就先翻成動作，觸發時不用等 AI。
 */
class Routine(
    val id: String,
    val triggers: List<String>,
    val reply: String,
    val commands: List<String>,
    val acts: List<Act>,
)

object Routines {
    const val NICK = "{稱呼}"
    const val MAX_TRIGGERS = 5
    const val MAX_COMMANDS = 10

    /** 會打開別的 App 或開始播放的動作：念完回應再做，才不會蓋掉聲音或跳走。 */
    val LATER = setOf("media", "play", "open_app", "navigate", "call", "search", "settings")

    @Volatile private var cache: List<Routine>? = null

    @Synchronized
    fun all(c: Context): List<Routine> {
        cache?.let { return it }
        val raw = Prefs.routinesJson(c)
        val list = if (raw == null) seed(c) else runCatching { parse(JSONArray(raw)) }.getOrDefault(emptyList())
        cache = list
        return list
    }

    @Synchronized
    fun save(c: Context, list: List<Routine>) {
        Prefs.setRoutinesJson(c, toJson(list).toString())
        cache = list
    }

    /** 第一次使用時放一個範例：「我到家了」。三句都是規則看得懂的，不需要 AI。 */
    private fun seed(c: Context): List<Routine> {
        val cmds = listOf("取消靜音", "媒體音量調到 70", "關閉勿擾")
        val acts = cmds.flatMap { Quick.match(c, it)?.acts.orEmpty() }
        val list = listOf(Routine(UUID.randomUUID().toString(), listOf("我到家了", "我回來了"), "$NICK，歡迎回家", cmds, acts))
        Prefs.setRoutinesJson(c, toJson(list).toString())
        return list
    }

    private fun toJson(list: List<Routine>) = JSONArray().apply {
        for (r in list) {
            put(
                JSONObject()
                    .put("id", r.id)
                    .put("triggers", JSONArray(r.triggers))
                    .put("reply", r.reply)
                    .put("commands", JSONArray(r.commands))
                    .put("actions", JSONArray().apply { r.acts.forEach { put(JSONObject(it.p.toString()).put("t", it.t)) } })
            )
        }
    }

    private fun parse(arr: JSONArray): List<Routine> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val acts = o.optJSONArray("actions")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Act(it.optString("t"), it) }
        }.orEmpty()
        Routine(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            strings(o.optJSONArray("triggers")),
            o.optString("reply"),
            strings(o.optJSONArray("commands")),
            acts,
        )
    }

    private fun strings(a: JSONArray?): List<String> =
        a?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()

    /** 觸發語句比對用的字：去掉標點、「請、幫我」與句尾語助詞。 */
    fun key(s: String): String = Names.norm(Quick.clean(s))

    /** 找出這句話對應的情境：字相同、同音，或只多了幾個字（「嗨，我到家了」）。 */
    fun match(c: Context, text: String): Routine? {
        val t = key(text)
        if (t.isEmpty()) return null
        val py = Names.pinyin(t)
        return all(c).firstOrNull { r ->
            r.triggers.any { trig ->
                val k = key(trig)
                k.isNotEmpty() && (
                    k == t ||
                        (py.length >= 2 && Names.pinyin(k) == py) ||
                        (k.length >= 3 && t.contains(k) && t.length - k.length <= 3)
                    )
            }
        }
    }

    /** 把回應裡的 {稱呼} 換成使用者的稱呼；沒設稱呼就連後面的逗號一起拿掉。 */
    fun fill(c: Context, s: String): String {
        val nick = Prefs.nickname(c)
        return if (nick.isNotEmpty()) s.replace(NICK, nick) else s.replace(Regex("\\{稱呼\\}[，,、\\s]*"), "")
    }

    class Compiled(val acts: List<Act>, val error: String?)

    /** 一句「要做的事」翻成動作：先用規則，規則看不懂才問 AI。在背景執行緒呼叫。 */
    fun compile(c: Context, line: String): Compiled {
        Quick.match(c, line)?.let { if (it.acts.isNotEmpty()) return Compiled(it.acts, null) }
        val r = Llm.ask(c, line, history = false)
        val out = r.text ?: return Compiled(emptyList(), r.error ?: "看不懂這句")
        val acts = Plan.parse(out).acts.filter { it.t.isNotEmpty() }
        return if (acts.isEmpty()) Compiled(emptyList(), "看不懂這句，換個說法試試") else Compiled(acts, null)
    }
}
