package com.example.audiologger

import android.content.Context
import java.io.File

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
} 