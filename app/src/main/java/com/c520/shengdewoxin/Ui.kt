package com.c520.shengdewoxin

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 程式碼組介面的小工具（設定頁、情境卡片、助理面板共用）。
 * 顏色一律引用 colors.xml 的設計 token（淺色、深色各一份），圓角與間距照 DESIGN.md。
 */
class Ui(private val c: Context) {
    fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt()

    fun col(id: Int) = c.getColor(id)

    fun box(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(col(fill))
        setStroke(dp(1), col(stroke))
        cornerRadius = dp(radius).toFloat()
    }

    fun pressable(d: Drawable) = RippleDrawable(ColorStateList.valueOf(col(R.color.hairline_strong)), d, null)

    /** 透明底、按下有漣漪：整列可點的標題列用。 */
    private fun clear() = pressable(GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = dp(12).toFloat()
    })

    fun text(s: CharSequence, sp: Float, color: Int = R.color.ink, bold: Boolean = false) = TextView(c).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(col(color))
        setLineSpacing(0f, 1.25f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** 膠囊按鈕：主要用 primary 底；次要用 surface-2 底＋hairline-strong 框。高度至少 48dp，好點。 */
    fun button(label: String, primary: Boolean, onClick: () -> Unit) = Button(c).apply {
        text = label
        isAllCaps = false
        stateListAnimator = null
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(col(if (primary) R.color.on_primary else R.color.ink))
        background = pressable(
            if (primary) {
                GradientDrawable().apply {
                    setColor(col(R.color.primary))
                    cornerRadius = dp(24).toFloat()
                }
            } else {
                box(R.color.surface_2, R.color.hairline_strong, 24)
            }
        )
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = dp(64)
        minimumWidth = dp(64)
        setPadding(dp(18), 0, dp(18), 0)
        setOnClickListener { onClick() }
    }

    fun field(hint: String, type: Int, max: Int = 0, lines: Int = 1) = EditText(c).apply {
        this.hint = hint
        inputType = if (lines > 1) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        textSize = 16f
        setTextColor(col(R.color.ink))
        setHintTextColor(col(R.color.ink_subtle))
        background = box(R.color.surface_2, R.color.hairline_strong, 12)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minHeight = dp(48)
        if (lines > 1) {
            minLines = lines
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
        }
        if (max > 0) filters = arrayOf(InputFilter.LengthFilter(max))
    }

    fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    fun gap(h: Int) = View(c).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    /** 欄位標題＋欄位；用 labelFor 讓螢幕閱讀器念出欄位名稱。 */
    fun labeled(label: String, v: View) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, 0)
        if (v.id == View.NO_ID) v.id = View.generateViewId()
        addView(text(label, 13f, R.color.ink_subtle).apply { labelFor = v.id })
        addView(v, lp(4))
    }

    /** 卡片：surface-1 底、hairline 框、16dp 圓角；有 help 時標題右邊多一顆「?」，點了才展開說明。 */
    fun card(title: String, vararg views: View) = cardHelp(title, null, *views)

    fun cardHelp(title: String, help: String?, vararg views: View) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = box(R.color.surface_1, R.color.hairline, 16)
        setPadding(dp(20), dp(12), dp(20), dp(16))
        val head = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(32)
            addView(text(title, 13f, R.color.ink_subtle), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        addView(head, lp())
        if (help != null) {
            val desc = text(help, 14f, R.color.ink_subtle).apply { visibility = View.GONE }
            head.addView(helpToggle(desc, title))
            addView(desc, lp(4))
        }
        for (v in views) if (v.layoutParams != null) addView(v) else addView(v, lp())
    }

    /** 「?」按鈕：點了展開或收起說明文字 desc（預設收起，畫面比較清爽）。 */
    fun helpToggle(desc: TextView, about: String) = TextView(c).apply {
        text = "?"
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(col(R.color.ink_subtle))
        background = pressable(box(R.color.surface_1, R.color.hairline_strong, 22))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(8) }
        contentDescription = "${about}的說明"
        stateDescription = "已收合"
        setOnClickListener {
            val open = desc.visibility != View.VISIBLE
            desc.visibility = if (open) View.VISIBLE else View.GONE
            stateDescription = if (open) "已展開" else "已收合"
        }
    }

    private fun chevron(open: Boolean, size: Int) = ImageView(c).apply {
        setImageResource(R.drawable.ic_chevron)
        imageTintList = ColorStateList.valueOf(col(R.color.ink_subtle))
        rotation = if (open) 180f else 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    /** 可收合的卡片：點標題列展開或收起；summary 是收起時看得到的摘要（例如「未設定」）。 */
    fun foldCard(title: String, summary: TextView?, open: Boolean, vararg views: View) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = box(R.color.surface_1, R.color.hairline, 16)
        setPadding(dp(20), dp(4), dp(20), dp(8))
        val body = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (open) View.VISIBLE else View.GONE
            setPadding(0, 0, 0, dp(8))
            for (v in views) if (v.layoutParams != null) addView(v) else addView(v, lp())
        }
        val chev = chevron(open, 24)
        val head = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            isClickable = true
            isFocusable = true
            background = clear()
            addView(text(title, 16f, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            summary?.let { addView(it, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) }) }
            addView(chev)
            stateDescription = if (open) "已展開" else "已收合"
            setOnClickListener {
                val o = body.visibility != View.VISIBLE
                body.visibility = if (o) View.VISIBLE else View.GONE
                chev.rotation = if (o) 180f else 0f
                stateDescription = if (o) "已展開" else "已收合"
            }
        }
        addView(head, lp())
        addView(body, lp())
    }

    /** 卡片裡可收合的一列（範例、常見問題）：點標題才展開內容；第二列起上方有細線。 */
    fun foldRow(title: String, body: String, first: Boolean) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        if (!first) addView(View(c).apply { setBackgroundColor(col(R.color.hairline)) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
        val b = text(body, 14f, R.color.ink_muted).apply {
            visibility = View.GONE
            setPadding(0, 0, 0, dp(12))
        }
        val chev = chevron(false, 20)
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            background = clear()
            addView(text(title, 16f, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(chev)
            stateDescription = "已收合"
            setOnClickListener {
                val o = b.visibility != View.VISIBLE
                b.visibility = if (o) View.VISIBLE else View.GONE
                chev.rotation = if (o) 180f else 0f
                stateDescription = if (o) "已展開" else "已收合"
            }
        }, lp())
        addView(b, lp())
    }

    /** 一列橫排的按鈕，第二顆起左邊留 8dp。 */
    fun row(vararg views: View) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { if (i > 0) marginStart = dp(8) })
        }
    }
}
