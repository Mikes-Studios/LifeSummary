package com.mikestudios.lifesummary

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mikestudios.lifesummary.ui.theme.LifeSummaryTheme
import com.mikestudios.lifesummary.ui.theme.primaryGradient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.mikestudios.lifesummary.ScheduleManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.mikestudios.lifesummary.DriveSync
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.IconToggleButton

class SettingsActivity : AppCompatActivity() {

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeSummaryTheme {
                SettingsScreen()
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
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    (context as? Activity)?.finish()
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
                    // API key field
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("OpenAI API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Summary model dropdown
                    ModelDropdown(
                        label = "Summary model",
                        options = summaryModels,
                        selected = summaryModel,
                        onSelect = { summaryModel = it }
                    )

                    // Transcription model dropdown
                    ModelDropdown(
                        label = "Transcription model",
                        options = transModels,
                        selected = transModel,
                        onSelect = { transModel = it }
                    )

                    // --- Scheduling controls ---
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

                    // --- Drive sync toggle ---
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

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = {
                            SecurePrefs.setApiKey(apiKey.trim())
                            SecurePrefs.setSummaryModel(summaryModel)
                            SecurePrefs.setTranscriptionModel(transModel)
                            SecurePrefs.setSchedule(scheduleEnabled, startTime, endTime)
                            SecurePrefs.setDriveSyncEnabled(syncEnabled)
                            val act = context as Activity
                            if (syncEnabled) {
                                DriveSync.startSignIn(act)
                            } else {
                                // No Drive sign-in pending – close immediately
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                act.finish()
                            }
                            ScheduleManager.updateAlarms(context)
                            GlobalScope.launch(Dispatchers.IO) {
                                DriveSync.syncAndMerge(this@SettingsActivity)
                                DriveSync.syncAndMergeFile(this@SettingsActivity, "transcripts", "transcripts.txt")
                                listOf(30,60,120,240).forEach { m ->
                                    DriveSync.syncAndMergeFile(
                                        this@SettingsActivity,
                                        SummaryUtils.dirName(m),
                                        SummaryUtils.fileName(m)
                                    )
                                }
                            }
                        }) {
                            Text("Save")
                        }

                        OutlinedButton(onClick = {
                            SecurePrefs.clearApiKey()
                            apiKey = ""
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Delete Key")
                        }
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
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onSelect(model)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    /** Simple read-only time field that pops a [TimePickerDialog] */
    @Composable
    private fun TimeField(label: String, time: String, onSelect: (String) -> Unit) {
        val context = LocalContext.current
        Box {
            OutlinedTextField(
                value = time,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
            )

            // Transparent overlay that captures taps reliably
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (requestCode == DriveSync.RC_SIGN_IN) {
            // After the user grants Drive permissions, download existing summaries
            GlobalScope.launch(Dispatchers.IO) {
                DriveSync.syncAndMerge(this@SettingsActivity)
                DriveSync.syncAndMergeFile(this@SettingsActivity, "transcripts", "transcripts.txt")
                // once merge done, close settings on UI thread
                kotlinx.coroutines.Dispatchers.Main.let {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        finish()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
} 