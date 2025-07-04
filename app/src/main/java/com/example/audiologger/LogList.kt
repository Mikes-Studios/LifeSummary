package com.mikestudios.lifesummary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.content.Intent
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListWithSearch(
    allEntries: List<LogEntry>,
    onDelete: ((LogEntry) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // State for incremental loading
    val listState = rememberLazyListState()
    var batchSize by remember { mutableStateOf(20) }
    val loadMoreThreshold = 2 // when 2 items from bottom, load more
    
    Column {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { },
            active = false,
            onActiveChange = { },
            placeholder = { Text("Search...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) { }
        
        // Filter entries based on search
        val filtered = remember(searchQuery, allEntries.size) {
            if (searchQuery.isBlank()) allEntries
            else allEntries.filter { 
                it.text.contains(searchQuery, ignoreCase = true) 
            }
        }
        
        // Slice based on current batch size
        val visible = remember(filtered.size, batchSize) {
            filtered.take(batchSize)
        }

        // Observe scroll to trigger loading next batch
        LaunchedEffect(listState, filtered.size) {
            snapshotFlow {
                val lastVisible = listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size
                lastVisible
            }
                .distinctUntilChanged()
                .filter { last -> last >= batchSize - loadMoreThreshold }
                .collectLatest {
                    if (batchSize < filtered.size) {
                        batchSize = (batchSize + 20).coerceAtMost(filtered.size)
                    }
                }
        }
        
        LogList(visible, listState, onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogList(
    entries: List<LogEntry>,
    listState: LazyListState,
    onDelete: ((LogEntry) -> Unit)? = null
) {
    if (entries.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No entries yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Start recording to see your transcripts here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // Group entries by date
        val groupedEntries = remember(entries) {
            entries.groupBy { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                val today = Calendar.getInstance()
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                
                when {
                    isSameDay(cal, today) -> "Today"
                    isSameDay(cal, yesterday) -> "Yesterday"
                    else -> SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                        .format(Date(entry.timestamp))
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp), // Space for FAB
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState
        ) {
            groupedEntries.forEach { (dateHeader, dayEntries) ->
                stickyHeader {
                    Text(
                        text = dateHeader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(dayEntries, key = { it.timestamp }) { entry ->
                    LogEntryCard(entry, onDelete)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogEntryCard(entry: LogEntry, onDelete: ((LogEntry) -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer { alpha = 0.85f }
            .animateContentSize()
            .combinedClickable(
                onClick = {
                    expanded = !expanded
                    if (!expanded) showTranscript = false // reset when collapsing
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Time stamp
            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Parse summary & transcript only once per entry for better performance
            val (summaryText, transcriptText) = remember(entry) {
                val lines = entry.text.lines()
                val delimIdx = lines.indexOfFirst { it.trim() == "---" }
                val summary = if (delimIdx >= 0) {
                    lines.take(delimIdx).joinToString("\n").trim()
                } else entry.text.trim()

                val transcript = if (delimIdx >= 0) {
                    lines.drop(delimIdx + 1).joinToString("\n").trim().takeIf { it.isNotBlank() }
                } else null

                summary to transcript
            }

            if (expanded) {
                // Expanded state: Summary, then optional transcript
                SelectionContainer {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (showTranscript && !transcriptText.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = transcriptText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f)
                        )
                    }
                }
            } else {
                // Collapsed state: Preview (no SelectionContainer for efficiency)
                Column {
                    Text(
                        text = summaryText.lines().firstOrNull() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    val previewBody = summaryText.lines().drop(1)
                        .joinToString(" ")
                        .take(100)
                    Text(
                        text = previewBody + if (summaryText.length > 100) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            
            // Action buttons (Copy, Share, Delete, Transcript toggle)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                val clipboard = LocalClipboardManager.current

                // Copy
                IconButton(onClick = {
                    val copyText = if (showTranscript && !transcriptText.isNullOrBlank()) {
                        "$summaryText\n\n---\n$transcriptText"
                    } else summaryText
                    clipboard.setText(AnnotatedString(copyText))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }

                // Share
                IconButton(onClick = {
                    val shareText = if (showTranscript && !transcriptText.isNullOrBlank()) {
                        "$summaryText\n\n---\n$transcriptText"
                    } else summaryText
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }

                // Delete (if provided)
                if (onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }

                // Transcript toggle (only if transcript exists)
                if (!transcriptText.isNullOrBlank()) {
                    val label = if (showTranscript) "Hide Transcript" else "Show Transcript"
                    TextButton(onClick = {
                        if (!expanded) expanded = true
                        showTranscript = !showTranscript
                    }) {
                        Text(label)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete entry?") },
            text = { Text("This will permanently delete the summary and its transcript.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete?.invoke(entry)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
} 