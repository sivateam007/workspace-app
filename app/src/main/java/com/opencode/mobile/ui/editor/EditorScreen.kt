package com.opencode.mobile.ui.editor

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.mobile.data.storage.FileItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    projectId: Long,
    projectPath: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onViewModelCreated: (EditorViewModel) -> Unit = {},
    viewModel: EditorViewModel = viewModel()
) {
    LaunchedEffect(projectId, projectPath) {
        viewModel.initialize(projectId, projectPath)
        onViewModelCreated(viewModel)
    }

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFileTree by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { event ->
            snackbarHostState.showSnackbar(event.message)
        }
    }

    if (showNewFileDialog) {
        NewItemDialog(
            title = "New File",
            onConfirm = { name ->
                viewModel.createFile(null, name)
                showNewFileDialog = false
            },
            onDismiss = { showNewFileDialog = false }
        )
    }
    if (showNewFolderDialog) {
        NewItemDialog(
            title = "New Folder",
            onConfirm = { name ->
                viewModel.createDirectory(null, name)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }
    showRenameDialog?.let { item ->
        NewItemDialog(
            title = "Rename",
            initial = item.name,
            onConfirm = { name ->
                viewModel.renameFile(item, name)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }
    showDeleteConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(item)
                    showDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.projectName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showFileTree = !showFileTree }) {
                        Icon(
                            if (showFileTree) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = "Toggle file tree"
                        )
                    }
                    IconButton(onClick = onOpenChat) {
                        Icon(Icons.Default.QuestionAnswer, contentDescription = "AI Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.openFiles.isNotEmpty()) {
                TabRow(
                    selectedTabIndex = uiState.activeFileIndex.coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    uiState.openFiles.forEachIndexed { index, file ->
                        Tab(
                            selected = index == uiState.activeFileIndex,
                            onClick = { viewModel.setActiveFile(index) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        file.name,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (file.isDirty) Text(" *", color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(16.dp).clickable { viewModel.closeFile(index) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (showSearch) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        viewModel.searchInProject(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    placeholder = { Text("Search in project...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = ""; viewModel.searchInProject("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                if (uiState.searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(uiState.searchResults) { result ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.openFile(FileItem(
                                        name = result.fileName,
                                        path = result.filePath,
                                        isDirectory = false
                                    ))
                                },
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(result.fileName, style = MaterialTheme.typography.bodySmall)
                                    Row {
                                        Text("${result.lineNumber}: ", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary)
                                        Text(result.lineContent, style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.weight(1f)) {
                if (showFileTree) {
                    Surface(
                        modifier = Modifier.width(250.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        FileTreeView(
                            files = uiState.fileTree,
                            onFileClick = { viewModel.openFile(it) },
                            onNavigateUp = { viewModel.navigateUp() },
                            onNewFile = { showNewFileDialog = true },
                            onNewFolder = { showNewFolderDialog = true },
                            onRename = { showRenameDialog = it },
                            onDelete = { showDeleteConfirm = it }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val activeFile = viewModel.getActiveFile()
                    if (activeFile != null) {
                        CodeEditorView(
                            content = activeFile.content,
                            fileName = activeFile.name,
                            onContentChanged = { newContent ->
                                val idx = uiState.activeFileIndex
                                if (idx >= 0) viewModel.updateFileContent(idx, newContent)
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select a file to edit", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeView(
    files: List<FileItem>,
    onFileClick: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onNewFile, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "New File", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onNewFolder, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(18.dp))
                }
            }
        }
        items(files) { file ->
            var showMenu by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onFileClick(file) },
                        onLongClick = { showMenu = true }
                    )
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    offset = DpOffset(0.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; onRename(file) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete(file) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NewItemDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CodeEditorView(
    content: String,
    fileName: String,
    onContentChanged: (String) -> Unit
) {
    val extension = fileName.substringAfterLast('.', "")
    val mode = when (extension) {
        "kt", "java" -> "text/x-java"
        "py" -> "text/x-python"
        "js", "ts" -> "text/javascript"
        "html" -> "text/html"
        "css" -> "text/css"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "md" -> "text/x-markdown"
        "sql" -> "text/x-sql"
        "yaml", "yml" -> "text/x-yaml"
        else -> "text/plain"
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        pageLoaded = true
                    }
                }
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onContentChanged(newContent: String) {
                        onContentChanged(newContent)
                    }
                }, "Android")
                loadUrl("file:///android_asset/codemirror/editor.html")
                webView = this
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    LaunchedEffect(pageLoaded, content) {
        if (pageLoaded && webView != null) {
            val escaped = content
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            webView?.evaluateJavascript("setContent('$escaped')", null)
        }
    }

    LaunchedEffect(pageLoaded, mode) {
        if (pageLoaded && webView != null) {
            webView?.evaluateJavascript("setMode('$mode')", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }
}
