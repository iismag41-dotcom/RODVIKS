package com.rodviks.app.data.api

import com.rodviks.app.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds Bearer token and rewrites host to the user-configured server address.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configuredUrl = runBlocking { sessionManager.currentBaseUrl() }
        val targetBase = configuredUrl ?: SessionManager.DEFAULT_BASE_URL

        val newUrl = original.url.newBuilder()
            .host(targetBase.removePrefix("http://").removePrefix("https://")
                .substringBefore(':').ifBlank { original.url.host })
            .build()
        // port + scheme
        val scheme = if (targetBase.startsWith("https")) "https" else "http"
        val port = targetBase.substringAfterLast(":", "")
            .filter { c -> c.isDigit() }

        val requestBuilder = original.newBuilder()
        val finalUrl = newUrl.newBuilder()
            .scheme(scheme)
            .apply {
                if (port.isNotEmpty()) port(port.toInt())
                else port(if (scheme == "https") 443 else 80)
            }
            .build()
        requestBuilder.url(finalUrl)

        val token = runBlocking { sessionManager.currentToken() }
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}
