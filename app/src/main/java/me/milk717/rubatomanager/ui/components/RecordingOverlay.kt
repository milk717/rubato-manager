package me.milk717.rubatomanager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

sealed class RecordingUiState {
    data object Idle : RecordingUiState()
    data object Recording : RecordingUiState()
    data object ProcessingAudio : RecordingUiState()
    data object Transcribing : RecordingUiState()
    data object ClassifyingIntent : RecordingUiState()
    data object SavingToGithub : RecordingUiState()
}

@Composable
fun RecordingOverlay(
    recordingState: RecordingUiState,
    audioLevel: Float,
    transcribedText: String?,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (recordingState) {
                    is RecordingUiState.Recording -> {
                        RecordingIndicator(audioLevel = audioLevel)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "녹음 중...",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "5초간 무음 시 자동 종료",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Waveform visualization
                        WaveformVisualizer(
                            audioLevel = audioLevel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        FilledTonalButton(
                            onClick = onStopClick,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("녹음 중지")
                        }
                    }

                    is RecordingUiState.ProcessingAudio,
                    is RecordingUiState.Transcribing,
                    is RecordingUiState.ClassifyingIntent,
                    is RecordingUiState.SavingToGithub -> {
                        CircularProgressIndicator()

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = when (recordingState) {
                                is RecordingUiState.ProcessingAudio -> "오디오 처리 중..."
                                is RecordingUiState.Transcribing -> "음성을 텍스트로 변환 중..."
                                is RecordingUiState.ClassifyingIntent -> "메모 분류 중..."
                                is RecordingUiState.SavingToGithub -> "GitHub에 저장 중..."
                                else -> "처리 중..."
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (!transcribedText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = transcribedText,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun RecordingIndicator(
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Combine pulse animation with audio level
    val combinedScale = scale * (1f + audioLevel * 0.3f)

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(combinedScale)
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Recording",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
