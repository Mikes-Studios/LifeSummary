package com.mikestudios.lifesummary

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Simple helper that syncs the summaries.txt file with Google Drive.
 * This is *not* a fully-featured client – it only supports creating the file once,
 * appending new lines and re-uploading the full file after deletions.
 */
object DriveSync {
    private const val TAG = "DriveSync"
    private const val FILE_NAME = "summaries.txt"
    private val idCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    const val RC_SIGN_IN = 9876

    /** Launch Google sign-in to obtain Drive permissions. */
    fun startSignIn(activity: Activity) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(Scopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        activity.startActivityForResult(client.signInIntent, RC_SIGN_IN)
    }

    /** Download the remote summaries.txt and overwrite the local copy. */
    suspend fun syncDown(ctx: Context) {
        if (!SecurePrefs.isDriveSyncEnabled()) return
        try {
            val token = getAccessToken(ctx) ?: return
            val fileId = ensureFile(ctx, token) ?: return
            val client = OkHttpClient()
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val text = resp.body?.string() ?: return
                val dir = ctx.getExternalFilesDir("summaries") ?: return
                dir.mkdirs()
                File(dir, FILE_NAME).writeText(text)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "syncDown failed", t)
        }
    }

    /** Upload the *entire* local summaries.txt to Drive, replacing remote content. */
    fun uploadAllSummaries(ctx: Context) {
        uploadFile(ctx, "summaries", FILE_NAME)
    }

    /** Upload arbitrary text file in external-files/[dir]/[fileName] to Drive */
    fun uploadFile(ctx: Context, dirName: String, fileName: String) {
        GlobalScope.launch(Dispatchers.IO) {
            if (!SecurePrefs.isDriveSyncEnabled()) return@launch
            val dir = ctx.getExternalFilesDir(dirName) ?: return@launch
            val local = File(dir, fileName)
            if (!local.exists()) return@launch
            val token = getAccessToken(ctx) ?: return@launch
            val fileId = ensureFile(ctx, token, fileName) ?: return@launch
            val client = OkHttpClient()
            val body = local.readText().toRequestBody("text/plain".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&fields=id")
                .patch(body)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().close()
        }
    }

    /** Remove a timestamped entry from the remote file. */
    fun deleteEntry(ctx: Context, timestamp: Long) {
        GlobalScope.launch(Dispatchers.IO) {
            if (!SecurePrefs.isDriveSyncEnabled()) return@launch
            val dir = ctx.getExternalFilesDir("summaries") ?: return@launch
            val local = File(dir, FILE_NAME)
            if (!local.exists()) return@launch
            val lines = local.readLines().filterNot { it.startsWith("$timestamp,") }
            local.writeText(lines.joinToString("\n"))
            val token = getAccessToken(ctx) ?: return@launch
            val fileId = ensureFile(ctx, token) ?: return@launch
            val client = OkHttpClient()
            val body = local.readText().toRequestBody("text/plain".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&fields=id")
                .patch(body)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().close()
        }
    }

    /** Pull remote file, merge with local (dedupe by timestamp), then upload merged result. */
    fun syncAndMerge(ctx: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            if (!SecurePrefs.isDriveSyncEnabled()) return@launch
            val dir = ctx.getExternalFilesDir("summaries") ?: return@launch
            dir.mkdirs()
            val localFile = File(dir, FILE_NAME)
            val localText = if (localFile.exists()) localFile.readText() else ""

            val token = getAccessToken(ctx) ?: return@launch
            val fileId = ensureFile(ctx, token) ?: return@launch
            val client = OkHttpClient()
            val remoteText = try {
                val dlReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer $token")
                    .build()
                client.newCall(dlReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Drive download failed: ${resp.code}")
                        ""
                    } else resp.body?.string() ?: ""
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Drive download error", t)
                ""
            }

            // Build merged map keyed by timestamp (keeps last seen line per ts)
            val merged = LinkedHashMap<Long, String>()
            fun addLines(text: String) {
                text.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach
                    val ts = line.substringBefore(',').toLongOrNull() ?: return@forEach
                    merged[ts] = line.trimEnd()
                }
            }
            addLines(remoteText)
            addLines(localText)

            val mergedText = merged.values.joinToString("\n")
            if (mergedText.isBlank()) return@launch // nothing to write

            // Write merged locally
            localFile.writeText(mergedText)

            // Upload merged back to Drive
            val body = mergedText.toRequestBody("text/plain".toMediaTypeOrNull())
            val upReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .patch(body)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(upReq).execute().close()

            // Notify UI layers that summaries file changed
            ctx.sendBroadcast(android.content.Intent(ACTION_SUMMARIES_UPDATED))
        }
    }

    /** Sync+merge single file (by dir/name) like summaries */
    fun syncAndMergeFile(ctx: Context, dirName: String, fileName: String) {
        GlobalScope.launch(Dispatchers.IO) {
            if (!SecurePrefs.isDriveSyncEnabled()) return@launch
            val dir = ctx.getExternalFilesDir(dirName) ?: return@launch
            dir.mkdirs()
            val localFile = File(dir, fileName)
            val localText = if (localFile.exists()) localFile.readText() else ""

            val token = getAccessToken(ctx) ?: return@launch
            val fileId = ensureFile(ctx, token, fileName) ?: return@launch
            val client = OkHttpClient()
            val remoteText = try {
                val dlReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer $token")
                    .build()
                client.newCall(dlReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Download failed $fileName: ${resp.code}")
                        ""
                    } else resp.body?.string() ?: ""
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Download error $fileName", t)
                ""
            }

            val merged = LinkedHashMap<Long, String>()
            fun addLines(text: String) {
                text.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach
                    val ts = line.substringBefore(',').toLongOrNull() ?: return@forEach
                    merged[ts] = line.trimEnd()
                }
            }
            addLines(remoteText); addLines(localText)
            val mergedText = merged.values.joinToString("\n")
            if (mergedText.isBlank()) return@launch
            localFile.writeText(mergedText)

            val body = mergedText.toRequestBody("text/plain".toMediaTypeOrNull())
            val upReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .patch(body)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(upReq).execute().close()

            ctx.sendBroadcast(android.content.Intent(ACTION_SUMMARIES_UPDATED))
            if (fileName == "transcripts.txt") {
                ctx.sendBroadcast(android.content.Intent(ACTION_TRANSCRIPTS_UPDATED))
            }
        }
    }

    // ---------- Helpers ----------
    private fun getAccessToken(ctx: Context): String? {
        return try {
            val acct = GoogleSignIn.getLastSignedInAccount(ctx) ?: return null
            val scope = "oauth2:${Scopes.DRIVE_FILE}"
            val acc = acct.account ?: return null
            @Suppress("DEPRECATION")
            return GoogleAuthUtil.getToken(ctx, acc, scope)
        } catch (t: Throwable) {
            Log.w(TAG, "Token fetch failed", t)
            null
        }
    }

    private fun ensureFile(ctx: Context, token: String): String? = ensureFile(ctx, token, FILE_NAME)

    /** Ensure a Drive file with given name exists, return its id (caches in-memory) */
    private fun ensureFile(ctx: Context, token: String, fileName: String): String? {
        idCache[fileName]?.let { return it }
        // If we already cached the file ID, use it
        if (fileName == FILE_NAME) {
            SecurePrefs.getDriveFileId()?.let { idCache[fileName] = it; return it }
        }
        val client = OkHttpClient()
        // 1. Try find existing file by name
        val listReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=name='$fileName' and trashed=false&spaces=drive&fields=files(id,name)")
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(listReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val arr = JSONObject(resp.body!!.string()).optJSONArray("files")
                if (arr != null && arr.length() > 0) {
                    val id = arr.getJSONObject(0).getString("id")
                    if (fileName == FILE_NAME) SecurePrefs.setDriveFileId(id)
                    idCache[fileName] = id
                    return id
                }
            }
        }
        // 2. Create the file if not found
        val meta = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "text/plain")
        }
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .post(meta.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(createReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val id = JSONObject(resp.body!!.string()).getString("id")
            if (fileName == FILE_NAME) SecurePrefs.setDriveFileId(id)
            idCache[fileName] = id
            // upload empty content so file exists with correct mime
            val initReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media")
                .patch("".toRequestBody("text/plain".toMediaTypeOrNull()))
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(initReq).execute().close()
            return id
        }
    }

    const val ACTION_SUMMARIES_UPDATED = "com.mikestudios.lifesummary.SUMMARIES_UPDATED"
    const val ACTION_TRANSCRIPTS_UPDATED = "com.mikestudios.lifesummary.TRANSCRIPTS_UPDATED"
} 