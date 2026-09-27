package com.bizzeh.bruce.models

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Stands in for a Storage Access Framework document provider. */
class TestDocumentProvider : ContentProvider() {
    class Document(val file: File?, val displayName: String?, val sizeBytes: Long?, val permissionRevoked: Boolean = false)

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val document = documents.getValue(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>(document.displayName, document.sizeBytes))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (documents[uri]?.permissionRevoked == true) throw SecurityException("permission revoked")
        val file = documents[uri]?.file ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.bizzeh.bruce.test.documents"
        val documents = mutableMapOf<Uri, Document>()
    }
}
