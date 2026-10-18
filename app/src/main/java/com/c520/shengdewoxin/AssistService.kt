package com.c520.shengdewoxin

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.RemoteViews

/** 系統的「預設數位助理」入口。設為預設後系統會一直綁著它，所以在這裡先暖機 TTS 與手電筒狀態。 */
class AssistService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        instance = this
        // 不抓畫面文字與截圖：叫出來更快，也不會把畫面內容外送
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
        // 助理面板也照設定頁選的外觀（預設淺色）
        runCatching { Prefs.applyLook(this) }
        Speaker.warmUp(this)
        runCatching { Torch.init(this) }
        // App 清單（含拼音）第一次建要約 1 秒，先在背景建好
        Bg.pool.execute { runCatching { Apps.all(this) } }
    }

    override fun onShutdown() {
        instance = null
        super.onShutdown()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        showSession(Bundle(), 0)
    }

    companion object {
        @Volatile private var instance: AssistService? = null

        /** 叫出助理面板；還不是預設助理時回 false。args 帶 AssistSession.ARG_ROUTINE 時直接執行那個情境（試跑）。 */
        fun show(args: Bundle = Bundle()): Boolean {
            val s = instance ?: return false
            s.showSession(args, 0)
            return true
        }
    }
}

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

/** 系統規定助理要宣告一個辨識服務；實際辨識用 Google 的，這個只回錯誤。 */
class StubRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}

/** 耳機鍵、藍牙耳機語音鍵、磁貼、小工具、圖示捷徑的入口：轉去叫出助理面板，自己不顯示畫面。 */
class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dry = intent?.getStringExtra("dry")
        if (dry != null) {
            // 偵錯：只看情境與規則判斷的結果，不執行任何動作
            // adb shell am start -n com.c520.shengdewoxin/.TriggerActivity --es dry "明天早上七點叫我"
            val r = Routines.match(this, dry)
            val out = r?.let { "情境：" + it.acts.joinToString("、") { a -> Actions.describe(a) } }
                ?: Quick.match(this, dry)?.toJson() ?: "交給 AI"
            Log.i("Dry", "「$dry」→ $out")
        } else if (!AssistService.show()) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}

/** 快速設定磁貼：下拉通知列點一下就開始聽。 */
class AssistTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    // Android 14 起用 PendingIntent 版；12、13 只有 Intent 版可用
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, TriggerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(i)
        }
    }
}

/** 主畫面／鎖定畫面小工具：一顆麥克風按鈕，點一下就開始聽。 */
class AssistWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, TriggerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_assist)
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        manager.updateAppWidget(ids, views)
    }
}
