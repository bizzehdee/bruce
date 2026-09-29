package com.bizzeh.bruce.skills.web

import java.util.Locale

/**
 * Readable text from an untrusted HTML page (TASK-069): headings, paragraphs, list items and the
 * text of links with their addresses. Scripts, styles, forms' hidden parts and elements marked
 * hidden are left out. A single linear scan with no regular expressions, so no page can make it
 * slow, and nothing in the page is run.
 */
object HtmlText {
    private val SKIPPED = setOf("script", "style", "noscript", "template", "svg", "head", "title", "iframe", "object", "canvas", "select", "button", "math")
    private val BLOCKS = setOf(
        "p", "div", "br", "tr", "section", "article", "header", "footer", "main", "nav", "aside", "ul", "ol", "table",
        "blockquote", "pre", "hr", "dl", "dt", "dd", "figure", "figcaption", "form", "fieldset", "details", "summary",
    )
    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val VOID = setOf("br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed", "source", "track", "wbr")
    private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…", "copy" to "©", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“")

    private class Tag(val name: String, val closing: Boolean, val selfClosing: Boolean, val attributes: Map<String, String>)

    fun title(html: String): String? {
        val start = html.indexOf("<title", ignoreCase = true).takeIf { it >= 0 } ?: return null
        val open = html.indexOf('>', start).takeIf { it >= 0 } ?: return null
        val end = html.indexOf("</title", open, ignoreCase = true).takeIf { it >= 0 } ?: return null
        return clean(decode(html.substring(open + 1, end))).ifBlank { null }
    }

    fun text(html: String): String {
        val out = StringBuilder()
        var skipping: String? = null
        var skipDepth = 0
        var link: String? = null
        var linkStart = 0
        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c != '<') {
                val next = html.indexOf('<', i).let { if (it < 0) html.length else it }
                if (skipping == null) out.append(decode(html.substring(i, next)))
                i = next
                continue
            }
            if (html.startsWith("<!--", i)) {
                i = html.indexOf("-->", i + 4).let { if (it < 0) html.length else it + 3 }
                continue
            }
            val end = tagEnd(html, i)
            val tag = parse(html.substring(i + 1, end))
            i = end + 1
            if (tag == null) continue
            if (skipping != null) {
                if (tag.name == skipping && !tag.selfClosing) skipDepth += if (tag.closing) -1 else 1
                if (skipDepth == 0) skipping = null
                continue
            }
            if (!tag.closing && !tag.selfClosing && tag.name !in VOID && (tag.name in SKIPPED || hidden(tag))) {
                skipping = tag.name
                skipDepth = 1
                continue
            }
            when {
                tag.name == "a" && !tag.closing -> {
                    link = tag.attributes["href"]?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
                    linkStart = out.length
                }
                tag.name == "a" && tag.closing -> {
                    val href = link
                    if (href != null && out.substring(linkStart).trim() != href) out.append(" (").append(href).append(')')
                    link = null
                }
                tag.name == "li" && !tag.closing -> out.append("\n- ")
                tag.name in HEADINGS && !tag.closing -> out.append("\n\n# ")
                tag.name in HEADINGS || tag.name in BLOCKS -> out.append('\n')
                tag.name == "td" || tag.name == "th" -> out.append(' ')
            }
        }
        return clean(out.toString())
    }

    /** The '>' that ends the tag starting at [start], skipping quoted attribute values. */
    private fun tagEnd(html: String, start: Int): Int {
        var quote: Char? = null
        var i = start + 1
        while (i < html.length) {
            val c = html[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        return html.length - 1
    }

    private fun parse(body: String): Tag? {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed[0] == '!' || trimmed[0] == '?') return null
        val closing = trimmed.startsWith("/")
        val rest = trimmed.removePrefix("/")
        val nameEnd = rest.indexOfFirst { it.isWhitespace() || it == '/' || it == '>' }.let { if (it < 0) rest.length else it }
        val name = rest.substring(0, nameEnd).lowercase(Locale.ROOT)
        if (name.isEmpty() || !name[0].isLetter()) return null
        return Tag(name, closing, trimmed.endsWith("/"), if (closing) emptyMap() else attributes(rest.substring(nameEnd)))
    }

    private fun attributes(text: String): Map<String, String> {
        val found = mutableMapOf<String, String>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && (text[i].isWhitespace() || text[i] == '/')) i++
            val nameStart = i
            while (i < text.length && !text[i].isWhitespace() && text[i] != '=' && text[i] != '/') i++
            val name = text.substring(nameStart, i).lowercase(Locale.ROOT)
            while (i < text.length && text[i].isWhitespace()) i++
            var value = ""
            if (i < text.length && text[i] == '=') {
                i++
                while (i < text.length && text[i].isWhitespace()) i++
                if (i < text.length && (text[i] == '"' || text[i] == '\'')) {
                    val quote = text[i]
                    val close = text.indexOf(quote, i + 1).let { if (it < 0) text.length else it }
                    value = text.substring(i + 1, close)
                    i = close + 1
                } else {
                    val start = i
                    while (i < text.length && !text[i].isWhitespace()) i++
                    value = text.substring(start, i)
                }
            }
            if (name.isNotEmpty()) found[name] = decode(value)
            if (i == nameStart) i++
        }
        return found
    }

    private fun hidden(tag: Tag): Boolean {
        val style = tag.attributes["style"]?.lowercase(Locale.ROOT)?.replace(" ", "").orEmpty()
        return "hidden" in tag.attributes || tag.attributes["aria-hidden"] == "true" ||
            "display:none" in style || "visibility:hidden" in style ||
            (tag.name == "input" && tag.attributes["type"].equals("hidden", ignoreCase = true))
    }

    private fun decode(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val semicolon = if (c == '&') text.indexOf(';', i).takeIf { it in (i + 2)..(i + 10) } else null
            if (semicolon == null) {
                out.append(c)
                i++
                continue
            }
            val entity = text.substring(i + 1, semicolon)
            val decoded = when {
                entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)?.let(::codePoint)
                entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::codePoint)
                else -> ENTITIES[entity]
            }
            if (decoded == null) {
                out.append(c)
                i++
            } else {
                out.append(decoded)
                i = semicolon + 1
            }
        }
        return out.toString()
    }

    private fun codePoint(value: Int): String? = if (Character.isValidCodePoint(value) && value != 0) String(Character.toChars(value)) else null

    /** Spaces collapsed within lines, at most one blank line between blocks. */
    private fun clean(text: String): String = text.lines()
        .map { line -> line.replace('\t', ' ').replace(' ', ' ').split(' ').filter { it.isNotEmpty() }.joinToString(" ") }
        .fold(mutableListOf<String>()) { lines, line ->
            if (line.isNotEmpty() || (lines.isNotEmpty() && lines.last().isNotEmpty())) lines += line
            lines
        }
        .joinToString("\n")
        .trim()
}
