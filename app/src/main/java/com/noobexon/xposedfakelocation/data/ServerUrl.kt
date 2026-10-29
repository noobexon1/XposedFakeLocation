package com.noobexon.xposedfakelocation.data

import java.net.URI
import java.net.URISyntaxException

/**
 * Trims [input] and strips trailing slashes. Returns `null` unless the result is an absolute
 * http(s) URL with a host, a valid port (if any) and no query or fragment.
 */
fun normalizeServerUrl(input: String?): String? {
    val trimmed = input?.trim()?.trimEnd('/') ?: return null
    if (trimmed.isEmpty()) return null
    val uri = try {
        URI(trimmed)
    } catch (e: URISyntaxException) {
        return null
    }
    val schemeOk = uri.scheme?.lowercase() in setOf("http", "https")
    val portOk = uri.port == -1 || uri.port in 1..65535
    val ok = schemeOk && portOk && !uri.host.isNullOrBlank() && uri.rawQuery == null && uri.rawFragment == null
    return trimmed.takeIf { ok }
}

/**
 * Like [normalizeServerUrl], but allows one [ROUTING_PROFILE_PLACEHOLDER], which is kept. No other
 * braces are allowed.
 */
fun normalizeRoutingServerUrl(input: String?): String? {
    val trimmed = input?.trim()?.trimEnd('/') ?: return null
    if (trimmed.split(ROUTING_PROFILE_PLACEHOLDER).size > 2) return null
    val rest = trimmed.replace(ROUTING_PROFILE_PLACEHOLDER, "")
    if ('{' in rest || '}' in rest) return null
    normalizeServerUrl(trimmed.replace(ROUTING_PROFILE_PLACEHOLDER, "car")) ?: return null
    return trimmed
}

/**
 * Trims [input]. Blank becomes `""` (use the default styles). Otherwise returns the URL only if it
 * is an absolute http(s) URL with a host and a valid port; a query is allowed, a fragment is not.
 */
fun normalizeMapStyleUrl(input: String?): String? {
    val trimmed = input?.trim() ?: return null
    if (trimmed.isEmpty()) return ""
    val uri = try {
        URI(trimmed)
    } catch (e: URISyntaxException) {
        return null
    }
    val schemeOk = uri.scheme?.lowercase() in setOf("http", "https")
    val portOk = uri.port == -1 || uri.port in 1..65535
    return trimmed.takeIf { schemeOk && portOk && !uri.host.isNullOrBlank() && uri.rawFragment == null }
}
