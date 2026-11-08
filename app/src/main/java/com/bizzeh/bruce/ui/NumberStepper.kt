package com.bizzeh.bruce.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * not express every value and had no way back to the effective default once touched). Compact
 * clickable text rather than [androidx.compose.material3.TextButton]: a Material3 button's own
 * minimum touch-target width (58.dp) made four of these in a row far wider than they needed to be
 * (owner feedback, 2026-11-08: "too wide").
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
    modifier: Modifier = Modifier,
) {
    StepperRow(
        label = label,
        valueText = String.format(Locale.ROOT, "%.${decimals}f", value),
        isOverridden = isOverridden,
        tag = tag,
        onMinus = { onChange(round(value - step, decimals).coerceIn(min, max)) },
        onPlus = { onChange(round(value + step, decimals).coerceIn(min, max)) },
        onReset = onReset,
        modifier = modifier,
    )
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
    modifier: Modifier = Modifier,
) {
    StepperRow(
        label = label,
        valueText = value.toString(),
        isOverridden = isOverridden,
        tag = tag,
        onMinus = { onChange((value - step).coerceIn(min, max)) },
        onPlus = { onChange((value + step).coerceIn(min, max)) },
        onReset = onReset,
        modifier = modifier,
    )
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    isOverridden: Boolean,
    tag: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        StepperButton(MINUS, onMinus, Modifier.testTag("$tag:minus"))
        Text(
            valueText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp).testTag("$tag:value").semantics(mergeDescendants = true) {},
        )
        StepperButton(PLUS, onPlus, Modifier.testTag("$tag:plus"))
        // Reset always takes its place in the row, invisible and inert while unset, rather than
        // appearing only once overridden: letting it pop in shifted every button after the label
        // (which fills the remaining width) left by its width, so a quick second tap on `+` right
        // after the first landed on Reset instead (owner, 2026-11-08).
        StepperButton(
            stringResource(R.string.settings_reset),
            onReset,
            Modifier.testTag("$tag:reset").alpha(if (isOverridden) 1f else 0f),
            enabled = isOverridden,
        )
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

private const val MINUS = "−"
private const val PLUS = "+"

/** Rounds away the binary floating-point drift repeated +/- steps build up (e.g. 0.1 + 0.1 + 0.1 != 0.3). */
private fun round(value: Float, decimals: Int): Float {
    val factor = Math.pow(10.0, decimals.toDouble()).toFloat()
    return (value * factor).roundToInt() / factor
}
