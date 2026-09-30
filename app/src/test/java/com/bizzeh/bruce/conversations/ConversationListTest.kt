package com.bizzeh.bruce.conversations

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.bizzeh.bruce.ui.theme.BruceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationListTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : ConversationActions {
        override fun open(id: Long) { calls += "open $id" }
        override fun rename(id: Long, title: String) { calls += "rename $id" }
        override fun archive(ids: Set<Long>) { calls += "archive $ids" }
        override fun restore(ids: Set<Long>) { calls += "restore $ids" }
        override fun delete(ids: Set<Long>) { calls += "delete $ids" }
        override fun export(ids: Set<Long>, format: ExportFormat) { calls += "export $ids $format" }
    }

    @Test
    fun selectedChatsAreExportedInTheChosenFormat() {
        compose.setContent {
            BruceTheme { ConversationList(listOf(Conversation(1, "One", 0, false), Conversation(2, "Two", 0, false)), null, archivedView = false, actions = actions) }
        }

        compose.onNodeWithTag("conversation:1").performTouchInput { longClick() }
        compose.onNodeWithTag("conversation:2").performClick()
        compose.onNodeWithTag("export").performClick()
        compose.onNodeWithTag("export:MARKDOWN").performClick()

        assertEquals(listOf("export [1, 2] MARKDOWN"), calls)
        compose.onNodeWithTag("selectionBar").assertDoesNotExist()
    }
}
