package fr.lkn.ganbare.tasks.io

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modèles JSON persistant pour les tâches.
 */
data class TaskJson(
    val id: String,
    val title: String,
    val priority: Int,
    val dueAtMillis: Long?,      // null si pas d’échéance
    val done: Boolean,
    val notes: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class TaskFile(
    val version: Int = 1,
    val tasks: List<TaskJson>
)

fun TaskJson.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("priority", priority)
    if (dueAtMillis == null) put("dueAtMillis", JSONObject.NULL) else put("dueAtMillis", dueAtMillis)
    put("done", done)
    if (notes == null) put("notes", JSONObject.NULL) else put("notes", notes)
    put("createdAtMillis", createdAtMillis)
    put("updatedAtMillis", updatedAtMillis)
}

fun taskJsonFrom(obj: JSONObject): TaskJson = TaskJson(
    id = obj.optString("id"),
    title = obj.optString("title"),
    priority = obj.optInt("priority"),
    dueAtMillis = if (obj.isNull("dueAtMillis")) null else obj.optLong("dueAtMillis"),
    done = obj.optBoolean("done", false),
    notes = if (obj.isNull("notes")) null else obj.optString("notes"),
    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
    updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
)

fun TaskFile.toJson(): JSONObject = JSONObject().apply {
    put("version", version)
    val arr = JSONArray()
    tasks.forEach { arr.put(it.toJson()) }
    put("tasks", arr)
}

fun taskFileFrom(root: JSONObject): TaskFile {
    val version = root.optInt("version", 1)
    val arr = root.optJSONArray("tasks") ?: JSONArray()
    val list = ArrayList<TaskJson>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        list.add(taskJsonFrom(o))
    }
    return TaskFile(version, list)
}
