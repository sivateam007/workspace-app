package com.opencode.mobile.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.db.AppDatabase
import com.opencode.mobile.data.db.RecentFileEntity
import com.opencode.mobile.data.storage.FileItem
import com.opencode.mobile.data.storage.FileManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class SnackbarEvent(val message: String)

data class OpenFile(
    val path: String,
    val name: String,
    val content: String,
    val isDirty: Boolean = false
)

data class EditorUiState(
    val projectName: String = "",
    val fileTree: List<FileItem> = emptyList(),
    val openFiles: List<OpenFile> = emptyList(),
    val activeFileIndex: Int = -1,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList()
)

data class SearchResult(
    val filePath: String,
    val fileName: String,
    val lineNumber: Int,
    val lineContent: String
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private lateinit var fileManager: FileManager
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val _snackbar = MutableSharedFlow<SnackbarEvent>()
    val snackbar: SharedFlow<SnackbarEvent> = _snackbar.asSharedFlow()

    fun initialize(projectId: Long, projectPath: String) {
        fileManager = FileManager(projectPath)
        val projectName = File(projectPath).name
        _uiState.update { it.copy(projectName = projectName) }
        loadFileTree()
    }

    private fun loadFileTree() {
        val files = fileManager.listFiles()
        _uiState.update { it.copy(fileTree = files) }
    }

    fun navigateToDir(path: String) {
        val files = fileManager.listFiles(path)
        _uiState.update { it.copy(fileTree = files) }
    }

    fun navigateUp() {
        val currentPath = getCurrentPath()
        val parent = fileManager.getParentPath(currentPath)
        if (parent != fileManager.getParentPath("")) {
            loadFileTree()
        }
    }

    private fun getCurrentPath(): String {
        if (_uiState.value.openFiles.isNotEmpty() && _uiState.value.activeFileIndex >= 0) {
            return fileManager.getParentPath(_uiState.value.openFiles[_uiState.value.activeFileIndex].path)
        }
        return ""
    }

    fun openFile(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            navigateToDir(fileItem.path)
            return
        }
        viewModelScope.launch {
            val existingIndex = _uiState.value.openFiles.indexOfFirst { it.path == fileItem.path }
            if (existingIndex >= 0) {
                _uiState.update { it.copy(activeFileIndex = existingIndex) }
            } else {
                val content = fileManager.readFile(fileItem.path)
                _uiState.update {
                    it.copy(
                        openFiles = it.openFiles + OpenFile(
                            path = fileItem.path,
                            name = fileItem.name,
                            content = content
                        ),
                        activeFileIndex = it.openFiles.size
                    )
                }
            }
        }
    }

    fun closeFile(index: Int) {
        val state = _uiState.value
        val newFiles = state.openFiles.toMutableList()
        newFiles.removeAt(index)
        val newActive = when {
            newFiles.isEmpty() -> -1
            index >= newFiles.size -> newFiles.size - 1
            else -> index
        }
        _uiState.update { it.copy(openFiles = newFiles, activeFileIndex = newActive) }
    }

    fun updateFileContent(index: Int, newContent: String) {
        _uiState.update { state ->
            val files = state.openFiles.toMutableList()
            if (index in files.indices) {
                files[index] = files[index].copy(content = newContent, isDirty = true)
            }
            state.copy(openFiles = files)
        }
    }

    fun saveFile(index: Int) {
        val file = _uiState.value.openFiles.getOrNull(index) ?: return
        viewModelScope.launch {
            fileManager.writeFile(file.path, file.content)
            _uiState.update { state ->
                val files = state.openFiles.toMutableList()
                files[index] = files[index].copy(isDirty = false)
                state.copy(openFiles = files)
            }
        }
    }

    fun saveFileAtPath(path: String, content: String) {
        viewModelScope.launch {
            try {
                fileManager.writeFile(path, content)
                val index = _uiState.value.openFiles.indexOfFirst { it.path == path }
                if (index >= 0) {
                    _uiState.update { state ->
                        val files = state.openFiles.toMutableList()
                        files[index] = files[index].copy(content = content, isDirty = false)
                        state.copy(openFiles = files)
                    }
                } else {
                    loadFileTree()
                }
                _snackbar.emit(SnackbarEvent("Saved ${File(path).name}"))
            } catch (e: Exception) {
                _snackbar.emit(SnackbarEvent("Failed to save: ${e.message}"))
            }
        }
    }

    fun searchInProject(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        val results = mutableListOf<SearchResult>()
        searchRecursive(File(fileManager.getParentPath("")), query, results)
        _uiState.update { it.copy(searchResults = results) }
    }

    private fun searchRecursive(dir: File, query: String, results: MutableList<SearchResult>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchRecursive(file, query, results)
            } else if (file.isFile) {
                try {
                    file.readLines().forEachIndexed { index, line ->
                        if (line.contains(query, ignoreCase = true) && results.size < 100) {
                            results.add(
                                SearchResult(
                                    filePath = file.absolutePath,
                                    fileName = file.name,
                                    lineNumber = index + 1,
                                    lineContent = line.trim()
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun setActiveFile(index: Int) {
        _uiState.update { it.copy(activeFileIndex = index) }
    }

    fun getActiveFile(): OpenFile? {
        val state = _uiState.value
        return if (state.activeFileIndex in state.openFiles.indices)
            state.openFiles[state.activeFileIndex] else null
    }

    fun createFile(parentPath: String?, name: String) {
        viewModelScope.launch {
            val path = if (parentPath != null) "$parentPath/$name" else "${fileManager.getParentPath("")}/$name"
            val success = fileManager.createFile(path)
            if (success) {
                loadFileTree()
                _snackbar.emit(SnackbarEvent("Created $name"))
            }
        }
    }

    fun createDirectory(parentPath: String?, name: String) {
        viewModelScope.launch {
            val path = if (parentPath != null) "$parentPath/$name" else "${fileManager.getParentPath("")}/$name"
            val success = fileManager.createDirectory(path)
            if (success) {
                loadFileTree()
                _snackbar.emit(SnackbarEvent("Created folder $name"))
            }
        }
    }

    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch {
            val success = if (fileItem.isDirectory) File(fileItem.path).deleteRecursively() else fileManager.deleteFile(fileItem.path)
            if (success) {
                val idx = _uiState.value.openFiles.indexOfFirst { it.path == fileItem.path }
                if (idx >= 0) closeFile(idx)
                loadFileTree()
                _snackbar.emit(SnackbarEvent("Deleted ${fileItem.name}"))
            }
        }
    }

    fun renameFile(fileItem: FileItem, newName: String) {
        viewModelScope.launch {
            val parent = fileManager.getParentPath(fileItem.path)
            val newPath = "$parent/$newName"
            val success = File(fileItem.path).renameTo(File(newPath))
            if (success) {
                val idx = _uiState.value.openFiles.indexOfFirst { it.path == fileItem.path }
                if (idx >= 0) {
                    val files = _uiState.value.openFiles.toMutableList()
                    files[idx] = files[idx].copy(path = newPath, name = newName)
                    _uiState.update { it.copy(openFiles = files) }
                }
                loadFileTree()
                _snackbar.emit(SnackbarEvent("Renamed to $newName"))
            }
        }
    }

    fun getCurrentDirectoryPath(): String {
        return fileManager.getParentPath("")
    }
}
