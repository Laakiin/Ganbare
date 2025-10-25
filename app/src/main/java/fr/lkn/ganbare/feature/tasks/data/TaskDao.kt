package fr.lkn.ganbare.feature.tasks.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueAt ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueAt >= :from ORDER BY dueAt ASC")
    fun observeUpcoming(from: Instant): Flow<List<TaskEntity>>

    // "Upsert" manuel : on remplace si le primary key existe déjà
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity): Long

    @Delete
    suspend fun delete(task: TaskEntity)
}
