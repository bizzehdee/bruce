package com.bizzeh.bruce.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A number with +/- buttons to step it by [step] within [min]..[max], and a Reset button shown
 * only while [isOverridden] (owner, 2026-11-08: replaced a row of preset pill chips, which could
 * not express every value and had no way back to the effective default once touched).
 */
@Composable
fun FloatStepper(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    decimals: Int,
    isOverridden: Boolean,
    tag: String,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(round(value - step, decimals).coerceIn(min, max)) }, modifier = Modifier.testTag("$tag:minus")) { Text(MINUS) }
        Text(
            String.format(Locale.ROOT, "%.${decimals}f", value),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp).testTag("$tag:value").semantics(mergeDescendants = true) {},
        )
        TextButton(onClick = { onChange(round(value + step, decimals).coerceIn(min, max)) }, modifier = Modifier.testTag("$tag:plus")) { Text(PLUS) }
        if (isOverridden) TextButton(onClick = onReset, modifier = Modifier.testTag("$tag:reset")) { Text(stringResource(R.string.settings_reset)) }
    }
}

/** The integer counterpart of [FloatStepper], for a setting with no fractional part (top K). */
@Composable
fun IntStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    isOverridden: Boolean,
    tag: String,
    onChange: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(min, max)) }, modifier = Modifier.testTag("$tag:minus")) { Text(MINUS) }
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp).testTag("$tag:value").semantics(mergeDescendants = true) {})
        TextButton(onClick = { onChange((value + step).coerceIn(min, max)) }, modifier = Modifier.testTag("$tag:plus")) { Text(PLUS) }
        if (isOverridden) TextButton(onClick = onReset, modifier = Modifier.testTag("$tag:reset")) { Text(stringResource(R.string.settings_reset)) }
    }
}

private const val MINUS = "−"
private const val PLUS = "+"

/** Rounds away the binary floating-point drift repeated +/- steps build up (e.g. 0.1 + 0.1 + 0.1 != 0.3). */
private fun round(value: Float, decimals: Int): Float {
    val factor = Math.pow(10.0, decimals.toDouble()).toFloat()
    return (value * factor).roundToInt() / factor
}
