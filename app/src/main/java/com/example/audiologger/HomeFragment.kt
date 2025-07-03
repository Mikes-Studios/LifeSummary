package com.example.audiologger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity

class HomeFragment : Fragment() {

    private lateinit var recordingState: MutableState<Boolean>

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            recordingState.value = toggleService()
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                com.example.audiologger.ui.theme.LifeSummaryTheme {
                    recordingState = remember { mutableStateOf(MicCaptureService.isRunning) }
                    HomeScreen(recordingState.value) {
                        ensurePermsThenToggle()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen(isRecording: Boolean, onToggle: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LargeFloatingActionButton(onClick = onToggle) {
                    val icon = if (isRecording) Icons.Default.Stop else Icons.Default.Mic
                    Icon(
                        icon,
                        contentDescription = if (isRecording) "Stop recording" else "Start recording",
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isRecording) "Recording…" else "Tap to start recording",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    private fun ensurePermsThenToggle() {
        val needs = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val missing = needs.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            recordingState.value = toggleService()
        } else permLauncher.launch(missing.toTypedArray())
    }

    private fun toggleService(): Boolean {
        val svc = Intent(requireContext(), MicCaptureService::class.java)
        val running = MicCaptureService.isRunning
        if (running) requireContext().stopService(svc) else ContextCompat.startForegroundService(requireContext(), svc)
        return !running
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Home"
    }
} 