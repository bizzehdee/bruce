package com.bizzeh.bruce.models

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions::setQuery,
                placeholder = { Text(stringResource(R.string.browse_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (state.query.isNotBlank()) search() }),
                modifier = Modifier.weight(1f).testTag("browseQuery"),
            )
            Button(onClick = search, enabled = state.query.isNotBlank() && !state.searching, modifier = Modifier.testTag("browseSearch")) {
                Text(stringResource(R.string.browse_search))
            }
        }
        if (state.searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.error?.let { ErrorText(BrowseText.hubError(it), it) }
        if (state.searched && state.error == null && state.results.isEmpty()) Text(stringResource(R.string.browse_no_results))
        state.results.forEach { model ->
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
                    Text(BrowseText.summary(model), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ErrorText(@StringRes text: Int, error: HubError) {
    Text(stringResource(text, error.name), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hubError"))
}

internal object BrowseText {
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
