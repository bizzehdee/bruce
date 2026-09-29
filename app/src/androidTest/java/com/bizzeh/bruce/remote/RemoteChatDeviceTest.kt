package com.bizzeh.bruce.remote

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.runtime.RuntimeEvent
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One chat turn through the phone's saved server model (TASK-076/077): the app's own runtime, policy
 * and skills, with replies from the server. Needs a server model chosen in the app and reachable;
 * keeps app data.
 */
@ManualOnly("needs a model server the phone can reach")
@RunWith(AndroidJUnit4::class)
class RemoteChatDeviceTest {
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BruceApplication).container

    @Test
    fun aTurnRunsThroughTheServerWithSkillsOnThePhone() = runBlocking {
        container.modelSelection.restore()
        assertNotNull("choose a server model in the app first", container.activeModel.state.value.remote)

        val events = container.runtime.respond(listOf(ToolChatMessage(ChatRole.USER, "What time is it?"))).toList()

        events.forEach { Log.i("RemoteChat", it.toString().take(300)) }
        assertTrue(events.last().toString(), events.last() is RuntimeEvent.Finished)
        assertTrue("the clock skill ran on the phone", events.filterIsInstance<RuntimeEvent.ToolResult>().any { it.call.name == "get_datetime" && it.ran })
    }
}
