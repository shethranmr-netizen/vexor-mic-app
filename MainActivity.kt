package com.vexor.micbooster

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.sqrt

private val Navy = Color(0xFF080F20)
private val Panel = Color(0xFF111D33)
private val Cyan = Color(0xFF48DFFF)

class MainActivity : ComponentActivity() {
    private var recorder: AudioRecord? = null
    private var monitorTrack: AudioTrack? = null
    @Volatile private var dspGain = 1.5f
    @Volatile private var dspBass = 0.35f
    @Volatile private var dspTreble = 0.25f
    @Volatile private var dspClarity = 0.7f
    @Volatile private var recordingMeter = false
    private var meterCallback: ((Float) -> Unit)? = null
    private var permissionCallback: (() -> Unit)? = null
    private var player: MediaPlayer? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permissionCallback?.invoke()
    }
    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            onAudioPicked?.invoke(uri)
        }
    }
    private var onAudioPicked: ((Uri) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var micActive by remember { mutableStateOf(false) }
            var micLevel by remember { mutableFloatStateOf(0f) }
            var gain by remember { mutableFloatStateOf(1.5f) }
            var bass by remember { mutableFloatStateOf(0.35f) }
            var treble by remember { mutableFloatStateOf(0.25f) }
            var clarity by remember { mutableFloatStateOf(0.7f) }
            var selectedAudio by remember { mutableStateOf<Uri?>(null) }
            var audioName by remember { mutableStateOf("No MP3 selected") }
            var audioPlaying by remember { mutableStateOf(false) }
            var audioVolume by remember { mutableFloatStateOf(1f) }
            var audioStatus by remember { mutableStateOf("Choose an MP3 file to begin") }
            var micStatus by remember { mutableStateOf("MIC TEST OFF") }

            meterCallback = { micLevel = it }
            SideEffect {
                dspGain = gain
                dspBass = bass
                dspTreble = treble
                dspClarity = clarity
            }
            onAudioPicked = { uri ->
                selectedAudio = uri
                audioName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected audio"
                stopPlayer()
                try {
                    val mp = MediaPlayer()
                    player = mp
                    mp.setDataSource(this, uri)
                    mp.setOnPreparedListener {
                        it.setVolume(audioVolume, audioVolume)
                        it.start()
                        audioPlaying = true
                        audioStatus = "Playing"
                    }
                    mp.setOnCompletionListener {
                        audioPlaying = false
                        audioStatus = "Playback finished"
                    }
                    mp.setOnErrorListener { _, _, _ ->
                        audioPlaying = false
                        audioStatus = "Could not play this file"
                        true
                    }
                    audioStatus = "Loading audio…"
                    mp.prepareAsync()
                } catch (_: Exception) {
                    audioStatus = "Unable to open this audio file"
                    audioPlaying = false
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                background = Navy, surface = Panel, primary = Cyan,
                onBackground = Color.White, onSurface = Color.White
            )) {
                Column(
                    Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("VEXOR", color = Cyan, style = MaterialTheme.typography.headlineLarge)
                    Text("MIC BOOSTER", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("Voice tools • MP3 soundboard", color = Color(0xFFB7C6DD), style = MaterialTheme.typography.bodySmall)

                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(micStatus, color = if (micActive) Cyan else Color.LightGray)
                            Spacer(Modifier.height(8.dp))
                            Text("${(micLevel * 100).toInt()}%", color = Cyan, style = MaterialTheme.typography.displayMedium)
                            LinearProgressIndicator(
                                progress = { micLevel.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(), color = Cyan
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                if (micActive) {
                                    stopMicMeter()
                                    micActive = false
                                    micStatus = "MIC TEST OFF"
                                    micLevel = 0f
                                } else {
                                    permissionCallback = {
                                        val ok = startMicMeter()
                                        micActive = ok
                                        micStatus = if (ok) "MIC INPUT METER RUNNING" else "MIC COULD NOT START"
                                    }
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        permissionCallback?.invoke()
                                    } else {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }) { Text(if (micActive) "STOP MIC TEST" else "START MIC TEST") }
                            Text("Live processed mic monitoring. Use wired headphones to prevent feedback. This does not replace the mic in other apps.", color = Color(0xFFB7C6DD), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Text("LIVE MIC DSP CONTROLS", color = Cyan, style = MaterialTheme.typography.titleMedium)
                    Control("Mic gain", "${(gain * 100).toInt()}%", gain, 0f, 3f) { gain = it }
                    Control("Bass", "${(bass * 100).toInt()}%", bass, 0f, 1f) { bass = it }
                    Control("Treble", "${(treble * 100).toInt()}%", treble, 0f, 1f) { treble = it }
                    Control("Clarity", "${(clarity * 100).toInt()}%", clarity, 0f, 1f) { clarity = it }

                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("MP3 SOUND PLAYER", color = Cyan, style = MaterialTheme.typography.titleMedium)
                            Text(audioName, color = Color.White)
                            Text(audioStatus, color = Color(0xFFB7C6DD), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { audioPicker.launch(arrayOf("audio/mpeg", "audio/*")) }) {
                                    Text("ADD MP3")
                                }
                                OutlinedButton(
                                    enabled = selectedAudio != null,
                                    onClick = {
                                        val mp = player
                                        if (mp != null && mp.isPlaying) {
                                            mp.pause()
                                            audioPlaying = false
                                            audioStatus = "Paused"
                                        } else if (mp != null) {
                                            try {
                                                mp.start()
                                                audioPlaying = true
                                                audioStatus = "Playing"
                                            } catch (_: Exception) {
                                                audioStatus = "Playback unavailable"
                                            }
                                        } else {
                                            selectedAudio?.let { uri ->
                                                onAudioPicked?.invoke(uri)
                                            }
                                        }
                                    }
                                ) { Text(if (audioPlaying) "PAUSE" else "PLAY") }
                                OutlinedButton(
                                    enabled = selectedAudio != null,
                                    onClick = {
                                        stopPlayer()
                                        audioPlaying = false
                                        audioStatus = "Stopped"
                                    }
                                ) { Text("STOP") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("MP3 playback volume", color = Color.White)
                                Text("${(audioVolume * 200).toInt()}%", color = Cyan)
                            }
                            Slider(
                                value = audioVolume,
                                onValueChange = {
                                    audioVolume = it
                                    player?.setVolume(it, it)
                                },
                                valueRange = 0f..2f
                            )
                            Text("MP3 plays through your phone speaker. It is not digitally injected into WhatsApp or game microphone audio.", color = Color(0xFFFFD28A), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PRESETS", color = Cyan)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Gaming", "Deep Bass", "Clear").forEach { preset ->
                                    OutlinedButton(onClick = {
                                        when (preset) {
                                            "Gaming" -> { gain = 1.7f; bass = 0.3f; treble = 0.45f; clarity = 0.85f }
                                            "Deep Bass" -> { gain = 1.4f; bass = 0.9f; treble = 0.2f; clarity = 0.55f }
                                            else -> { gain = 1.3f; bass = 0.2f; treble = 0.55f; clarity = 1f }
                                        }
                                    }) { Text(preset) }
                                }
                            }
                        }
                    }

                    Text("IMPORTANT: Android 16 on a non-rooted Xiaomi/Redmi/POCO phone does not let a normal app replace the mic input of every other app. The MP3 player currently plays locally; call/game users may not receive it. System-level routing needs a separate device-specific solution.", color = Color(0xFFB7C6DD), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    private fun startMicMeter(): Boolean {
        if (recordingMeter) return true
        val rate = 44100
        val minSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minSize <= 0) return false
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minSize * 2)
        } catch (_: Exception) { return false }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        val outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (outMin <= 0) { rec.release(); return false }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(outMin * 2, minSize * 2))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
        } catch (_: Exception) { rec.release(); return false }
        if (track.state != AudioTrack.STATE_INITIALIZED) { track.release(); rec.release(); return false }
        recorder = rec
        monitorTrack = track
        recordingMeter = true
        try {
            rec.startRecording()
            track.play()
        } catch (_: Exception) {
            recordingMeter = false
            try { rec.release() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
            recorder = null
            monitorTrack = null
            return false
        }
        thread(name = "VexorMicMeter") {
            val buffer = ShortArray(minSize.coerceAtMost(4096))
            val output = ShortArray(buffer.size)
            var low = 0.0
            var previous = 0.0
            while (recordingMeter) {
                val count = try { rec.read(buffer, 0, buffer.size) } catch (_: Exception) { -1 }
                if (count > 0) {
                    var sum = 0.0
                    val g = dspGain.coerceIn(0f, 3f).toDouble()
                    val bassAmount = (dspBass - 0.5f).toDouble() * 1.4
                    val trebleAmount = (dspTreble - 0.5f).toDouble() * 1.2
                    val clarityAmount = dspClarity.toDouble()
                    for (i in 0 until count) {
                        val x = buffer[i].toDouble() / 32768.0
                        low += 0.075 * (x - low)
                        val high = x - low
                        val enhanced = x + bassAmount * low + trebleAmount * high + clarityAmount * (x - previous) * 0.35
                        previous = x
                        val y = (enhanced * g).coerceIn(-1.0, 1.0)
                        output[i] = (y * 32767.0).toInt().toShort()
                        sum += x * x
                    }
                    try { monitorTrack?.write(output, 0, count) } catch (_: Exception) {}
                    val rms = sqrt(sum / count).toFloat()
                    val normalized = (rms * 5f).coerceIn(0f, 1f)
                    runOnUiThread { meterCallback?.invoke(normalized) }
                }
            }
        }
        return true
    }

    private fun stopMicMeter() {
        recordingMeter = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { monitorTrack?.pause() } catch (_: Exception) {}
        try { monitorTrack?.flush() } catch (_: Exception) {}
        try { monitorTrack?.release() } catch (_: Exception) {}
        monitorTrack = null
    }

    private fun stopPlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    override fun onDestroy() {
        stopMicMeter()
        stopPlayer()
        super.onDestroy()
    }
}

@Composable
private fun Control(
    label: String, valueText: String, value: Float, min: Float, max: Float,
    onChange: (Float) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label)
                Text(valueText, color = Cyan)
            }
            Slider(value = value, onValueChange = onChange, valueRange = min..max)
        }
    }
}
