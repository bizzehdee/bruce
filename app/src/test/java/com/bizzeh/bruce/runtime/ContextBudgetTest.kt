package com.bizzeh.bruce.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {
    @Test
    fun smallContextsKeepAQuarterForTheReplyAndLargeOnes1024Tokens() {
        assertEquals(1536, ContextBudget.promptLimit(2048))
        assertEquals(3072, ContextBudget.promptLimit(4096))
        assertEquals(7168, ContextBudget.promptLimit(8192))
    }

    @Test
    fun roomForTheChatIsWhatTheFixedPromptLeaves() {
        assertEquals(154, ContextBudget.roomForChat(2048, 1382))
        assertEquals(0, ContextBudget.roomForChat(2048, 2000))
    }

    @Test
    fun tightMeansLessThanAQuarterOfTheContextForTheChat() {
        assertTrue(ContextBudget.isTight(2048, 1382))
        assertFalse(ContextBudget.isTight(2048, 834))
        assertFalse(ContextBudget.isTight(4096, 1382))
    }
}
