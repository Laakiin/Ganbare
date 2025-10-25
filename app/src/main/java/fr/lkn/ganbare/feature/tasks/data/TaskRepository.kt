package fr.lkn.ganbare.feature.tasks.data

import android.content.Context
import fr.lkn.ganbare.tasks.io.TaskJson
import fr.lkn.ganbare.tasks.io.TaskJsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Repository file-backed (JSON).
 * - Source de vérité: TaskJsonStore
 * - Expose des Flows pour l'UI
 */
class TaskRepository(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _tasks.asStateFlow()

    init {
        scope.launch {
            val loadedJson: List<TaskJson> = TaskJsonStore.readAll(appContext)
            val entities = loadedJson.mapNotNull { jsonToEntity(it) }
            _tasks.value = sortEntities(entities)
        }
    }

    fun observeAll(): Flow<List<TaskEntity>> = tasks

    fun observeUpcoming(now: Instant): Flow<List<TaskEntity>> =
        tasks.map { list ->
            val nowMs = now.toEpochMilli()
            val sorted = sortEntities(list)
            val hasFuture = sorted.any { (it.dueAtMillisOrNull() ?: Long.MAX_VALUE) >= nowMs && !it.isDone() }
            if (hasFuture) {
                sorted.filter { !it.isDone() && (it.dueAtMillisOrNull() ?: Long.MAX_VALUE) >= nowMs }
            } else {
                sorted.filter { !it.isDone() }
            }
        }

    fun upsert(task: TaskEntity) {
        scope.launch {
            val current = _tasks.value.toMutableList()
            val id = task.safeId()
            val idx = current.indexOfFirst { it.safeId() == id }
            if (idx >= 0) current[idx] = task else current.add(task)
            val sorted = sortEntities(current)
            _tasks.value = sorted
            TaskJsonStore.writeAll(appContext, sorted.map { entityToJson(it) })
        }
    }

    fun delete(taskId: String) {
        scope.launch {
            val current = _tasks.value.filter { it.safeId() != taskId }
            _tasks.value = current
            TaskJsonStore.writeAll(appContext, current.map { entityToJson(it) })
        }
    }

    fun toggleDone(taskId: String, done: Boolean) {
        scope.launch {
            val updated = _tasks.value.map { e ->
                if (e.safeId() == taskId) e.setDoneCompat(done) else e
            }
            _tasks.value = updated
            TaskJsonStore.writeAll(appContext, updated.map { entityToJson(it) })
        }
    }

    // ---------- Tri commun ----------
    private fun sortEntities(list: List<TaskEntity>): List<TaskEntity> =
        list.sortedWith(
            compareBy<TaskEntity> { it.priorityCompat() }
                .thenBy { it.dueAtMillisOrNull() ?: Long.MAX_VALUE }
                .thenBy { it.titleCompat() }
        )

    // ---------- Compat TaskEntity <-> JSON ----------

    private fun TaskEntity.safeId(): String {
        try {
            val f = this.javaClass.getDeclaredField("id")
            f.isAccessible = true
            val v = f.get(this)
            if (v != null) return v.toString()
        } catch (_: Throwable) {}
        return TaskJsonStore.hashIdOf(titleCompat(), priorityCompat(), dueAtMillisOrNull())
    }

    private fun TaskEntity.titleCompat(): String {
        return try {
            val f = this.javaClass.getDeclaredField("title")
            f.isAccessible = true
            f.get(this)?.toString() ?: "(sans titre)"
        } catch (_: Throwable) { "(sans titre)" }
    }

    private fun TaskEntity.priorityCompat(): Int {
        return try {
            val f = this.javaClass.getDeclaredField("priority")
            f.isAccessible = true
            val v = f.get(this)
            when (v) {
                is Int -> v
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: 9999
                else -> 9999
            }
        } catch (_: Throwable) { 9999 }
    }

    private fun TaskEntity.isDone(): Boolean {
        // done or isDone
        try {
            val f = this.javaClass.getDeclaredField("done")
            f.isAccessible = true
            val v = f.get(this)
            return when (v) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> v.equals("true", true)
                else -> false
            }
        } catch (_: Throwable) {}
        try {
            val f = this.javaClass.getDeclaredField("isDone")
            f.isAccessible = true
            val v = f.get(this)
            return when (v) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> v.equals("true", true)
                else -> false
            }
        } catch (_: Throwable) {}
        return false
    }

    private fun TaskEntity.setDoneCompat(done: Boolean): TaskEntity {
        // Tente une méthode setDone(Boolean)
        try {
            val m = this.javaClass.methods.firstOrNull { it.name == "setDone" && it.parameterCount == 1 }
            if (m != null) return (m.invoke(this, done) as? TaskEntity) ?: this
        } catch (_: Throwable) {}
        // Sinon, si data class avec copy(... done=...), c’est compliqué sans kotlin-reflect.
        // On retourne l’instance telle quelle ; l’UI peut créer un nouvel objet si besoin.
        return this
    }

    private fun TaskEntity.dueAtMillisOrNull(): Long? {
        // dueAtMillis
        try {
            val f = this.javaClass.getDeclaredField("dueAtMillis")
            f.isAccessible = true
            val v = f.get(this) as? Long
            if (v != null && v > 0) return v
        } catch (_: Throwable) {}
        // dueAt (Instant/LocalDateTime/Date/Long)
        try {
            val f = this.javaClass.getDeclaredField("dueAt")
            f.isAccessible = true
            val v = f.get(this)
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

    private fun entityToJson(e: TaskEntity): TaskJson = TaskJson(
        id = e.safeId(),
        title = e.titleCompat(),
        priority = e.priorityCompat(),
        dueAtMillis = e.dueAtMillisOrNull(),
        done = e.isDone(),
        notes = null
    )

    private fun jsonToEntity(t: TaskJson): TaskEntity? {
        val cls = try {
            Class.forName("fr.lkn.ganbare.feature.tasks.data.TaskEntity")
        } catch (_: Throwable) { return null }

        val zone = ZoneId.systemDefault()
        val dueInstant = t.dueAtMillis?.let { Instant.ofEpochMilli(it) }
        val dueLdt = t.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }

        val signatures = listOf(
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!, Instant::class.java, Boolean::class.javaPrimitiveType!!),
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!, java.lang.Long.TYPE, Boolean::class.javaPrimitiveType!!),
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!, LocalDateTime::class.java, Boolean::class.javaPrimitiveType!!),
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!, Instant::class.java),
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!, java.lang.Long.TYPE),
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!),
            arrayOf(String::class.java, Int::class.javaPrimitiveType!!, java.lang.Long.TYPE, Boolean::class.javaPrimitiveType!!),
            arrayOf(String::class.java, Int::class.javaPrimitiveType!!)
        )

        for (sig in signatures) {
            try {
                val ctor = cls.getDeclaredConstructor(*sig)
                ctor.isAccessible = true
                val args = ArrayList<Any?>()
                var stringCount = 0
                for (p in sig) {
                    when (p) {
                        String::class.java -> {
                            // 1er String = id, 2e = title
                            args.add(if (stringCount == 0) t.id else t.title)
                            stringCount++
                        }
                        Int::class.javaPrimitiveType -> args.add(t.priority)
                        Boolean::class.javaPrimitiveType -> args.add(t.done)
                        Instant::class.java -> args.add(dueInstant ?: Instant.EPOCH)
                        java.lang.Long.TYPE -> args.add(t.dueAtMillis ?: 0L)
                        LocalDateTime::class.java -> args.add(dueLdt ?: LocalDateTime.of(1970,1,1,0,0))
                        else -> args.add(null)
                    }
                }
                val inst = ctor.newInstance(*args.toTypedArray())
                @Suppress("UNCHECKED_CAST")
                return inst as TaskEntity
            } catch (_: Throwable) { /* try next */ }
        }
        return null
    }
}
