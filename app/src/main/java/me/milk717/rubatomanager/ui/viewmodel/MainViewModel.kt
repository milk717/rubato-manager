package me.milk717.rubatomanager.ui.viewmodel

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.milk717.rubatomanager.audio.RecordingState
import me.milk717.rubatomanager.audio.VoiceRecorder
import me.milk717.rubatomanager.data.model.MemoData
import me.milk717.rubatomanager.data.repository.GithubRepository
import me.milk717.rubatomanager.data.repository.WhisperRepository
import me.milk717.rubatomanager.processor.IntentProcessor
import me.milk717.rubatomanager.ui.components.RecordingUiState
import java.io.File

data class UiState(
    val status: Status = Status.Idle,
    val lastInput: String = "",
    val lastMemo: MemoData? = null,
    val message: String = "",
    val fileContent: String = "",
    val isLoadingFile: Boolean = false,
    val isPromptLoaded: Boolean = false,
    // Recording states
    val recordingState: RecordingUiState = RecordingUiState.Idle,
    val audioLevel: Float = 0f,
    val transcribedText: String? = null
)

sealed class Status {
    data object Idle : Status()
    data object Processing : Status()
    data object Success : Status()
    data class Error(val message: String) : Status()
}

class MainViewModel(
    private val geminiApiKey: String,
    private val githubToken: String,
    private val githubOwner: String,
    private val githubRepo: String,
    private val githubFilePath: String = "00_obsidian-meta/rubato-manager.md",
    private val githubBranch: String = "main",
    private val openAiApiKey: String = ""
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val intentProcessor = IntentProcessor(geminiApiKey)
    private val githubRepository = GithubRepository(
        owner = githubOwner,
        repo = githubRepo,
        filePath = githubFilePath,
        token = githubToken,
        branch = githubBranch
    )
    private val whisperRepository = WhisperRepository(openAiApiKey)

    private var voiceRecorder: VoiceRecorder? = null

    init {
        loadAll()
    }

    fun initVoiceRecorder(context: Context) {
        voiceRecorder = VoiceRecorder(context)

        // Observe recording state
        viewModelScope.launch {
            voiceRecorder?.recordingState?.collect { state ->
                when (state) {
                    is RecordingState.Idle -> {
                        _uiState.value = _uiState.value.copy(
                            recordingState = RecordingUiState.Idle
                        )
                    }
                    is RecordingState.Recording -> {
                        _uiState.value = _uiState.value.copy(
                            recordingState = RecordingUiState.Recording,
                            message = "녹음 중... (5초간 무음 시 자동 종료)"
                        )
                    }
                    is RecordingState.Processing -> {
                        _uiState.value = _uiState.value.copy(
                            recordingState = RecordingUiState.ProcessingAudio,
                            message = "오디오 처리 중..."
                        )
                    }
                    is RecordingState.Completed -> {
                        processAudioFile(state.audioFile)
                    }
                    is RecordingState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            recordingState = RecordingUiState.Idle,
                            status = Status.Error(state.message),
                            message = "녹음 실패: ${state.message}"
                        )
                    }
                }
            }
        }

        // Observe audio levels
        viewModelScope.launch {
            voiceRecorder?.audioLevel?.collect { level ->
                _uiState.value = _uiState.value.copy(
                    audioLevel = level.rms.toFloat().coerceIn(0f, 1f)
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        voiceRecorder?.startRecording(viewModelScope)
    }

    fun stopRecording() {
        voiceRecorder?.stopRecording()
    }

    private fun processAudioFile(audioFile: File) {
        viewModelScope.launch {
            // Step 1: Transcribe with Whisper
            _uiState.value = _uiState.value.copy(
                recordingState = RecordingUiState.Transcribing,
                message = "음성을 텍스트로 변환 중..."
            )

            val transcriptionResult = whisperRepository.transcribe(audioFile)

            transcriptionResult.fold(
                onSuccess = { text ->
                    // Check if transcription is empty
                    if (text.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            recordingState = RecordingUiState.Idle,
                            status = Status.Error("음성이 인식되지 않았습니다"),
                            message = "음성이 인식되지 않았습니다. 다시 시도해주세요."
                        )
                        return@launch
                    }

                    _uiState.value = _uiState.value.copy(
                        transcribedText = text,
                        message = "변환 완료: $text"
                    )

                    // Proceed to classification
                    processTranscribedText(text)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        recordingState = RecordingUiState.Idle,
                        status = Status.Error(error.message ?: "STT 실패"),
                        message = "음성 변환 실패: ${error.message}"
                    )
                }
            )

            // Clean up audio file
            audioFile.delete()
        }
    }

    private suspend fun processTranscribedText(text: String) {
        // Step 2: Classify with Gemini
        _uiState.value = _uiState.value.copy(
            recordingState = RecordingUiState.ClassifyingIntent,
            status = Status.Processing,
            lastInput = text,
            message = "Gemini로 분석 중..."
        )

        val processResult = intentProcessor.process(text)

        processResult.fold(
            onSuccess = { memoData ->
                // Step 3: Save to GitHub
                _uiState.value = _uiState.value.copy(
                    recordingState = RecordingUiState.SavingToGithub,
                    lastMemo = memoData,
                    message = "GitHub에 저장 중..."
                )

                val saveResult = githubRepository.appendMemo(memoData)

                saveResult.fold(
                    onSuccess = { commitUrl ->
                        _uiState.value = _uiState.value.copy(
                            recordingState = RecordingUiState.Idle,
                            status = Status.Success,
                            message = "저장 완료!",
                            transcribedText = null
                        )
                        Log.i(TAG, "Voice memo saved successfully: $commitUrl")
                        loadMemoContent()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            recordingState = RecordingUiState.Idle,
                            status = Status.Error(error.message ?: "저장 실패"),
                            message = "GitHub 저장 실패: ${error.message}"
                        )
                    }
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    recordingState = RecordingUiState.Idle,
                    status = Status.Error(error.message ?: "분석 실패"),
                    message = "Gemini 분석 실패: ${error.message}"
                )
            }
        )
    }

    fun loadAll() {
        _uiState.value = _uiState.value.copy(isLoadingFile = true)

        viewModelScope.launch {
            // Load prompt and file content in parallel
            val promptDeferred = async { loadPrompt() }
            val contentDeferred = async { loadMemoContent() }

            promptDeferred.await()
            contentDeferred.await()

            _uiState.value = _uiState.value.copy(isLoadingFile = false)
        }
    }

    private suspend fun loadPrompt() {
        val result = githubRepository.getFileContentByPath(PROMPT_FILE_PATH)

        result.fold(
            onSuccess = { prompt ->
                intentProcessor.updatePrompt(prompt)
                _uiState.value = _uiState.value.copy(isPromptLoaded = true)
                Log.i(TAG, "Prompt loaded successfully")
            },
            onFailure = { error ->
                // Use default prompt if file not found
                _uiState.value = _uiState.value.copy(isPromptLoaded = false)
                Log.w(TAG, "Failed to load prompt, using default: ${error.message}")
            }
        )
    }

    private suspend fun loadMemoContent() {
        val result = githubRepository.getFileContent()

        result.fold(
            onSuccess = { content ->
                _uiState.value = _uiState.value.copy(fileContent = content)
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    fileContent = "파일을 불러올 수 없습니다: ${error.message}"
                )
                Log.e(TAG, "Failed to load file content", error)
            }
        )
    }

    fun processInput(text: String) {
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(
                status = Status.Error("입력된 텍스트가 없습니다."),
                message = "텍스트가 비어있습니다."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            status = Status.Processing,
            lastInput = text,
            message = "Gemini로 분석 중..."
        )

        viewModelScope.launch {
            val processResult = intentProcessor.process(text)

            processResult.fold(
                onSuccess = { memoData ->
                    _uiState.value = _uiState.value.copy(
                        lastMemo = memoData,
                        message = "GitHub에 저장 중..."
                    )

                    val saveResult = githubRepository.appendMemo(memoData)

                    saveResult.fold(
                        onSuccess = { commitUrl ->
                            _uiState.value = _uiState.value.copy(
                                status = Status.Success,
                                lastInput = text,
                                lastMemo = memoData,
                                message = "저장 완료!"
                            )
                            Log.i(TAG, "Memo saved successfully: $commitUrl")
                            // Reload file content after successful save
                            launch { loadMemoContent() }
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                status = Status.Error(error.message ?: "GitHub 저장 실패"),
                                lastInput = text,
                                lastMemo = memoData,
                                message = "GitHub 저장 실패: ${error.message}"
                            )
                            Log.e(TAG, "GitHub save failed", error)
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        status = Status.Error(error.message ?: "Gemini 처리 실패"),
                        lastInput = text,
                        message = "Gemini 분석 실패: ${error.message}"
                    )
                    Log.e(TAG, "Gemini processing failed", error)
                }
            )
        }
    }

    fun resetStatus() {
        _uiState.value = _uiState.value.copy(
            status = Status.Idle,
            message = "",
            recordingState = RecordingUiState.Idle,
            transcribedText = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder?.reset()
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val PROMPT_FILE_PATH = "01_Permanent/prompt/rubato-manager.md"
    }
}
