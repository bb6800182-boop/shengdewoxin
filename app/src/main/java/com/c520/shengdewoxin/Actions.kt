package com.c520.shengdewoxin

import android.Manifest
import android.app.NotificationManager
import android.app.SearchManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 一個動作的結果。intent 由面板在主執行緒送出；leave=true 會打開別的 App，面板隨即收起。
 * info=true 表示 text 本身就是答案（天氣、行事曆），要念它而不是 AI 的 say。
 * choices 有值時面板列出按鈕讓使用者點（同名聯絡人、前往授權）。
 */
class Res(
    val text: String,
    val speak: Boolean = false,
    val intent: Intent? = null,
    val leave: Boolean = false,
    val error: Boolean = false,
    val info: Boolean = false,
    val choices: List<Pair<String, Intent>> = emptyList(),
    val fallback: Intent? = null,
)

private fun JSONObject.str(k: String): String? = optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }

/** 執行動作。在背景執行緒呼叫（查聯絡人、天氣會用到資料庫或網路）。參數一律先驗證再用。 */
object Actions {
    private const val TAG = "Actions"
    private const val MAPS = "com.google.android.apps.maps"
    private const val YT = "com.google.android.youtube"
    private const val YTM = "com.google.android.apps.youtube.music"
    private const val SPOTIFY = "com.spotify.music"

    fun exec(c: Context, a: Act): Res = try {
        when (a.t) {
            "alarm" -> alarm(a.p)
            "timer" -> timer(a.p)
            "calendar" -> calendar(c, a.p)
            "open_app" -> openApp(c, a.p)
            "navigate" -> navigate(c, a.p)
            "call" -> call(c, a.p)
            "flashlight" -> flashlight(c, a.p)
            "volume" -> volume(c, a.p)
            "ringer" -> ringer(c, a.p)
            "brightness" -> brightness(c, a.p)
            "dnd" -> dnd(c, a.p)
            "media" -> media(c, a.p)
            "play" -> play(c, a.p)
            "settings" -> settings(a.p)
            "search" -> search(a.p)
            "weather" -> weather(c, a.p)
            else -> fail("還不會做「${a.t}」")
        }
    } catch (e: Exception) {
        Log.w(TAG, "exec ${a.t} failed", e)
        fail("執行失敗，請再試一次")
    }

    /** 動作的中文說明（情境儲存時列出「會做什麼」）。 */
    fun describe(a: Act): String {
        val p = a.p
        val q = p.optString("q")
        return when (a.t) {
            "alarm" -> "鬧鐘 ${Quick.hm(p.optInt("h"), p.optInt("m"))}"
            "timer" -> "計時 ${Quick.dur(p.optInt("sec"))}"
            "calendar" -> "行事曆：${p.optString("title")}"
            "open_app" -> "打開 ${p.optJSONArray("names")?.optString(0) ?: p.optString("name")}"
            "navigate" -> "導航到 $q"
            "call" -> "打給 ${p.optString("name")}"
            "flashlight" -> when {
                !p.has("on") -> "切換手電筒"
                p.optBoolean("on") -> "打開手電筒"
                else -> "關掉手電筒"
            }
            "volume" -> {
                val s = when (p.optString("stream")) {
                    "ring" -> "鈴聲"
                    "alarm" -> "鬧鐘"
                    else -> "媒體"
                }
                when {
                    p.has("mute") -> if (p.optBoolean("mute")) "${s}靜音" else "取消${s}靜音"
                    p.has("level") -> "${s}音量 ${p.optInt("level")}%"
                    p.optInt("delta", 1) > 0 -> "${s}音量調大"
                    else -> "${s}音量調小"
                }
            }
            "ringer" -> when (p.optString("mode")) {
                "silent" -> "切到靜音模式"
                "vibrate" -> "切到震動模式"
                else -> "切到響鈴模式"
            }
            "brightness" -> when {
                p.has("level") -> "亮度 ${p.optInt("level")}%"
                p.optInt("delta", 1) > 0 -> "螢幕調亮"
                else -> "螢幕調暗"
            }
            "dnd" -> if (p.optBoolean("on", true)) "開啟勿擾" else "關閉勿擾"
            "media" -> when (p.optString("cmd")) {
                "pause" -> "暫停播放"
                "next" -> "下一首"
                "prev" -> "上一首"
                else -> "播放音樂"
            }
            "play" -> "播放「$q」"
            "settings" -> "打開設定頁"
            "search" -> "搜尋「$q」"
            "weather" -> "查天氣"
            else -> a.t
        }
    }

    private fun fail(msg: String, picks: List<Pair<String, Intent>> = emptyList()) =
        Res(msg, speak = true, error = true, choices = picks)

    private fun needPerm(c: Context, name: String) =
        fail("需要「$name」權限，請到聲得我心設定授權", listOf("開啟聲得我心設定" to Intent(c, MainActivity::class.java)))

    private fun has(c: Context, perm: String) = c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun installed(c: Context, pkg: String) = runCatching { c.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    private fun alarm(p: JSONObject): Res {
        val h = p.optInt("h", -1)
        val m = p.optInt("m", 0)
        if (h !in 0..23 || m !in 0..59) return fail("時間沒聽清楚，請再說一次")
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        p.str("label")?.let { i.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return Res("${Quick.hm(h, m)}的鬧鐘設好了", speak = true, intent = i)
    }

    private fun timer(p: JSONObject): Res {
        val sec = p.optInt("sec", 0)
        if (sec !in 1..86_400) return fail("計時長度沒聽清楚，請再說一次")
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        p.str("label")?.let { i.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return Res("計時${Quick.dur(sec)}開始", speak = true, intent = i)
    }

    private fun calendar(c: Context, p: JSONObject): Res {
        if (!has(c, Manifest.permission.WRITE_CALENDAR) || !has(c, Manifest.permission.READ_CALENDAR)) return needPerm(c, "行事曆")
        val title = p.str("title") ?: "提醒"
        val start = runCatching { LocalDateTime.parse(p.str("start")!!.replace(' ', 'T')) }.getOrNull()
            ?: return fail("日期時間沒聽清楚，請再說一次")
        val min = p.optInt("min", 30).coerceIn(5, 24 * 60)
        val calId = calendarId(c) ?: return fail("找不到可以寫入的行事曆")
        val zone = ZoneId.systemDefault()
        val begin = start.atZone(zone).toInstant().toEpochMilli()
        val ev = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, begin + min * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ev) ?: return fail("寫入行事曆失敗")
        c.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(uri))
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        })
        val md = "${start.monthValue}月${start.dayOfMonth}日${Quick.hm(start.hour, start.minute)}"
        return Res("已加到行事曆：$md $title", speak = true, info = true)
    }

    /** 主要的 Google 行事曆優先，其次任何可寫入的。 */
    private fun calendarId(c: Context): Long? {
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val sel = "${CalendarContract.Calendars.VISIBLE}=1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        var best: Long? = null
        var bestRank = -1
        c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, sel, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val rank = (if (cur.getInt(1) == 1) 2 else 0) + (if (cur.getString(2) == "com.google") 1 else 0)
                if (rank > bestRank) {
                    bestRank = rank
                    best = cur.getLong(0)
                }
            }
        }
        return best
    }

    private fun openApp(c: Context, p: JSONObject): Res {
        val names = ArrayList<String>()
        p.optJSONArray("names")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { names.add(it) }
        }
        p.str("name")?.let { names.add(it) }
        if (names.isEmpty()) return fail("要打開哪個 App？")
        val hit = Apps.find(c, names)
        if (hit == null || hit.second < 60) return fail("找不到「${names.first()}」這個 App")
        return Res("打開${hit.first.label}", intent = Apps.launchIntent(hit.first), leave = true)
    }

    private fun navigate(c: Context, p: JSONObject): Res {
        val q = p.str("q") ?: return fail("要去哪裡？")
        val i = if (installed(c, MAPS)) {
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q))).setPackage(MAPS)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))
        }
        return Res("導航到$q", intent = i, leave = true)
    }

    private fun call(c: Context, p: JSONObject): Res {
        val name = p.str("name") ?: return fail("要打給誰？")
        val digits = name.replace(Regex("[\\s-]"), "")
        if (digits.matches(Regex("\\+?\\d{3,15}"))) return Res("撥打 $digits", intent = dial(c, digits), leave = true)
        if (!has(c, Manifest.permission.READ_CONTACTS)) return needPerm(c, "聯絡人")
        val hits = Contacts.find(c, name)
        if (hits.isEmpty()) return fail("找不到聯絡人「$name」")
        val top = hits[0]
        // 名字完全相同（或同音）而且只有一位才直接撥；其他列出來讓使用者點，避免打錯人
        val sure = top.score >= 95 && (hits.size == 1 || hits[1].score < top.score)
        if (sure) return Res("打給${top.name}", intent = dial(c, top.number), leave = true)
        val list = hits.take(4).map { "${it.name}　${it.number}" to dial(c, it.number) }
        return Res("要打給哪一位？", speak = true, choices = list)
    }

    private fun dial(c: Context, number: String): Intent {
        val direct = has(c, Manifest.permission.CALL_PHONE)
        return Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    }

    private fun flashlight(c: Context, p: JSONObject): Res {
        val want = if (p.has("on")) p.optBoolean("on") else null
        return try {
            if (Torch.set(c, want)) Res("手電筒開了") else Res("手電筒關了")
        } catch (e: Exception) {
            Log.w(TAG, "torch", e)
            fail("手電筒現在不能用，相機可能正在使用")
        }
    }

    private fun volume(c: Context, p: JSONObject): Res {
        val am = c.getSystemService(AudioManager::class.java)
        val (stream, label) = when (p.optString("stream")) {
            "ring" -> AudioManager.STREAM_RING to "鈴聲"
            "alarm" -> AudioManager.STREAM_ALARM to "鬧鐘"
            else -> AudioManager.STREAM_MUSIC to "媒體"
        }
        val flags = AudioManager.FLAG_SHOW_UI
        val max = am.getStreamMaxVolume(stream)
        return try {
            when {
                p.has("mute") -> {
                    val mute = p.optBoolean("mute")
                    am.adjustStreamVolume(stream, if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, flags)
                    Res(if (mute) "${label}靜音" else "取消${label}靜音")
                }
                p.has("level") -> {
                    val lv = p.optInt("level").coerceIn(0, 100)
                    am.setStreamVolume(stream, (max * lv + 50) / 100, flags)
                    Res("${label}音量 $lv%")
                }
                else -> {
                    val d = p.optInt("delta", 1).sign
                    val step = maxOf(1, (max * 15 + 50) / 100)
                    val v = (am.getStreamVolume(stream) + d * step).coerceIn(0, max)
                    am.setStreamVolume(stream, v, flags)
                    Res("${label}音量 ${v * 100 / max}%")
                }
            }
        } catch (e: SecurityException) {
            fail("勿擾模式中不能調整鈴聲，請先授權勿擾權限")
        }
    }

    /** 響鈴模式：響鈴、震動、靜音。切到或切出靜音要有勿擾權限（系統規定）。 */
    private fun ringer(c: Context, p: JSONObject): Res {
        val am = c.getSystemService(AudioManager::class.java)
        val (mode, label) = when (p.optString("mode")) {
            "silent" -> AudioManager.RINGER_MODE_SILENT to "靜音模式"
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE to "震動模式"
            else -> AudioManager.RINGER_MODE_NORMAL to "響鈴模式"
        }
        return try {
            am.ringerMode = mode
            Res("切到$label")
        } catch (e: SecurityException) {
            fail("需要「勿擾權限」才能切換$label", listOf("前往授權" to Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
        }
    }

    private fun brightness(c: Context, p: JSONObject): Res {
        if (!Settings.System.canWrite(c)) {
            return fail(
                "需要「修改系統設定」權限才能調亮度",
                listOf("前往授權" to Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}"))),
            )
        }
        val cr = c.contentResolver
        val auto = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        // 自動亮度開著時系統會蓋掉手動值，所以改成手動
        if (auto) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val now = toPct(Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
        val pct = if (p.has("level")) {
            p.optInt("level").coerceIn(0, 100)
        } else {
            (now + p.optInt("delta", 1).sign * 20).coerceIn(0, 100)
        }
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, fromPct(pct))
        return Res("亮度 $pct%" + if (auto) "（已關閉自動亮度）" else "")
    }

    // 系統亮度拉桿是感知曲線，用 2.2 次方換算，說 50% 才會接近拉桿一半
    private fun fromPct(p: Int) = (255 * (p / 100.0).pow(2.2)).roundToInt().coerceIn(1, 255)
    private fun toPct(v: Int) = (100 * (v / 255.0).pow(1 / 2.2)).roundToInt().coerceIn(0, 100)

    private fun dnd(c: Context, p: JSONObject): Res {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            return fail("需要「勿擾權限」才能切換勿擾模式", listOf("前往授權" to Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
        }
        val on = p.optBoolean("on", true)
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return Res(if (on) "勿擾模式開了" else "勿擾模式關了")
    }

    private fun media(c: Context, p: JSONObject): Res {
        val (code, label) = when (p.optString("cmd")) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE to "暫停"
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT to "下一首"
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to "上一首"
            else -> KeyEvent.KEYCODE_MEDIA_PLAY to "播放"
        }
        val am = c.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return Res(label)
    }

    private fun play(c: Context, p: JSONObject): Res {
        val q = p.str("q") ?: return media(c, JSONObject().put("cmd", "play"))
        val app = p.str("app")?.lowercase().orEmpty()
        if (app.contains("youtube") && !app.contains("music")) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
            if (installed(c, YT)) i.setPackage(YT)
            return Res("在 YouTube 找「$q」", intent = i, leave = true)
        }
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, q)
        val pkg = when {
            app.contains("spotify") && installed(c, SPOTIFY) -> SPOTIFY
            installed(c, YTM) -> YTM
            installed(c, SPOTIFY) -> SPOTIFY
            else -> null
        }
        pkg?.let { i.setPackage(it) }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(q)))
        return Res("播放「$q」", intent = i, leave = true, fallback = web)
    }

    private fun settings(p: JSONObject): Res {
        val (action, label) = when (p.optString("page")) {
            "wifi" -> Settings.Panel.ACTION_WIFI to "Wi-Fi"
            "internet" -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY to "網路"
            "nfc" -> Settings.Panel.ACTION_NFC to "NFC"
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS to "藍牙"
            "display" -> Settings.ACTION_DISPLAY_SETTINGS to "螢幕"
            "sound" -> Settings.ACTION_SOUND_SETTINGS to "音效"
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY to "電池"
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "定位"
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS to "應用程式"
            else -> Settings.ACTION_SETTINGS to ""
        }
        return Res("打開${label}設定", intent = Intent(action), leave = true, fallback = Intent(Settings.ACTION_SETTINGS))
    }

    private fun search(p: JSONObject): Res {
        val q = p.str("q") ?: return fail("要搜尋什麼？")
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
        return Res("搜尋「$q」", intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q), leave = true, fallback = web)
    }

    private fun weather(c: Context, p: JSONObject): Res {
        val day = p.optInt("day", 0).coerceIn(0, 2)
        val place = p.str("place").orEmpty()
        var lat = p.optDouble("lat", Double.NaN)
        var lon = p.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            if (!has(c, Manifest.permission.ACCESS_COARSE_LOCATION)) return needPerm(c, "位置")
            val loc = Place.current(c) ?: return fail("拿不到目前位置，請直接說地點，例如「台中明天天氣」")
            lat = loc.latitude
            lon = loc.longitude
        }
        val url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f", lat, lon) +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=3"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 6000
        val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val d = j.getJSONObject("daily")
        val hi = deg(d.getJSONArray("temperature_2m_max").optDouble(day))
        val lo = deg(d.getJSONArray("temperature_2m_min").optDouble(day))
        val rain = d.getJSONArray("precipitation_probability_max").optInt(day, -1)
        val sb = StringBuilder(place)
        if (day == 0) {
            val cur = j.getJSONObject("current")
            sb.append("現在${deg(cur.optDouble("temperature_2m"))}度，${wmo(cur.optInt("weather_code", -1))}，今天")
        } else {
            sb.append(if (day == 1) "明天" else "後天").append(wmo(d.getJSONArray("weather_code").optInt(day, -1))).append("，")
        }
        sb.append("${lo}到${hi}度")
        if (rain >= 0) sb.append("，降雨機率$rain%")
        return Res(sb.toString(), speak = true, info = true)
    }

    private fun deg(v: Double) = if (v.isNaN()) "?" else v.roundToInt().toString()

    /** WMO 天氣代碼 → 中文。 */
    private fun wmo(code: Int) = when (code) {
        0 -> "晴天"
        1 -> "大致晴朗"
        2 -> "多雲"
        3 -> "陰天"
        45, 48 -> "有霧"
        51, 53, 55, 56, 57 -> "毛毛雨"
        61, 66 -> "小雨"
        63 -> "下雨"
        65, 67 -> "大雨"
        71, 73, 75, 77 -> "下雪"
        80, 81 -> "陣雨"
        82 -> "大陣雨"
        85, 86 -> "陣雪"
        95 -> "雷雨"
        96, 99 -> "雷雨夾冰雹"
        else -> "天氣不明"
    }
}

/** 手電筒：記住目前狀態，才能「切換」。 */
object Torch {
    @Volatile private var on = false
    private var id: String? = null
    private var ready = false

    @Synchronized
    fun init(c: Context) {
        if (ready) return
        val cm = c.applicationContext.getSystemService(CameraManager::class.java) ?: return
        id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) on = enabled
            }
        }, Handler(Looper.getMainLooper()))
        ready = true
    }

    /** want=null 表示切換；回傳之後的狀態。 */
    fun set(c: Context, want: Boolean?): Boolean {
        init(c)
        val cid = id ?: throw IllegalStateException("這支手機沒有閃光燈")
        val target = want ?: !on
        c.applicationContext.getSystemService(CameraManager::class.java).setTorchMode(cid, target)
        on = target
        return target
    }
}

/** 目前大概位置（只要粗略位置權限）：先用最近的紀錄，太舊才現抓。 */
object Place {
    fun current(c: Context): Location? {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = c.getSystemService(LocationManager::class.java) ?: return null
        val last = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3_600_000L) return last
        val latch = CountDownLatch(1)
        var fresh: Location? = null
        val provider = if (runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER
        }
        try {
            lm.getCurrentLocation(provider, null, Bg.pool) { loc ->
                fresh = loc
                latch.countDown()
            }
            latch.await(4, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w("Place", "getCurrentLocation", e)
        }
        return fresh ?: last
    }
}
