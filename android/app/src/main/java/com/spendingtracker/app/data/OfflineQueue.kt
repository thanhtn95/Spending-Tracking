package com.spendingtracker.app.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID

data class QueuedUploadMeta(
    val id: String,
    val sourceType: String?,
    val files: List<QueuedFileEntry>
)

data class QueuedFileEntry(
    val fileName: String,
    val mimeType: String
)

data class QueuedManualTx(
    val id: String,
    val request: CreateTransactionRequest
)

/**
 * Persists failed media uploads in app-private storage (`filesDir/offline_queue`)
 * so receipts scanned offline are automatically uploaded once connectivity returns.
 */
class OfflineQueue(private val context: Context) {

    private val gson = Gson()
    private val rootDir: File
        get() = File(context.filesDir, "offline_queue").apply { mkdirs() }
    private val manualDir: File
        get() = File(context.filesDir, "offline_manual_queue").apply { mkdirs() }

    fun count(): Int = listBatches().size + listManualTransactions().size

    fun enqueueManualTransaction(request: CreateTransactionRequest): Boolean {
        return try {
            val id = UUID.randomUUID().toString()
            val item = QueuedManualTx(id = id, request = request)
            File(manualDir, "$id.json").writeText(gson.toJson(item))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun listManualTransactions(): List<QueuedManualTx> {
        return manualDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    gson.fromJson(file.readText(), QueuedManualTx::class.java)
                } catch (_: Exception) {
                    null
                }
            }
            .orEmpty()
    }

    fun removeManualTransaction(id: String) {
        File(manualDir, "$id.json").delete()
    }

    fun enqueue(uris: List<Uri>, sourceType: String?): Boolean {
        if (uris.isEmpty()) return false
        val id = UUID.randomUUID().toString()
        val batchDir = File(rootDir, id).apply { mkdirs() }
        val entries = mutableListOf<QueuedFileEntry>()

        for ((idx, uri) in uris.withIndex()) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime == "application/pdf" -> "pdf"
                mime.contains("csv") -> "csv"
                else -> "jpg"
            }
            val fileName = "item_$idx.$ext"
            val outFile = File(batchDir, fileName)
            val copied = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } != null
            } catch (_: Exception) {
                false
            }
            if (copied) {
                entries.add(QueuedFileEntry(fileName, mime))
            }
        }

        if (entries.isEmpty()) {
            batchDir.deleteRecursively()
            return false
        }

        val meta = QueuedUploadMeta(id = id, sourceType = sourceType, files = entries)
        File(batchDir, "meta.json").writeText(gson.toJson(meta))
        return true
    }

    fun listBatches(): List<QueuedUploadMeta> {
        return rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val metaFile = File(dir, "meta.json")
                if (!metaFile.exists()) return@mapNotNull null
                try {
                    gson.fromJson(metaFile.readText(), QueuedUploadMeta::class.java)
                } catch (_: Exception) {
                    null
                }
            }
            .orEmpty()
    }

    fun toMultipartParts(meta: QueuedUploadMeta): List<MultipartBody.Part> {
        val batchDir = File(rootDir, meta.id)
        return meta.files.mapNotNull { entry ->
            val file = File(batchDir, entry.fileName)
            if (!file.exists()) return@mapNotNull null
            val body = file.asRequestBody(entry.mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("files[]", entry.fileName, body)
        }
    }

    fun remove(batchId: String) {
        File(rootDir, batchId).deleteRecursively()
    }
}
