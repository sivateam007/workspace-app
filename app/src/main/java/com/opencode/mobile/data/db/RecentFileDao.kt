package com.opencode.mobile.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files WHERE projectId = :projectId ORDER BY lastOpened DESC")
    fun getRecentFilesForProject(projectId: Long): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(file: RecentFileEntity)

    @Delete
    suspend fun deleteRecentFile(file: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE projectId = :projectId")
    suspend fun clearRecentFilesForProject(projectId: Long)
}
