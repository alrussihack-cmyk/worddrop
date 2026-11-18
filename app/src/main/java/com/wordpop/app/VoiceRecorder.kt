package com.wordpop.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

/**
 * Records one spoken utterance (16 kHz mono WAV) and sends it to the WordPop Worker,
 * which transcribes it with an AI speech model. Google's recogniser is not used.
 */
object VoiceRecorder {
    @Volatile private var stopRequested = false
    fun stop() { stopRequested = true }

    private const val RATE = 16000

    /** Returns WAV bytes, or null when nothing was said. Ends on silence after speech, on [stop], or at 15 s. */
    @SuppressLint("MissingPermission")
    fun record(): ByteArray? {
        stopRequested = false
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val chunk = 1600 // 100 ms
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, chunk * 4)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); throw Exception("تعذر تشغيل الميكروفون.") }
        val pcm = ByteArrayOutputStream()
        val buf = ShortArray(chunk)
        var heardSpeech = false
        var silentMs = 0
        var totalMs = 0
        try {
            rec.startRecording()
            while (!stopRequested && totalMs < 15000) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                val rms = sqrt(sum / n)
                val ms = n * 1000 / RATE
                totalMs += ms
                if (rms > 900) { heardSpeech = true; silentMs = 0 } else silentMs += ms
                if (heardSpeech) {
                    val bytes = ByteArray(n * 2)
                    for (i in 0 until n) { bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte(); bytes[i * 2 + 1] = (buf[i].toInt() shr 8).toByte() }
                    pcm.write(bytes)
                    if (silentMs >= 800) break
                } else if (totalMs >= 6000) break
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        if (!heardSpeech || pcm.size() < RATE / 2) return null
        return wav(pcm.toByteArray())
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun int(v: Int) { for (i in 0..3) out.write((v shr (8 * i)) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + pcm.size); out.write("WAVEfmt ".toByteArray())
        int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
        out.write("data".toByteArray()); int(pcm.size); out.write(pcm)
        return out.toByteArray()
    }

    /** Sends the WAV to the Worker's /ai/transcribe route (AI speech-to-text). */
    fun transcribe(workerUrl: String, wav: ByteArray): String {
        if (!WordPopAgeProfile.allowsExternalServices()) {
            throw Exception("خدمات الذكاء الاصطناعي عبر الإنترنت متاحة لحسابات البالغين فقط.")
        }
        val endpoint = workerUrl.trim().trimEnd('/') + "/ai/transcribe"
        val body = JSONObject().put("audio", Base64.encodeToString(wav, Base64.NO_WRAP)).put("language", "en")
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Firebase-AppCheck", AppCheckTokens.currentToken())
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                throw Exception(
                    if (code == 404) "الـ Worker لسه محتاج deploy للنسخة الجديدة (مسار /ai/transcribe مش موجود)."
                    else msg.ifBlank { "تعذر تحويل الصوت لنص ($code)." }
                )
            }
            return JSONObject(text).optString("text").trim()
        } catch (e: java.io.IOException) {
            throw Exception("مفيش اتصال بالنت.")
        } finally {
            conn.disconnect()
        }
    }
}
