package me.milk717.rubatomanager.data.repository

import android.util.Log
import me.milk717.rubatomanager.data.api.WhisperClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class WhisperRepository(private val apiKey: String) {

    private val api = WhisperClient.api
    private val authHeader = "Bearer $apiKey"

    suspend fun transcribe(
        audioFile: File,
        language: String = "ko"  // Korean by default
    ): Result<String> {
        return try {
            Log.d(TAG, "Starting transcription for file: ${audioFile.name}, size: ${audioFile.length()} bytes")

            val mediaType = when {
                audioFile.name.endsWith(".wav") -> "audio/wav"
                audioFile.name.endsWith(".m4a") -> "audio/mp4"
                audioFile.name.endsWith(".mp3") -> "audio/mpeg"
                else -> "audio/wav"
            }.toMediaTypeOrNull()

            val requestFile = audioFile.asRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                requestFile
            )

            val modelBody = "whisper-1".toRequestBody("text/plain".toMediaTypeOrNull())
            val languageBody = language.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.transcribeAudio(
                authorization = authHeader,
                file = filePart,
                model = modelBody,
                language = languageBody
            )

            Log.d(TAG, "Transcription successful: ${response.text}")
            Result.success(response.text)

        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "WhisperRepository"
    }
}
