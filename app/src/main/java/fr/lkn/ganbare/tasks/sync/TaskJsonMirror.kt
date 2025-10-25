package fr.lkn.ganbare.tasks.sync

import android.content.Context
import android.util.Log
import fr.lkn.ganbare.tasks.io.TaskJson
import fr.lkn.ganbare.tasks.io.TaskJsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mini miroir DB -> JSON au démarrage (best-effort, aucune dépendance forte).
 * - Tente de lire les tâches via DAO et écrit tasks.json si des données existent.
 * - S’il n’y a pas de DB ou de DAO accessible, ne fait rien (pas de crash).
 */
object TaskJsonMirror {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appCtx = context.applicationContext

        scope.launch {
            try {
                val dao = resolveDao(appCtx) ?: return@launch
                val list = readListOnce(dao)
                if (list.isEmpty()) return@launch
                val json = list.mapNotNull { anyToTaskJson(it) }
                if (json.isNotEmpty()) {
                    TaskJsonStore.writeAll(appCtx, json)
                }
            } catch (t: Throwable) {
                Log.e("TaskJsonMirror", "Mirror start failed", t)
            }
        }
    }

    // --- Reflection helpers ---

    private fun resolveDao(context: Context): Any? {
        return try {
            val dbCls = Class.forName("fr.lkn.ganbare.core.db.AppDatabase")
            val get = arrayOf("getDatabase", "getInstance", "get").firstNotNullOfOrNull { name ->
                try { dbCls.getDeclaredMethod(name, Context::class.java).invoke(null, context) } catch (_: Throwable) { null }
            } ?: return null
            dbCls.getMethod("taskDao").invoke(get)
        } catch (_: Throwable) { null }
    }

    private fun readListOnce(dao: Any): List<Any> {
        // essaye getAll()/all()/loadAll()
        val names = listOf("getAll", "all", "loadAll", "listAll", "selectAll")
        for (n in names) {
            try {
                val m = dao.javaClass.methods.firstOrNull { it.name == n && it.parameterCount == 0 } ?: continue
                val res = m.invoke(dao)
                if (res is List<*>) return res.filterNotNull()
            } catch (_: Throwable) { }
        }
        return emptyList()
    }

    private fun anyToTaskJson(o: Any): TaskJson? {
        return try {
            val c = o.javaClass
            val id = readString(c, o, "id") ?: ""
            val title = readString(c, o, "title") ?: "(sans titre)"
            val prio = readInt(c, o, "priority") ?: 9999
            val done = readBool(c, o, "done") ?: (readBool(c, o, "isDone") ?: false)
            val dueMs = readDueAtMillis(c, o)
            val finalId = if (id.isNotBlank()) id else TaskJsonStore.hashIdOf(title, prio, dueMs)
            TaskJson(
                id = finalId,
                title = title,
                priority = prio,
                dueAtMillis = dueMs,
                done = done,
                notes = null
            )
        } catch (_: Throwable) { null }
    }

    private fun readString(cls: Class<*>, inst: Any, name: String): String? = try {
        val f = cls.getDeclaredField(name); f.isAccessible = true; f.get(inst)?.toString()
    } catch (_: Throwable) { null }

    private fun readInt(cls: Class<*>, inst: Any, name: String): Int? = try {
        val f = cls.getDeclaredField(name); f.isAccessible = true
        when (val v = f.get(inst)) {
            is Int -> v
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
    } catch (_: Throwable) { null }

    private fun readBool(cls: Class<*>, inst: Any, name: String): Boolean? = try {
        val f = cls.getDeclaredField(name); f.isAccessible = true
        when (val v = f.get(inst)) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.equals("true", true)
            else -> null
        }
    } catch (_: Throwable) { null }

    private fun readDueAtMillis(cls: Class<*>, inst: Any): Long? {
        try {
            val f = cls.getDeclaredField("dueAtMillis"); f.isAccessible = true
            val v = f.get(inst) as? Long
            if (v != null && v > 0) return v
        } catch (_: Throwable) {}
        try {
            val f = cls.getDeclaredField("dueAt"); f.isAccessible = true
            val v = f.get(inst)
            return when (v) {
                is Long -> v
                is Instant -> v.toEpochMilli()
                is LocalDateTime -> v.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                is java.util.Date -> v.time
                else -> null
            }
        } catch (_: Throwable) {}
        return null
    }
}
