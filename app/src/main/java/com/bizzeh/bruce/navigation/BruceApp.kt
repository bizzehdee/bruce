package com.bizzeh.bruce.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.chat.ChatActions
import com.bizzeh.bruce.chat.ChatScreen
import com.bizzeh.bruce.chat.ChatState
import com.bizzeh.bruce.conversations.Conversation
import com.bizzeh.bruce.conversations.ConversationActions
import com.bizzeh.bruce.conversations.ConversationList
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bizzeh.bruce.models.ActiveModelState
import com.bizzeh.bruce.settings.LicencesScreen
import kotlinx.coroutines.launch
import java.io.File

enum class Destination {
    CHAT,
    MODELS,
    SETTINGS,
    DIAGNOSTICS,
    LICENCES,
    PERMISSIONS,
    SKILLS,
    MEMORY,
    ARCHIVED,
}

/** Where the system back button goes from each destination. */
internal fun backTarget(destination: Destination): Destination? = when (destination) {
    Destination.CHAT -> null
    Destination.DIAGNOSTICS, Destination.LICENCES, Destination.PERMISSIONS, Destination.SKILLS, Destination.MEMORY -> Destination.SETTINGS
    Destination.MODELS, Destination.SETTINGS, Destination.ARCHIVED -> Destination.CHAT
}

interface AppActions {
    fun newChat()
    fun selectModel(file: File)
}

/**
 * The app shell: chat is the start screen, a side drawer lists saved chats and leads to Archived,
 * Models and Settings, and the chat title opens a quick model switcher. Permissions are reached
 * from Settings, not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BruceApp(
    chat: ChatState,
    chatActions: ChatActions,
    activeModel: ActiveModelState,
    actions: AppActions,
    modelsScreen: @Composable (onBack: () -> Unit) -> Unit,
    settingsScreen: @Composable (onBack: () -> Unit, open: (Destination) -> Unit) -> Unit,
    diagnosticsScreen: @Composable (onBack: () -> Unit) -> Unit,
    skillsScreen: @Composable (onBack: () -> Unit, openPermissions: () -> Unit, openSettings: () -> Unit) -> Unit,
    permissionsScreen: @Composable (onBack: () -> Unit, openNetworkSettings: () -> Unit) -> Unit,
    memoryScreen: @Composable (onBack: () -> Unit) -> Unit,
    startDestination: Destination = Destination.CHAT,
    conversations: List<Conversation> = emptyList(),
    archived: List<Conversation> = emptyList(),
    conversationActions: ConversationActions? = null,
) {
    var destination by rememberSaveable { mutableStateOf(startDestination) }
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val goBack = { backTarget(destination)?.let { destination = it } }
    BackHandler(enabled = destination != Destination.CHAT) { goBack() }
    // The keyboard would otherwise stay up over the drawer and hide its lower items.
    val focus = LocalFocusManager.current
    LaunchedEffect(drawer) {
        snapshotFlow { drawer.targetValue }.collect { if (it == DrawerValue.Open) focus.clearFocus() }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = destination == Destination.CHAT,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.testTag("drawer")) {
                fun go(target: Destination?) {
                    scope.launch { drawer.close() }
                    target?.let { destination = it }
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_new_chat)) },
                    selected = false,
                    onClick = { actions.newChat(); go(Destination.CHAT) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (conversationActions != null) {
                    ConversationList(
                        conversations = conversations,
                        currentId = chat.conversationId,
                        archivedView = false,
                        actions = object : ConversationActions by conversationActions {
                            override fun open(id: Long) {
                                conversationActions.open(id)
                                go(Destination.CHAT)
                            }
                        },
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("conversations"),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
                if (conversationActions != null) {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_archived)) },
                        selected = false,
                        onClick = { go(Destination.ARCHIVED) },
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("nav:archived"),
                    )
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_models)) },
                    selected = false,
                    onClick = { go(Destination.MODELS) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("nav:models"),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = false,
                    onClick = { go(Destination.SETTINGS) },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("nav:settings"),
                )
            }
        },
    ) {
        when (destination) {
            Destination.CHAT -> ChatScreen(
                state = chat,
                actions = chatActions,
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawer.open() } }, modifier = Modifier.testTag("openDrawer")) {
                        Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.nav_open_drawer))
                    }
                },
                titleAction = { switcherOpen = true },
            )
            Destination.MODELS -> modelsScreen { goBack() }
            Destination.SETTINGS -> settingsScreen({ goBack() }, { destination = it })
            Destination.DIAGNOSTICS -> diagnosticsScreen { goBack() }
            Destination.LICENCES -> LicencesScreen { goBack() }
            Destination.MEMORY -> memoryScreen { goBack() }
            Destination.SKILLS -> skillsScreen({ goBack() }, { destination = Destination.PERMISSIONS }, { destination = Destination.SETTINGS })
            Destination.PERMISSIONS -> permissionsScreen({ goBack() }, { destination = Destination.SETTINGS })
            Destination.ARCHIVED -> SubScreen(stringResource(R.string.nav_archived), { goBack() }) {
                if (conversationActions != null) {
                    ConversationList(
                        conversations = archived,
                        currentId = null,
                        archivedView = true,
                        actions = conversationActions,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("archived"),
                    )
                }
            }
        }
    }

    if (switcherOpen) {
        ModalBottomSheet(onDismissRequest = { switcherOpen = false }, modifier = Modifier.testTag("switcher")) {
            ModelSwitcher(
                state = activeModel,
                onSelect = { file -> actions.selectModel(file); switcherOpen = false },
                onManage = { switcherOpen = false; destination = Destination.MODELS },
            )
        }
    }
}

@Composable
private fun ModelSwitcher(state: ActiveModelState, onSelect: (File) -> Unit, onManage: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(stringResource(R.string.switcher_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (state.installed.isEmpty()) {
            Text(stringResource(R.string.switcher_empty), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        state.installed.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !state.loading) { onSelect(file) }
                    .padding(horizontal = 12.dp).testTag("switch:${file.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = file == state.active, onClick = null, modifier = Modifier.padding(12.dp))
                Text(file.nameWithoutExtension)
            }
        }
        if (state.loading) Text(stringResource(R.string.switcher_loading), modifier = Modifier.padding(horizontal = 24.dp))
        TextButton(onClick = onManage, modifier = Modifier.padding(horizontal = 12.dp).testTag("manageModels")) {
            Text(stringResource(R.string.switcher_manage))
        }
    }
}

/** A screen with a back arrow, for destinations reached from the drawer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
    }
}
