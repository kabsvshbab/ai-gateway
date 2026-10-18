package dev.aigateway.core

/**
 * Definition of one upstream AI web provider.
 * Each provider is reverse-proxied via a hidden WebView that loads [loginUrl].
 */
data class ProviderDef(
    val id: String,
    val label: String,
    val loginUrl: String,
    val apiUrl: String,
    val cookieDomain: String,
    val sessionCookieNames: List<String>,
    val modelIds: List<String>,
    /** JS payload builder + SSE parser injected into the page. */
    val hookScript: String,
)
