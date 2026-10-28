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
