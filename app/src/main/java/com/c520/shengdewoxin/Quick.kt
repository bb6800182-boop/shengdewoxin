package com.c520.shengdewoxin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** 常用指令用規則直接判斷，不必等 AI（0 延遲）；判斷不了的回 null，交給 AI。 */
object Quick {
    private const val N = "[0-9０-９零〇一二兩三四五六七八九十百]+"
    private val digit = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '兩' to 2, '三' to 3,
        '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    private val noise = Regex("[\\s,，.。!！?？、~～]")
    private val lead = Regex("^(請你|請|麻煩你|麻煩|幫我|幫忙|替我|可以|能不能|可不可以)+")
    private val tail = Regex("(一下子|一下|好嗎|好不好|可以嗎|嗎|吧|喔|哦|啦|囉|呀|啊|謝謝)+$")
    private val durRe = Regex("($N|半)(個)?(半)?(小時|鐘頭|分鐘|分|秒鐘|秒)")
    private val clockRe = Regex("(凌晨|半夜|清晨|早上|上午|中午|下午|傍晚|晚上)?($N)(?:點|:|：)(?:(半)|($N)分?)?")
    private val dateWord = Regex("後天|下週|下周|下禮拜|下星期|星期|禮拜|週[一二三四五六日天]|周[一二三四五六日天]|${N}號|${N}月")

    /** 「二十五」「兩」「15」「１５」→ 數字；不是數字回 null。 */
    fun num(raw: String): Int? {
        val s = raw.map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("")
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it }
        var total = 0
        var cur = 0
        for (ch in s) {
            when (ch) {
                in digit -> cur = digit.getValue(ch)
                '十' -> { total += (if (cur == 0) 1 else cur) * 10; cur = 0 }
                '百' -> { total += (if (cur == 0) 1 else cur) * 100; cur = 0 }
                else -> return null
            }
        }
        return total + cur
    }

    fun clean(s: String): String = s.replace(noise, "").replace(lead, "").replace(tail, "")

    /** 句子裡的時間長度（秒）：「1個半小時」「5分30秒」「半小時」。 */
    fun seconds(t: String): Int? {
        var sec = 0.0
        for (m in durRe.findAll(t)) {
            val (n, _, half, unit) = m.destructured
            val base = when (unit) {
                "小時", "鐘頭" -> 3600
                "分鐘", "分" -> 60
                else -> 1
            }
            val v = if (n == "半") 0.5 else (num(n) ?: continue) + (if (half.isNotEmpty()) 0.5 else 0.0)
            sec += v * base
        }
        return sec.toInt().takeIf { it > 0 }
    }

    /** 句子裡的時刻（24 小時制）：「晚上七點半」「下午3點15分」「7:30」。 */
    fun clock(t: String): Pair<Int, Int>? {
        val m = clockRe.find(t) ?: return null
        val (period, hs, half, ms) = m.destructured
        var h = num(hs) ?: return null
        val min = when {
            half.isNotEmpty() -> 30
            ms.isNotEmpty() -> num(ms) ?: return null
            else -> 0
        }
        when (period) {
            "下午", "傍晚" -> { if (h in 1..11) h += 12 }
            "晚上" -> { if (h in 1..11) h += 12 else if (h == 12) h = 0 }
            "中午" -> { if (h in 1..5) h += 12 }
            "凌晨", "半夜" -> { if (h == 12) h = 0 }
        }
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    /** 念給人聽的時刻：「早上7點30分」。 */
    fun hm(h: Int, m: Int): String {
        val period = when (h) {
            in 0..4 -> "凌晨"
            in 5..11 -> "早上"
            12 -> "中午"
            in 13..17 -> "下午"
            else -> "晚上"
        }
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$period${h12}點" + (if (m > 0) "${m}分" else "")
    }

    /** 念給人聽的長度：「1小時30分鐘」。 */
    fun dur(sec: Int): String = buildString {
        val h = sec / 3600
        val m = sec % 3600 / 60
        val s = sec % 60
        if (h > 0) append("${h}小時")
        if (m > 0) append("${m}分鐘")
        if (s > 0) append("${s}秒")
    }

    fun match(c: Context, raw: String): Plan? {
        val t = clean(raw)
        if (t.isEmpty()) return null

        // 現在幾點、今天幾號：本機直接回答，不必問 AI
        if (t.length <= 10 && !Regex("鬧鐘|計時|提醒|叫我|倒數|明天|昨天|後天|前天|下週|下個|上週|上個").containsMatchIn(t)) {
            val now = LocalDateTime.now()
            if (Regex("幾點|什麼時間|時間是").containsMatchIn(t)) return Plan("現在${hm(now.hour, now.minute)}", emptyList())
            if (Regex("幾號|幾月幾|星期幾|禮拜幾|日期").containsMatchIn(t)) {
                val wd = "一二三四五六日"[now.dayOfWeek.value - 1]
                return Plan("今天${now.monthValue}月${now.dayOfMonth}日，星期$wd", emptyList())
            }
        }

        // 天氣（沒說地點）：用手機位置直接查；有說地點的交給 AI 判斷經緯度
        Regex("^(現在|今天|明天|後天)?的?(天氣|天氣如何|天氣怎樣|天氣怎麼樣|天氣好|會下雨|會不會下雨|有沒有下雨|下雨|要帶傘|幾度|氣溫|溫度|冷不冷|熱不熱)$").find(t)?.let {
            val day = when (it.groupValues[1]) {
                "明天" -> 1
                "後天" -> 2
                else -> 0
            }
            return Plan.of("", "weather", JSONObject().put("day", day))
        }

        // 計時：「計時5分鐘」「倒數三十秒」「十分鐘後提醒我關火」
        val timerWord = Regex("計時|倒數|定時").containsMatchIn(t)
        val laterWord = t.contains("後") && Regex("提醒|叫我").containsMatchIn(t)
        if ((timerWord || laterWord) && !dateWord.containsMatchIn(t) && clockRe.find(t) == null) {
            seconds(t)?.let { sec ->
                val p = JSONObject().put("sec", sec)
                Regex("提醒我(.+)$").find(t)?.let { p.put("label", it.groupValues[1]) }
                return Plan.of(if (laterWord) "好，${dur(sec)}後提醒你" else "好，計時${dur(sec)}", "timer", p)
            }
        }

        // 鬧鐘：「明天早上七點叫我」「設定6點半的鬧鐘」；有說日期的交給 AI 寫行事曆
        if (Regex("鬧鐘|叫我|叫醒").containsMatchIn(t) && !t.contains("後") && !dateWord.containsMatchIn(t)) {
            clock(t)?.let { (h, m) ->
                return Plan.of("好，${hm(h, m)}叫你", "alarm", JSONObject().put("h", h).put("m", m))
            }
        }

        // 手電筒
        if (Regex("手電筒|閃光燈").containsMatchIn(t)) {
            val p = JSONObject()
            when {
                Regex("關|熄").containsMatchIn(t) -> p.put("on", false)
                Regex("開|亮").containsMatchIn(t) -> p.put("on", true)
            }
            return Plan.of("", "flashlight", p)
        }

        // 勿擾
        if (Regex("勿擾|零打擾").containsMatchIn(t)) {
            return Plan.of("", "dnd", JSONObject().put("on", !Regex("關|取消|解除|結束").containsMatchIn(t)))
        }

        // 響鈴模式（手機的靜音／震動）：「取消靜音」同時把媒體的靜音也解除；講到音樂、影片的才只動媒體
        if (!Regex("音樂|媒體|影片|歌").containsMatchIn(t)) {
            if (Regex("取消靜音|關閉靜音|關掉靜音|解除靜音|不要靜音|打開聲音|開啟聲音|恢復聲音|響鈴模式|有聲模式|取消震動|關閉震動|關掉震動").containsMatchIn(t)) {
                return Plan(
                    "",
                    listOf(
                        Act("ringer", JSONObject().put("mode", "normal")),
                        Act("volume", JSONObject().put("stream", "media").put("mute", false)),
                    ),
                )
            }
            if (t.contains("震動")) return Plan.of("", "ringer", JSONObject().put("mode", "vibrate"))
            if (Regex("^(手機|電話)?(開啟|打開|切成|切到|改成|設成|設為)?靜音(模式)?$").matches(t)) {
                return Plan.of("", "ringer", JSONObject().put("mode", "silent"))
            }
        }

        // 音樂控制（整句完全相同才算，「播放周杰倫」這種交給 AI）
        when (t) {
            "暫停", "暫停音樂", "暫停播放", "音樂暫停", "停止播放", "停止音樂", "先暫停" -> return media("pause")
            "播放", "繼續播放", "繼續", "播放音樂", "繼續音樂", "開始播放", "放音樂" -> return media("play")
            "下一首", "下一首歌", "下一曲", "切歌", "跳過", "換一首", "換下一首" -> return media("next")
            "上一首", "上一首歌", "上一曲" -> return media("prev")
        }

        // 音量：講「鈴聲、通知」調鈴聲，講「鬧鐘」調鬧鐘，其他調媒體；通話音量交給 AI
        if (Regex("音量|大聲|小聲|靜音").containsMatchIn(t) && !t.contains("通話")) {
            val stream = when {
                Regex("鈴聲|通知|來電").containsMatchIn(t) -> "ring"
                t.contains("鬧鐘") -> "alarm"
                else -> "media"
            }
            val p = JSONObject().put("stream", stream)
            val lv = Regex("音量(?:調到|調成|設為|設成|設定為|開到|到)?($N)(%|％|趴|成)?").find(t)
            when {
                Regex("取消靜音|解除靜音|關閉靜音|關掉靜音").containsMatchIn(t) -> p.put("mute", false)
                t.contains("靜音") -> p.put("mute", true)
                lv != null -> {
                    var v = num(lv.groupValues[1]) ?: return null
                    if (lv.groupValues[2] == "成") v *= 10
                    p.put("level", v.coerceIn(0, 100))
                }
                t.contains("最大") -> p.put("level", 100)
                t.contains("最小") -> p.put("level", 0)
                Regex("大|高|加").containsMatchIn(t) -> p.put("delta", 1)
                Regex("小|低|減|降").containsMatchIn(t) -> p.put("delta", -1)
                else -> return null
            }
            return Plan.of("", "volume", p)
        }

        // 螢幕亮度
        if (Regex("亮度|調亮|調暗|太暗|太亮|亮一點|暗一點").containsMatchIn(t)) {
            val p = JSONObject()
            val lv = Regex("亮度(?:調到|調成|設為|設成|設定為|開到|到)?($N)(%|％|趴|成)?").find(t)
            when {
                lv != null -> {
                    var v = num(lv.groupValues[1]) ?: return null
                    if (lv.groupValues[2] == "成") v *= 10
                    p.put("level", v.coerceIn(0, 100))
                }
                Regex("最亮|最大|最高").containsMatchIn(t) -> p.put("level", 100)
                Regex("最暗|最小|最低").containsMatchIn(t) -> p.put("level", 0)
                Regex("調亮|亮一點|亮一些|調高|高一點|太暗|提高").containsMatchIn(t) -> p.put("delta", 1)
                Regex("調暗|暗一點|暗一些|調低|低一點|太亮|降低").containsMatchIn(t) -> p.put("delta", -1)
                else -> return null
            }
            return Plan.of("", "brightness", p)
        }

        // 導航（放在「開…」之前，「開車去」才不會被當成開 App）
        Regex("^(?:導航到|導航去|導航至|導航|帶我去|帶我到|開車去|開車到|騎車去|走路去)(.{2,})$").find(t)?.let {
            return Plan.of("", "navigate", JSONObject().put("q", it.groupValues[1]))
        }
        Regex("^(.{2,})怎麼(?:去|走)$").find(t)?.let {
            return Plan.of("", "navigate", JSONObject().put("q", it.groupValues[1]))
        }

        // 打電話（「打給媽媽說我晚點到」這種要傳話的交給 AI）
        Regex("^(?:打電話給|打給|撥電話給|撥給|撥打|打電話到)(.+)$").find(t)?.let {
            val name = it.groupValues[1]
            if (!Regex("說|講|告訴|傳").containsMatchIn(name)) {
                return Plan.of("", "call", JSONObject().put("name", name))
            }
        }

        // 設定頁（Wi-Fi、藍牙不能直接切換，打開設定面板讓使用者點）
        val page = when {
            Regex("wi-?fi|無線網路", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "wifi"
            Regex("藍牙|藍芽").containsMatchIn(t) -> "bluetooth"
            Regex("行動數據|行動網路").containsMatchIn(t) -> "internet"
            Regex("定位|gps", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "location"
            Regex("nfc", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "nfc"
            t == "設定" || t == "打開設定" || t == "開啟設定" || t == "系統設定" -> "main"
            else -> null
        }
        if (page != null && (page == "main" || Regex("開|關|設定|切換|連").containsMatchIn(t))) {
            return Plan.of("", "settings", JSONObject().put("page", page))
        }

        // 打開 App：名稱夠像才直接開，不確定的交給 AI 判斷別名
        Regex("^(?:打開|開啟|啟動|執行|進入|開)(.+)$").find(t)?.let { m ->
            val hit = Apps.find(c, listOf(m.groupValues[1]))
            if (hit != null && hit.second >= 85) {
                return Plan.of("", "open_app", JSONObject().put("names", JSONArray().put(hit.first.label)))
            }
        }
        return null
    }

    private fun media(cmd: String) = Plan.of("", "media", JSONObject().put("cmd", cmd))
}
