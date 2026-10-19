package com.c520.shengdewoxin

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * App 主畫面，分兩頁：「設定」放要動手改的東西，「說明」放怎麼叫出來、可以說什麼、常見問題。
 * 每個項目只留一行，說明收在「?」裡；選用的區塊預設收起。介面用程式碼組，不另外加套件。
 */
class MainActivity : Activity() {
    private val ui by lazy { Ui(this) }
    private val extraPerms = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    /** check 回 null 表示讀不到狀態：只顯示按鈕，不標示完成與否。 */
    private class Step(val check: () -> Boolean?, val done: TextView, val btn: View?)

    private val steps = ArrayList<Step>()
    private lateinit var spinner: Spinner
    private lateinit var keyField: EditText
    private lateinit var modelField: EditText
    private lateinit var urlField: EditText
    private lateinit var urlBox: View
    private lateinit var testResult: TextView
    private lateinit var aiSummary: TextView
    private lateinit var ytField: EditText
    private lateinit var ytSummary: TextView
    private lateinit var permSummary: TextView
    private lateinit var nickField: EditText
    private lateinit var lookRow: LinearLayout
    private lateinit var routines: RoutineCard
    private lateinit var tabRow: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var pages: List<View>
    private var tab = 0
    private var shown: Provider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 外觀（預設淺色）交給系統；跟目前不同時系統會自動重建這個畫面
        Prefs.applyLook(this)
        setContentView(buildUi())
        fill(Prefs.provider(this))
        showTab(savedInstanceState?.getInt(KEY_TAB) ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun isAssistant() = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

    /** 系統「按住電源鍵」設定：5＝數位助理。系統不開放讀取時回 null。 */
    private fun powerKeyIsAssistant(): Boolean? =
        runCatching { Settings.Global.getInt(contentResolver, "power_button_long_press") == 5 }.getOrNull()

    private fun canWriteSettings() = Settings.System.canWrite(this)

    private fun canDnd() = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun render() {
        for (s in steps) {
            val ok = s.check()
            s.done.visibility = if (ok == true) View.VISIBLE else View.GONE
            s.btn?.visibility = if (ok == true) View.GONE else View.VISIBLE
        }
        val p = Prefs.provider(this)
        aiSummary.text = if (Prefs.key(this, p).isEmpty()) "未設定" else "已設定・" + p.label.substringBefore('（')
        ytSummary.text = if (Prefs.ytKey(this).isEmpty()) "未設定" else "已設定"
        val on = listOf(extraPerms.all(::granted), canWriteSettings(), canDnd()).count { it }
        permSummary.text = "已開 $on/3"
        routines.render()
    }

    // ---------- 外框：標題＋分頁 ----------

    private fun buildUi(): View {
        pages = listOf(buildSettings(), buildHelp())
        tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(12))
            addView(ui.text("聲得我心", 28f, bold = true))
            addView(tabRow, ui.lp(12))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            // 開頁時由捲動區自己拿焦點，不然會自動捲到輸入欄位
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Android 15 起強制滿版，內容要自己讓開狀態列與導覽列
            fitsSystemWindows = true
            addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(View(this@MainActivity).apply { setBackgroundColor(ui.col(R.color.hairline)) }, LinearLayout.LayoutParams(MATCH_PARENT, ui.dp(1)))
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
    }

    /** 分頁：選中的用主要按鈕樣式（實心），沒選的是外框；螢幕閱讀器會念「已選」。 */
    private fun showTab(i: Int) {
        tab = i.coerceIn(0, pages.size - 1)
        tabRow.removeAllViews()
        listOf("設定", "說明").forEachIndexed { idx, label ->
            val sel = idx == tab
            val b = ui.button(label, primary = sel) { if (tab != idx) showTab(idx) }
            b.contentDescription = if (sel) "$label 分頁，已選" else "$label 分頁"
            tabRow.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (idx > 0) marginStart = ui.dp(8) })
        }
        scroll.removeAllViews()
        scroll.addView(pages[tab])
        scroll.scrollTo(0, 0)
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(32))
    }

    // ---------- 「設定」分頁 ----------

    private fun buildSettings(): View {
        val p = page()
        p.addView(
            ui.card(
                "開始使用",
                step("設為預設數位助理", "在系統設定點「預設數位助理應用程式」，選「聲得我心」。長按電源鍵、背面點兩下、耳機語音鍵都靠這一步。", "設定", check = ::isAssistant) {
                    openAssistantSettings()
                },
                step("麥克風權限", "用來聽你說話；只有叫出助理的時候才會收音。", "授權", check = { granted(Manifest.permission.RECORD_AUDIO) }) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMS)
                },
                step("長按電源鍵叫出來", "在「按住電源鍵」選「數位助理」。其他叫法見「說明」分頁。", "設定", check = ::powerKeyIsAssistant) {
                    openPowerSettings()
                },
                ui.gap(8),
                ui.button("開始說話", true) {
                    if (!AssistService.show()) toast("請先完成「設為預設數位助理」")
                },
            ),
            ui.lp(16),
        )

        nickField = ui.field("例如：小明", InputType.TYPE_CLASS_TEXT, max = Prefs.NICK_MAX).apply {
            setText(Prefs.nickname(this@MainActivity))
        }
        val nickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(nickField, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(ui.button("儲存", false) { saveNick() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.dp(8) })
        }
        lookRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        renderLook()
        p.addView(
            ui.cardHelp(
                "個人化",
                "稱呼會用在聆聽提示、情境的回應和 AI 的回答；空白就不稱呼。",
                ui.labeled("怎麼稱呼你", nickRow),
                ui.labeled("外觀", lookRow),
            ),
            ui.lp(16),
        )

        routines = RoutineCard(this, ui)
        p.addView(routines.view, ui.lp(16))

        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Provider.entries.map { it.label })
            setSelection(Prefs.provider(this@MainActivity).ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    fill(Provider.entries[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        // 金鑰欄位不是登入密碼：不讓密碼管理員（Bitwarden 等）跳出自動填入，也不會提示存成密碼
        keyField = ui.field("貼上 API 金鑰", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        modelField = ui.field("模型名稱", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        urlField = ui.field("https://…/v1", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        urlBox = ui.labeled("API 網址（OpenAI 相容格式）", urlField)
        testResult = ui.text("", 14f, R.color.ink_subtle).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        aiSummary = ui.text("", 13f, R.color.ink_subtle)
        p.addView(
            ui.foldCard(
                "AI（選用）",
                aiSummary,
                false,
                ui.text("不填也能用：常用指令都在手機上直接判斷。填了金鑰，只有規則聽不懂的句子和一般問答才會送給 AI；金鑰只存在這支手機。", 14f, R.color.ink_subtle),
                ui.labeled("服務商", spinner),
                ui.labeled("API 金鑰", keyField),
                ui.labeled("模型（清空就用預設，最快）", modelField),
                urlBox,
                ui.gap(16),
                ui.row(
                    ui.button("儲存", true) {
                        if (save()) {
                            toast("已儲存")
                            render()
                        }
                    },
                    ui.button("測試連線", false) { if (save()) test() },
                ),
                ui.gap(8), testResult,
            ),
            ui.lp(16),
        )

        ytField = ui.field("貼上 YouTube 金鑰", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setText(Prefs.ytKey(this@MainActivity))
        }
        ytSummary = ui.text("", 13f, R.color.ink_subtle)
        val sha = Actions.signSha1(this)?.chunked(2)?.joinToString(":").orEmpty()
        p.addView(
            ui.foldCard(
                "YouTube 金鑰（選用）",
                ytSummary,
                false,
                ui.text("填了之後，說「播放周杰倫」會直接在 YT Music 播第一首，播完自動接相似的歌；不填就打開搜尋結果，點一下才播。金鑰免費（每天大約 100 次），只存在這支手機。", 14f, R.color.ink_subtle),
                ui.labeled("YouTube Data API 金鑰", ytField),
                ui.gap(8),
                ui.text("建議在 Google Cloud 把金鑰限制成只能用 YouTube Data API v3，應用程式選 Android：套件 $packageName，SHA-1 $sha", 13f, R.color.ink_subtle).apply {
                    setTextIsSelectable(true)
                },
                ui.gap(16),
                ui.row(
                    ui.button("儲存", true) { saveYt() },
                    ui.button("申請金鑰", false) { open(Intent(Intent.ACTION_VIEW, Uri.parse(YT_CONSOLE))) },
                ),
            ),
            ui.lp(16),
        )

        permSummary = ui.text("", 13f, R.color.ink_subtle)
        p.addView(
            ui.foldCard(
                "權限（選用）",
                permSummary,
                false,
                step("打電話、天氣、行事曆", "聯絡人、電話、大概位置、行事曆。用不到的可以不給，其他功能照常。", "授權", check = { extraPerms.all(::granted) }) {
                    requestPermissions(extraPerms, REQ_PERMS)
                },
                step("調整亮度", "允許「修改系統設定」，才能用講的調螢幕亮度。", "授權", check = ::canWriteSettings) {
                    open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                },
                step("勿擾、靜音", "允許「勿擾權限」，才能用講的開關勿擾、切換靜音模式。", "授權", check = ::canDnd) {
                    open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                },
            ),
            ui.lp(16),
        )
        return p
    }

    // ---------- 「說明」分頁 ----------

    private fun buildHelp(): View {
        val p = page()
        p.addView(
            ui.cardHelp(
                "叫出助理的方式",
                "這些叫法都不用一直開著麥克風，不會額外耗電。",
                step("長按電源鍵", "在「按住電源鍵」選「數位助理」。", "設定", check = ::powerKeyIsAssistant) { openPowerSettings() },
                step("手機背面點兩下", "打開「快速輕觸」（Quick Tap），動作選「數位助理」。螢幕亮著時，隨手點兩下就能講。", "設定") {
                    openFirst("系統設定 → 系統 → 手勢 → 快速輕觸", "com.google.android.settings.gestures.QUICK_TAP_SETTINGS")
                },
                step(
                    "主畫面小工具",
                    "一顆麥克風按鈕，點一下就開始聽。有深色、淺色兩種：桌布亮選深色，桌布暗選淺色。",
                    "深色",
                    extra = "淺色" to { pinWidget(AssistWidgetLight::class.java) },
                ) { pinWidget(AssistWidget::class.java) },
                step("鎖定畫面小工具", "先開啟鎖定畫面小工具；之後在鎖定畫面往左滑，長按進入編輯，新增「聲得我心」（深色或淺色）。", "設定") {
                    openFirst("系統設定 → 螢幕 → 鎖定畫面 → 鎖定畫面小工具", "android.settings.COMMUNAL_SETTINGS", "android.settings.LOCK_SCREEN_SETTINGS")
                },
                step("快速設定磁貼", "下拉通知列點一下就能講。", "加入") { addTile() },
                step("圖示捷徑", "長按「聲得我心」圖示，點「說話」直接開始聽；也能把「說話」拖到主畫面。"),
                step("耳機、車用語音鍵", "長按藍牙耳機或方向盤上的語音鍵（要裝置有支援）；收音目前用手機的麥克風。"),
            ),
            ui.lp(16),
        )

        p.addView(
            ui.card("可以說什麼", *EXAMPLES.mapIndexed { i, (t, b) -> ui.foldRow(t, b, i == 0) }.toTypedArray()),
            ui.lp(16),
        )
        p.addView(
            ui.card("常見問題", *FAQ.mapIndexed { i, (q, a) -> ui.foldRow(q, a, i == 0) }.toTypedArray()),
            ui.lp(16),
        )

        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        p.addView(
            ui.card(
                "關於",
                ui.text("聲得我心 $ver・免費、MIT 開源・C520", 15f),
                ui.gap(4),
                ui.text("天氣資料來自 Open-Meteo.com（CC BY 4.0）。", 13f, R.color.ink_subtle),
                ui.gap(12),
                ui.row(
                    ui.button("介紹網頁", false) { open(Intent(Intent.ACTION_VIEW, Uri.parse(SITE))) },
                    ui.button("原始碼", false) { open(Intent(Intent.ACTION_VIEW, Uri.parse(REPO))) },
                ),
            ),
            ui.lp(16),
        )
        return p
    }

    /**
     * 一個項目一行：標題、完成時的「✓」或動作按鈕、「?」；說明點「?」才展開。
     * 有 check 的會在 render() 更新：完成就藏按鈕、顯示「✓」。
     */
    private fun step(
        title: String,
        help: String,
        action: String? = null,
        check: (() -> Boolean?)? = null,
        extra: Pair<String, () -> Unit>? = null,
        onClick: (() -> Unit)? = null,
    ): View {
        val done = ui.text("✓ 已完成", 13f, R.color.success).apply { visibility = View.GONE }
        // 按鈕念成「標題：動作」，同一列兩顆（小工具的深色、淺色）也分得出來
        val btns = listOfNotNull(if (action != null && onClick != null) action to onClick else null, extra)
            .map { (label, f) -> ui.button(label, false) { f() }.apply { contentDescription = "$title：$label" } }
        val btn = btns.firstOrNull()
        val desc = ui.text(help, 14f, R.color.ink_subtle).apply {
            visibility = View.GONE
            setPadding(0, 0, 0, ui.dp(8))
        }
        if (check != null) steps.add(Step(check, done, btn))
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(56)
            addView(ui.text(title, 16f, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (check != null) addView(done, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.dp(8) })
            // 一顆按鈕放標題右邊；兩顆（小工具的深色、淺色）擠在同一行標題會斷字，改放下面一列
            if (btns.size == 1) addView(btns[0], LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.dp(8) })
            addView(ui.helpToggle(desc, title))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(head, ui.lp())
            addView(desc, ui.lp())
            if (btns.size > 1) addView(ui.row(*btns.toTypedArray()).apply { setPadding(0, 0, 0, ui.dp(8)) }, ui.lp())
        }
    }

    // ---------- 個人化 ----------

    private fun saveNick() {
        // 只留可見字元：換行、控制字元都拿掉
        val v = nickField.text.toString().filter { !it.isISOControl() }.trim()
        nickField.setText(v)
        Prefs.setNickname(this, v)
        toast(if (v.isEmpty()) "已清除稱呼" else "之後會叫你「$v」")
    }

    /** 外觀三選一：選中的用主要按鈕樣式並標「✓」。 */
    private fun renderLook() {
        lookRow.removeAllViews()
        val cur = Prefs.look(this)
        Look.entries.forEachIndexed { i, v ->
            val b = ui.button(if (v == cur) "✓ ${v.label}" else v.label, primary = v == cur) { setLook(v) }
            b.contentDescription = if (v == cur) "${v.label}，已選" else v.label
            lookRow.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { if (i > 0) marginStart = ui.dp(8) })
        }
    }

    private fun setLook(v: Look) {
        if (v == Prefs.look(this)) return
        Prefs.setLook(this, v)
        renderLook()
        // 顏色有變時系統會自動重建畫面（分頁會保留）
        Prefs.applyLook(this)
    }

    // ---------- AI 設定 ----------

    private fun fill(p: Provider) {
        if (shown == p) return
        shown = p
        keyField.setText(Prefs.key(this, p))
        modelField.setText(Prefs.model(this, p))
        modelField.hint = p.defModel.ifEmpty { "模型名稱" }
        urlField.setText(if (p == Provider.CUSTOM) Prefs.url(this, p) else "")
        urlBox.visibility = if (p == Provider.CUSTOM) View.VISIBLE else View.GONE
        testResult.text = ""
    }

    /** 驗證後存檔；不合格就提示並回 false。 */
    private fun save(): Boolean {
        val p = shown ?: return false
        val key = keyField.text.toString().trim()
        val model = modelField.text.toString().trim()
        val url = urlField.text.toString().trim()
        if (key.any { it.isWhitespace() }) {
            toast("金鑰裡有空白或換行，請重新貼上")
            return false
        }
        if (model.any { it.isWhitespace() }) {
            toast("模型名稱不能有空白")
            return false
        }
        if (p == Provider.CUSTOM && !url.startsWith("https://")) {
            toast("API 網址要用 https:// 開頭")
            return false
        }
        Prefs.save(this, p, key, model, if (p == Provider.CUSTOM) url else "")
        return true
    }

    /** YouTube 金鑰：空白就清除；Google 的金鑰只有英數字、底線、連字號。 */
    private fun saveYt() {
        val v = ytField.text.toString().trim()
        if (v.isNotEmpty() && !Regex("^[A-Za-z0-9_-]{20,100}$").matches(v)) {
            toast("金鑰格式不對，請重新貼上")
            return
        }
        Prefs.setYtKey(this, v)
        toast(if (v.isEmpty()) "已清除 YouTube 金鑰" else "已儲存")
        render()
    }

    private fun test() {
        testResult.setTextColor(ui.col(R.color.ink_subtle))
        testResult.text = "測試中…"
        val t0 = System.currentTimeMillis()
        Bg.pool.execute {
            val r = Llm.ask(this, "現在幾點？")
            val sec = (System.currentTimeMillis() - t0) / 1000.0
            runOnUiThread {
                val out = r.text
                if (out != null) {
                    val say = Plan.parse(out).say.ifBlank { out.take(60) }
                    testResult.text = String.format(Locale.ROOT, "✓ 連線成功，%.2f 秒：%s", sec, say)
                    testResult.setTextColor(ui.col(R.color.success))
                } else {
                    testResult.text = "⚠ ${r.error}" + (r.detail?.let { "\n$it" } ?: "")
                    testResult.setTextColor(ui.col(R.color.ink))
                }
                render()
            }
        }
    }

    // ---------- 系統設定頁 ----------

    private fun openAssistantSettings() {
        toast("點「預設數位助理應用程式」，選「聲得我心」")
        openFirst(
            "系統設定 → 應用程式 → 預設應用程式 → 數位助理應用程式",
            Settings.ACTION_VOICE_INPUT_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        )
    }

    private fun openPowerSettings() =
        openFirst("系統設定 → 系統 → 手勢 → 按住電源鍵", "android.settings.ACTION_POWER_MENU_SETTINGS")

    /** 依序試這些設定頁，第一個打得開的就用；都打不開就開系統設定首頁，並提示路徑。 */
    private fun openFirst(path: String, vararg actions: String) {
        for (a in actions) {
            try {
                startActivity(Intent(a))
                return
            } catch (e: ActivityNotFoundException) {
                // 換下一個
            } catch (e: SecurityException) {
                // 這支手機的設定頁不對外開放，換下一個
            }
        }
        toast("請到 $path")
        open(Intent(Settings.ACTION_SETTINGS))
    }

    private fun open(i: Intent) {
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            toast("找不到能開啟的 App")
        }
    }

    private fun pinWidget(cls: Class<out AssistWidget>) {
        val mgr = getSystemService(AppWidgetManager::class.java)
        if (mgr == null || !mgr.isRequestPinAppWidgetSupported) {
            toast("請長按主畫面空白處 → 小工具 → 找「聲得我心」，把深色或淺色拖到主畫面")
            return
        }
        mgr.requestPinAppWidget(ComponentName(this, cls), null, null)
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT < 33) {
            toast("請下拉通知列 → 編輯，把「聲得我心」拖進快速設定")
            return
        }
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, AssistTile::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, R.drawable.ic_mic),
            mainExecutor,
        ) { result ->
            toast(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已加入快速設定"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "快速設定裡已經有了"
                    else -> "沒有加入；之後也可以在快速設定的編輯畫面手動加"
                }
            )
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
        // 被拒絕兩次後系統不再跳詢問視窗，只能到 App 資訊頁手動開
        val blocked = permissions.indices.any { i ->
            grantResults.getOrNull(i) == PackageManager.PERMISSION_DENIED && !shouldShowRequestPermissionRationale(permissions[i])
        }
        if (blocked) {
            toast("有權限被拒絕，請在「權限」裡手動開啟")
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    companion object {
        private const val REQ_PERMS = 1
        private const val KEY_TAB = "tab"
        private const val SITE = "https://c520.pages.dev/shengdewoxin/"
        private const val REPO = "https://github.com/bb6800182-boop/shengdewoxin"
        private const val YT_CONSOLE = "https://console.cloud.google.com/apis/library/youtube.googleapis.com"

        private val EXAMPLES = listOf(
            "鬧鐘、計時" to "「明天早上七點叫我」「計時十分鐘」「十分鐘後提醒我關火」",
            "聲音與螢幕" to "「取消靜音」「震動模式」「音量調到五十」「開勿擾」「螢幕亮一點」「打開手電筒」",
            "App 與音樂" to "「我要聽音樂」（接著上次的歌放）「播放我喜歡的歌」「我想聽周杰倫」（填了 YouTube 金鑰會直接播，沒填就打開搜尋結果）「下一首」「暫停」「打開 LINE」「打開 Wi-Fi 設定」",
            "導航、打電話" to "「導航到台北車站」「打給媽媽」（名字相近的人會列出來讓你選）",
            "時間與天氣" to "「現在幾點」「今天星期幾」「明天會下雨嗎」",
            "情境" to "「我到家了」，或你在「設定」裡自己加的句子",
            "要填 AI 金鑰才會的" to "一般問答、有說地點的天氣（「台中明天天氣」）、指定日期的提醒（「提醒我週五下午三點開會」）",
        )

        private val FAQ = listOf(
            "為什麼不能喊名字叫出來？" to "手機裡專門聽喚醒詞的低耗電晶片只開放給系統內建的助理；第三方 App 要一直開著麥克風才能喊名字，很耗電。所以改用長按電源鍵、背面點兩下、小工具這些不耗電的叫法。",
            "一定要填 AI 金鑰嗎？" to "不用。「可以說什麼」裡大部分的指令都在手機上直接判斷。填了金鑰，只有規則聽不懂的句子才會送出去。",
            "我說的話會被上傳嗎？" to "語音辨識由手機上的 Google 語音服務處理。聲得我心不收集資料；填了 AI 金鑰時，只有規則聽不懂的那一句會送給你選的 AI 服務；填了 YouTube 金鑰時，指定的歌名會送到 YouTube 查詢；查天氣時會送出大概位置。",
            "鎖定時要打開別的 App 怎麼辦？" to "導航、開 App 這類要打開其他 App 的指令，在鎖定畫面會先請你解鎖，這是 Android 的安全規定。",
        )
    }
}
