package com.example.audiologger

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import com.example.audiologger.SecurePrefs
import java.util.concurrent.TimeUnit

/**
 * Worker that sends the recorded audio to the OpenAI Audio endpoint for
 * transcription, then summarises the transcription using Chat Completions.
 *
 * Update the OPENAI_API_KEY build config field with your own key before use.
 */
class TranscribeWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "TranscribeWorker"
        private const val OPENAI_BASE = "https://api.openai.com/v1"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val path = inputData.getString("path") ?: return@withContext Result.failure()
        val audioFile = File(path)
        if (!audioFile.exists()) return@withContext Result.failure()
        val apiKey = SecurePrefs.getApiKey()
        if (apiKey.isBlank()) {
            Log.e(TAG, "Missing OpenAI API key – set it in Settings")
            return@withContext Result.failure()
        }

        // Ensure the recording file has been fully written before processing.
        var lastLen = -1L
        repeat(10) { // wait up to ~3s total
            val cur = audioFile.length()
            if (cur > 0 && cur == lastLen) return@repeat
            lastLen = cur
            kotlinx.coroutines.delay(300)
        }
        // one final check
        if (audioFile.length() <= 0) {
            Log.e(TAG, "Audio file appears empty – aborting")
            return@withContext Result.failure()
        }

        val client = OkHttpClient()
        return@withContext try {
            val transcript = transcribeAudio(client, apiKey, audioFile)
            if (transcript.isBlank() || transcript.length < 5 ) {
                Log.d(TAG, "Transcript too short (${transcript.length} chars) – skipping")
                // Delete the raw recording after processing attempt
                audioFile.delete()
                return@withContext Result.success()
            }
            Log.i(TAG, "Transcript: $transcript")
            // Use one timestamp for both transcript and summary so they can be correlated later
            val now = System.currentTimeMillis()

            // Persist full transcript to file for later viewing
            applicationContext.getExternalFilesDir("transcripts")!!.apply {
                mkdirs()
                File(this, "transcripts.txt").appendText(
                    "${now},$transcript\n"
                )
            }
            val (title, summary) = summariseText(client, apiKey, transcript)
            Log.i(TAG, "Summary title: $title")
            Log.i(TAG, "Summary: $summary")

            // Persist summary to file so the UI can display it later
            val dir = applicationContext.getExternalFilesDir("summaries")!!
            dir.mkdirs()
            File(dir, "summaries.txt").appendText(
                "${now},$title|$summary\n"
            )

            // Generate aggregated summaries for multiple windows
            listOf(30, 60, 120, 240).forEach { window ->
                generateWindowSummary(client, apiKey, now, window)
            }

            // Delete the original recording now that processing is complete
            audioFile.delete()
            Result.success()
        } catch (t: Throwable) {
            t.printStackTrace()
            Result.retry()
        }
    }

    private fun transcribeAudio(client: OkHttpClient, apiKey: String, file: File): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            // model name subject to change – refer to OpenAI docs
            .addFormDataPart("model", "gpt-4o-transcribe")
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            .build()

        val req = Request.Builder()
            .url("$OPENAI_BASE/audio/transcriptions")
            .post(body)
            .header("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Transcription failed: ${'$'}{resp.code}")
            val json = JSONObject(resp.body!!.string())
            return json.getString("text")
        }
    }

    private fun summariseText(client: OkHttpClient, apiKey: String, transcript: String): Pair<String, String> {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    "Summarise the following transcript. Respond ONLY in JSON with keys 'title' (max 10 words) and 'summary'. Example: {\"title\":\"My Title\",\"summary\":\"The summary...\"}"
                )
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", transcript)
            })
        }

        val payload = JSONObject().apply {
            put("model", "gpt-4.1")
            put("messages", messages)
        }

        val req = Request.Builder()
            .url("$OPENAI_BASE/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string()
            if (!resp.isSuccessful) error("Summarisation failed: ${'$'}{resp.code}. ${'$'}bodyStr")

            val obj = JSONObject(bodyStr ?: "{}")
            val choices = obj.getJSONArray("choices")
            val first = choices.getJSONObject(0)
            val message = first.getJSONObject("message")
            val contentJson = JSONObject(message.getString("content"))
            val title = contentJson.optString("title")
            val summary = contentJson.optString("summary")
            return Pair(title, summary)
        }
    }

    private fun generateWindowSummary(
        client: OkHttpClient,
        apiKey: String,
        now: Long,
        windowMinutes: Int
    ) {
        val tDir = applicationContext.getExternalFilesDir("transcripts") ?: return
        val tFile = File(tDir, "transcripts.txt")
        if (!tFile.exists()) return

        val windowStart = now - TimeUnit.MINUTES.toMillis(windowMinutes.toLong())
        val transcripts = tFile.readLines().mapNotNull { line ->
            val idx = line.indexOf(',')
            if (idx <= 0) return@mapNotNull null
            val ts = line.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
            if (ts < windowStart) return@mapNotNull null
            line.substring(idx + 1)
        }

        // Require at least one transcript roughly every five minutes
        val minSegments = windowMinutes / 5
        if (transcripts.size < minSegments) return

        val sumDir = applicationContext.getExternalFilesDir(SummaryUtils.dirName(windowMinutes))!!
        sumDir.mkdirs()
        val sumFile = File(sumDir, SummaryUtils.fileName(windowMinutes))

        // Avoid duplicate summaries within half the window duration
        val lastTs = if (sumFile.exists()) {
            sumFile.readLines().lastOrNull()?.substringBefore(',')?.toLongOrNull() ?: 0L
        } else 0L
        val minGapMillis = TimeUnit.MINUTES.toMillis((windowMinutes / 2).toLong())
        if (now - lastTs < minGapMillis) return

        val combined = transcripts.joinToString("\n")
        val (title, summary) = summariseText(client, apiKey, combined)
        sumFile.appendText("${now},$title|$summary\n")
    }
} 