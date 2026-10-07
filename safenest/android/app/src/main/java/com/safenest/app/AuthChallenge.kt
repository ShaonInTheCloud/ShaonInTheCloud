package com.safenest.app

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The WebView sees only a challenge; email/password never cross the JS bridge. */
@SuppressLint("SetJavaScriptEnabled")
suspend fun awaitAuthChallenge(context: Context, language: String): AuthCaptchaToken = withContext(Dispatchers.Main) {
    withTimeout(120_000) {
        suspendCancellableCoroutine { continuation ->
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                continuation.resumeWithException(IllegalStateException("Update Android System WebView to complete the security check."))
                return@suspendCancellableCoroutine
            }
            val page = Uri.parse(BuildConfig.AUTH_CHALLENGE_URL)
            check(page.scheme == "https" && page.host != null && page.userInfo == null && page.query == null && page.fragment == null)
            val origin = "https://${page.encodedAuthority}"
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val dialog = Dialog(context)
            val web = WebView(context)
            val status = TextView(context).apply {
                text = if (language == "bn") "নিরাপত্তা যাচাই শেষ করুন।" else "Complete the security check."
                setPadding(24, 24, 24, 24)
            }
            fun fail() {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Security check unavailable. Check your connection and try again."))
                dialog.dismiss()
            }
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                // Preserve the default UA and browser environment throughout the challenge.
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
            WebViewCompat.addWebMessageListener(web, "SafeNestChallenge", setOf(origin)) { view, message, source, mainFrame, _ ->
                if (!continuation.isActive) return@addWebMessageListener
                val token = readChallengeMessage(page.toString(), origin, nonce, view.url, source.toString(), mainFrame, message.data)
                    ?: return@addWebMessageListener
                continuation.resume(token)
                dialog.dismiss()
            }
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return if (request.isForMainFrame) uri.toString().substringBefore('#') != page.toString()
                    else !(uri.toString() in setOf("about:blank", "about:srcdoc") ||
                        (uri.scheme == "https" && uri.host in setOf(page.host, "challenges.cloudflare.com")))
                }
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (url.substringBefore('#') != page.toString()) { view.stopLoading(); fail() }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) fail()
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) fail()
                }
                // Default SSL-error handling cancels; no certificate bypass.
            }
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(status)
                addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(Button(context).apply {
                    text = if (language == "bn") "বাতিল" else "Cancel"
                    setOnClickListener { dialog.cancel() }
                })
            }
            dialog.setContentView(layout)
            dialog.setOnCancelListener { continuation.cancel(CancellationException("Security check cancelled.")) }
            dialog.setOnDismissListener {
                web.stopLoading()
                WebViewCompat.removeWebMessageListener(web, "SafeNestChallenge")
                web.destroy()
            }
            continuation.invokeOnCancellation { web.post { dialog.dismiss() } }
            dialog.show()
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.8).toInt())
            // Values are fixed/hex; preserve the parameter separators in the fragment.
            web.loadUrl(page.buildUpon().encodedFragment("nonce=$nonce&lang=${if (language == "bn") "bn" else "en"}").build().toString())
        }
    }
}
