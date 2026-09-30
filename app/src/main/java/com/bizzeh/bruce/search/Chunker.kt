package com.bizzeh.bruce.search

/**
 * Splits a document's text into passages for the index: about [size] characters, cut at a
 * paragraph, else a sentence, else a space, with [overlap] characters carried into the next so an
 * answer split across a cut is still found.
 */
class Chunker(private val size: Int = SIZE, private val overlap: Int = OVERLAP) {
    init {
        require(overlap in 0 until size / 2)
    }

    fun chunks(text: String): List<String> {
        val clean = text.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
        val result = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = minOf(clean.length, start + size)
            if (end < clean.length) end = cut(clean, start, end)
            result += clean.substring(start, end).trim()
            if (end >= clean.length) break
            start = maxOf(end - overlap, start + 1)
        }
        return result.filter { it.isNotEmpty() }
    }

    /** The best cut before [end], no earlier than half a chunk in. */
    private fun cut(text: String, start: Int, end: Int): Int {
        val floor = start + size / 2
        for (separator in listOf("\n\n", "\n", ". ", " ")) {
            val at = text.lastIndexOf(separator, end - separator.length)
            if (at >= floor) return at + separator.length
        }
        return end
    }

    companion object {
        /** About 300 tokens, well inside the model's context (EmbeddingModel.CONTEXT). */
        const val SIZE = 1200
        const val OVERLAP = 200
    }
}
