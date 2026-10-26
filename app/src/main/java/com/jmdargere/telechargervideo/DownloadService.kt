package com.jmdargere.telechargervideo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.concurrent.thread

class DownloadService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_UPDATE = "update"
        const val CH_PROGRESS = "progress"
        const val CH_DONE = "done"
        @Volatile var initDone = false
        val extOk = setOf("mp4", "mkv", "webm", "mp3", "m4a", "mov", "avi", "flv", "ogg", "opus", "aac", "wav", "3gp")
    }

    private var wake: PowerManager.WakeLock? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        creerCanaux()
        when (intent?.action) {
            ACTION_STOP -> {
                Store.stopRequested = true
                Store.currentProcessId?.let { try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Throwable) {} }
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                demarrerPremierPlan("Mise à jour de yt-dlp…", 0)
                thread {
                    var msg: String
                    try {
                        initLibs()
                        YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                        msg = "yt-dlp est à jour"
                    } catch (e: Throwable) {
                        msg = "Mise à jour impossible : " + (e.message ?: "erreur")
                    }
                    Store.toast = msg
                    Store.changed()
                    if (!Store.running) stopForeground(STOP_FOREGROUND_REMOVE)
                    if (!Store.running) stopSelf()
                }
                return START_NOT_STICKY
            }
        }
        if (worker?.isAlive == true) return START_NOT_STICKY
        demarrerPremierPlan("Préparation…", 0)
        Store.running = true
        Store.stopRequested = false
        Store.finished = false
        Store.changed()
        worker = thread { travail() }
        return START_NOT_STICKY
    }

    private fun initLibs() {
        if (initDone) return
        YoutubeDL.getInstance().init(applicationContext)
        FFmpeg.getInstance().init(applicationContext)
        initDone = true
    }

    private fun travail() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telechargervideo:dl").also { it.acquire(3 * 60 * 60 * 1000L) }
        var ok = 0
        var ko = 0
        try {
            try {
                initLibs()
            } catch (e: Throwable) {
                for (i in Store.items) if (i.status == Store.ATTENTE) {
                    i.status = Store.ERREUR
                    i.erreur = "Initialisation impossible : " + (e.message ?: "")
                    ko++
                }
                return
            }
            while (!Store.stopRequested) {
                val item = Store.items.firstOrNull { it.status == Store.ATTENTE } ?: break
                item.status = Store.COURS
                item.pct = 0f
                Store.changed()
                majNotif(item, "Téléchargement…")
                val tmp = File(getExternalFilesDir(null) ?: cacheDir, "tmp_" + System.currentTimeMillis())
                tmp.mkdirs()
                val pid = "dl" + System.currentTimeMillis()
                Store.currentProcessId = pid
                try {
                    try {
                        executer(construire(item.url, tmp, null, null, false), pid, item)
                    } catch (e: Throwable) {
                        if (Store.stopRequested) throw e
                        val unsupported = (e.message ?: "").contains("Unsupported URL")
                        if (!unsupported) {
                            // 2e essai : version simplifiée (un seul fichier, sans fusion)
                            tmp.listFiles()?.forEach { it.delete() }
                            item.pct = 0f
                            majNotif(item, "Nouvel essai…")
                            try {
                                executer(construire(item.url, tmp, null, null, true), pid, item)
                                throw OkSimple()
                            } catch (_: OkSimple) {
                                // réussi : on continue plus bas
                            } catch (e2: Throwable) {
                                if (Store.stopRequested) throw e2
                                detecterEtTelecharger(item, tmp, pid, e2)
                            }
                        } else {
                            detecterEtTelecharger(item, tmp, pid, e)
                        }
                    }
                    val fichier = tmp.listFiles()
                        ?.filter { it.isFile && it.extension.lowercase() in extOk }
                        ?.maxByOrNull { it.length() }
                    if (fichier == null) throw RuntimeException("Aucun fichier reçu")
                    item.titre = fichier.nameWithoutExtension.replace('_', ' ')
                    enregistrer(fichier)
                    item.pct = 100f
                    item.status = Store.OK
                    ok++
                } catch (e: Throwable) {
                    if (Store.stopRequested) {
                        item.status = Store.ATTENTE
                        item.pct = 0f
                    } else {
                        item.status = Store.ERREUR
                        item.erreur = (e.message ?: "erreur").lines().filter { it.isNotBlank() }.takeLast(3).joinToString(" | ").take(420)
                        ko++
                    }
                } finally {
                    Store.currentProcessId = null
                    tmp.deleteRecursively()
                    Store.changed()
                }
            }
        } finally {
            Store.running = false
            val stoppe = Store.stopRequested
            Store.stopRequested = false
            Store.finished = !stoppe && (ok > 0 || ko > 0)
            try { wake?.release() } catch (_: Throwable) {}
            Store.changed()
            if (Store.finished) fin(ok, ko)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private class OkSimple : RuntimeException()

    /** Dernier recours : repère le lien direct de la vidéo dans la page puis le télécharge. */
    private fun detecterEtTelecharger(item: Store.Item, tmp: File, pid: String, cause: Throwable) {
        item.pct = 0f
        majNotif(item, "Détection de la vidéo…")
        Store.changed()
        StreamSniffer.urlCible = item.url
        val r = StreamSniffer.detect(applicationContext) { Store.stopRequested }
        if (Store.stopRequested) throw cause
        tmp.listFiles()?.forEach { it.delete() }
        if (r.flux == null) {
            throw RuntimeException("Aucune vidéo libre trouvée sur cette page. Détail : " + (cause.message ?: "").take(300))
        }
        val nom = r.titre.replace(Regex("[\\\\/:*?\"<>|%\\r\\n]+"), " ").trim().take(80).ifEmpty { "video" }
        executer(construire(r.flux, tmp, nom, r.page, true), pid, item)
    }

    private fun construire(url: String, tmp: File, nom: String?, referer: String?, simple: Boolean): YoutubeDLRequest {
        val req = YoutubeDLRequest(url)
        req.addOption("--no-playlist")
        req.addOption("--retries", "5")
        if (!simple) {
            req.addOption("--no-mtime")
            req.addOption("--restrict-filenames")
        }
        req.addOption("-o", tmp.absolutePath + "/" + (nom ?: "%(title).80s") + ".%(ext)s")
        if (referer != null) {
            req.addOption("--referer", referer)
            req.addOption("--user-agent", android.webkit.WebSettings.getDefaultUserAgent(applicationContext))
        }
        when (Store.format) {
            1 -> {
                if (simple) {
                    req.addOption("-f", "b[height<=720]/b/best")
                } else {
                    req.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b")
                    req.addOption("--merge-output-format", "mp4")
                }
            }
            2 -> {
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
            }
            else -> {
                if (simple) {
                    req.addOption("-f", "b/best")
                } else {
                    req.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    req.addOption("--merge-output-format", "mp4")
                }
            }
        }
        return req
    }

    private fun executer(req: YoutubeDLRequest, pid: String, item: Store.Item) {
        YoutubeDL.getInstance().execute(req, pid) { progress, _, _ ->
            item.pct = if (progress < 0f) item.pct else progress
            Store.changed()
            majNotif(item, "Téléchargement…")
        }
    }

    private fun enregistrer(f: File) {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (ext == "mkv") "video/x-matroska" else "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/TELECHARGEMENTS")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("Impossible d'écrire dans Téléchargements")
        contentResolver.openOutputStream(uri)?.use { out ->
            f.inputStream().use { it.copyTo(out) }
        } ?: throw RuntimeException("Impossible d'écrire le fichier")
        val v2 = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        contentResolver.update(uri, v2, null, null)
    }

    private fun fin(ok: Int, ko: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 400)
            Thread.sleep(500)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
            Thread.sleep(700)
            tg.release()
        } catch (_: Throwable) {}
        val nm = getSystemService(NotificationManager::class.java)
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val txt = if (ko == 0) "$ok vidéo(s) dans Téléchargements/TELECHARGEMENTS" else "$ok réussie(s), $ko en erreur"
        val n = Notification.Builder(this, CH_DONE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("✔ TERMINÉ")
            .setContentText(txt)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2, n)
    }

    private fun majNotif(item: Store.Item, texte: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, notif(texte + " " + item.url.take(50), (Store.progressionGlobale() * 100).toInt()))
    }

    private fun notif(texte: String, pct: Int): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("TÉLÉCHARGER VIDÉO")
            .setContentText(texte)
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setContentIntent(pi)
            .addAction(Notification.Action.Builder(null, "STOP", stop).build())
            .build()
    }

    private fun demarrerPremierPlan(texte: String, pct: Int) {
        startForeground(1, notif(texte, pct), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun creerCanaux() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Progression", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Terminé", NotificationManager.IMPORTANCE_DEFAULT))
    }
}
