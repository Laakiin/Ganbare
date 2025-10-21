package fr.lkn.ganbare.feature.tasks.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Instant,
    val priority: Int,
    val notes: String? = null,
    val createdAt: Instant = Instant.now()
)
