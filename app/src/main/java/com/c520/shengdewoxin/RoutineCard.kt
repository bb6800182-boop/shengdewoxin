package com.c520.shengdewoxin

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

/** 設定頁的「情境」卡片：列出情境，點一下就地展開編輯（不跳對話框）。 */
class RoutineCard(private val a: Activity, private val ui: Ui) {
    private val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private var editing: String? = null // 正在編輯的情境 id；NEW＝新增中

    val view: View = ui.cardHelp(
        "情境",
        "說出觸發語句，就依序做好幾件事，例如說「我到家了」就取消靜音、調大音量。回應裡的 {稱呼} 會換成你的稱呼。",
        list,
    )

    fun render() {
        list.removeAllViews()
        for (r in Routines.all(a)) list.addView(if (editing == r.id) editor(r) else row(r), ui.lp(12))
        if (editing == NEW) {
            list.addView(editor(null), ui.lp(12))
        } else {
            list.addView(
                ui.button("＋ 新增情境", primary = false) {
                    editing = NEW
                    render()
                },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.dp(12) },
            )
        }
    }

    /** 收合時的一列：觸發語句＋一行摘要（回應、做幾件事）；整列可點開編輯。 */
    private fun row(r: Routine) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = ui.pressable(ui.box(R.color.surface_2, R.color.hairline, 12))
        setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
        minimumHeight = ui.dp(48)
        isClickable = true
        isFocusable = true
        val title = r.triggers.joinToString("、")
        val sub = listOfNotNull(
            r.reply.takeIf { it.isNotBlank() }?.let { "「${Routines.fill(a, it)}」" },
            if (r.commands.isNotEmpty()) "做 ${r.commands.size} 件事" else null,
        ).joinToString("・")
        contentDescription = "編輯情境：$title。$sub"
        addView(ui.text(title, 16f, bold = true))
        if (sub.isNotEmpty()) addView(ui.text(sub, 14f, R.color.ink_subtle), ui.lp(2))
        setOnClickListener {
            editing = r.id
            render()
        }
    }

    private fun editor(start: Routine?): View {
        var cur = start // 儲存後換成新的內容，再按儲存才不會重複新增
        val triggers = ui.field("例如：我到家了、我回來了", InputType.TYPE_CLASS_TEXT, max = 120)
        val reply = ui.field("例如：{稱呼}，歡迎回家", InputType.TYPE_CLASS_TEXT, max = 80)
        val cmds = ui.field("一行一句，例如：\n取消靜音\n媒體音量調到 70\n關閉勿擾", InputType.TYPE_CLASS_TEXT, max = 600, lines = 4)
        start?.let {
            triggers.setText(it.triggers.joinToString("、"))
            reply.setText(it.reply)
            cmds.setText(it.commands.joinToString("\n"))
        }
        val result = ui.text("", 14f, R.color.ink_subtle).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        val buttons = ArrayList<Button>()
        fun save(test: Boolean) {
            val ts = triggers.text.toString().split(Regex("[、，,\\n]")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val rp = reply.text.toString().trim()
            val cs = cmds.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
            validate(cur?.id, ts, rp, cs)?.let {
                show(result, "⚠ $it", R.color.ink)
                return
            }
            show(result, "翻譯指令中…", R.color.ink_subtle)
            buttons.forEach { it.isEnabled = false }
            val app = a.applicationContext
            Bg.pool.execute {
                val compiled = cs.map { Routines.compile(app, it) }
                a.runOnUiThread {
                    if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                    buttons.forEach { it.isEnabled = true }
                    val bad = compiled.withIndex().filter { it.value.error != null }
                    if (bad.isNotEmpty()) {
                        show(result, bad.joinToString("\n") { "⚠ 第 ${it.index + 1} 行「${cs[it.index]}」：${it.value.error}" }, R.color.ink)
                        return@runOnUiThread
                    }
                    val old = cur
                    val r = Routine(old?.id ?: UUID.randomUUID().toString(), ts, rp, cs, compiled.flatMap { it.acts })
                    val all = Routines.all(a)
                    Routines.save(a, if (old == null) all + r else all.map { if (it.id == old.id) r else it })
                    cur = r
                    editing = r.id
                    val what = r.acts.joinToString("\n") { "・" + Actions.describe(it) }
                    show(result, "✓ 已儲存" + if (what.isEmpty()) "" else "，會做：\n$what", R.color.success)
                    if (test && !AssistService.show(Bundle().apply { putString(AssistSession.ARG_ROUTINE, r.id) })) {
                        toast("請先完成「設為預設數位助理」才能試跑")
                    }
                }
            }
        }
        val saveBtn = ui.button("儲存", true) { save(test = false) }
        val testBtn = ui.button("儲存並試跑", false) { save(test = true) }
        val closeBtn = ui.button("收起", false) {
            editing = null
            render()
        }
        buttons += listOf(saveBtn, testBtn, closeBtn)
        val second = ArrayList<View>()
        second += closeBtn
        start?.let { second += deleteButton(it) }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.box(R.color.surface_1, R.color.hairline_strong, 12)
            setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(16))
            addView(ui.labeled("觸發語句（多個用「、」分開）", triggers))
            addView(ui.labeled("回應（可用 {稱呼}）", reply))
            addView(ui.labeled("要做的事（一行一句）", cmds))
            addView(result, ui.lp(8))
            addView(ui.row(saveBtn, testBtn), ui.lp(12))
            addView(ui.row(*second.toTypedArray()), ui.lp(8))
        }
    }

    /** 刪除要按兩次：第一次變成「再按一次確認刪除」，4 秒內沒按就恢復。 */
    private fun deleteButton(r: Routine): Button {
        var armed = false
        lateinit var b: Button
        b = ui.button("刪除", false) {
            if (!armed) {
                armed = true
                b.text = "再按一次確認刪除"
                b.postDelayed({
                    armed = false
                    b.text = "刪除"
                }, 4000)
            } else {
                Routines.save(a, Routines.all(a).filter { it.id != r.id })
                editing = null
                render()
                toast("已刪除情境")
            }
        }
        return b
    }

    private fun validate(id: String?, triggers: List<String>, reply: String, cmds: List<String>): String? {
        if (triggers.isEmpty()) return "請填觸發語句"
        if (triggers.size > Routines.MAX_TRIGGERS) return "觸發語句最多 ${Routines.MAX_TRIGGERS} 個"
        triggers.firstOrNull { Routines.key(it).length < 2 }?.let { return "觸發語句「$it」太短，至少要兩個字" }
        triggers.firstOrNull { it.length > 20 }?.let { return "觸發語句「$it」太長，請在 20 字以內" }
        val others = Routines.all(a).filter { it.id != id }.flatMap { it.triggers }.map { Routines.key(it) }.toSet()
        triggers.firstOrNull { Routines.key(it) in others }?.let { return "「$it」已經用在別的情境" }
        if (reply.isEmpty() && cmds.isEmpty()) return "回應和要做的事至少填一個"
        if (cmds.size > Routines.MAX_COMMANDS) return "要做的事最多 ${Routines.MAX_COMMANDS} 行"
        cmds.firstOrNull { it.length > 60 }?.let { return "「${it.take(10)}…」太長，一行請在 60 字以內" }
        return null
    }

    private fun show(v: TextView, text: String, color: Int) {
        v.text = text
        v.setTextColor(ui.col(color))
        v.visibility = View.VISIBLE
    }

    private fun toast(msg: String) = Toast.makeText(a, msg, Toast.LENGTH_LONG).show()

    private companion object {
        const val NEW = "new"
    }
}
