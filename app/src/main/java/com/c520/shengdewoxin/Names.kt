package com.c520.shengdewoxin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.icu.text.Transliterator
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.speech.RecognitionService
import kotlin.math.abs

/** 名稱比對：先比原字，再比拼音，同音字、辨識錯字也找得到。拼音用系統內建 ICU，不加套件。 */
object Names {
    private val toLatin: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin/Names; Latin-ASCII; Any-Lower") }.getOrNull()
    }
    private val junk = Regex("[\\s\\p{P}\\p{S}]")
    private val nonAlnum = Regex("[^a-z0-9]")

    fun norm(s: String): String = s.lowercase().replace(junk, "")

    @Synchronized
    fun pinyin(s: String): String {
        val t = toLatin?.let { runCatching { it.transliterate(s) }.getOrNull() } ?: s
        return t.lowercase().replace(nonAlnum, "")
    }

    /** 只比原字：完全相同 100、開頭相同 85、包含 75、其他 0。 */
    fun keyScore(q: String, key: String): Int = when {
        q.isEmpty() || key.isEmpty() -> 0
        key == q -> 100
        q.length >= 2 && (key.startsWith(q) || (key.length >= 2 && q.startsWith(key))) -> 85
        q.length >= 2 && key.contains(q) -> 75
        else -> 0
    }
}

/** 手機上的 App 清單（快取 10 分鐘），用名稱或拼音找 App。 */
object Apps {
    class App(val label: String, val pkg: String, val cls: String, val key: String, val py: String)

    private var cache: List<App> = emptyList()
    private var at = 0L

    @Synchronized
    fun all(c: Context): List<App> {
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - at < 600_000) return cache
        val pm = c.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        cache = pm.queryIntentActivities(launcher, 0).map {
            val label = it.loadLabel(pm).toString()
            App(label, it.activityInfo.packageName, it.activityInfo.name, Names.norm(label), Names.pinyin(label))
        }
        at = now
        return cache
    }

    /** 回傳最像的 App 與分數（0~100）。 */
    fun find(c: Context, names: List<String>): Pair<App, Int>? {
        var best: App? = null
        var bestScore = 0
        val list = all(c)
        for (n in names) {
            val k = Names.norm(n)
            if (k.isEmpty()) continue
            val py = Names.pinyin(n)
            for (a in list) {
                var s = Names.keyScore(k, a.key)
                if (s < 95 && py.length >= 2 && a.py == py) s = 95
                if (s == 0 && py.length >= 4 && a.py.contains(py)) s = 65
                if (s == 0 && k.length >= 3 && k.all { it in 'a'..'z' } && a.pkg.contains(k)) s = 70
                if (s > bestScore) {
                    bestScore = s
                    best = a
                }
            }
        }
        return best?.let { it to bestScore }
    }

    fun launchIntent(a: App): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(a.pkg, a.cls)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** 聯絡人比對：每位聯絡人只留一支號碼（主要號碼 > 手機 > 其他）。 */
object Contacts {
    class Hit(val name: String, val number: String, val score: Int)

    fun find(c: Context, query: String): List<Hit> {
        val q = Names.norm(query)
        if (q.isEmpty()) return emptyList()
        val qPy = Names.pinyin(query)
        val best = HashMap<Long, Pair<Int, Hit>>()
        val proj = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.IS_SUPER_PRIMARY)
        c.contentResolver.query(Phone.CONTENT_URI, proj, null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(1) ?: continue
                val number = cur.getString(2) ?: continue
                val key = Names.norm(name)
                var s = Names.keyScore(q, key)
                // 字數差不多才比拼音（同音字、辨識錯字），不用每筆都轉拼音
                if (s < 95 && qPy.length >= 2 && abs(key.length - q.length) <= 1 && Names.pinyin(name) == qPy) s = 95
                if (s == 0) continue
                val rank = when {
                    cur.getInt(4) != 0 -> 3
                    cur.getInt(3) == Phone.TYPE_MOBILE -> 2
                    else -> 1
                }
                val id = cur.getLong(0)
                if (rank > (best[id]?.first ?: 0)) best[id] = rank to Hit(name, number, s)
            }
        }
        return best.values.map { it.second }.sortedByDescending { it.score }
    }
}

/** 手機上的語音辨識服務（排除自己），Google 的排前面。 */
object Recognizers {
    private val prefer = listOf("com.google.android.tts", "com.google.android.googlequicksearchbox", "com.google.android.as")

    fun candidates(c: Context): List<ComponentName> =
        c.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
            .filter { it.packageName != c.packageName }
            .sortedBy { prefer.indexOf(it.packageName).let { i -> if (i < 0) prefer.size else i } }
}
