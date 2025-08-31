package dev.t0rzz.samloaderreloaded.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import android.provider.DocumentsContract

object SafUtils {
    fun persistTreePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Best-effort; ignore if not persistable on this device
        }
    }

    fun isPersisted(context: Context, uriString: String): Boolean {
        return try {
            val cr: ContentResolver = context.contentResolver
            cr.persistedUriPermissions.any { it.uri.toString() == uriString && it.isReadPermission && it.isWritePermission }
        } catch (_: Throwable) {
            false
        }
    }

    fun getReadablePathFromTreeUri(context: Context, treeUri: Uri): String {
        if ("com.android.externalstorage.documents" == treeUri.authority) {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parts = docId.split(":", limit = 2)
            val volumeId = parts.getOrNull(0) ?: ""
            val rel = parts.getOrNull(1) ?: ""
            return if (volumeId.equals("primary", ignoreCase = true)) {
                val base = Environment.getExternalStorageDirectory().absolutePath
                if (rel.isNotEmpty()) "$base/$rel" else base
            } else {
                // Try to resolve non-primary UUID via StorageManager
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val vol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sm.storageVolumes.firstOrNull { v ->
                        val uuid = try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) v.uuid else null } catch (_: Throwable) { null }
                        uuid != null && uuid.equals(volumeId, ignoreCase = true)
                    }
                } else null
                val base = if (vol != null) {
                    // Best effort label/uuid presentation
                    val label = try { vol.getDescription(context) } catch (_: Throwable) { volumeId }
                    label ?: volumeId
                } else volumeId
                if (rel.isNotEmpty()) "$base/$rel" else base
            }
        }
        // Fallbacks for other providers
        DocumentFile.fromTreeUri(context, treeUri)?.name?.let { return it }
        return DocumentsContract.getTreeDocumentId(treeUri)
    }

    fun createOrFindFile(context: Context, treeUri: Uri, mimeType: String, filename: String): Uri? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        // Try to find existing
        root.findFile(filename)?.let { return it.uri }
        // Create new
        return root.createFile(mimeType, filename)?.uri
    }
}
