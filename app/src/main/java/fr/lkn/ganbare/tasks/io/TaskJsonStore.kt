package fr.lkn.ganbare.tasks.io

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Stockage JSON partagé app + widgets.
 * - Interne (source de vérité) : files/tasks/tasks.json
 * - Externe (consultable) : Android/data/<pkg>/files/tasks/tasks.json
 */
object TaskJsonStore {

    private val ioMutex = Mutex()

    private fun internalDir(context: Context): File =
        File(context.filesDir, "tasks").apply { if (!exists()) mkdirs() }

    private fun internalFile(context: Context): File =
        File(internalDir(context), "tasks.json")

    private fun externalDir(context: Context): File? =
        context.getExternalFilesDir(null)?.let { base ->
            File(base, "tasks").apply { if (!exists()) mkdirs() }
        }

    private fun externalFile(context: Context): File? =
        externalDir(context)?.let { File(it, "tasks.json") }

    suspend fun readAll(context: Context): List<TaskJson> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val fin = internalFile(context)
            if (fin.exists()) return@withLock parseFile(fin)

            val fext = externalFile(context)
            if (fext != null && fext.exists()) return@withLock parseFile(fext)

            emptyList()
        }
    }

    private fun parseFile(f: File): List<TaskJson> {
        val bytes = FileInputStream(f).use { it.readBytes() }
        if (bytes.isEmpty()) return emptyList()
        val txt = String(bytes, StandardCharsets.UTF_8)
        if (txt.isBlank()) return emptyList()
        val root = JSONObject(txt)
        return taskFileFrom(root).tasks
    }

    suspend fun writeAll(context: Context, tasks: List<TaskJson>) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val payload = TaskFile(1, tasks).toJson().toString()

            // Ecrit interne (source)
            runCatching {
                val f = internalFile(context)
                val tmp = File(f.parentFile, f.name + ".tmp")
                FileOutputStream(tmp).use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
                if (f.exists()) f.delete()
                tmp.renameTo(f)
            }

            // Copie externe (consultable)
            runCatching {
                val f = externalFile(context)
                if (f != null) {
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    FileOutputStream(tmp).use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
                    if (f.exists()) f.delete()
                    tmp.renameTo(f)
                }
            }

            notifyTopTaskWidget(context)
        }
    }

    /** ID stable utile pour compat. */
    fun hashIdOf(title: String, priority: Int, dueAtMillis: Long?): String {
        val src = "$title|$priority|${dueAtMillis ?: "null"}"
        val digest = MessageDigest.getInstance("SHA-256").digest(src.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun notifyTopTaskWidget(context: Context) {
        // Notifie le widget "TopPriorityTaskWidgetProvider" s'il existe dans l'appli
        runCatching {
            val cls = Class.forName("fr.lkn.ganbare.widgets.TopPriorityTaskWidgetProvider")
            val mgr = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, cls)
            val ids: IntArray = mgr.getAppWidgetIds(cn)
            if (ids.isEmpty()) return@runCatching
            val intent = Intent(context, cls).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
