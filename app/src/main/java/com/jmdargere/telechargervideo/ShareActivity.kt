package com.jmdargere.telechargervideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Reçoit un lien depuis le menu "Partager" (Firefox, YouTube, Facebook...) et le met dans la file. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val txt = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val n = Store.ajouter(txt)
        val msg = if (n > 0) "✔ $n lien(s) ajouté(s) à la file" else "Aucun nouveau lien trouvé"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        if (n > 0) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}
