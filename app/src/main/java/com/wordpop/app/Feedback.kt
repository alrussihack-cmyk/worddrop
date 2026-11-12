package com.wordpop.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin

/**
 * App-wide sound effects. Tones are synthesized in code, so there are no audio files to ship.
 * One switch (SettingsStore.sfxEnabled) mutes every effect and the welcome voice.
 */
object Sfx {
    private const val RATE = 22050
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null

    private val correctWave by lazy { build(listOf(Note(659f, 90), Note(784f, 90), Note(1047f, 190)), Shape.SINE) }
    private val wrongWave by lazy { build(listOf(Note(233f, 150), Note(165f, 280)), Shape.SQUARE, volume = 0.35f) }
    private val passWave by lazy {
        build(listOf(Note(523f, 110), Note(659f, 110), Note(784f, 110), Note(1047f, 420)), Shape.SINE)
    }
    private val failWave by lazy {
        build(listOf(Note(392f, 170), Note(330f, 170), Note(262f, 380)), Shape.TRIANGLE, volume = 0.6f)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun enabled(): Boolean = appContext?.let { SettingsStore(it).sfxEnabled } ?: false

    fun correct() = play(correctWave)
    fun wrong() = play(wrongWave)
    fun passed() = play(passWave)
    fun failed() = play(failWave)

    private fun play(wave: ShortArray) {
        if (!enabled()) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(wave.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(wave, 0, wave.size)
            track.play()
            main.postDelayed({ runCatching { track.release() } }, wave.size * 1000L / RATE + 300L)
        } catch (_: Exception) {
            // A missing sound must never break an answer.
        }
    }

    private class Note(val freq: Float, val ms: Int)
    private enum class Shape { SINE, SQUARE, TRIANGLE }

    private fun build(notes: List<Note>, shape: Shape, volume: Float = 0.8f): ShortArray {
        val out = ArrayList<Short>()
        notes.forEach { n ->
            val count = RATE * n.ms / 1000
            for (i in 0 until count) {
                val t = i.toDouble() / RATE
                val phase = 2 * PI * n.freq * t
                val raw = when (shape) {
                    Shape.SINE -> sin(phase)
                    Shape.SQUARE -> if (sin(phase) >= 0) 0.6 else -0.6
                    Shape.TRIANGLE -> 2 / PI * kotlin.math.asin(sin(phase))
                }
                // short attack and release so notes do not click
                val edge = minOf(i, count - i).toDouble() / (RATE * 0.012)
                val env = minOf(1.0, edge) * (1.0 - 0.35 * i / count)
                out.add((raw * env * volume * Short.MAX_VALUE).toInt().toShort())
            }
        }
        return out.toShortArray()
    }
}

/** Says "Hello, I am WordPop." once when the app is opened. */
object WelcomeVoice {
    fun speak(context: Context) {
        val app = context.applicationContext
        if (!SettingsStore(app).sfxEnabled) return
        var tts: TextToSpeech? = null
        tts = TextToSpeech(app) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                tts?.shutdown()
                return@TextToSpeech
            }
            applyEnglishAccent(engine, SettingsStore(app).speechAccent)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { engine.shutdown() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { engine.shutdown() }
            })
            engine.speak("Hello, I am WordPop.", TextToSpeech.QUEUE_FLUSH, null, "wordpop_welcome")
        }
    }
}

/**
 * Reacts to an answer: a quick pop when [result] is true, a sideways shake when false.
 * Pass a [key] that changes with every new answer so the reaction replays.
 */
fun Modifier.reactTo(result: Boolean?, key: Any?): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val shift = remember { Animatable(0f) }
    LaunchedEffect(key, result) {
        when (result) {
            true -> {
                scale.snapTo(0.94f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
            }
            false -> {
                for (x in listOf(-14f, 14f, -10f, 10f, -5f, 0f)) shift.animateTo(x, tween(55))
            }
            null -> {
                scale.snapTo(1f)
                shift.snapTo(0f)
            }
        }
    }
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationX = shift.value
    }
}
