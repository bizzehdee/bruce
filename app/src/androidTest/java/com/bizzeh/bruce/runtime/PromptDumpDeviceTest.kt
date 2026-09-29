package com.bizzeh.bruce.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolChatPrompt
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.skills.ToolOutput
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Writes the prompt an empty chat is measured with to files/prompt-dump.txt: the phone's saved
 * model, template, skill states, grants and personality, as the chat's context bar counts them.
 * Keeps app data.
 */
@ManualOnly("needs the phone's own model and settings")
@RunWith(AndroidJUnit4::class)
class PromptDumpDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container = (instrumentation.targetContext.applicationContext as BruceApplication).container

    @Test
    fun dumpTheEmptyChatPrompt() = runBlocking {
        container.modelSelection.restore()
        var captured: ToolChatPrompt? = null
        val engine = object : InferenceEngine by container.engine {
            override suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean): ToolChatPrompt? =
                container.engine.formatToolChat(messages, tools, enableThinking).also { captured = it }
        }
        val policy = PolicyEngine(container.skills, container.skillStates, ToolOutput(), permissionGranted = { true }, scope = { ScopeCheck.InScope() })
        val runtime = BruceRuntime(engine, container.skills, container.skillStates, policy, temperature = { 0f }, personality = container::personalityRules, grantNames = container::grantNames)

        val use = runtime.measure(emptyList())

        assertNotNull("no model loaded", use)
        val prompt = captured!!
        File(instrumentation.targetContext.filesDir, "prompt-dump.txt").writeText("tokens=${use!!.used} of ${use.total}\nnative tools=${prompt.format.supportsTools}\n\n${prompt.text}")
    }
}
