package fr.lkn.ganbare.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.core.work.RemindersScheduler
import fr.lkn.ganbare.tasks.io.TaskJson
import fr.lkn.ganbare.tasks.io.TaskJsonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.LocalTime

/**
 * ViewModel de l'onglet "Tâches"
 * - Persistance **JSON** via TaskJsonStore (partagée avec le widget)
 * - Planifie/annule les rappels via RemindersScheduler
 */
class TasksViewModel(app: Application) : AndroidViewModel(app) {

    data class TaskVM(
        val id: Long,        // identifiant interne pour WorkManager
        val title: String,
        val dueAt: Long,     // epoch millis
        val priority: Int    // 0..3 (P1..P4)
    )

    private val _tasks = MutableStateFlow<List<TaskVM>>(emptyList())
    val tasks: StateFlow<List<TaskVM>> = _tasks

    init {
        // Charger le JSON au démarrage
        viewModelScope.launch(Dispatchers.IO) {
            val list = TaskJsonStore.readAll(getApplication())
            val mapped = list.map { json ->
                TaskVM(
                    id = json.id.toStableLong(),
                    title = json.title,
                    dueAt = json.dueAtMillis ?: 0L,
                    priority = json.priority
                )
            }.sortedWith(
                compareBy<TaskVM> { it.priority }
                    .thenBy { it.dueAt.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }
                    .thenBy { it.title }
            )
            _tasks.value = mapped
            // Replanifie toutes les tâches lues du JSON (remplace si déjà présent)
            mapped.forEach { t ->
                RemindersScheduler.scheduleForTask(
                    context = getApplication(),
                    taskId = t.id,
                    title = t.title,
                    dueAtMillis = t.dueAt,
                    priority = t.priority,
                    summaryTime = null
                )
            }
        }
    }

    /** Ajoute une tâche et persiste en JSON. */
    fun addTask(title: String, dueAt: Long, priority: Int) {
        val vm = TaskVM(
            id = System.currentTimeMillis(),
            title = title,
            dueAt = dueAt,
            priority = priority
        )
        _tasks.value = (_tasks.value + vm).sortedWith(
            compareBy<TaskVM> { it.priority }
                .thenBy { it.dueAt.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }
                .thenBy { it.title }
        )
        persistAndSchedule(vmAdded = vm)
    }

    /** Édite une tâche (si tu ajoutes un écran d'édition plus tard). */
    fun editTask(id: Long, newTitle: String, newDueAt: Long, newPriority: Int) {
        val updated = _tasks.value.map { old ->
            if (old.id == id) old.copy(title = newTitle, dueAt = newDueAt, priority = newPriority) else old
        }.sortedWith(
            compareBy<TaskVM> { it.priority }
                .thenBy { it.dueAt.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }
                .thenBy { it.title }
        )
        _tasks.value = updated
        persistAndSchedule()
    }

    /** Supprime une tâche. */
    fun removeTask(id: Long) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        // Annuler les rappels de cette tâche
        viewModelScope.launch {
            RemindersScheduler.cancelForTask(getApplication(), id)
        }
        persistAndSchedule()
    }

    /** Replanifie toutes les tâches (par ex. après modification de l'heure de récap). */
    fun rescheduleAll(summaryTime: LocalTime? = null) {
        viewModelScope.launch {
            _tasks.value.forEach { t ->
                RemindersScheduler.scheduleForTask(
                    context = getApplication(),
                    taskId = t.id,
                    title = t.title,
                    dueAtMillis = t.dueAt,
                    priority = t.priority,
                    summaryTime = summaryTime
                )
            }
        }
    }

    /** Utilitaire public si tu veux planifier une tâche déjà existante (optionnel). */
    fun scheduleForExistingTask(
        id: Long,
        title: String,
        dueAt: Long,
        priority: Int,
        summaryTime: LocalTime? = null
    ) {
        viewModelScope.launch {
            RemindersScheduler.scheduleForTask(
                context = getApplication(),
                taskId = id,
                title = title,
                dueAtMillis = dueAt,
                priority = priority,
                summaryTime = summaryTime
            )
        }
    }

    // ---------- Persistance JSON + Planification ----------

    private fun persistAndSchedule(vmAdded: TaskVM? = null) {
        val snapshot = _tasks.value
        // Ecriture JSON + éventuelle planification en IO
        viewModelScope.launch(Dispatchers.IO) {
            val jsonList = snapshot.map { vm ->
                TaskJson(
                    id = vm.stableStringId(),
                    title = vm.title,
                    priority = vm.priority,
                    dueAtMillis = vm.dueAt,
                    done = false,
                    notes = null
                )
            }
            TaskJsonStore.writeAll(getApplication(), jsonList)

            // Planifie la tâche ajoutée si fournie (plus réactif)
            if (vmAdded != null) {
                RemindersScheduler.scheduleForTask(
                    context = getApplication(),
                    taskId = vmAdded.id,
                    title = vmAdded.title,
                    dueAtMillis = vmAdded.dueAt,
                    priority = vmAdded.priority,
                    summaryTime = null
                )
            }
        }
    }

    private fun TaskVM.stableStringId(): String {
        // On génère une clé stable pour le JSON à partir du contenu (id interne + titre + due + prio)
        val raw = "${this.id}|${this.title}|${this.dueAt}|${this.priority}"
        return raw.sha256Hex().take(32)
    }

    private fun String.toStableLong(): Long {
        // Transforme un ID texte stable en Long (WorkManager) via un hash (64 bits)
        val bytes = this.sha256Bytes()
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (bytes[i].toLong() and 0xffL)
        }
        return if (v == 0L) System.currentTimeMillis() else v
    }

    private fun String.sha256Bytes(): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(this.toByteArray(Charsets.UTF_8))

    private fun String.sha256Hex(): String =
        this.sha256Bytes().joinToString("") { "%02x".format(it) }
}
