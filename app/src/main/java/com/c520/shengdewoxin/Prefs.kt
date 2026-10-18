package com.c520.shengdewoxin

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences

/** AI 服務商與預設模型。Claude 走 Anthropic 格式，其他走 OpenAI 相容格式。 */
enum class Provider(val label: String, val defModel: String, val defUrl: String) {
    CLAUDE("Claude（Anthropic）", "claude-haiku-5-5", "https://api.anthropic.com/v1/messages"),
    DEEPSEEK("DeepSeek", "deepseek-flash", "https://api.deepseek.com/chat/completions"),
    OPENAI("OpenAI", "gpt-6-luna", "https://api.openai.com/v1/chat/completions"),
    CUSTOM("其他（OpenAI 相容）", "", "");

    val anthropic: Boolean get() = this == CLAUDE
}

/** 外觀：淺色（預設）、深色、跟隨系統。 */
enum class Look(val label: String) {
    LIGHT("淺色"),
    DARK("深色"),
    SYSTEM("跟隨系統"),
}

/** 設定只存在本機（App 私有空間、不備份），金鑰不會離開這支手機。 */
object Prefs {
    const val NICK_MAX = 20

    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun provider(c: Context): Provider =
        Provider.entries.firstOrNull { it.name == sp(c).getString("provider", null) } ?: Provider.CLAUDE

    fun key(c: Context, p: Provider): String = sp(c).getString("key_${p.name}", null).orEmpty().trim()

    fun model(c: Context, p: Provider): String =
        sp(c).getString("model_${p.name}", null).orEmpty().trim().ifEmpty { p.defModel }

    fun url(c: Context, p: Provider): String =
        sp(c).getString("url_${p.name}", null).orEmpty().trim().ifEmpty { p.defUrl }

    /** OpenAI 相容服務只填到 /v1 時，自動補上 /chat/completions。 */
    fun endpoint(c: Context, p: Provider): String {
        val u = url(c, p).trimEnd('/')
        return if (p.anthropic || u.isEmpty() || u.endsWith("/chat/completions")) u else "$u/chat/completions"
    }

    fun save(c: Context, p: Provider, key: String, model: String, url: String) {
        sp(c).edit()
            .putString("provider", p.name)
            .putString("key_${p.name}", key.trim())
            .putString("model_${p.name}", model.trim())
            .putString("url_${p.name}", url.trim())
            .apply()
    }

    /** 助理怎麼稱呼你；空白就不稱呼。 */
    fun nickname(c: Context): String = sp(c).getString("nickname", null).orEmpty().trim()

    fun setNickname(c: Context, v: String) {
        sp(c).edit().putString("nickname", v.trim().take(NICK_MAX)).apply()
    }

    fun look(c: Context): Look = Look.entries.firstOrNull { it.name == sp(c).getString("look", null) } ?: Look.LIGHT

    /** 把外觀交給系統（系統會記住這個 App 的深淺色）；沒選過就用淺色。 */
    fun applyLook(c: Context) {
        c.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (look(c)) {
                Look.LIGHT -> UiModeManager.MODE_NIGHT_NO
                Look.DARK -> UiModeManager.MODE_NIGHT_YES
                Look.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
    }

    fun setLook(c: Context, v: Look) {
        sp(c).edit().putString("look", v.name).apply()
    }

    /** 情境清單（JSON），由 Routines 讀寫。 */
    fun routinesJson(c: Context): String? = sp(c).getString("routines", null)

    fun setRoutinesJson(c: Context, v: String) {
        sp(c).edit().putString("routines", v).apply()
    }
}
