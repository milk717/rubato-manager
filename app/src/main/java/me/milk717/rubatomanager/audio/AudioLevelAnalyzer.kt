package me.milk717.rubatomanager.audio

import kotlin.math.log10
import kotlin.math.sqrt

class AudioLevelAnalyzer {

    /**
     * Calculate RMS (Root Mean Square) value from audio buffer
     * @param buffer PCM 16-bit audio data
     * @param readSize number of samples read
     * @return RMS value normalized to 0.0 - 1.0
     */
    fun calculateRms(buffer: ShortArray, readSize: Int): Double {
        if (readSize <= 0) return 0.0

        var sum = 0.0

        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble() / Short.MAX_VALUE
            sum += sample * sample
        }

        return sqrt(sum / readSize)
    }

    /**
     * Convert RMS to decibels
     * @param rms RMS value (0.0 - 1.0)
     * @return decibel value (negative, -infinity for silence)
     */
    fun rmsToDecibels(rms: Double): Double {
        return if (rms > 0) {
            20 * log10(rms)
        } else {
            Double.NEGATIVE_INFINITY
        }
    }

    /**
     * Check if audio level indicates silence
     */
    fun isSilence(decibelLevel: Double): Boolean {
        return decibelLevel < VadConfig.SILENCE_THRESHOLD_DB
    }
}
