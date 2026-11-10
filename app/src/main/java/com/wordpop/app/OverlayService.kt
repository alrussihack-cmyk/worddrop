package com.wordpop.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground service that shows a small notification-like banner over other apps
 * every N minutes. The banner can be dragged, resized, recolored and has control buttons.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.wordpop.app.START"
        const val ACTION_TICK = "com.wordpop.app.SERVICE_TICK"
        const val ACTION_SHOW_NOW = "com.wordpop.app.SHOW_NOW"
        const val EXTRA_WORD_ID = "word_id"
        private const val CHANNEL_ID = "wordpop_service"
        private const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var banner: View? = null
    private var bannerParams: WindowManager.LayoutParams? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var scale = 1f
    private val dismissRunnable = Runnable { finishBanner() }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startAsForeground()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { applyEnglishAccent(it, SettingsStore(this).speechAccent) }
                ttsReady = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        val settings = SettingsStore(this)
        when (intent?.action) {
            ACTION_SHOW_NOW -> showWord()
            ACTION_TICK -> {
                if (settings.enabled) {
                    // Schedule first so a failure while showing the banner never breaks the chain.
                    Scheduler.scheduleNext(this)
                    if (canShowNow(settings)) showWord()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                // ACTION_START, or the system restarting the service after it was killed.
                if (settings.enabled) {
                    Scheduler.scheduleNext(this)
                } else if (banner == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeBanner()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    // ---------- when to show ----------

    /** Active hours, pause, calls, games and videos. A test from the app ignores all of these. */
    private fun canShowNow(s: SettingsStore): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!Scheduler.isActiveHour(s, hour)) return false
        if (System.currentTimeMillis() < s.pausedUntil) return false
        if (s.skipCalls && inCall()) return false
        if (s.skipGames && Focus.gameOrVideoInForeground(this)) return false
        return true
    }

    private fun inCall(): Boolean {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val m = am.mode
        return m == AudioManager.MODE_IN_CALL ||
            m == AudioManager.MODE_IN_COMMUNICATION ||
            m == AudioManager.MODE_RINGTONE
    }

    // ---------- foreground notification ----------

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ووردبوب", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun startAsForeground() {
        val s = SettingsStore(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ووردبوب شغّال")
            .setContentText("كلمة جديدة " + intervalLabel(s.intervalMin))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------- banner ----------

    /** Size in pixels, scaled by the banner size the user picked. */
    private fun dp(v: Int): Int = maxOf(1, (v * resources.displayMetrics.density * scale).toInt())

    /** Size in pixels that does not depend on the banner size. */
    private fun rawDp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else rawDp(24)
    }

    private fun showWord(silent: Boolean = false) {
        if (!Settings.canDrawOverlays(this)) return
        val s = SettingsStore(this)
        val w = WordRepo.pick(s)
        removeBanner()

        scale = when (s.bannerSize) {
            0 -> 0.85f
            2 -> 1.2f
            else -> 1f
        }
        val view = buildBanner(w, s, BannerThemes.get(s.bannerTheme))
        val screenW = resources.displayMetrics.widthPixels
        val lp = WindowManager.LayoutParams(
            minOf(screenW - 2 * rawDp(12), dp(340)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = statusBarHeight() + rawDp(8)

        // Start slightly lower and smaller, then pop into place like the WordPop sticker identity.
        view.alpha = 0f
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.translationY = dp(14).toFloat()
        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            return
        }
        banner = view
        bannerParams = lp
        view.post {
            placeBanner(view, lp, s)
            val targetAlpha = s.opacity / 100f
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                view.animate()
                    .alpha(targetAlpha)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(280L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                    .start()
            } else {
                // Respect the system's Reduce motion setting: no scale or travel.
                view.scaleX = 1f
                view.scaleY = 1f
                view.translationY = 0f
                view.animate().alpha(targetAlpha).setDuration(120L).start()
            }
        }

        resetTimer()
        if (s.sound && !silent) playChime()
        if (s.autoSpeak) handler.postDelayed({ speak(w.en) }, if (s.sound && !silent) 1000L else 0L)
    }

    /** Puts the banner where the user dragged it, or at the top / middle / bottom preset. */
    private fun placeBanner(view: View, lp: WindowManager.LayoutParams, s: SettingsStore) {
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val maxX = ((sw - view.width) / 2).coerceAtLeast(0)
        val maxY = (sh - view.height).coerceAtLeast(0)
        lp.x = s.bannerX.coerceIn(-maxX, maxX)
        lp.y = if (s.bannerY >= 0) {
            s.bannerY.coerceIn(0, maxY)
        } else {
            when (s.bannerPos) {
                1 -> maxY / 2
                2 -> (maxY - rawDp(56)).coerceAtLeast(0)
                else -> (statusBarHeight() + rawDp(8)).coerceAtMost(maxY)
            }
        }
        try {
            wm.updateViewLayout(view, lp)
        } catch (e: Exception) {
        }
    }

    /** Keeps the banner on screen after its height changed (example shown, pause options shown). */
    private fun clampBanner() {
        val v = banner ?: return
        v.post {
            val lp = bannerParams ?: return@post
            val maxY = (resources.displayMetrics.heightPixels - v.height).coerceAtLeast(0)
            if (lp.y > maxY) {
                lp.y = maxY
                try {
                    wm.updateViewLayout(v, lp)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun resetTimer() {
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, SettingsStore(this).displaySec * 1000L)
    }

    private fun playChime() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(this, uri) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.play()
        } catch (e: Exception) {
        }
    }

    private fun font(res: Int, fallback: Typeface): Typeface = try {
        resources.getFont(res)
    } catch (e: Exception) {
        fallback
    }

    /** Rounded panel with a thick border and a solid shadow under it, like a sticker. */
    private fun chunkyBackground(fill: Int, edge: Int, radius: Int, depth: Int): android.graphics.drawable.Drawable {
        val shadow = GradientDrawable().apply {
            setColor(edge)
            cornerRadius = radius.toFloat()
        }
        val main = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            setStroke(dp(3), edge)
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(shadow, main)).apply {
            setLayerInset(0, 0, depth, 0, 0)
            setLayerInset(1, 0, 0, 0, depth)
        }
    }

    private fun pill(label: String, t: BannerTheme, onClick: (TextView) -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12.5f * scale
            typeface = font(R.font.readex_bold, Typeface.DEFAULT_BOLD)
            setTextColor(t.text)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4), dp(9), dp(4), dp(9))
            background = GradientDrawable().apply {
                setColor(t.chip)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), t.stroke)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener { onClick(this) }
        }

    private fun buttonRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun buildBanner(w: Word, s: SettingsStore, t: BannerTheme): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(10), dp(12), dp(10) + dp(5))
            background = chunkyBackground(t.bg, t.stroke, dp(22), dp(5))
        }

        // ----- header: logo, word, translation, speaker, close -----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        }
        val en = TextView(this).apply {
            text = w.en
            textSize = 26f * scale
            typeface = font(R.font.lalezar, Typeface.DEFAULT_BOLD)
            setTextColor(t.text)
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.START
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val ar = TextView(this).apply {
            text = w.ar
            textSize = 14f * scale
            typeface = font(R.font.readex_medium, Typeface.DEFAULT)
            setTextColor(t.sub)
            gravity = Gravity.START
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(en)
        texts.addView(ar)

        val speaker = ImageView(this).apply {
            setImageResource(R.drawable.ic_volume)
            setColorFilter(t.onAccent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(t.accent)
                setStroke(dp(2), t.stroke)
            }
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "نطق الكلمة"
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener {
                resetTimer()
                speak(w.en)
            }
        }
        val close = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(t.sub)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            contentDescription = "إغلاق"
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(6) }
            setOnClickListener { finishBanner() }
        }
        header.addView(icon)
        header.addView(texts)
        header.addView(speaker)
        header.addView(close)
        root.addView(header)

        // ----- example (hidden until the user asks) -----
        val exampleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        if (w.example.isNotEmpty()) {
            exampleBox.addView(TextView(this).apply {
                text = w.example
                textSize = 15f * scale
                setTextColor(t.text)
                typeface = font(R.font.readex_regular, Typeface.DEFAULT)
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.START
                setOnClickListener {
                    resetTimer()
                    speak(w.example)
                }
            })
            if (w.exampleAr.isNotEmpty()) {
                exampleBox.addView(TextView(this).apply {
                    text = w.exampleAr
                    textSize = 13f * scale
                    setTextColor(t.sub)
                    typeface = font(R.font.readex_regular, Typeface.DEFAULT)
                    gravity = Gravity.START
                })
            }
        }
        root.addView(exampleBox)

        fun showExample(show: Boolean) {
            if (w.example.isEmpty()) return
            exampleBox.visibility = if (show) View.VISIBLE else View.GONE
            clampBanner()
        }

        // ----- control buttons (only the ones the user picked; 3 per row) -----
        val chosen = s.actions
        if (chosen.isNotEmpty()) {
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            val rows = mutableListOf<LinearLayout>()
            val buttons = mutableListOf<TextView>()
            val pauseRow = buttonRow().apply { visibility = View.GONE }

            fun pauseFor(minutes: Int) {
                s.pausedUntil = System.currentTimeMillis() + minutes * 60_000L
                finishBanner()
            }

            if ("fav" in chosen) {
                val favBtn = pill("", t) { }
                fun refreshFav() {
                    val on = w.id in s.favWords
                    favBtn.text = if (on) "★ في المفضلة" else "☆ مفضلة"
                    favBtn.setTextColor(if (on) t.accent else t.text)
                }
                refreshFav()
                favBtn.setOnClickListener {
                    resetTimer()
                    s.toggleFavWord(w.id)
                    refreshFav()
                }
                buttons += favBtn
            }
            if ("know" in chosen) {
                buttons += pill("✓ أعرفها", t) { b ->
                    s.markLearned(w.id)
                    s.hard = s.hard - w.id
                    b.text = "✓ تمام!"
                    handler.removeCallbacks(dismissRunnable)
                    handler.postDelayed(dismissRunnable, 700L)
                }
            }
            if ("dont" in chosen) {
                buttons += pill("✗ لسه", t) { b ->
                    s.learned = s.learned - w.id
                    s.hard = s.hard + w.id
                    b.text = "هتظهرلك تاني"
                    showExample(true)
                    resetTimer()
                }
            }
            if ("example" in chosen && w.example.isNotEmpty()) {
                buttons += pill("💡 مثال", t) {
                    resetTimer()
                    showExample(exampleBox.visibility != View.VISIBLE)
                }
            }
            if ("skip" in chosen) {
                buttons += pill("⏭ تخطي", t) { showWord(silent = true) }
            }
            if ("pause" in chosen) {
                buttons += pill("⏸ إيقاف", t) {
                    rows.forEach { it.visibility = View.GONE }
                    pauseRow.visibility = View.VISIBLE
                    resetTimer()
                }
            }

            buttons.chunked(3).forEach { chunk ->
                val row = buttonRow()
                chunk.forEach { row.addView(it) }
                // Empty slots keep every button the same width.
                repeat(3 - chunk.size) {
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                    })
                }
                rows += row
                actions.addView(row)
            }

            pauseRow.addView(pill("30 د", t) { pauseFor(30) })
            pauseRow.addView(pill("ساعة", t) { pauseFor(60) })
            pauseRow.addView(pill("3 ساعات", t) { pauseFor(180) })
            pauseRow.addView(pill("رجوع", t) {
                pauseRow.visibility = View.GONE
                rows.forEach { it.visibility = View.VISIBLE }
                resetTimer()
            })
            actions.addView(pauseRow)
            root.addView(actions)
        }

        // Tap = open the app on this word, drag = move the banner anywhere (the spot is remembered).
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        root.setOnTouchListener { v, e ->
            val lp = bannerParams
            if (lp == null) {
                false
            } else when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    handler.removeCallbacks(dismissRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > rawDp(8) || abs(dy) > rawDp(8)) moved = true
                    if (moved) {
                        val sw = resources.displayMetrics.widthPixels
                        val sh = resources.displayMetrics.heightPixels
                        val maxX = ((sw - v.width) / 2).coerceAtLeast(0)
                        moveBanner(
                            (startX + dx).toInt().coerceIn(-maxX, maxX),
                            (startY + dy).toInt().coerceIn(0, (sh - v.height).coerceAtLeast(0))
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val st = SettingsStore(this)
                        st.bannerX = lp.x
                        st.bannerY = lp.y
                        resetTimer()
                    } else {
                        openLearn(w)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    resetTimer()
                    true
                }
                else -> false
            }
        }
        return root
    }

    private fun moveBanner(x: Int, y: Int) {
        val v = banner ?: return
        val lp = bannerParams ?: return
        lp.x = x
        lp.y = y
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) {
        }
    }

    private fun openLearn(w: Word) {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_WORD_ID, w.id)
        try {
            startActivity(i)
        } catch (e: Exception) {
        }
        finishBanner()
    }

    private fun speak(text: String) {
        val settings = SettingsStore(this)
        val fallback = {
            if (ttsReady) {
                tts?.let { engine ->
                    applyEnglishAccent(engine, settings.speechAccent)
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordpop")
                }
            }
        }
        if (AiConfigSync.workerUrl.isNotBlank()) {
            EasyVoiceClient.speak(this, settings.ttsWorkerUrl, text, settings.speechVoice, fallback)
        } else {
            fallback()
        }
    }

    private fun removeBanner() {
        handler.removeCallbacks(dismissRunnable)
        banner?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        banner = null
        bannerParams = null
    }

    /** Removes the banner; if the feature is switched off (a one-off test), the service stops too. */
    private fun finishBanner() {
        removeBanner()
        if (!SettingsStore(this).enabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}
