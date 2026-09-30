package com.bizzeh.bruce.skills.apps

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
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

    /** Puts [text] on the clipboard, marked sensitive so Android does not preview it (TASK-088). */
    fun copy(text: String)

    /** Opens the messaging app with [text] written to [number] (or to no one yet); the user sends it (TASK-089). */
    fun compose(number: String?, text: String): Boolean

    /** Opens the phone app with [number] dialled; the user places the call. */
    fun dial(number: String): Boolean
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

    override fun copy(text: String) {
        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    override fun compose(number: String?, text: String): Boolean =
        start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number.orEmpty()))).putExtra("sms_body", text))

    override fun dial(number: String): Boolean = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** The clip's label, which the keyboard and clipboard tools show. */
private const val CLIP_LABEL = "Bruce"
