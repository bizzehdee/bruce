package com.bizzeh.bruce.remote

import androidx.compose.ui.Alignment
import com.bizzeh.bruce.models.RemoteChoice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R

interface ServersActions {
    fun add(name: String, address: String, key: String)
    fun check(server: RemoteServer)
    fun remove(server: RemoteServer)

    /** Chat with [model] on [server] instead of a model on the phone (TASK-077). */
    fun use(server: RemoteServer, model: String) = Unit
}

/** Model servers the user added, and a form to add one (TASK-075). */
@Composable
fun ServersPane(state: ServersState, actions: ServersActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.servers_explained), style = MaterialTheme.typography.bodyMedium)
        state.servers.forEach { server -> ServerCard(server, state.statuses[server.id], state.active, actions) }
        AddServer(state.addError, actions)
    }
}

@Composable
private fun ServerCard(server: RemoteServer, status: ServerStatus?, active: RemoteChoice?, actions: ServersActions) {
    Card(modifier = Modifier.fillMaxWidth().testTag("server:${server.id}")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(server.name, style = MaterialTheme.typography.titleMedium)
            Text(server.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (server.plain) {
                Text(stringResource(R.string.servers_plain_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("plain:${server.id}"))
            }
            Text(
                when (status) {
                    null -> stringResource(R.string.servers_unchecked)
                    ServerStatus.Checking -> stringResource(R.string.servers_checking)
                    is ServerStatus.Ready -> if (status.models.isEmpty()) stringResource(R.string.servers_no_models) else stringResource(R.string.servers_models_heading)
                    is ServerStatus.Failed -> status.reason
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (status is ServerStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("status:${server.id}"),
            )
            (status as? ServerStatus.Ready)?.models?.forEach { model ->
                val inUse = active?.serverId == server.id && active.model == model
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("serverModel:${server.id}:$model")) {
                    Text(model, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (inUse) {
                        Text(stringResource(R.string.models_active), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = { actions.use(server, model) }, modifier = Modifier.testTag("use:${server.id}:$model")) { Text(stringResource(R.string.servers_use)) }
                    }
                }
            }
            Row {
                TextButton(onClick = { actions.check(server) }, enabled = status != ServerStatus.Checking, modifier = Modifier.testTag("check:${server.id}")) { Text(stringResource(R.string.servers_check)) }
                TextButton(onClick = { actions.remove(server) }, modifier = Modifier.testTag("remove:${server.id}")) { Text(stringResource(R.string.servers_remove)) }
            }
        }
    }
}

@Composable
private fun AddServer(error: String?, actions: ServersActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.servers_add), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.servers_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("serverName"))
        OutlinedTextField(
            address, { address = it },
            label = { Text(stringResource(R.string.servers_address)) },
            placeholder = { Text("https://example.com or 192.168.1.20:8080") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().testTag("serverAddress"),
        )
        OutlinedTextField(
            key, { key = it },
            label = { Text(stringResource(R.string.servers_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().testTag("serverKey"),
        )
        Button(
            onClick = {
                actions.add(name, address, key)
                key = ""
            },
            enabled = address.isNotBlank(),
            modifier = Modifier.testTag("addServer"),
        ) { Text(stringResource(R.string.servers_add_button)) }
    }
}
