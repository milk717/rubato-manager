package me.milk717.rubatomanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.milk717.rubatomanager.BuildConfig
import me.milk717.rubatomanager.ui.theme.RubatoManagerTheme
import me.milk717.rubatomanager.ui.viewmodel.MainViewModel
import me.milk717.rubatomanager.ui.viewmodel.MainViewModelFactory
import me.milk717.rubatomanager.ui.viewmodel.Status
import me.milk717.rubatomanager.ui.viewmodel.UiState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            geminiApiKey = BuildConfig.GEMINI_API_KEY,
            githubToken = BuildConfig.GITHUB_TOKEN,
            githubOwner = BuildConfig.GITHUB_OWNER,
            githubRepo = BuildConfig.GITHUB_REPO,
            githubFilePath = BuildConfig.GITHUB_FILE_PATH,
            githubBranch = BuildConfig.GITHUB_BRANCH
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle intent if app was launched via deep link
        handleIntent(intent)

        setContent {
            RubatoManagerTheme {
                val uiState by viewModel.uiState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        uiState = uiState,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data = intent.data

        Log.d(TAG, "Received intent - Action: $action, Data: $data")

        if (action == Intent.ACTION_VIEW && data != null) {
            val scheme = data.scheme
            val host = data.host

            if (scheme == "rubatomanager" && host == "memo") {
                val text = data.getQueryParameter("text")
                Log.d(TAG, "Extracted text from deep link: $text")

                if (!text.isNullOrBlank()) {
                    viewModel.processVoiceInput(text)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun MainScreen(
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Rubato Manager",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Voice-to-Obsidian Assistant",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (uiState.status) {
            is Status.Idle -> {
                IdleContent()
            }
            is Status.Processing -> {
                ProcessingContent(message = uiState.message)
            }
            is Status.Success -> {
                SuccessContent(uiState = uiState)
            }
            is Status.Error -> {
                ErrorContent(uiState = uiState, error = uiState.status)
            }
        }
    }
}

@Composable
fun IdleContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\"루바토 매니저한테\n[내용]이라고 말해줘\"",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Google 어시스턴트로 메모를 추가하세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ProcessingContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SuccessContent(uiState: UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "저장 완료!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))

            uiState.lastMemo?.let { memo ->
                Text(
                    text = "유형: ${memo.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memo.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun ErrorContent(uiState: UiState, error: Status.Error) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "오류 발생",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            if (uiState.lastInput.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "입력: ${uiState.lastInput}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
