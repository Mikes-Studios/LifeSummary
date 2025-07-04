package com.mikestudios.lifesummary

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.mikestudios.lifesummary.ui.theme.LifeSummaryTheme
import com.mikestudios.lifesummary.ui.theme.primaryGradient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.IconToggleButton

/**
 * Fragment-based Settings screen so it can live inside MainActivity and keep the drawer.
 */
class SettingsFragment : Fragment() {

    private val summaryModels = listOf(
        "gpt-4o",
        "gpt-4.1",
        "chatgpt-4o-latest",
        "gpt-4.1-mini",
        "gpt-4.1-nano",
        "gpt-4o-mini"
    )
    private val transModels = listOf(
        "gpt-4o-mini-transcribe",
        "gpt-4o-transcribe"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LifeSummaryTheme { SettingsScreen() }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen() {
        val context = LocalContext.current

        var apiKey by remember { mutableStateOf(SecurePrefs.getApiKey()) }
        var summaryModel by remember { mutableStateOf(SecurePrefs.getSummaryModel()) }
        var transModel by remember { mutableStateOf(SecurePrefs.getTranscriptionModel()) }
        var scheduleEnabled by remember { mutableStateOf(SecurePrefs.isScheduleEnabled()) }
        var startTime by remember { mutableStateOf(SecurePrefs.getStartTime()) }
        var endTime by remember { mutableStateOf(SecurePrefs.getEndTime()) }
        var syncEnabled by remember { mutableStateOf(SecurePrefs.isDriveSyncEnabled()) }

        Scaffold(
            modifier = Modifier.background(primaryGradient()),
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    // Navigate back to home screen inside MainActivity
                    (requireActivity() as MainActivity).loadFragment(HomeFragment())
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Back to home")
                }
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // API Key
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("OpenAI API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Summary model
                    ModelDropdown("Summary model", summaryModels, summaryModel) { summaryModel = it }

                    // Transcription model
                    ModelDropdown("Transcription model", transModels, transModel) { transModel = it }

                    // Schedule toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable schedule")
                        Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                    }

                    if (scheduleEnabled) {
                        TimeField("Start time", startTime) { startTime = it }
                        TimeField("End time", endTime) { endTime = it }
                    }

                    // Drive sync toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sync to Google Drive")
                        IconToggleButton(
                            checked = syncEnabled,
                            onCheckedChange = { syncEnabled = it }
                        ) {
                            val icon = if (syncEnabled) Icons.Default.CloudDone else Icons.Default.CloudOff
                            Icon(icon, contentDescription = if (syncEnabled) "Synced" else "Not synced")
                        }
                    }

                    // Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = {
                            SecurePrefs.setApiKey(apiKey.trim())
                            SecurePrefs.setSummaryModel(summaryModel)
                            SecurePrefs.setTranscriptionModel(transModel)
                            SecurePrefs.setSchedule(scheduleEnabled, startTime, endTime)
                            SecurePrefs.setDriveSyncEnabled(syncEnabled)

                            val act = requireActivity()
                            if (syncEnabled) {
                                DriveSync.startSignIn(act as Activity)
                            } else {
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                (act as MainActivity).loadFragment(HomeFragment())
                            }
                            ScheduleManager.updateAlarms(context)
                            GlobalScope.launch(Dispatchers.IO) {
                                DriveSync.syncAndMerge(context)
                                DriveSync.syncAndMergeFile(context, "transcripts", "transcripts.txt")
                                listOf(30, 60, 120, 240).forEach { m ->
                                    DriveSync.syncAndMergeFile(context, SummaryUtils.dirName(m), SummaryUtils.fileName(m))
                                }
                            }
                        }) { Text("Save") }

                        OutlinedButton(onClick = {
                            SecurePrefs.clearApiKey()
                            apiKey = ""
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        }) { Text("Delete Key") }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ModelDropdown(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                readOnly = true,
                value = selected,
                onValueChange = {},
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { model ->
                    DropdownMenuItem(text = { Text(model) }, onClick = {
                        onSelect(model)
                        expanded = false
                    })
                }
            }
        }
    }

    @Composable
    private fun TimeField(label: String, time: String, onSelect: (String) -> Unit) {
        val context = LocalContext.current
        Box {
            OutlinedTextField(
                value = time,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        val parts = time.split(":").map { it.toIntOrNull() ?: 0 }
                        val dlg = android.app.TimePickerDialog(
                            context,
                            { _, h, m -> onSelect(String.format("%02d:%02d", h, m)) },
                            parts.getOrNull(0) ?: 9,
                            parts.getOrNull(1) ?: 0,
                            true
                        )
                        dlg.show()
                    }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Settings"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DriveSync.RC_SIGN_IN) {
            GlobalScope.launch(Dispatchers.IO) {
                val ctx = requireContext().applicationContext
                DriveSync.syncAndMerge(ctx)
                DriveSync.syncAndMergeFile(ctx, "transcripts", "transcripts.txt")
                listOf(30,60,120,240).forEach { m ->
                    DriveSync.syncAndMergeFile(ctx, SummaryUtils.dirName(m), SummaryUtils.fileName(m))
                }
                // Close settings on UI thread
                Dispatchers.Main.let {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        (requireActivity() as MainActivity).loadFragment(HomeFragment())
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
} 