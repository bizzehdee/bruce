package com.bizzeh.bruce.testing

import android.net.Uri
import com.bizzeh.bruce.policy.DocumentAccess
import com.bizzeh.bruce.policy.DocumentRef
import com.bizzeh.bruce.policy.GrantKind

/** A document provider in memory: each folder URI maps to its children, each file to its type and bytes. */
class FakeDocuments : DocumentAccess {
    val held = mutableSetOf<Uri>()
    val released = mutableListOf<Uri>()
    val names = mutableMapOf<Uri, String>()
    val tree = mutableMapOf<Uri, List<Pair<String, DocumentRef>>>()
    val contents = mutableMapOf<Uri, Pair<String?, ByteArray>>()

    override fun take(uri: Uri) {
        held += uri
    }

    override fun release(uri: Uri) {
        if (!held.remove(uri)) throw SecurityException("not held")
        released += uri
    }

    override fun persisted(): Set<Uri> = held.toSet()
    override fun displayName(uri: Uri, kind: GrantKind): String? = names[uri]
    override fun root(tree: Uri) = DocumentRef(Uri.parse("$tree#root"), isDirectory = true)
    override fun children(tree: Uri, folder: DocumentRef) = this.tree[folder.uri].orEmpty()
    override fun mimeType(document: Uri) = contents[document]?.first
    override fun read(document: Uri, maxBytes: Int) = contents[document]?.second?.let { it.copyOf(minOf(it.size, maxBytes)) }
}
