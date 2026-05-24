package com.opencode.mobile.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.db.AppDatabase
import com.opencode.mobile.data.db.ProjectEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            db.projectDao().getAllProjects().collect { projects ->
                _uiState.update { it.copy(projects = projects, isLoading = false) }
            }
        }
    }

    fun openProject(path: String, name: String) {
        viewModelScope.launch {
            val existing = db.projectDao().getAllProjects().first().find { it.localPath == path }
            if (existing == null) {
                db.projectDao().insertProject(
                    ProjectEntity(
                        name = name,
                        localPath = path,
                        isGitRepo = File(path, ".git").exists()
                    )
                )
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            db.projectDao().deleteProject(project)
        }
    }
}
