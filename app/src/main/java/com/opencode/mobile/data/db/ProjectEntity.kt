package com.opencode.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val localPath: String,
    val lastOpened: Long = System.currentTimeMillis(),
    val isGitRepo: Boolean = false,
    val gitRemoteUrl: String? = null
)
