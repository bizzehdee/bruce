package com.bizzeh.bruce.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import kotlinx.coroutines.launch

/** What a reply's links and images do; both need the user's say before anything leaves the phone. */
interface MarkdownActions {
    /** The user tapped an http or https link; it opens only after they confirm. */
    fun link(url: String)

    suspend fun image(url: String): ImageResult
}

/** A reply drawn from its Markdown with the app theme. */
@Composable
fun MarkdownText(markdown: String, actions: MarkdownActions, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { Block(it, actions) }
    }
}

@Composable
private fun Block(block: MdBlock, actions: MarkdownActions) {
    val typography = MaterialTheme.typography
    when (block) {
        is MdBlock.Heading -> InlineText(
            block.content,
            actions,
            when (block.level) {
                1 -> typography.headlineSmall
                2 -> typography.titleLarge
                3 -> typography.titleMedium
                else -> typography.titleSmall
            },
        )
        is MdBlock.Paragraph -> InlineText(block.content, actions, typography.bodyLarge)
        is MdBlock.CodeBlock -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                block.code,
                style = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp).testTag("codeBlock"),
            )
        }
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { i, item ->
                Row {
                    Text(if (block.ordered) "${block.start + i}." else "•", style = typography.bodyLarge, modifier = Modifier.widthIn(min = 24.dp).padding(end = 6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { item.forEach { Block(it, actions) } }
                }
            }
        }
        is MdBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            VerticalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.outline)
            Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { block.blocks.forEach { Block(it, actions) } }
        }
        is MdBlock.Table -> Table(block, actions)
        MdBlock.Rule -> HorizontalDivider(modifier = Modifier.testTag("rule"))
        is MdBlock.Image -> RemoteImage(block, actions)
    }
}

@Composable
private fun Table(table: MdBlock.Table, actions: MarkdownActions) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    Column(modifier = Modifier.horizontalScroll(rememberScrollState()).testTag("table")) {
        (listOf(table.header) + table.rows).forEachIndexed { r, row ->
            Row {
                for (c in 0 until columns) {
                    val style = if (r == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                    Column(modifier = Modifier.width(CELL_WIDTH).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        InlineText(row.getOrNull(c).orEmpty(), actions, style)
                    }
                }
            }
            if (r == 0) HorizontalDivider()
        }
    }
}

@Composable
private fun InlineText(content: List<MdInline>, actions: MarkdownActions, style: TextStyle) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val linkColour = MaterialTheme.colorScheme.primary
    val text = remember(content, codeBackground, linkColour) { annotated(content, actions, codeBackground, linkColour) }
    Text(text, style = style)
}

private fun annotated(content: List<MdInline>, actions: MarkdownActions, codeBackground: Color, linkColour: Color): AnnotatedString = buildAnnotatedString {
    fun append(items: List<MdInline>) {
        items.forEach { item ->
            when (item) {
                is MdInline.Plain -> append(item.text)
                is MdInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(item.children) }
                is MdInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(item.children) }
                is MdInline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(item.text) }
                is MdInline.Link -> if (item.url == null) {
                    append(item.children)
                } else {
                    val styles = TextLinkStyles(SpanStyle(color = linkColour, textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Clickable(item.url, styles) { actions.link(item.url) }) { append(item.children) }
                }
                MdInline.LineBreak -> append('\n')
            }
        }
    }
    append(content)
}

@Composable
private fun RemoteImage(image: MdBlock.Image, actions: MarkdownActions) {
    var result by remember(image.address) { mutableStateOf<ImageResult?>(null) }
    var loading by remember(image.address) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loaded = result as? ImageResult.Loaded
    if (loaded != null) {
        Image(loaded.image, contentDescription = image.alt, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().testTag("image"))
        return
    }
    OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag("imagePlaceholder")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(image.alt.ifBlank { stringResource(R.string.markdown_image) }, style = MaterialTheme.typography.bodyMedium)
            Text(image.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                result == ImageResult.NotAllowed -> Text(stringResource(R.string.markdown_image_not_allowed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("imageNotAllowed"))
                result == ImageResult.Failed -> Text(stringResource(R.string.markdown_image_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("imageFailed"))
            }
            if (image.url != null && result != ImageResult.NotAllowed) {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        scope.launch {
                            result = actions.image(image.url)
                            loading = false
                        }
                    },
                    modifier = Modifier.testTag("loadImage"),
                ) { Text(stringResource(if (loading) R.string.markdown_image_loading else R.string.markdown_image_load)) }
            }
        }
    }
}

private val CELL_WIDTH = 140.dp
