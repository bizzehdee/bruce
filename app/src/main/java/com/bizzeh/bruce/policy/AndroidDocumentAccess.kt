package com.bizzeh.bruce.policy

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/** [DocumentAccess] through Android's content resolver and document providers. */
class AndroidDocumentAccess(private val resolver: ContentResolver) : DocumentAccess {
    override fun take(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Some providers offer read-only access; the write skills then fail on that grant.
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun release(uri: Uri) {
        val held = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags = (if (held.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (held.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        resolver.releasePersistableUriPermission(uri, flags)
    }

    override fun persisted(): Set<Uri> = resolver.persistedUriPermissions.mapTo(mutableSetOf()) { it.uri }

    override fun displayName(uri: Uri, kind: GrantKind): String? {
        val document = if (kind == GrantKind.FOLDER) root(uri).uri else uri
        return resolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    override fun root(tree: Uri): DocumentRef =
        DocumentRef(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), isDirectory = true)

    override fun children(tree: Uri, folder: DocumentRef): List<Pair<String, DocumentRef>> {
        val listing = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder.uri))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return resolver.query(listing, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    add(name to DocumentRef(DocumentsContract.buildDocumentUriUsingTree(tree, id), directory))
                }
            }
        }.orEmpty()
    }
}
