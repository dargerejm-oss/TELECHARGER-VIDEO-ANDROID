package com.jmdargere.telechargervideo

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var champ: EditText
    private lateinit var liste: LinearLayout
    private lateinit var runner: RunnerView
    private lateinit var banniere: TextView
    private lateinit var compteur: TextView
    private lateinit var btnGo: Button
    private lateinit var btnStop: Button
    private lateinit var auto: CheckBox
    private val chips = ArrayList<TextView>()
    private var dernierPresse = ""

    private val vert = Color.parseColor("#2E7D32")
    private val rouge = Color.parseColor("#C62828")

    private val ecoute: () -> Unit = { refresh() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fond(couleur: Int, rayon: Int = 14): GradientDrawable =
        GradientDrawable().apply { setColor(couleur); cornerRadius = dp(rayon).toFloat() }

    private fun bouton(txt: String, couleur: Int, taille: Float = 16f): Button =
        Button(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            textSize = taille
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = fond(couleur)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        construire()
        Store.listeners.add(ecoute)
        ajouterDepuisIntent(intent)
        refresh()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        refresh()
    }

    private fun ajouterDepuisIntent(i: Intent?) {
        val t = i?.getStringExtra(Intent.EXTRA_TEXT) ?: return
        Store.ajouter(t)
    }

    override fun onDestroy() {
        Store.listeners.remove(ecoute)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && auto.isChecked) capturePressePapier()
    }

    private fun lirePressePapier(): String {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        } catch (_: Throwable) { "" }
    }

    private fun capturePressePapier() {
        val t = lirePressePapier()
        if (t.isNotEmpty() && t != dernierPresse) {
            dernierPresse = t
            val n = Store.ajouter(t)
            if (n > 0) Toast.makeText(this, "✔ $n lien(s) ajouté(s)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun construire() {
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7F5"))
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }

        racine.addView(TextView(this).apply {
            text = "TÉLÉCHARGER VIDÉO"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(vert)
            gravity = Gravity.CENTER
        })
        racine.addView(TextView(this).apply {
            text = "Colle un ou plusieurs liens, puis appuie sur le gros bouton vert"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        })

        // Saisie
        champ = EditText(this).apply {
            hint = "Colle ici un ou plusieurs liens…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI
            minLines = 2
            maxLines = 4
            textSize = 14f
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            background = fond(Color.WHITE, 10)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        racine.addView(champ, lp(top = 10))

        val ligne1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnColler = bouton("📋 COLLER", Color.parseColor("#1565C0"), 14f).apply {
            setOnClickListener {
                val t = lirePressePapier()
                if (t.isNotEmpty()) {
                    dernierPresse = t
                    val n = Store.ajouter(t)
                    Toast.makeText(this@MainActivity, if (n > 0) "✔ $n lien(s) ajouté(s)" else "Aucun nouveau lien", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this@MainActivity, "Rien à coller", Toast.LENGTH_SHORT).show()
            }
        }
        val btnAjout = bouton("➕ AJOUTER", Color.parseColor("#546E7A"), 14f).apply {
            setOnClickListener {
                val n = Store.ajouter(champ.text.toString())
                if (n > 0) champ.setText("")
                else Toast.makeText(this@MainActivity, "Aucun lien reconnu (il doit commencer par http)", Toast.LENGTH_SHORT).show()
            }
        }
        ligne1.addView(btnColler, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        ligne1.addView(btnAjout, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4) })
        racine.addView(ligne1, lp(top = 6))

        auto = CheckBox(this).apply {
            text = "Capturer automatiquement les liens copiés"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            isChecked = true
        }
        racine.addView(auto, lp(top = 2))

        // Formats
        val ligneF = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val noms = listOf("Meilleure qualité", "720p léger", "MP3 audio")
        noms.forEachIndexed { idx, nom ->
            val tv = TextView(this).apply {
                text = nom
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setOnClickListener {
                    if (!Store.running) {
                        Store.format = idx
                        refresh()
                    }
                }
            }
            chips.add(tv)
            ligneF.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(3); rightMargin = dp(3)
            })
        }
        racine.addView(ligneF, lp(top = 4))

        // Liste
        compteur = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.DKGRAY)
        }
        racine.addView(compteur, lp(top = 8))

        liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val defil = ScrollView(this).apply {
            background = fond(Color.WHITE, 10)
            addView(liste)
        }
        racine.addView(defil, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })

        // Coureur + bannière
        runner = RunnerView(this)
        racine.addView(runner, lp(top = 4))

        banniere = TextView(this).apply {
            text = "✔ TERMINÉ !"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = fond(vert)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            visibility = View.GONE
        }
        racine.addView(banniere, lp(top = 4))

        // Boutons principaux
        btnGo = bouton("⬇  TÉLÉCHARGER", vert, 22f).apply {
            setOnClickListener { demarrer() }
        }
        racine.addView(btnGo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { topMargin = dp(6) })

        val ligneB = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnStop = bouton("⏹ STOP", rouge, 15f).apply {
            setOnClickListener {
                startService(Intent(this@MainActivity, DownloadService::class.java).setAction(DownloadService.ACTION_STOP))
            }
        }
        val btnVider = bouton("🗑 Vider", Color.parseColor("#757575"), 13f).apply { setOnClickListener { Store.vider() } }
        val btnRetry = bouton("🔁 Réessayer", Color.parseColor("#EF6C00"), 13f).apply { setOnClickListener { Store.relancerErreurs() } }
        ligneB.addView(btnStop, LinearLayout.LayoutParams(0, dp(46), 1.2f).apply { rightMargin = dp(3) })
        ligneB.addView(btnRetry, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        ligneB.addView(btnVider, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3) })
        racine.addView(ligneB, lp(top = 6))

        val ligneM = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ligneM.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        ligneM.addView(TextView(this).apply {
            text = "↻ Mettre à jour le moteur"
            textSize = 11f
            setTextColor(Color.parseColor("#1565C0"))
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setOnClickListener {
                if (Store.running) {
                    Toast.makeText(this@MainActivity, "Attends la fin des téléchargements", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Mise à jour en cours…", Toast.LENGTH_SHORT).show()
                    startForegroundService(Intent(this@MainActivity, DownloadService::class.java).setAction(DownloadService.ACTION_UPDATE))
                }
            }
        })
        racine.addView(ligneM, lp(top = 4))

        setContentView(racine)
    }

    private fun lp(top: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun demarrer() {
        if (Store.running) return
        if (champ.text.isNotBlank()) {
            val n = Store.ajouter(champ.text.toString())
            if (n > 0) champ.setText("")
        }
        if (Store.items.none { it.status == Store.ATTENTE }) {
            Toast.makeText(this, "Ajoute d'abord un lien", Toast.LENGTH_SHORT).show()
            return
        }
        Store.finished = false
        startForegroundService(Intent(this, DownloadService::class.java).setAction(DownloadService.ACTION_START))
    }

    private fun refresh() {
        Store.toast?.let {
            Store.toast = null
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }
        // formats
        chips.forEachIndexed { idx, tv ->
            val sel = idx == Store.format
            tv.background = fond(if (sel) vert else Color.parseColor("#CFD8DC"), 20)
            tv.setTextColor(if (sel) Color.WHITE else Color.parseColor("#37474F"))
        }
        // liste
        liste.removeAllViews()
        for (it in Store.items) {
            liste.addView(ligneItem(it))
        }
        val total = Store.items.size
        val faits = Store.items.count { it.status == Store.OK }
        val erreurs = Store.items.count { it.status == Store.ERREUR }
        compteur.text = if (total == 0) "File d'attente vide"
        else "File d'attente : $faits/$total terminé(s)" + if (erreurs > 0) " · $erreurs erreur(s)" else ""

        val enCours = Store.running
        btnGo.isEnabled = !enCours
        btnGo.alpha = if (enCours) 0.5f else 1f
        btnGo.text = if (enCours) "⏳ EN COURS…" else "⬇  TÉLÉCHARGER"
        btnStop.isEnabled = enCours
        btnStop.alpha = if (enCours) 1f else 0.4f

        runner.actif = enCours
        runner.progress = if (Store.finished) 1f else Store.progressionGlobale()
        runner.invalidate()

        if (Store.finished) {
            banniere.visibility = View.VISIBLE
            banniere.text = if (erreurs == 0) "✔ TERMINÉ !" else "✔ TERMINÉ ($erreurs erreur(s))"
            banniere.background = fond(if (erreurs == 0) vert else Color.parseColor("#EF6C00"))
        } else {
            banniere.visibility = View.GONE
        }
    }

    private fun ligneItem(it: Store.Item): View {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(4), dp(6))
        }
        val icone = when (it.status) {
            Store.OK -> "✅"
            Store.COURS -> "⬇"
            Store.ERREUR -> "❌"
            else -> "⏳"
        }
        l.addView(TextView(this).apply { text = icone; textSize = 18f }, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val nom = if (it.titre.isNotEmpty()) it.titre else it.url
        col.addView(TextView(this).apply {
            text = nom
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.BLACK)
        })
        val detail = when (it.status) {
            Store.COURS -> "${it.pct.toInt()} %"
            Store.ERREUR -> it.erreur
            Store.OK -> "Enregistré dans Téléchargements/TELECHARGEMENTS"
            else -> "En attente"
        }
        col.addView(TextView(this).apply {
            text = detail
            textSize = 11f
            maxLines = 8
            setTextColor(if (it.status == Store.ERREUR) rouge else Color.GRAY)
        })
        l.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (it.status != Store.COURS) {
            l.addView(TextView(this).apply {
                text = "✕"
                textSize = 20f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setOnClickListener { _ -> Store.retirer(it) }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        return l
    }
}
