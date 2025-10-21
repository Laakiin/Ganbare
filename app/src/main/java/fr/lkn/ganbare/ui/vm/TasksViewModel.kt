package fr.lkn.ganbare.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lkn.ganbare.feature.tasks.data.TaskEntity
import fr.lkn.ganbare.feature.tasks.data.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: TaskRepository
) : ViewModel() {

    val tasks = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addTask(title: String, dueAt: Instant, priority: Int, notes: String?) {
        viewModelScope.launch {
            repo.upsert(TaskEntity(title = title, dueAt = dueAt, priority = priority, notes = notes))
        }
    }
}
