package com.bizzeh.bruce.policy

import com.bizzeh.bruce.settings.NetworkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** What the network mode says about one web site (TASK-068). */
enum class SiteRule {
    /** The network mode allows no web skills. */
    BLOCKED,

    /** Approved sites mode, and the user has not approved this site: ask. */
    ASK,
    ALLOWED,
}

/** The host of an http or https address, lower-cased; null for anything else, or an address carrying a user name or password. */
object WebAddress {
    fun host(address: String): String? = try {
        val uri = URI(address.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        uri.host?.takeIf { (scheme == "https" || scheme == "http") && uri.rawUserInfo == null }?.lowercase(Locale.ROOT)?.removeSuffix(".")
    } catch (e: URISyntaxException) {
        null
    }
}

/**
 * The sites web skills may reach. Offline and Hugging Face modes allow none; Any site allows every
 * one; Approved sites allows those the user always allows and asks about the rest. Only the user's
 * answer on a confirmation card, or Settings, changes the list: nothing the model can reach does.
 */
class SiteAccess(private val dao: PolicyDao, private val mode: suspend () -> NetworkMode, private val clock: () -> Long = System::currentTimeMillis) {
    val approved: Flow<List<String>> = dao.approvedSites().map { sites -> sites.map { it.host } }

    suspend fun rule(host: String): SiteRule = when (mode()) {
        NetworkMode.OFFLINE, NetworkMode.HUGGING_FACE -> SiteRule.BLOCKED
        NetworkMode.GENERAL -> SiteRule.ALLOWED
        NetworkMode.APPROVED_DOMAINS -> if (dao.isApprovedSite(host)) SiteRule.ALLOWED else SiteRule.ASK
    }

    suspend fun approve(hosts: List<String>) {
        hosts.forEach { dao.approveSite(ApprovedSiteEntity(it, clock())) }
    }

    suspend fun remove(host: String) = dao.removeSite(host)

    suspend fun clear() = dao.clearSites()
}
