package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/** llama.cpp's own judgement of whether a template can express tool calls, with no model loaded. */
@RunWith(AndroidJUnit4::class)
class TemplateCheckDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)

    @After
    fun tearDown() = nativeThread.close()

    @Test
    fun templatesThatRenderToolsAndCallsSupportThem() {
        assertEquals(true, engine.templateSupportsTools(WITH_TOOLS, "<s>", "</s>"))
        assertEquals(false, engine.templateSupportsTools(WITHOUT_TOOLS, "<s>", "</s>"))
        assertEquals(null, engine.templateSupportsTools("{% for m in messages", null, null))
    }

    private companion object {
        val WITH_TOOLS = """
            {%- if tools %}Tools: {{ tools | tojson }}
            {% endif %}{%- for m in messages %}{{ m.role }}: {{ m.content }}{% if m.tool_calls %}{% for c in m.tool_calls %}
            CALL {{ c.function.name }} {{ c.function.arguments | tojson }}{% endfor %}{% endif %}
            {% endfor %}{% if add_generation_prompt %}assistant:{% endif %}
        """.trimIndent()

        // Like the stripped template some Llama 3.2 copies carry: role and text only.
        val WITHOUT_TOOLS = """{% for m in messages %}{{ m.role }}: {{ m.content | trim }}
{% endfor %}{% if add_generation_prompt %}assistant:{% endif %}"""
    }
}
