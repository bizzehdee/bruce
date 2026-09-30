package com.bizzeh.bruce.surfaces

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.bizzeh.bruce.MainActivity
import com.bizzeh.bruce.R

/**
 * Ways into Bruce from outside the app (TASK-101): launcher shortcuts (res/xml/shortcuts.xml), a
 * Quick Settings tile and a home-screen widget. Each only opens Bruce on a new chat, or a new chat
 * listening; none shows chat content (owner, 2026-09-30).
 */
object Surfaces {
    const val ACTION_NEW_CHAT = "com.bizzeh.bruce.NEW_CHAT"
    const val ACTION_VOICE = "com.bizzeh.bruce.VOICE"

    fun intent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun pending(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getActivity(context, code, intent(context, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** The Quick Settings tile: a new chat. */
class NewChatTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(Surfaces.pending(this, Surfaces.ACTION_NEW_CHAT, TILE_CODE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(Surfaces.intent(this, Surfaces.ACTION_NEW_CHAT))
        }
    }

    private companion object {
        const val TILE_CODE = 10
    }
}

/** The home-screen widget: New chat and Voice buttons. */
class BruceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_bruce).apply {
            setOnClickPendingIntent(R.id.widget_new_chat, Surfaces.pending(context, Surfaces.ACTION_NEW_CHAT, NEW_CHAT_CODE))
            setOnClickPendingIntent(R.id.widget_voice, Surfaces.pending(context, Surfaces.ACTION_VOICE, VOICE_CODE))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private companion object {
        const val NEW_CHAT_CODE = 11
        const val VOICE_CODE = 12
    }
}
