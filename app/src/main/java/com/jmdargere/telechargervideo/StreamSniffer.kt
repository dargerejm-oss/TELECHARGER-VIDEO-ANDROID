package com.jmdargere.telechargervideo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Ouvre une page dans un navigateur interne invisible, lance la lecture et repère
 * le lien direct de la vidéo (.m3u8 ou .mp4) que la page charge.
 */
object StreamSniffer {

    class Result(val flux: String?, val page: String, val titre: String)

    private const val PLAY = "document.querySelectorAll('video').forEach(function(v){" +
        "v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});});"

    @Volatile var urlCible: String = ""

    fun detect(ctx: Context, stop: () -> Boolean): Result {
        val url = urlCible
        val main = Handler(Looper.getMainLooper())
        val trouves = CopyOnWriteArrayList<String>()
        val page = arrayOf(url)
        val titre = arrayOf("")
        val holder = arrayOfNulls<WebView>(1)

        main.post {
            val w = WebView(ctx.applicationContext)
            holder[0] = w
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.mediaPlaybackRequiresUserGesture = false
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse? {
                    val u = r?.url?.toString() ?: return null
                    val b = u.substringBefore('?').lowercase()
                    if ((b.endsWith(".m3u8") || b.endsWith(".mp4")) && !trouves.contains(u)) trouves.add(u)
                    return null
                }

                override fun onPageFinished(v: WebView?, u: String?) {
                    if (v == null) return
                    page[0] = u ?: page[0]
                    titre[0] = v.title ?: ""
                    v.evaluateJavascript(PLAY, null)
                }
            }
            w.loadUrl(url)
        }

        val fin = System.currentTimeMillis() + 35000
        while (System.currentTimeMillis() < fin && trouves.isEmpty() && !stop()) {
            Thread.sleep(1000)
            main.post {
                holder[0]?.evaluateJavascript(PLAY, null)
                holder[0]?.title?.let { if (it.isNotBlank()) titre[0] = it }
            }
        }
        main.post { try { holder[0]?.destroy() } catch (_: Throwable) {} }

        val m3u8 = trouves.firstOrNull { it.substringBefore('?').lowercase().endsWith(".m3u8") }
        return Result(m3u8 ?: trouves.firstOrNull(), page[0], titre[0])
    }
}
