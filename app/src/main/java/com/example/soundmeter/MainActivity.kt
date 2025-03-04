package com.example.soundmeter

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.soundmeter.ui.theme.SoundMeterTheme
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.log10

class MainActivity : ComponentActivity() {

    // Holds the current decibel level
    private val _decibelLevel = mutableStateOf(0f)
    private val decibelLevel: State<Float> get() = _decibelLevel

    // AudioRecord configuration
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Set a noise threshold in db
    private val noiseThreshold = 80f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request microphone permission if not granted
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        } else {
            startAudioRecording()
        }

        setContent {
            SoundMeterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SoundMeterScreen(
                        decibelLevel = decibelLevel.value,
                        noiseThreshold = noiseThreshold,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioEncoding,
            bufferSize
        )
        audioRecord?.startRecording()

        // Coroutine to continuously read audio data
        recordingJob = CoroutineScope(Dispatchers.Default).launch {
            val audioBuffer = ShortArray(bufferSize)
            while (isActive) {
                val readCount = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (readCount > 0) {
                    var maxAmplitude = 0
                    for (i in 0 until readCount) {
                        maxAmplitude = maxAmplitude.coerceAtLeast(abs(audioBuffer[i].toInt()))
                    }
                    // Ensuring a minimum amplitude value
                    val amplitude = if (maxAmplitude == 0) 1f else maxAmplitude.toFloat()
                    // Convert amplitude to dB using: dB = 20 * log10(amplitude)
                    val dB = 20 * log10(amplitude)
                    withContext(Dispatchers.Main) {
                        _decibelLevel.value = dB
                    }
                }
                delay(50) // Read audio data every 50ms
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
    }
}

@Composable
fun SoundMeterScreen(decibelLevel: Float, noiseThreshold: Float, modifier: Modifier = Modifier) {
    val maxDb = 90f
    val progress = (decibelLevel / maxDb).coerceIn(0f, 1f)
    // Change the color if noise exceeds the threshold
    val meterColor = if (decibelLevel >= noiseThreshold) Color.Red else Color.Green

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Sound Meter", fontSize = 32.sp, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(20.dp),
            color = meterColor,
            trackColor = Color.DarkGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "dB: ${"%.1f".format(decibelLevel)}", fontSize = 24.sp, color = Color.White)
        if (decibelLevel >= noiseThreshold) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Noise level too high!", fontSize = 24.sp, color = Color.Red)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SoundMeterScreenPreview() {
    SoundMeterTheme {
        SoundMeterScreen(decibelLevel = 40f, noiseThreshold = 80f)
    }
}
