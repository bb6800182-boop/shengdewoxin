package com.c520.shengdewoxin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * 助理面板：叫出來就開始聽 → 情境、規則判斷（0 延遲）或交給 AI → 執行 → 念結果 → 自動收起。
 * gen 每次重聽或收起就加 1，舊的回呼一律作廢，上一句的結果不會跑到這一句。
 */
class AssistSession(context: Context) : VoiceInteractionSession(context) {
    private val ui = Ui(context)
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    @Volatile private var gen = 0
    private var busyRetried = false
    private var spec: Pair<String, Future<Llm.Result?>>? = null

    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var reply: TextView
    private lateinit var choices: LinearLayout
    private lateinit var mic: ImageButton

    override fun onCreateContentView(): View {
        val c = context
        status = ui.text("", 14f, R.color.accent_text).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val gear = ui.text("設定", 14f, R.color.ink_muted).apply {
            gravity = Gravity.CENTER
            minHeight = ui.dp(48)
            minWidth = ui.dp(48)
            setPadding(ui.dp(16), 0, ui.dp(16), 0)
            background = ui.pressable(ui.box(R.color.surface_2, R.color.hairline_strong, 24))
            contentDescription = "開啟聲得我心設定"
            setOnClickListener {
                hide()
                start(settingsIntent())
            }
        }
        val top = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(status, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(gear)
        }
        heard = ui.text("", 22f, R.color.ink)
        reply = ui.text("", 18f, R.color.ink_muted).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        choices = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        mic = ImageButton(c).apply {
            setImageResource(R.drawable.ic_mic)
            imageTintList = ColorStateList.valueOf(ui.col(R.color.on_accent))
            background = ui.pressable(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ui.col(R.color.accent))
            })
            contentDescription = "再說一次"
            setOnClickListener { listen() }
        }
        val pad = ui.dp(24)
        val radius = ui.dp(24).toFloat()
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ui.col(R.color.surface_1))
                setStroke(ui.dp(1), ui.col(R.color.hairline))
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            isClickable = true // 點面板本身不收起
            setPadding(pad, ui.dp(16), pad, pad)
            addView(top, ui.lp(0))
            addView(heard, ui.lp(8))
            addView(reply, ui.lp(8))
            addView(choices, ui.lp(4))
            addView(mic, LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = ui.dp(16)
            })
        }
        return FrameLayout(c).apply {
            setBackgroundColor(ui.col(R.color.scrim))
            contentDescription = "點空白處關閉助理"
            setOnClickListener { hide() }
            addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
            // 面板畫在導覽列後面，底部讓出導覽列的高度
            setOnApplyWindowInsetsListener { _, insets ->
                card.setPadding(pad, ui.dp(16), pad, pad + insets.getInsets(WindowInsets.Type.systemBars()).bottom)
                insets
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val c = context
        Speaker.warmUp(c)
        Bg.pool.execute { runCatching { Apps.all(c) } }
        busyRetried = false
        val routine = args?.getString(ARG_ROUTINE)?.let { id -> Routines.all(c).firstOrNull { it.id == id } }
        if (routine != null) {
            startRoutine(routine)
        } else {
            Llm.warm(c)
            listen()
        }
    }

    override fun onHide() {
        gen++
        main.removeCallbacksAndMessages(null)
        sr?.cancel()
        Speaker.stop()
        dropSpec()
        super.onHide()
    }

    override fun onDestroy() {
        sr?.destroy()
        sr = null
        super.onDestroy()
    }

    /** 換下一輪：舊的回呼作廢、清掉畫面。回傳這一輪的代號。 */
    private fun reset(): Int {
        val my = ++gen
        main.removeCallbacksAndMessages(null)
        Speaker.stop()
        dropSpec()
        heard.text = ""
        reply.text = ""
        reply.visibility = View.GONE
        choices.removeAllViews()
        choices.visibility = View.GONE
        pulse(0f)
        return my
    }

    // ---------- 聽 ----------

    private fun listen() {
        val my = reset()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("需要麥克風權限，請到聲得我心設定授權", listOf("開啟聲得我心設定" to settingsIntent()))
            return
        }
        val rec = sr ?: newRecognizer()?.also { sr = it }
        if (rec == null) {
            fail("這支手機找不到語音辨識服務")
            return
        }
        rec.cancel()
        rec.setRecognitionListener(Listener(my))
        rec.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        )
        val nick = Prefs.nickname(context)
        setStatus(if (nick.isEmpty()) "請說…" else "$nick，請說…", R.color.accent_text)
    }

    /** 明確指定 Google 的辨識服務（被設成預設助理後，系統預設辨識可能指到自己的空殼）。 */
    private fun newRecognizer(): SpeechRecognizer? {
        val comp = Recognizers.candidates(context).getOrNull(recIndex)
        return when {
            comp != null -> SpeechRecognizer.createSpeechRecognizer(context, comp)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> null
        }
    }

    private inner class Listener(private val my: Int) : RecognitionListener {
        private var partial = ""

        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            if (my == gen) pulse(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (my != gen) return
            setStatus("辨識中…", R.color.ink_subtle)
            pulse(0f)
            if (partial.isNotBlank()) speculate(partial)
        }

        override fun onError(error: Int) {
            if (my == gen) recognizeError(error)
        }

        override fun onResults(results: Bundle?) {
            if (my != gen) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { partial }
            if (text.isBlank()) recognizeError(SpeechRecognizer.ERROR_NO_MATCH) else handle(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (my != gen) return
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) {
                partial = t
                heard.text = t
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun recognizeError(code: Int) {
        pulse(0f)
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                fail("沒聽到，點麥克風再說一次", speak = false, hideAfter = 6_000L)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                fail("需要麥克風權限，請到聲得我心設定授權", listOf("開啟聲得我心設定" to settingsIntent()))
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                fail("語音辨識連不上網路")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                if (!busyRetried) {
                    busyRetried = true
                    sr?.cancel()
                    main.postDelayed({ listen() }, 300)
                } else {
                    fail("語音辨識忙碌中，請再試一次")
                }
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> switchRecognizer(code)
            else -> fail("語音辨識失敗（代碼 $code），請再試一次")
        }
    }

    /** 這個辨識服務不能用（沒有中文、斷線），換下一個。 */
    private fun switchRecognizer(code: Int) {
        val n = Recognizers.candidates(context).size
        sr?.destroy()
        sr = null
        if (recIndex + 1 < n) {
            recIndex++
            Log.i(TAG, "辨識服務錯誤 $code，改用第 ${recIndex + 1} 個")
            listen()
        } else {
            recIndex = 0
            fail("語音辨識失敗（代碼 $code），請再試一次")
        }
    }

    // ---------- 理解＋執行 ----------

    private fun handle(text: String) {
        val my = gen
        busyRetried = false
        heard.text = text
        val c = context
        Bg.pool.execute {
            // 情境優先：說出觸發語句就照設定做，不必等 AI
            val routine = runCatching { Routines.match(c, text) }.getOrNull()
            if (routine != null) {
                dropSpec()
                runRoutine(c, routine, my)
                return@execute
            }
            val quick = runCatching { Quick.match(c, text) }.getOrNull()
            val plan = if (quick != null) {
                quick
            } else {
                onMain(my) { setStatus("思考中…", R.color.ink_subtle) }
                val r = takeSpec(text) ?: Llm.ask(c, text)
                val out = r.text
                if (out == null) {
                    onMain(my) { fail(r.error ?: "AI 沒有回應") }
                    return@execute
                }
                Llm.remember(text, out)
                Plan.parse(out)
            }
            if (my != gen) return@execute // 使用者已經收起面板，就不要再執行
            val results = plan.acts.map { Actions.exec(c, it) }
            onMain(my) { present(plan, results) }
        }
    }

    /** 講完的瞬間先拿辨識中的文字送 AI；最終文字一樣就直接用這個結果，省掉等最終辨識的時間。 */
    private fun speculate(partial: String) {
        val c = context
        val key = Names.norm(partial)
        if (key.isEmpty()) return
        val f = Bg.pool.submit(Callable<Llm.Result?> {
            if (Routines.match(c, partial) != null || Quick.match(c, partial) != null) null else Llm.ask(c, partial)
        })
        synchronized(this) {
            spec?.second?.cancel(true)
            spec = key to f
        }
    }

    private fun takeSpec(finalText: String): Llm.Result? {
        val s = synchronized(this) { spec.also { spec = null } } ?: return null
        if (s.first != Names.norm(finalText)) {
            s.second.cancel(true)
            return null
        }
        return try {
            s.second.get(20, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        }
    }

    private fun dropSpec() {
        synchronized(this) {
            spec?.second?.cancel(true)
            spec = null
        }
    }

    // ---------- 情境 ----------

    /** 設定頁的「試跑」：不聽，直接執行情境。 */
    private fun startRoutine(r: Routine) {
        val my = reset()
        sr?.cancel()
        heard.text = r.triggers.firstOrNull().orEmpty()
        setStatus("執行情境…", R.color.ink_subtle)
        val c = context
        Bg.pool.execute { runRoutine(c, r, my) }
    }

    /** 先做設定類動作 → 念回應 → 念完再做開 App、放音樂這類動作。在背景執行緒呼叫。 */
    private fun runRoutine(c: Context, r: Routine, my: Int) {
        if (my != gen) return
        val (later, now) = r.acts.partition { it.t in Routines.LATER }
        val done = now.map { Actions.exec(c, it) }
        val say = Routines.fill(c, r.reply)
        onMain(my) { presentRoutine(say, done, later) }
    }

    private fun presentRoutine(say: String, done: List<Res>, later: List<Act>) {
        val infos = done.filter { it.info && !it.error }.joinToString("。") { it.text }
        val msg = listOf(say, infos).filter { it.isNotBlank() }.joinToString("。")
        val bad = done.count { it.error }
        if (bad == 0) {
            setStatus("✓ 情境完成", R.color.success)
            mic.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            setStatus("有 $bad 項沒完成", R.color.ink_subtle)
            mic.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        val lines = done.filter { !it.info || it.error }.map { mark(it) }
        showReply((listOf(msg.ifBlank { "情境完成" }) + lines).joinToString("\n"))
        val picks = done.firstOrNull { it.choices.isNotEmpty() }
        picks?.let { showChoices(it.choices) }
        for (res in done) {
            val i = res.intent ?: continue
            if (!res.leave && !start(i, res.fallback)) appendReply("⚠ 找不到能處理的 App")
        }
        val my = gen
        Speaker.speak(context, msg) { if (my == gen) runLater(later, my, picks != null) }
    }

    /** 念完回應後才做的動作（開 App、放音樂…）。 */
    private fun runLater(later: List<Act>, my: Int, picks: Boolean) {
        if (later.isEmpty()) {
            autoHide(if (picks) 15_000L else 1_500L)
            return
        }
        val c = context
        Bg.pool.execute {
            val rs = later.map { Actions.exec(c, it) }
            onMain(my) {
                rs.forEach { appendReply(mark(it)) }
                for (res in rs) {
                    val i = res.intent ?: continue
                    if (!res.leave && !start(i, res.fallback)) appendReply("⚠ 找不到能處理的 App")
                }
                val leave = rs.firstOrNull { it.leave && it.intent != null }
                if (leave != null && !picks) {
                    if (start(leave.intent!!, leave.fallback)) {
                        hide()
                        return@onMain
                    }
                    appendReply("⚠ 找不到能處理的 App")
                }
                autoHide(if (picks || rs.any { it.error }) 15_000L else 1_500L)
            }
        }
    }

    private fun mark(r: Res) = (if (r.error) "⚠ " else "✓ ") + r.text

    // ---------- 呈現 ----------

    private fun present(plan: Plan, rs: List<Res>) {
        val pick = rs.firstOrNull { it.choices.isNotEmpty() }
        val err = rs.firstOrNull { it.error }
        val infos = rs.filter { it.info }
        val msg = when {
            err != null -> err.text
            infos.isNotEmpty() -> infos.joinToString("。") { it.text }
            plan.say.isNotBlank() -> plan.say
            rs.isNotEmpty() -> rs.joinToString("，") { it.text }
            else -> "我不太確定要做什麼，可以換個說法嗎？"
        }
        if (err != null) {
            setStatus("沒辦法完成", R.color.ink_subtle)
            showReply("⚠ $msg")
            mic.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            setStatus("✓ 完成", R.color.success)
            showReply(msg)
            mic.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        pick?.let { showChoices(it.choices) }
        // 先送不會離開畫面的（鬧鐘、計時），再送會打開別的 App 的
        for (r in rs) {
            val i = r.intent ?: continue
            if (!r.leave && !start(i, r.fallback)) showReply("⚠ 找不到能處理的 App")
        }
        val leave = rs.firstOrNull { it.leave && it.intent != null }
        if (leave != null && pick == null && err == null) {
            if (start(leave.intent!!, leave.fallback)) {
                hide()
                return
            }
            showReply("⚠ 找不到能處理的 App")
        }
        val talk = err != null || pick != null || rs.isEmpty() || rs.any { it.speak }
        val wait = when {
            pick != null -> 15_000L
            rs.isEmpty() -> 4_000L
            else -> 1_500L
        }
        val my = gen
        if (talk) Speaker.speak(context, msg) { if (my == gen) autoHide(wait) } else autoHide(1_200L)
    }

    private fun fail(
        msg: String,
        picks: List<Pair<String, Intent>> = emptyList(),
        speak: Boolean = true,
        hideAfter: Long = 4_000L,
    ) {
        setStatus("沒辦法完成", R.color.ink_subtle)
        showReply("⚠ $msg")
        if (picks.isNotEmpty()) showChoices(picks)
        val wait = if (picks.isEmpty()) hideAfter else 15_000L
        val my = gen
        if (speak) Speaker.speak(context, msg) { if (my == gen) autoHide(wait) } else autoHide(wait)
    }

    private fun showReply(text: String) {
        reply.text = text
        reply.visibility = View.VISIBLE
    }

    private fun appendReply(line: String) {
        val cur = reply.text?.toString().orEmpty()
        reply.text = if (cur.isEmpty()) line else "$cur\n$line"
        reply.visibility = View.VISIBLE
    }

    private fun setStatus(text: String, color: Int) {
        status.text = text
        status.setTextColor(ui.col(color))
    }

    private fun showChoices(list: List<Pair<String, Intent>>) {
        choices.removeAllViews()
        for ((label, intent) in list) {
            val b = ui.button(label, primary = false) {
                gen++ // 取消自動收起
                Speaker.stop()
                if (start(intent)) hide() else showReply("⚠ 找不到能處理的 App")
            }
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            choices.addView(b, ui.lp(8))
        }
        choices.visibility = View.VISIBLE
    }

    private fun start(i: Intent, fallback: Intent? = null): Boolean {
        for (x in listOfNotNull(i, fallback)) {
            try {
                context.startActivity(Intent(x).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "沒有 App 能處理 ${x.action}")
            } catch (e: SecurityException) {
                Log.w(TAG, "不允許開啟 ${x.action}", e)
            }
        }
        return false
    }

    private fun onMain(my: Int, f: () -> Unit) {
        main.post { if (my == gen) f() }
    }

    private fun autoHide(ms: Long) {
        val my = gen
        main.postDelayed({ if (my == gen) hide() }, ms)
    }

    /** 麥克風按鈕跟著音量微微放大，看得出有在聽。 */
    private fun pulse(rms: Float) {
        val s = 1f + rms.coerceIn(0f, 10f) / 40f
        mic.scaleX = s
        mic.scaleY = s
    }

    private fun settingsIntent() = Intent(context, MainActivity::class.java)

    companion object {
        const val ARG_ROUTINE = "routine"
        private const val TAG = "AssistSession"
        private var recIndex = 0
    }
}
