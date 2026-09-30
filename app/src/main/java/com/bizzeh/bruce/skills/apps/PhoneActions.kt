package com.bizzeh.bruce.skills.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** An app the user can open from the launcher. */
data class LaunchableApp(val label: String, val packageName: String)

/** What the app skills ask of Android; each opens something the user then sees and controls. */
interface PhoneActions {
    /** Opens the share sheet with [text]; the user picks where it goes. False if nothing can take it. */
    fun share(text: String, subject: String?): Boolean

    /** Opens an http or https address in the user's browser. */
    fun openUrl(url: String): Boolean

    fun launchable(): List<LaunchableApp>

    fun launch(packageName: String): Boolean
}

/**
 * Activities are started from the app context with a new task; Android only lets that happen while
 * Bruce is on screen, which the skills check first.
 */
class AndroidPhoneActions(private val context: Context) : PhoneActions {
    override fun share(text: String, subject: String?): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
        return start(Intent.createChooser(send, null))
    }

    override fun openUrl(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))

    override fun launchable(): List<LaunchableApp> {
        val manager = context.packageManager
        return manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { LaunchableApp(it.loadLabel(manager).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
    }

    override fun launch(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName)?.let(::start) ?: false

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
