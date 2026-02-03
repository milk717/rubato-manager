package me.milk717.rubatomanager.audio

import android.media.AudioFormat

object VadConfig {
    const val SAMPLE_RATE = 44100
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val BUFFER_SIZE_MULTIPLIER = 2

    // VAD parameters
    const val SILENCE_THRESHOLD_DB = -40.0  // Decibels below which is silence
    const val SILENCE_DURATION_MS = 5000L   // 5 seconds of silence to stop
    const val MIN_RECORDING_DURATION_MS = 1000L  // Minimum recording duration
    const val AUDIO_LEVEL_UPDATE_INTERVAL_MS = 50L  // Update UI every 50ms
}
