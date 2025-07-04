package com.mikestudios.lifesummary

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val path = inputData.getString("path") ?: return@withContext Result.failure()
        val file = File(path)
        val ok = try {
            postFile(file); true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
        if (ok) { file.delete(); Result.success() } else Result.retry()
    }

    // ⇣ Replace with your real endpoint
    private fun postFile(f: File) {
        val client = OkHttpClient()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio", f.name,
                f.asRequestBody("audio/mp4".toMediaTypeOrNull())
            )
            .build()

        val req = Request.Builder()
            .url("https://example.com/upload")
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Upload failed: ${resp.code}")
        }
    }
}
