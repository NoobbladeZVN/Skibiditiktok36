package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "solved_sessions")
data class SolvedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val educationLevel: String,
    val subject: String,
    val title: String,
    val problemText: String,
    val imagePath: String?,
    val finalAnswer: String,
    val detailedSteps: String,
    val similarExercise: String,
    val similarExerciseHint: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = SolvedSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val isUser: Boolean,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class SessionWithMessages(
    @Embedded val session: SolvedSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val messages: List<ChatMessageEntity>
)
