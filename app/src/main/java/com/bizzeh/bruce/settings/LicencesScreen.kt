package com.bizzeh.bruce.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.navigation.SubScreen

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val component = open?.let(OpenSourceLicences.components::getOrNull)
    if (component == null) {
        SubScreen(stringResource(R.string.settings_licences), onBack) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                OpenSourceLicences.components.forEachIndexed { index, item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text(item.licence) },
                        modifier = Modifier.clickable { open = index }.testTag("licence:$index"),
                    )
                }
            }
        }
    } else {
        val context = LocalContext.current
        val text = remember(component) { context.resources.openRawResource(component.text).use { it.readBytes().decodeToString() } }
        androidx.activity.compose.BackHandler { open = null }
        SubScreen(component.name, onBack = { open = null }) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("licenceText"),
            )
        }
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    SubScreen(stringResource(R.string.settings_permissions), onBack) {
        Text(stringResource(R.string.permissions_placeholder), modifier = Modifier.padding(16.dp).testTag("permissionsPlaceholder"))
    }
}
