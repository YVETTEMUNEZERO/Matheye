package com.example.matheye

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(item: HistoryItem)

    @Query("SELECT * FROM history_items ORDER BY id DESC")
    fun getAll(): Flow<List<HistoryItem>>
}
