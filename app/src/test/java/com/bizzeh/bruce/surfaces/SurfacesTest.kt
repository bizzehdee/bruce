package com.bizzeh.bruce.surfaces

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SurfacesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun surfacesOpenBruceWithTheirAction() {
        val intent = Surfaces.intent(context, Surfaces.ACTION_VOICE)
        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertEquals(Surfaces.ACTION_VOICE, intent.action)
        assertEquals(Surfaces.ACTION_NEW_CHAT, shadowOf(Surfaces.pending(context, Surfaces.ACTION_NEW_CHAT, 1)).savedIntent.action)
    }

    @Test
    fun theWidgetSetsUpItsTwoButtons() {
        val manager = AppWidgetManager.getInstance(context)
        val shadow = shadowOf(manager)
        val id = shadow.createWidget(BruceWidget::class.java, com.bizzeh.bruce.R.layout.widget_bruce)
        val view = shadow.getViewFor(id)

        view.findViewById<android.view.View>(com.bizzeh.bruce.R.id.widget_voice).performClick()
        assertEquals(Surfaces.ACTION_VOICE, shadowOf(context as android.app.Application).nextStartedActivity.action)
        view.findViewById<android.view.View>(com.bizzeh.bruce.R.id.widget_new_chat).performClick()
        assertEquals(Surfaces.ACTION_NEW_CHAT, shadowOf(context as android.app.Application).nextStartedActivity.action)
    }
}
