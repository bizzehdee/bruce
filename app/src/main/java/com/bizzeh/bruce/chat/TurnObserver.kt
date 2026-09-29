package com.bizzeh.bruce.chat

/** Told when a chat turn starts and ends, so a reply can finish and be announced off screen (TASK-048). */
interface TurnObserver {
    fun turnStarted()

    /** [reply] is the answer's text when the turn [completed]; the chat was saved as [conversationId]. */
    fun turnFinished(conversationId: Long, reply: String?, completed: Boolean)

    object None : TurnObserver {
        override fun turnStarted() = Unit

        override fun turnFinished(conversationId: Long, reply: String?, completed: Boolean) = Unit
    }
}
