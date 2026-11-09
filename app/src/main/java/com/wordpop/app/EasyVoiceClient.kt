package com.wordpop.app

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Calls the user's Cloudflare Worker. The EasyVoice credential never enters the APK.
 * On any network/API/playback failure, the caller can fall back to Android TTS.
 */
object EasyVoiceClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var activePlayer: MediaPlayer? = null

    fun speak(
        context: Context,
        workerUrl: String,
        text: String,
        voice: String,
        onFailure: () -> Unit
    ) {
        if (workerUrl.isBlank() || text.isBlank()) {
            onFailure()
            return
        }
        executor.execute {
            var audioFile: File? = null
            try {
                val endpoint = workerUrl.trim().trimEnd('/')
                require(endpoint.startsWith("https://")) { "Worker URL must use HTTPS" }
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = org.json.JSONObject()
                    .put("input", text.take(5000))
                    .put("voice", voice)
                    .put("speed", 1.0)
                    .toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    connection.disconnect()
                    throw IllegalStateException("TTS Worker returned HTTP $status")
                }
                audioFile = File.createTempFile("wordpop-tts-", ".mp3", context.cacheDir)
                connection.inputStream.use { input ->
                    audioFile.outputStream().use { output -> input.copyTo(output) }
                }
                connection.disconnect()
                if (audioFile.length() == 0L) throw IllegalStateException("Empty TTS audio")
                val fileToPlay = audioFile ?: throw IllegalStateException("Missing TTS audio file")
                main.post {
                    try {
                        activePlayer?.runCatching { stop(); release() }
                        val player = MediaPlayer()
                        activePlayer = player
                        player.setDataSource(fileToPlay.absolutePath)
                        player.setOnCompletionListener {
                            it.release()
                            if (activePlayer === it) activePlayer = null
                            fileToPlay.delete()
                        }
                        player.setOnErrorListener { mp, _, _ ->
                            mp.release()
                            if (activePlayer === mp) activePlayer = null
                            fileToPlay.delete()
                            onFailure()
                            true
                        }
                        player.setOnPreparedListener { it.start() }
                        player.prepareAsync()
                    } catch (_: Exception) {
                        fileToPlay.delete()
                        onFailure()
                    }
                }
            } catch (_: Exception) {
                audioFile?.delete()
                main.post { onFailure() }
            }
        }
    }
}
