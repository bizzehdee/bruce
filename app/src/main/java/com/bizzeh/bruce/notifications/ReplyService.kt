package com.bizzeh.bruce.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.bizzeh.bruce.appContainer

/**
 * Keeps Bruce running while a reply is written with the screen off or another app in front
 * (TASK-048). A short service on Android 14 and later: its limit of about three minutes matches a
 * turn's own (BruceRuntime.MAX_DURATION); at the limit the turn is stopped and what was written kept.
 */
class ReplyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
        ServiceCompat.startForeground(this, ReplyNotifications.WORKING_ID, appContainer.replyNotifications.working(), type)
        return START_NOT_STICKY
    }

    // Android 14 calls the first, Android 15 and later the second.
    override fun onTimeout(startId: Int) = timedOut()

    override fun onTimeout(startId: Int, fgsType: Int) = timedOut()

    private fun timedOut() {
        appContainer.stopTurn()
        stopSelf()
    }
}
