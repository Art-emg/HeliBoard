// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

enum class VoiceListeningState {
    OFF,
    CONNECTING,
    LISTENING,
}

/**
 * Streams microphone audio to the Soniox realtime STT WebSocket API and emits finalized text.
 *
 * Lifecycle:
 *  - [start] opens the mic immediately (CONNECTING) and the WebSocket in parallel. Audio captured
 *    while the socket is opening is buffered and flushed right after the config frame is sent.
 *  - Each server message may contain newly finalized tokens (is_final=true) plus a non-final
 *    preview tail. Only finalized tokens are committed, in arrival order.
 *  - [stop] stops the mic, flushes via an empty frame, and keeps reading trailing finals until
 *    the server reports "finished" or a short grace timeout elapses.
 */
class SonioxVoiceInputManager(
    private val context: Context,
    private val onFinalText: (String) -> Unit,
    private val onPartialText: (String) -> Unit = {},
    private val onError: (String) -> Unit,
    private val onStateChanged: (VoiceListeningState) -> Unit,
    private val onAudioLevel: (Float) -> Unit = {},
    private val onListeningReady: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS) // keep the socket alive
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var graceJob: Job? = null
    private var silenceTimeoutJob: Job? = null

    /** PCM chunks captured before the WebSocket is ready to receive audio. */
    private val preConnectChunks = ArrayDeque<ByteArray>()
    private var preConnectBufferedBytes = 0
    private val preConnectLock = Any()
    @Volatile private var streamTarget: WebSocket? = null

    @Volatile private var sessionActive = false // socket open, results still wanted
    @Volatile private var capturing = false     // mic actively streaming
    private var uiState = VoiceListeningState.OFF

    val isRecordingActive get() = uiState != VoiceListeningState.OFF

    fun toggle() {
        if (uiState == VoiceListeningState.OFF) start() else stop()
    }

    fun start() {
        if (sessionActive) {
            Log.w(TAG, "start ignored: session already active")
            return
        }
        if (!PermissionsUtil.checkAllPermissionsGranted(context, Manifest.permission.RECORD_AUDIO)) {
            onError(context.getString(helium314.keyboard.latin.R.string.soniox_microphone_permission_required))
            return
        }
        val prefs = context.prefs()
        val apiKey = prefs.getString(Settings.PREF_SONIOX_API_KEY, Defaults.PREF_SONIOX_API_KEY)?.trim()
        if (apiKey.isNullOrBlank()) {
            onError("Soniox API key is not configured")
            return
        }
        val model = (prefs.getString(Settings.PREF_SONIOX_MODEL, Defaults.PREF_SONIOX_MODEL)
            ?: Defaults.PREF_SONIOX_MODEL).trim()

        sessionActive = true
        capturing = false
        synchronized(preConnectLock) {
            preConnectChunks.clear()
            preConnectBufferedBytes = 0
            streamTarget = null
        }
        setState(VoiceListeningState.CONNECTING)
        Log.i(TAG, "start: model=$model")

        if (!beginMicrophoneCapture()) {
            return
        }

        val config = buildConfigJson(apiKey, model)
        val request = Request.Builder().url(WEBSOCKET_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (!sessionActive) { ws.cancel(); return }
                Log.i(TAG, "socket open, sending config")
                attachWebSocketStream(ws, config)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleResponse(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "socket failure code=${response?.code}", t)
                fail(t.message ?: "Connection failed")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "socket closing $code $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "socket closed $code $reason")
                scope.launch(Dispatchers.Main) { finishCleanup() }
            }
        })
    }

    /** User-initiated stop: stop mic, flush, then wait briefly for trailing finals. */
    fun stop() {
        if (!sessionActive) return
        Log.i(TAG, "stop requested")
        cancelSilenceTimeout()
        stopCapture()
        setState(VoiceListeningState.OFF) // hide UI immediately, results keep flowing
        try {
            webSocket?.send(ByteString.EMPTY) // ask server to finalize
        } catch (e: Exception) {
            Log.w(TAG, "failed to send end frame", e)
        }
        graceJob?.cancel()
        graceJob = scope.launch {
            delay(FLUSH_GRACE_MS)
            finishCleanup()
        }
    }

    fun release() {
        forceReset()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }

    private fun buildConfigJson(apiKey: String, model: String): String {
        val language = context.resources.configuration.locales[0].language
        return json.encodeToString(SonioxConfig(
            apiKey = apiKey,
            model = model,
            audioFormat = AUDIO_FORMAT,
            sampleRate = SAMPLE_RATE,
            numChannels = NUM_CHANNELS,
            languageHints = listOf(language),
            enableEndpointDetection = true,
        ))
    }

    /** Start mic capture immediately so speech during CONNECTING is not lost. */
    private fun beginMicrophoneCapture(): Boolean {
        if (!sessionActive || capturing) return false
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            fail("Unable to initialize audio capture")
            return false
        }
        try {
            @Suppress("MissingPermission")
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 4,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                fail("Microphone unavailable")
                return false
            }
            audioRecord = record
            capturing = true
            record.startRecording()
            Log.i(TAG, "mic capture started (pre-connect buffer)")
            recordingJob = scope.launch {
                val chunk = ByteArray(CHUNK_BYTES)
                while (isActive && capturing) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        val data = chunk.copyOf(read)
                        val level = computeAudioLevel(data, read)
                        scope.launch(Dispatchers.Main) { onAudioLevel(level) }
                        enqueueOrSendAudio(data)
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord.read error: $read")
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            fail(e.message ?: "Audio capture failed")
            return false
        }
    }

    /** Send config, flush pre-connect audio, then stream live PCM on the open socket. */
    private fun attachWebSocketStream(ws: WebSocket, config: String) {
        if (!sessionActive || !capturing) {
            ws.cancel()
            return
        }
        if (!ws.send(config)) {
            fail("Failed to send Soniox config")
            return
        }
        val buffered = synchronized(preConnectLock) {
            val chunks = preConnectChunks.toList()
            preConnectChunks.clear()
            preConnectBufferedBytes = 0
            streamTarget = ws
            chunks
        }
        Log.i(TAG, "flushing ${buffered.size} pre-connect chunk(s)")
        for (chunk in buffered) {
            try {
                if (!ws.send(ByteString.of(*chunk))) {
                    fail("Failed to send buffered audio")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "buffered audio send failed", e)
                fail("Failed to send buffered audio")
                return
            }
        }
        setState(VoiceListeningState.LISTENING)
        startSilenceTimeout()
        Log.i(TAG, "live streaming started")
    }

    private fun enqueueOrSendAudio(data: ByteArray) {
        synchronized(preConnectLock) {
            val ws = streamTarget
            if (ws != null) {
                try {
                    ws.send(ByteString.of(*data))
                } catch (e: Exception) {
                    Log.w(TAG, "audio send failed", e)
                }
            } else {
                bufferPreConnectChunkLocked(data)
            }
        }
    }

    private fun bufferPreConnectChunkLocked(data: ByteArray) {
        preConnectChunks.addLast(data)
        preConnectBufferedBytes += data.size
        while (preConnectBufferedBytes > MAX_PRECONNECT_BYTES && preConnectChunks.isNotEmpty()) {
            preConnectBufferedBytes -= preConnectChunks.removeFirst().size
        }
    }

    private fun handleResponse(text: String) {
        if (!sessionActive) return
        val response = try {
            json.decodeFromString<SonioxResponse>(text)
        } catch (e: Exception) {
            Log.w(TAG, "parse error: $text", e)
            return
        }
        response.errorMessage?.let {
            Log.e(TAG, "server error: $it")
            fail(it)
            return
        }
        val finalSb = StringBuilder()
        val partialSb = StringBuilder()
        for (token in response.tokens) {
            val cleaned = cleanToken(token.text)
            if (cleaned.isEmpty()) continue
            if (token.isFinal) finalSb.append(cleaned)
            else partialSb.append(cleaned)
        }
        if (finalSb.isNotEmpty()) {
            val out = finalSb.toString()
            val partialOut = partialSb.toString()
            scope.launch(Dispatchers.Main) {
                onFinalText(out)
                onPartialText(partialOut)
            }
            resetSilenceTimeout()
        } else if (partialSb.isNotEmpty()) {
            scope.launch(Dispatchers.Main) { onPartialText(partialSb.toString()) }
            resetSilenceTimeout()
        } else {
            scope.launch(Dispatchers.Main) { onPartialText(partialSb.toString()) }
        }
        if (response.finished) {
            scope.launch(Dispatchers.Main) { finishCleanup() }
        }
    }

    /** Drops endpoint/control markers like `<end>` and `<fin>` that Soniox emits as tokens. */
    private fun cleanToken(raw: String): String {
        if (raw.isEmpty()) return ""
        val trimmed = raw.trim()
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) return ""
        return CONTROL_TOKEN_REGEX.replace(raw, "")
    }

    private fun stopCapture() {
        capturing = false
        recordingJob?.cancel()
        recordingJob = null
        synchronized(preConnectLock) {
            preConnectChunks.clear()
            preConnectBufferedBytes = 0
            streamTarget = null
        }
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) { }
        audioRecord = null
    }

    private fun finishCleanup() {
        if (!sessionActive && webSocket == null) return
        graceJob?.cancel()
        graceJob = null
        cancelSilenceTimeout()
        stopCapture()
        sessionActive = false
        try { webSocket?.close(1000, null) } catch (_: Exception) { }
        webSocket = null
        if (uiState != VoiceListeningState.OFF) setState(VoiceListeningState.OFF)
        scope.launch(Dispatchers.Main) { onPartialText("") }
    }

    private fun forceReset() {
        graceJob?.cancel()
        cancelSilenceTimeout()
        stopCapture()
        sessionActive = false
        try { webSocket?.cancel() } catch (_: Exception) { }
        webSocket = null
        uiState = VoiceListeningState.OFF
    }

    private fun fail(message: String) {
        scope.launch(Dispatchers.Main) {
            onError(message)
            finishCleanup()
        }
    }

    private fun setState(newState: VoiceListeningState) {
        val previous = uiState
        uiState = newState
        scope.launch(Dispatchers.Main) {
            if (previous != VoiceListeningState.LISTENING && newState == VoiceListeningState.LISTENING) {
                onListeningReady()
            }
            onStateChanged(newState)
        }
    }

    /** Normalized RMS level (0..1) from 16-bit PCM mono samples. */
    private fun computeAudioLevel(chunk: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val lo = chunk[i].toInt() and 0xFF
            val hi = chunk[i + 1].toInt()
            val sample = (lo or (hi shl 8)).toShort().toInt()
            sumSquares += sample.toDouble() * sample
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        val rms = kotlin.math.sqrt(sumSquares / samples).toFloat() / 32768f
        return (rms * 5f).coerceIn(0f, 1f)
    }

    private fun readSilenceTimeoutSeconds(): Int =
        context.prefs().getInt(Settings.PREF_SONIOX_SILENCE_TIMEOUT, Defaults.PREF_SONIOX_SILENCE_TIMEOUT)

    /** Auto-stop mic after no new partial/final text for the configured interval. */
    private fun startSilenceTimeout() {
        cancelSilenceTimeout()
        val timeoutSec = readSilenceTimeoutSeconds()
        if (timeoutSec > Settings.SONIOX_SILENCE_TIMEOUT_MAX) return
        silenceTimeoutJob = scope.launch {
            delay(timeoutSec * 1000L)
            if (sessionActive) {
                Log.i(TAG, "silence timeout (${timeoutSec}s), stopping dictation")
                stop()
            }
        }
    }

    private fun resetSilenceTimeout() {
        if (!capturing) return
        startSilenceTimeout()
    }

    private fun cancelSilenceTimeout() {
        silenceTimeoutJob?.cancel()
        silenceTimeoutJob = null
    }

    @Serializable
    private data class SonioxConfig(
        @SerialName("api_key") val apiKey: String,
        val model: String,
        @SerialName("audio_format") val audioFormat: String,
        @SerialName("sample_rate") val sampleRate: Int,
        @SerialName("num_channels") val numChannels: Int,
        @SerialName("language_hints") val languageHints: List<String>,
        @SerialName("enable_endpoint_detection") val enableEndpointDetection: Boolean,
    )

    @Serializable
    private data class SonioxResponse(
        val tokens: List<SonioxToken> = emptyList(),
        val finished: Boolean = false,
        @SerialName("error_message") val errorMessage: String? = null,
    )

    @Serializable
    private data class SonioxToken(
        val text: String = "",
        @SerialName("is_final") val isFinal: Boolean = false,
    )

    companion object {
        private const val TAG = "SonioxVoiceInput"
        private const val WEBSOCKET_URL = "wss://stt-rt.soniox.com/transcribe-websocket"
        private const val AUDIO_FORMAT = "s16le"
        private const val SAMPLE_RATE = 16000
        private const val NUM_CHANNELS = 1
        // 16000 Hz * 2 bytes * 0.1 s = 3200 bytes per ~100 ms
        private const val CHUNK_BYTES = 3200
        private const val FLUSH_GRACE_MS = 1500L
        // ~10 s of PCM while waiting for the WebSocket (drop oldest if connect is very slow).
        private const val MAX_PRECONNECT_BYTES = SAMPLE_RATE * 2 * 10
        private val CONTROL_TOKEN_REGEX = Regex("<[^>]*>")
    }
}
