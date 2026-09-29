package com.bizzeh.bruce.models

import androidx.compose.foundation.layout.FlowRow
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.huggingface.HubError
import com.bizzeh.bruce.huggingface.HubModel
import com.bizzeh.bruce.ui.Format

interface BrowseActions {
    fun setQuery(query: String)
    fun search()
    fun recommend()
    fun setFilters(filters: BrowseFilters)
    fun openRepository(model: HubModel)
    fun download(model: HubModel, assessment: Assessment)
    fun cancel(repositoryId: String, path: String)
}

@Composable
fun BrowsePane(state: BrowseState, actions: BrowseActions) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val search = {
        keyboard?.hide()
        focus.clearFocus()
        actions.search()
    }
    LaunchedEffect(Unit) { actions.recommend() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions::setQuery,
                placeholder = { Text(stringResource(R.string.browse_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f).testTag("browseQuery"),
            )
            Button(onClick = search, enabled = !state.searching, modifier = Modifier.testTag("browseSearch")) {
                Text(stringResource(R.string.browse_search))
            }
        }
        Filters(state.filters, actions::setFilters)
        if (state.searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.searching && state.recommended) Text(stringResource(R.string.browse_sniffing), style = MaterialTheme.typography.bodySmall)
        state.error?.let { ErrorText(BrowseText.hubError(it), it) }
        if (state.recommended && state.searched && state.error == null) {
            Text(stringResource(R.string.browse_recommended), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("browseRecommended"))
        }
        if (state.searched && !state.searching && state.error == null && state.listings.isEmpty()) Text(stringResource(R.string.browse_no_results))
        state.listings.forEach { listing ->
            val model = listing.model
            val expanded = open == model.id
            Card(modifier = Modifier.fillMaxWidth().testTag("repo:${model.id}")) {
                Column(
                    modifier = Modifier.clickable {
                        open = if (expanded) null else model.id
                        if (!expanded) actions.openRepository(model)
                    }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.id, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (model.gated) Text(stringResource(R.string.browse_gated), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (listing.skills == false) {
                        Text(
                            stringResource(R.string.limited_skills),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("limitedSkills:${model.id}"),
                        )
                    }
                    Text(BrowseText.summary(model), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listing.best?.let { best ->
                        Text(
                            stringResource(R.string.browse_best_file, Format.bytes(best.candidate.sizeBytes), best.quantisation.orEmpty(), stringResource(ModelsText.fitLabel(best))),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("best:${model.id}"),
                        )
                    }
                    if (model.architecture != null && !LlamaArchitectures.supportsChat(model.architecture)) {
                        Text(stringResource(R.string.models_unsupported), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (expanded) Files(model, state, actions)
                }
            }
        }
    }
}

@Composable
private fun Files(model: HubModel, state: BrowseState, actions: BrowseActions) {
    val files = state.files[model.id] ?: return
    if (files.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    files.error?.let { ErrorText(BrowseText.hubError(it), it) }
    if (files.ranked.isNotEmpty()) {
        Text(stringResource(R.string.browse_weights_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    files.ranked.forEach { assessment ->
        val path = assessment.candidate.path
        val download = state.downloads[ModelBrowserViewModel.key(model.id, path)]
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("file:$path"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(Format.bytes(assessment.candidate.sizeBytes), assessment.quantisation).joinToString(" · ") +
                    " · " + stringResource(ModelsText.fitLabel(assessment)) +
                    if (assessment.supported) " · " + stringResource(ModelsText.speedLabel(assessment.speed)) + " " + ModelsText.speed(assessment) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                download == null -> OutlinedButton(
                    onClick = { actions.download(model, assessment) },
                    enabled = assessment.supported && assessment.fit != Fit.DOES_NOT_FIT,
                    modifier = Modifier.testTag("download:$path"),
                ) { Text(stringResource(R.string.browse_download)) }
                download.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.download_error, download.error.name), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { actions.download(model, assessment) }, modifier = Modifier.testTag("retry:$path")) { Text(stringResource(R.string.browse_retry)) }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.weight(1f).testTag("progress:$path"))
                    Text("${(download.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { actions.cancel(model.id, path) }, modifier = Modifier.testTag("cancel:$path")) {
                        Text(stringResource(R.string.browse_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(filters: BrowseFilters, onChange: (BrowseFilters) -> Unit) {
    FilterRow(R.string.browse_filter_parameters, listOf(null) + ParameterBucket.entries, filters.parameters, "parameters", { BrowseText.parameterLabel(it) }) {
        onChange(filters.copy(parameters = it))
    }
    FilterRow(R.string.browse_filter_size, listOf(null) + SizeBucket.entries, filters.size, "size", { BrowseText.sizeLabel(it) }) {
        onChange(filters.copy(size = it))
    }
    FilterRow(R.string.browse_filter_runs, RunsFilter.entries, filters.runs, "runs", { stringResource(BrowseText.runsLabel(it)) }) {
        onChange(filters.copy(runs = it))
    }
    FilterRow(R.string.browse_filter_task, listOf(true, false), filters.textGeneration, "task", { stringResource(if (it) R.string.browse_task_text else R.string.browse_any) }) {
        onChange(filters.copy(textGeneration = it))
    }
}

@Composable
private fun <T> FilterRow(@StringRes label: Int, options: List<T>, selected: T, tag: String, text: @Composable (T) -> String?, onSelect: (T) -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text(option) ?: stringResource(R.string.browse_any)) },
                    modifier = Modifier.testTag("$tag:${option ?: "any"}"),
                )
            }
        }
    }
}

@Composable
private fun ErrorText(@StringRes text: Int, error: HubError) {
    Text(stringResource(text, error.name), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hubError"))
}

internal object BrowseText {
    fun parameterLabel(bucket: ParameterBucket?): String? = when (bucket) {
        null -> null
        ParameterBucket.UNDER_1B -> "< 1B"
        ParameterBucket.FROM_1B_TO_3B -> "1–3B"
        ParameterBucket.FROM_3B_TO_8B -> "3–8B"
        ParameterBucket.FROM_8B_TO_14B -> "8–14B"
        ParameterBucket.FROM_14B -> "14B+"
    }

    fun sizeLabel(bucket: SizeBucket?): String? = when (bucket) {
        null -> null
        SizeBucket.UNDER_1GB -> "< 1 GB"
        SizeBucket.FROM_1GB_TO_2GB -> "1–2 GB"
        SizeBucket.FROM_2GB_TO_4GB -> "2–4 GB"
        SizeBucket.FROM_4GB_TO_8GB -> "4–8 GB"
        SizeBucket.FROM_8GB -> "8 GB+"
    }

    @StringRes
    fun runsLabel(filter: RunsFilter): Int = when (filter) {
        RunsFilter.FITS -> R.string.browse_runs_fits
        RunsFilter.FITS_OR_TIGHT -> R.string.browse_runs_tight
        RunsFilter.ANY -> R.string.browse_any
    }

    fun summary(model: HubModel): String = listOfNotNull(
        model.architecture,
        model.parameterCount?.let { Format.count(it) + " parameters" },
        Format.count(model.downloads) + " downloads",
        model.license,
    ).joinToString(" · ")

    @StringRes
    fun hubError(error: HubError): Int = when (error) {
        HubError.NETWORK_DISABLED -> R.string.hub_error_network_disabled
        HubError.OFFLINE -> R.string.hub_error_offline
        HubError.RATE_LIMITED -> R.string.hub_error_rate_limited
        HubError.UNAUTHORISED -> R.string.hub_error_unauthorised
        HubError.NOT_FOUND -> R.string.hub_error_not_found
        HubError.SERVER_ERROR, HubError.MALFORMED_RESPONSE, HubError.RESPONSE_TOO_LARGE -> R.string.hub_error_other
    }
}
