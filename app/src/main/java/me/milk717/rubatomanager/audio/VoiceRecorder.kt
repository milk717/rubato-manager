package me.milk717.rubatomanager.audio

import android.Manifest
import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream

sealed class RecordingState {
    data object Idle : RecordingState()
    data object Recording : RecordingState()
    data object Processing : RecordingState()
    data class Completed(val audioFile: File) : RecordingState()
    data class Error(val message: String) : RecordingState()
}

data class AudioLevel(
    val rms: Double,          // 0.0 - 1.0
    val decibels: Double,     // -infinity to 0
    val isSilence: Boolean
)

class VoiceRecorder(private val context: Context) {

    private val audioLevelAnalyzer = AudioLevelAnalyzer()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _audioLevel = MutableSharedFlow<AudioLevel>(replay = 1)
    val audioLevel: SharedFlow<AudioLevel> = _audioLevel

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val bufferSize: Int by lazy {
        val minBufferSize = AudioRecord.getMinBufferSize(
            VadConfig.SAMPLE_RATE,
            VadConfig.CHANNEL_CONFIG,
            VadConfig.AUDIO_FORMAT
        )
        minBufferSize * VadConfig.BUFFER_SIZE_MULTIPLIER
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(scope: CoroutineScope) {
        if (isRecording) return

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                _recordingState.value = RecordingState.Recording
                isRecording = true

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    VadConfig.SAMPLE_RATE,
                    VadConfig.CHANNEL_CONFIG,
                    VadConfig.AUDIO_FORMAT,
                    bufferSize
                )

                val outputFile = createAudioFile()
                val buffer = ShortArray(bufferSize / 2)
                val audioData = mutableListOf<Short>()

                var silenceStartTime = 0L
                val recordingStartTime = System.currentTimeMillis()

                audioRecord?.startRecording()
                Log.d(TAG, "Recording started")

                while (isRecording && isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (readSize > 0) {
                        // Store audio data
                        for (i in 0 until readSize) {
                            audioData.add(buffer[i])
                        }

                        // Calculate audio level
                        val rms = audioLevelAnalyzer.calculateRms(buffer, readSize)
                        val decibels = audioLevelAnalyzer.rmsToDecibels(rms)
                        val isSilence = audioLevelAnalyzer.isSilence(decibels)

                        _audioLevel.emit(AudioLevel(rms, decibels, isSilence))

                        // VAD logic
                        val currentTime = System.currentTimeMillis()
                        val recordingDuration = currentTime - recordingStartTime

                        if (isSilence) {
                            if (silenceStartTime == 0L) {
                                silenceStartTime = currentTime
                            }

                            val silenceDuration = currentTime - silenceStartTime

                            // Auto-stop after 5 seconds of silence (if minimum duration met)
                            if (silenceDuration >= VadConfig.SILENCE_DURATION_MS &&
                                recordingDuration >= VadConfig.MIN_RECORDING_DURATION_MS) {
                                Log.d(TAG, "Auto-stopping due to ${silenceDuration}ms of silence")
                                break
                            }
                        } else {
                            silenceStartTime = 0L
                        }
                    }

                    delay(VadConfig.AUDIO_LEVEL_UPDATE_INTERVAL_MS)
                }

                // Check if any meaningful audio was recorded
                val minSamplesRequired = VadConfig.SAMPLE_RATE / 2  // At least 0.5 seconds of audio
                if (audioData.size < minSamplesRequired) {
                    Log.w(TAG, "No meaningful audio recorded: ${audioData.size} samples")
                    _recordingState.value = RecordingState.Error("음성이 입력되지 않았습니다")
                    return@launch
                }

                // Save audio to file
                _recordingState.value = RecordingState.Processing
                Log.d(TAG, "Saving ${audioData.size} samples to file")
                saveToWavFile(audioData, outputFile)

                _recordingState.value = RecordingState.Completed(outputFile)
                Log.d(TAG, "Recording completed: ${outputFile.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                _recordingState.value = RecordingState.Error(e.message ?: "Recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    fun stopRecording() {
        Log.d(TAG, "Stop recording requested")
        isRecording = false
    }

    fun reset() {
        stopRecording()
        recordingJob?.cancel()
        _recordingState.value = RecordingState.Idle
    }

    private fun createAudioFile(): File {
        val cacheDir = context.cacheDir
        val timestamp = System.currentTimeMillis()
        return File(cacheDir, "recording_$timestamp.wav")
    }

    private fun saveToWavFile(audioData: List<Short>, file: File) {
        val byteData = ByteArray(audioData.size * 2)
        for (i in audioData.indices) {
            val sample = audioData[i].toInt()
            byteData[i * 2] = (sample and 0xff).toByte()
            byteData[i * 2 + 1] = ((sample shr 8) and 0xff).toByte()
        }

        FileOutputStream(file).use { fos ->
            // Write WAV header
            writeWavHeader(fos, byteData.size)
            // Write audio data
            fos.write(byteData)
        }
    }

    private fun writeWavHeader(fos: FileOutputStream, audioLength: Int) {
        val totalLength = audioLength + 36
        val channels = 1
        val bitsPerSample = 16
        val byteRate = VadConfig.SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // File size
        header[4] = (totalLength and 0xff).toByte()
        header[5] = ((totalLength shr 8) and 0xff).toByte()
        header[6] = ((totalLength shr 16) and 0xff).toByte()
        header[7] = ((totalLength shr 24) and 0xff).toByte()

        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Chunk size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // Audio format (1 for PCM)
        header[20] = 1
        header[21] = 0

        // Number of channels
        header[22] = channels.toByte()
        header[23] = 0

        // Sample rate
        header[24] = (VadConfig.SAMPLE_RATE and 0xff).toByte()
        header[25] = ((VadConfig.SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((VadConfig.SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((VadConfig.SAMPLE_RATE shr 24) and 0xff).toByte()

        // Byte rate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // Block align
        header[32] = blockAlign.toByte()
        header[33] = 0

        // Bits per sample
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Data size
        header[40] = (audioLength and 0xff).toByte()
        header[41] = ((audioLength shr 8) and 0xff).toByte()
        header[42] = ((audioLength shr 16) and 0xff).toByte()
        header[43] = ((audioLength shr 24) and 0xff).toByte()

        fos.write(header)
    }

    private fun releaseRecorder() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        isRecording = false
    }

    companion object {
        private const val TAG = "VoiceRecorder"
    }
}
