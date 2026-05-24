package com.opencode.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val filePath: String,
    val fileName: String,
    val scrollPosition: Int = 0,
    val lastOpened: Long = System.currentTimeMillis()
)
