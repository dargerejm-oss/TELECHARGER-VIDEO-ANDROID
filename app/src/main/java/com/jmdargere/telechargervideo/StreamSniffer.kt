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
 * Ouvre une page dans un navigateur interne invisible et repère le lien direct de la vidéo
 * (.m3u8 ou .mp4) : par les requêtes que la page lance, ET en lisant le code de la page.
 */
object StreamSniffer {

    class Result(val flux: String?, val page: String, val titre: String)

    // Lance la lecture et clique sur les boutons "play"
    private const val PLAY = """(function(){
        document.querySelectorAll('video').forEach(function(v){v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});});
        document.querySelectorAll('.vjs-big-play-button,button[aria-label*="lay"],[class*="play-button"],[class*="PlayButton"],[class*="play_button"]').forEach(function(b){try{b.click();}catch(e){}});
        var v=document.querySelector('video');if(v){try{v.click();}catch(e){}}
    })();"""

    // Cherche les liens .m3u8 / .mp4 dans les balises vidéo et dans tout le code de la page
    private const val SCAN = """(function(){
        var out=[];function add(u){if(u&&out.indexOf(u)<0)out.push(u);}
        document.querySelectorAll('video,source').forEach(function(e){add(e.currentSrc||e.src);});
        var h=document.documentElement.innerHTML.replace(/\\\//g,'/').replace(/&amp;/g,'&').replace(/\\u0026/g,'&');
        var m=h.match(/https?:\/\/[^"'\s\\<>()]+?\.(?:m3u8|mp4)(?:\?[^"'\s\\<>()]*)?/gi)||[];
        m.forEach(add);
        return out.join('\n');
    })();"""

    @Volatile var urlCible: String = ""

    private fun valide(u: String): Boolean {
        if (!u.startsWith("http")) return false
        val b = u.substringBefore('?').lowercase()
        if (!(b.endsWith(".m3u8") || b.endsWith(".mp4"))) return false
        return true
    }

    private fun mauvais(u: String): Boolean {
        val l = u.lowercase()
        return l.contains("thumb") || l.contains("sprite") || l.contains("preview") || l.contains("trailer")
    }

    fun detect(ctx: Context, stop: () -> Boolean): Result {
        val url = urlCible
        val main = Handler(Looper.getMainLooper())
        val trouves = CopyOnWriteArrayList<String>()
        val page = arrayOf(url)
        val titre = arrayOf("")
        val holder = arrayOfNulls<WebView>(1)

        fun ajouter(u: String) {
            if (valide(u) && !trouves.contains(u)) trouves.add(u)
        }

        main.post {
            val w = WebView(ctx.applicationContext)
            holder[0] = w
            // Donne une vraie taille au navigateur invisible, sinon la lecture reste bloquée
            w.layout(0, 0, 1080, 1920)
            w.onResume()
            w.resumeTimers()
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.mediaPlaybackRequiresUserGesture = false
            w.settings.loadWithOverviewMode = true
            w.settings.useWideViewPort = true
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse? {
                    r?.url?.toString()?.let { ajouter(it) }
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

        val fin = System.currentTimeMillis() + 40000
        while (System.currentTimeMillis() < fin && trouves.isEmpty() && !stop()) {
            Thread.sleep(1500)
            main.post {
                val w = holder[0] ?: return@post
                w.evaluateJavascript(PLAY, null)
                w.evaluateJavascript(SCAN) { res ->
                    if (res != null && res != "null") {
                        val t = res.trim('"').replace("\\n", "\n").replace("\\u0026", "&").replace("\\/", "/")
                        t.split('\n').forEach { ajouter(it.trim()) }
                    }
                }
                w.title?.let { if (it.isNotBlank()) titre[0] = it }
            }
        }
        // petite pause : laisse arriver d'autres candidats (qualités) avant de choisir
        if (trouves.isNotEmpty()) Thread.sleep(1500)
        main.post { try { holder[0]?.destroy() } catch (_: Throwable) {} }

        val liste = trouves.toList()
        val choix = liste.firstOrNull { it.substringBefore('?').lowercase().endsWith(".m3u8") && !mauvais(it) }
            ?: liste.firstOrNull { !mauvais(it) }
            ?: liste.firstOrNull()
        return Result(choix, page[0], titre[0])
    }
}
