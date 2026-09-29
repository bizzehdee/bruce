package com.bizzeh.bruce.remote

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

sealed interface AddressCheck {
    /** [base] is the server's address without a trailing slash or `/v1`. [plain] means unencrypted HTTP on the local network. */
    data class Allowed(val base: String, val plain: Boolean) : AddressCheck

    data class Refused(val reason: String) : AddressCheck
}

/**
 * Which server addresses Bruce connects to (TASK-075, owner 2026-09-29): HTTPS anywhere, and plain
 * HTTP only to the local network. "Local" is decided from the address as written, never by a DNS
 * lookup, so a name cannot be made to look local: private and loopback IP addresses, `localhost`,
 * and `.local` and `.home.arpa` names.
 */
object ServerAddress {
    fun check(input: String): AddressCheck {
        val raw = input.trim()
        if (raw.isEmpty()) return AddressCheck.Refused("Enter the server's address.")
        val hasScheme = "://" in raw
        val text = if (hasScheme) raw.substringBefore("://") + "://" + raw.substringAfter("://").trimEnd('/') else raw.trimEnd('/')
        val withScheme = if (hasScheme) text else if (isLocal(hostOf("http://$text") ?: "")) "http://$text" else "https://$text"
        val uri = try {
            URI(withScheme)
        } catch (e: URISyntaxException) {
            return AddressCheck.Refused("That is not a web address.")
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return AddressCheck.Refused("That is not a web address.")
        if (uri.rawUserInfo != null) return AddressCheck.Refused("Put the API key in its own field, not in the address.")
        if (uri.rawQuery != null || uri.rawFragment != null) return AddressCheck.Refused("Enter only the server's address, without anything after ? or #.")
        val base = withScheme.removeSuffix("/").removeSuffix("/v1").removeSuffix("/")
        return when (scheme) {
            "https" -> AddressCheck.Allowed(base, plain = false)
            "http" -> if (isLocal(host)) AddressCheck.Allowed(base, plain = true) else AddressCheck.Refused("Unencrypted http:// is only allowed on your local network. Use https:// for this server.")
            else -> AddressCheck.Refused("Use an http:// or https:// address.")
        }
    }

    private fun hostOf(address: String): String? = try {
        URI(address).host?.lowercase(Locale.ROOT)
    } catch (e: URISyntaxException) {
        null
    }

    fun isLocal(host: String): Boolean {
        val name = host.removePrefix("[").removeSuffix("]").removeSuffix(".")
        if (name == "localhost" || name.endsWith(".local") || name.endsWith(".home.arpa")) return true
        ipv4(name)?.let { (a, b) ->
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (':' in name) {
            val first = name.substringBefore(':').ifEmpty { "0" }.toIntOrNull(16) ?: return false
            return name == "::1" || first and 0xFE00 == 0xFC00 || first and 0xFFC0 == 0xFE80
        }
        return false
    }

    /** The first two octets of a dotted IPv4 address, or null if [name] is not one. */
    private fun ipv4(name: String): Pair<Int, Int>? {
        val parts = name.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part -> part.takeIf { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) }?.toInt()?.takeIf { it <= 255 } ?: return null }
        return octets[0] to octets[1]
    }
}
