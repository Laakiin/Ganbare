package fr.lkn.ganbare.feature.tasks.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    fun observeUpcoming(from: Instant): Flow<List<TaskEntity>> = dao.observeUpcoming(from)
    suspend fun upsert(task: TaskEntity): Long = dao.upsert(task)
    suspend fun delete(task: TaskEntity) = dao.delete(task)
}
