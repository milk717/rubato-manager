package me.milk717.rubatomanager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.milk717.rubatomanager.data.model.MemoData
import me.milk717.rubatomanager.data.repository.GithubRepository
import me.milk717.rubatomanager.processor.IntentProcessor

data class UiState(
    val status: Status = Status.Idle,
    val lastInput: String = "",
    val lastMemo: MemoData? = null,
    val message: String = "",
    val fileContent: String = "",
    val isLoadingFile: Boolean = false,
    val isPromptLoaded: Boolean = false
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
    private val githubBranch: String = "main"
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

    init {
        loadAll()
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
            message = ""
        )
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val PROMPT_FILE_PATH = "01_Permanent/prompt/rubato-manager.md"
    }
}
