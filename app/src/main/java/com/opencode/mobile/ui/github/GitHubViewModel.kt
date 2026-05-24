package com.opencode.mobile.ui.github

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.github.GitHubApiService
import com.opencode.mobile.data.github.GitHubRepo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GitHubUiState(
    val repos: List<GitHubRepo> = emptyList(),
    val token: String = "",
    val isLoading: Boolean = false,
    val cloneProgress: String = "",
    val commitMessage: String = "",
    val commitResult: String = "",
    val pullResult: String = "",
    val error: String? = null
)

class GitHubViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("opencode_prefs", 0)
    private val _uiState = MutableStateFlow(GitHubUiState())
    val uiState: StateFlow<GitHubUiState> = _uiState.asStateFlow()
    private var apiService: GitHubApiService? = null

    init {
        val savedToken = prefs.getString("github_token", "") ?: ""
        _uiState.update { it.copy(token = savedToken) }
        if (savedToken.isNotBlank()) {
            apiService = GitHubApiService(savedToken)
            loadRepos()
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString("github_token", token).apply()
        _uiState.update { it.copy(token = token) }
        apiService = GitHubApiService(token)
        loadRepos()
    }

    fun loadRepos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val repos = apiService?.listRepos() ?: emptyList()
                _uiState.update { it.copy(repos = repos, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun cloneRepo(repo: GitHubRepo, localBasePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, cloneProgress = "Downloading ${repo.name}...") }
            try {
                val parts = repo.fullName.split("/")
                val localPath = "$localBasePath/${repo.name}"
                apiService?.downloadRepoToLocal(parts[0], parts[1], localPath)
                _uiState.update { it.copy(isLoading = false, cloneProgress = "Cloned to $localPath") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message, cloneProgress = "") }
            }
        }
    }

    fun setCommitMessage(msg: String) {
        _uiState.update { it.copy(commitMessage = msg) }
    }

    fun commit(repo: GitHubRepo, owner: String, repoName: String, branch: String, localPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, commitResult = "", error = null) }
            try {
                val files = withContext(Dispatchers.IO) {
                    val base = File(localPath)
                    if (base.exists()) {
                        base.walkTopDown().filter { it.isFile }.map { file ->
                            val relativePath = file.relativeTo(base).path
                            val content = file.readText()
                            relativePath to content
                        }.toList()
                    } else emptyList()
                }

                apiService?.commitChanges(
                    owner = owner,
                    repo = repoName,
                    branch = branch,
                    message = _uiState.value.commitMessage.ifBlank { "Update via Workspace" },
                    files = files
                )
                _uiState.update { it.copy(isLoading = false, commitResult = "Committed $branch (${files.size} files)!", commitMessage = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun pullRepo(repo: GitHubRepo, localPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pullResult = "Pulling ${repo.name}...", error = null) }
            try {
                val parts = repo.fullName.split("/")
                val targetDir = File(localPath)
                if (targetDir.exists()) targetDir.deleteRecursively()
                apiService?.downloadRepoToLocal(parts[0], parts[1], localPath)
                _uiState.update { it.copy(isLoading = false, pullResult = "Pull complete for ${repo.name}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message, pullResult = "") }
            }
        }
    }

    fun clearCloneProgress() {
        _uiState.update { it.copy(cloneProgress = "") }
    }

    fun clearPullResult() {
        _uiState.update { it.copy(pullResult = "") }
    }
}
