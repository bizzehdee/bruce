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
 * (TASK-048). A special-use service on Android 14 and later, which Android does not time out: the
 * turn's own limit (the Longest wait setting, TASK-115) ends the reply, and the turn stops the service.
 */
class ReplyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ReplyNotifications.WORKING_ID, appContainer.replyNotifications.working(), type)
        return START_NOT_STICKY
    }
}
