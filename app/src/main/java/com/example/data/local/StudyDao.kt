package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {

    @Transaction
    @Query("SELECT * FROM solved_sessions ORDER BY updatedAt DESC")
    fun observeAllSessionsWithMessages(): Flow<List<SessionWithMessages>>

    @Transaction
    @Query("SELECT * FROM solved_sessions WHERE id = :sessionId")
    fun observeSessionById(sessionId: Long): Flow<SessionWithMessages?>

    @Query("SELECT * FROM solved_sessions WHERE id = :sessionId")
    suspend fun getSessionOnce(sessionId: Long): SolvedSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SolvedSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE solved_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: Long, updatedAt: Long)

    @Query("DELETE FROM solved_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}
