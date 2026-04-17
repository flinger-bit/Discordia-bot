package com.discordia.terminal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.media.AudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlin.math.sin
import kotlin.math.PI

object SoundManager {

    private var toneGen: ToneGenerator? = null

    fun init(context: Context) {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 60)
        } catch (_: Exception) {}
    }

    fun playClick() {
        try { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 40) } catch (_: Exception) {}
    }

    fun playSuccess() {
        CoroutineScope(Dispatchers.IO).launch { playTones(listOf(880 to 80, 1100 to 120)) }
    }

    fun playError() {
        CoroutineScope(Dispatchers.IO).launch { playTones(listOf(440 to 80, 330 to 150)) }
    }

    fun playStartup() {
        CoroutineScope(Dispatchers.IO).launch { playTones(listOf(440 to 60, 550 to 60, 660 to 100)) }
    }

    fun playKeypress() {
        try { toneGen?.startTone(ToneGenerator.TONE_DTMF_0, 20) } catch (_: Exception) {}
    }

    fun playBuild() {
        CoroutineScope(Dispatchers.IO).launch { playTones(listOf(523 to 60, 659 to 60, 784 to 100, 1047 to 160)) }
    }

    private fun playTones(notes: List<Pair<Int, Int>>) {
        try {
            val sampleRate = 44100
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            var totalSamples = notes.sumOf { (_, ms) -> (sampleRate * ms / 1000) }
            val buffer = ShortArray(totalSamples)
            var pos = 0
            for ((freq, ms) in notes) {
                val count = sampleRate * ms / 1000
                for (i in 0 until count) {
                    val t = i.toDouble() / sampleRate
                    val envelope = when {
                        i < count * 0.05 -> i.toDouble() / (count * 0.05)
                        i > count * 0.7 -> 1.0 - (i - count * 0.7) / (count * 0.3)
                        else -> 1.0
                    }
                    buffer[pos++] = (Short.MAX_VALUE * 0.4 * envelope * sin(2 * PI * freq * t)).toInt().toShort()
                }
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(notes.sumOf { it.second }.toLong() + 50)
            track.release()
        } catch (_: Exception) {}
    }

    fun release() {
        toneGen?.release()
        toneGen = null
    }
}
