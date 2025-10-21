package fr.lkn.ganbare.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.lkn.ganbare.feature.tasks.data.TaskEntity
import fr.lkn.ganbare.feature.tasks.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class NewTaskUi(
    val title: String = "",
    val priority: Int = 1,
    val date: LocalDate = LocalDate.now(),         // choisi via DatePicker
    val time: LocalTime = LocalTime.of(18, 0),     // choisi via TimePicker
    val hasDueTime: Boolean = true                 // si false -> échéance à 00:00
)

data class TasksUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val newTask: NewTaskUi = NewTaskUi(),
    val isSaving: Boolean = false,
    val error: String? = null
)

class TasksViewModel(
    private val repo: TaskRepository
) : ViewModel() {

    private val _new = MutableStateFlow(NewTaskUi())
    val new: StateFlow<NewTaskUi> = _new.asStateFlow()

    val state: StateFlow<TasksUiState> =
        repo.observeAll()
            .map { list -> TasksUiState(tasks = list, newTask = _new.value) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TasksUiState())

    fun onTitleChange(t: String) {
        _new.value = _new.value.copy(title = t)
    }

    fun onPriorityChange(p: Int) {
        _new.value = _new.value.copy(priority = p.coerceIn(1, 5))
    }

    fun onDatePicked(date: LocalDate) {
        _new.value = _new.value.copy(date = date)
    }

    fun onTimePicked(time: LocalTime) {
        _new.value = _new.value.copy(time = time, hasDueTime = true)
    }

    fun clearTime() {
        _new.value = _new.value.copy(hasDueTime = false)
    }

    fun addTask() {
        val cur = _new.value
        if (cur.title.isBlank()) return

        val zone = ZoneId.systemDefault()
        val dueInstant: Instant =
            if (cur.hasDueTime) {
                LocalDateTime.of(cur.date, cur.time).atZone(zone).toInstant()
            } else {
                cur.date.atStartOfDay(zone).toInstant()
            }

        viewModelScope.launch {
            repo.upsert(
                TaskEntity(
                    id = 0,
                    title = cur.title.trim(),
                    dueAt = dueInstant,
                    priority = cur.priority,
                    notes = null
                )
            )
            // reset pour la prochaine saisie (on garde priorité + heure choisie)
            _new.value = _new.value.copy(
                title = "",
                date = LocalDate.now()
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val task = state.value.tasks.firstOrNull { it.id == id } ?: return@launch
            repo.delete(task)
        }
    }

    companion object {
        fun factory(repo: TaskRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TasksViewModel(repo) as T
                }
            }
    }
}
