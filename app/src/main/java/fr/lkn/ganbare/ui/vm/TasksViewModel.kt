package fr.lkn.ganbare.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.core.work.RemindersScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * NOTE :
 * - Cette VM maintient un état en mémoire pour lister les tâches.
 * - Tu peux facilement brancher ton repository persistant :
 *   - Remplace les ajouts/suppressions/edits par des appels Room/DAO,
 *   - Récupère l'id créé et appelle scheduleForTask(...) juste après l'insertion,
 *   - Appelle cancelForTask(...) avant suppression.
 */
class TasksViewModel(app: Application) : AndroidViewModel(app) {

    data class TaskVM(
        val id: Long,
        val title: String,
        val dueAt: Long,   // epoch millis UTC
        val priority: Int  // 0..3 (P1..P4 -> 1..4 acceptés aussi par le scheduler)
    )

    private val _tasks = MutableStateFlow<List<TaskVM>>(emptyList())
    val tasks: StateFlow<List<TaskVM>> = _tasks

    /** Ajout utilisé par l'écran "Tâches" : signature attendue par ton UI (title, dueAt, priority) */
    fun addTask(title: String, dueAt: Long, priority: Int) {
        val id = System.currentTimeMillis() // à remplacer par l'id DB une fois branché
        val task = TaskVM(id = id, title = title, dueAt = dueAt, priority = priority)

        // Mise à jour locale
        _tasks.value = _tasks.value + task

        // Planif des rappels (lecture des réglages utilisateur en DataStore)
        viewModelScope.launch {
            RemindersScheduler.scheduleForTask(
                context = getApplication(),
                taskId = task.id,
                title = task.title,
                dueAtMillis = task.dueAt,
                priority = task.priority,
                // Tu peux passer l'heure de récap depuis tes prefs actuelles ; null => 20:00
                summaryTime = null // LocalTime.of(20, 0)
            )
        }
    }

    /** Edition (replanifie les rappels) */
    fun editTask(id: Long, newTitle: String, newDueAt: Long, newPriority: Int) {
        val current = _tasks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return
        current[idx] = current[idx].copy(title = newTitle, dueAt = newDueAt, priority = newPriority)
        _tasks.value = current

        viewModelScope.launch {
            // On remplace les works par de nouveaux (ExistingWorkPolicy.REPLACE dans le scheduler)
            RemindersScheduler.scheduleForTask(
                context = getApplication(),
                taskId = id,
                title = newTitle,
                dueAtMillis = newDueAt,
                priority = newPriority,
                summaryTime = null
            )
        }
    }

    /** Suppression (annule les rappels) */
    fun removeTask(id: Long) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        viewModelScope.launch {
            RemindersScheduler.cancelForTask(getApplication(), id)
        }
    }

    // ---------- Helpers possibles si tu veux brancher un repo plus tard ----------

    /** Appeler ceci après avoir inséré en DB et obtenu l'id officiel */
    fun scheduleForExistingTask(id: Long, title: String, dueAt: Long, priority: Int, summaryTime: LocalTime? = null) {
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

    /** Replanifie pour toutes les tâches (ex. après reboot si tu veux) */
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
}
