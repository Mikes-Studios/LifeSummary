package com.mikestudios.lifesummary

import android.content.Context
import java.io.File
import com.mikestudios.lifesummary.DriveSync

object SummaryUtils {
    fun dirName(windowMinutes: Int) = "summaries${windowMinutes}"
    fun fileName(windowMinutes: Int) = "summaries${windowMinutes}.txt"

    fun loadSummaries(ctx: Context, windowMinutes: Int): List<LogEntry> {
        val dir = ctx.getExternalFilesDir(dirName(windowMinutes)) ?: return emptyList()
        val f = File(dir, fileName(windowMinutes))
        if (!f.exists()) return emptyList()
        return f.readLines()
            .mapNotNull { line ->
                val idx = line.indexOf(',')
                if (idx <= 0) return@mapNotNull null
                val ts = line.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
                val content = line.substring(idx + 1)
                val parts = content.split('|', limit = 2)
                val text = if (parts.size == 2) {
                    "${parts[0]}\n\n${parts[1]}"
                } else content
                LogEntry(ts, text)
            }
            .asReversed()
    }

    /**
     * Permanently delete a summary (and any associated transcript) identified by its timestamp.
     * Removes the matching line from summaries, transcripts and window-summary files if present.
     */
    fun deleteEntry(ctx: Context, timestamp: Long) {
        val files = mutableListOf<File>()
        // Base summaries & transcripts
        ctx.getExternalFilesDir("summaries")?.let { files += File(it, "summaries.txt") }
        ctx.getExternalFilesDir("transcripts")?.let { files += File(it, "transcripts.txt") }
        // Aggregated summary windows
        listOf(30, 60, 120, 240).forEach { w ->
            ctx.getExternalFilesDir(dirName(w))?.let { dir -> files += File(dir, fileName(w)) }
        }

        val prefix = "${timestamp},"
        files.forEach { f ->
            if (!f.exists()) return@forEach
            val newLines = f.readLines().filterNot { it.startsWith(prefix) }
            f.writeText(newLines.joinToString("\n"))
            // Push updated file to Drive
            DriveSync.uploadFile(ctx, f.parentFile.name, f.name)
        }
        // summaries.txt deletion handled above but ensure remote base summaries updated as well
        DriveSync.uploadFile(ctx, "summaries", "summaries.txt")
    }
} 