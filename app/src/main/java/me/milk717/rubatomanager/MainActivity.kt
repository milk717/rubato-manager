package me.milk717.rubatomanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mikepenz.markdown.m3.Markdown
import me.milk717.rubatomanager.ui.components.RecordingOverlay
import me.milk717.rubatomanager.ui.components.RecordingUiState
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
            githubBranch = BuildConfig.GITHUB_BRANCH,
            openAiApiKey = BuildConfig.OPENAI_API_KEY
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecordingWithPermission()
        } else {
            Log.w(TAG, "Microphone permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize voice recorder
        viewModel.initVoiceRecorder(applicationContext)

        handleIntent(intent)

        setContent {
            RubatoManagerTheme {
                val uiState by viewModel.uiState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScreen(
                            uiState = uiState,
                            onSendClick = { text -> viewModel.processInput(text) },
                            onRefreshClick = { viewModel.loadAll() },
                            onStartRecording = { checkPermissionAndRecord() },
                            modifier = Modifier.padding(innerPadding)
                        )

                        // Recording overlay
                        if (uiState.recordingState != RecordingUiState.Idle) {
                            RecordingOverlay(
                                recordingState = uiState.recordingState,
                                audioLevel = uiState.audioLevel,
                                transcribedText = uiState.transcribedText,
                                onStopClick = { viewModel.stopRecording() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun checkPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecordingWithPermission()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startRecordingWithPermission() {
        viewModel.startRecording()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data = intent.data
        val source = intent.getStringExtra("source")
        val feature = intent.getStringExtra("feature") ?: intent.getStringExtra("featureParam")
        val referrer = referrer?.toString() ?: ""

        Log.d(TAG, "Received intent - Action: $action, Data: $data, Source: $source, Feature: $feature, Referrer: $referrer")

        // Check if opened via voice shortcut (App Actions)
        if (source == "voice_shortcut") {
            Log.d(TAG, "Voice shortcut triggered via App Actions")
            checkPermissionAndRecord()
            return
        }

        // Check if opened via OPEN_APP_FEATURE with feature parameter
        if (feature != null) {
            Log.d(TAG, "OPEN_APP_FEATURE triggered with feature: $feature")
            checkPermissionAndRecord()
            return
        }

        // Check if opened from Google Assistant
        if (referrer.contains("google") && referrer.contains("assistant")) {
            Log.d(TAG, "Opened from Google Assistant")
            checkPermissionAndRecord()
            return
        }

        if (action == Intent.ACTION_VIEW && data != null) {
            val scheme = data.scheme
            val host = data.host

            if (scheme == "rubatomanager") {
                when (host) {
                    "memo" -> {
                        // Text memo flow
                        val text = data.getQueryParameter("text")
                        Log.d(TAG, "Extracted text from deep link: $text")

                        if (!text.isNullOrBlank()) {
                            viewModel.processInput(text)
                        }
                    }
                    "voice" -> {
                        // Voice recording flow - auto start recording
                        Log.d(TAG, "Voice recording flow triggered")
                        checkPermissionAndRecord()
                    }
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
    onSendClick: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Rubato Manager",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Voice-to-Obsidian Assistant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRefreshClick) {
                Text("새로고침")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Voice Recording Button
        FilledTonalButton(
            onClick = onStartRecording,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.status !is Status.Processing && uiState.recordingState == RecordingUiState.Idle
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Record")
            Spacer(modifier = Modifier.width(8.dp))
            Text("음성으로 메모하기")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("메모 내용을 입력하세요...") },
                singleLine = true,
                enabled = uiState.status !is Status.Processing
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onSendClick(inputText)
                    inputText = ""
                },
                enabled = inputText.isNotBlank() && uiState.status !is Status.Processing
            ) {
                Text("전송")
            }
        }

        // Status Message
        if (uiState.message.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusCard(uiState = uiState)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // File Content Section
        Text(
            text = "저장된 메모",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (uiState.isLoadingFile) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.fileContent.isBlank()) {
                    Text(
                        text = "저장된 메모가 없습니다.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Markdown(
                            content = uiState.fileContent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(uiState: UiState) {
    val (containerColor, contentColor) = when (uiState.status) {
        is Status.Processing -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        is Status.Success -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is Status.Error -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.status is Status.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = uiState.message,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
