package com.jmdargere.telechargervideo

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/** Etat partagé entre l'écran, le menu Partager et le service de téléchargement. */
object Store {
    const val ATTENTE = 0
    const val COURS = 1
    const val OK = 2
    const val ERREUR = 3

    class Item(val url: String) {
        @Volatile var status: Int = ATTENTE
        @Volatile var titre: String = ""
        @Volatile var erreur: String = ""
        @Volatile var pct: Float = 0f
    }

    val items = CopyOnWriteArrayList<Item>()
    val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** 0 = meilleure qualité, 1 = 720p (léger), 2 = MP3 audio */
    @Volatile var format: Int = 0
    @Volatile var running: Boolean = false
    @Volatile var stopRequested: Boolean = false
    @Volatile var finished: Boolean = false   // vrai quand tout vient de se terminer
    @Volatile var currentProcessId: String? = null
    @Volatile var toast: String? = null

    private val main = Handler(Looper.getMainLooper())

    fun changed() {
        main.post { for (l in listeners) l() }
    }

    fun extraire(txt: String): List<String> {
        val res = ArrayList<String>()
        for (part in txt.split(Regex("(?=https?://)"))) {
            val p = part.trim().trim('"', '\'', '<', '>')
            if (p.startsWith("http://") || p.startsWith("https://")) {
                val u = p.split(Regex("\\s+"))[0].trim('"', '\'', '<', '>', ',', ')')
                if (u.length > 10) res.add(u)
            }
        }
        return res
    }

    /** Ajoute les liens trouvés dans le texte (sans doublons). Retourne le nombre ajouté. */
    fun ajouter(txt: String): Int {
        var n = 0
        for (u in extraire(txt)) {
            if (items.none { it.url == u && it.status != ERREUR }) {
                items.add(Item(u))
                n++
            }
        }
        if (n > 0) {
            finished = false
            changed()
        }
        return n
    }

    fun retirer(it: Item) {
        if (it.status != COURS) {
            items.remove(it)
            changed()
        }
    }

    fun vider() {
        if (!running) {
            items.clear()
            finished = false
            changed()
        }
    }

    fun relancerErreurs() {
        for (i in items) if (i.status == ERREUR) {
            i.status = ATTENTE; i.erreur = ""; i.pct = 0f
        }
        finished = false
        changed()
    }

    fun progressionGlobale(): Float {
        if (items.isEmpty()) return 0f
        var total = 0f
        for (i in items) {
            total += when (i.status) {
                OK, ERREUR -> 1f
                COURS -> (i.pct / 100f).coerceIn(0f, 1f)
                else -> 0f
            }
        }
        return total / items.size
    }
}
