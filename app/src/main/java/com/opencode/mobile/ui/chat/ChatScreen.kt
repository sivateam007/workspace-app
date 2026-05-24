package com.opencode.mobile.ui.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentFileContent: String = "",
    currentFileName: String = "",
    projectPath: String = "",
    onApplyCode: (String, String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showModelPicker by remember { mutableStateOf(false) }
    val projectFileTree = remember(projectPath) { buildFileTreeString(projectPath) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.autoApply, uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            val last = uiState.messages.last()
            if (last.role == "assistant" && uiState.autoApply) {
                last.codeBlocks.forEach { block ->
                    onApplyCode(block.code, block.filePath)
                }
            }
        }
    }

    if (uiState.apiKey.isBlank()) {
        ApiKeySetupScreen(
            onSave = { viewModel.saveApiKey(it) },
            onDismiss = onDismiss
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            // Header with mode selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI - ${uiState.selectedModel}", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 1)

                IconButton(onClick = { viewModel.clearMessages() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                }
            }

            // Agent mode tabs
            TabRow(selectedTabIndex = uiState.agentMode.ordinal) {
                Tab(selected = uiState.agentMode == AgentMode.CHAT,
                    onClick = { viewModel.setAgentMode(AgentMode.CHAT) },
                    text = { Text("Chat", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) })
                Tab(selected = uiState.agentMode == AgentMode.PLAN,
                    onClick = { viewModel.setAgentMode(AgentMode.PLAN) },
                    text = { Text("Plan", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp)) })
                Tab(selected = uiState.agentMode == AgentMode.BUILD,
                    onClick = { viewModel.setAgentMode(AgentMode.BUILD) },
                    text = { Text("Build", style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }

            // Model picker row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ModelTraining, contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
                Text("Model:", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
                Text(uiState.selectedModel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                if (uiState.availableModels.isNotEmpty()) {
                    IconButton(onClick = { showModelPicker = true },
                        modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick model",
                            modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (uiState.autoApply) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Build mode: code blocks applied automatically",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }

            // Model picker dropdown
            DropdownMenu(
                expanded = showModelPicker,
                onDismissRequest = { showModelPicker = false }
            ) {
                uiState.availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model, fontSize = 13.sp) },
                        onClick = {
                            viewModel.setSelectedModel(model)
                            showModelPicker = false
                        },
                        leadingIcon = {
                            if (model == uiState.selectedModel) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }

            Divider()

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageBubble(
                        message = message,
                        onApplyCode = onApplyCode,
                        autoApply = uiState.autoApply
                    )
                }

                if (uiState.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Thinking...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (uiState.error != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp)) {
                                Icon(Icons.Default.Error, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(uiState.error!!, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Divider()

            // Input
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(
                        when (uiState.agentMode) {
                            AgentMode.CHAT -> "Ask AI about code..."
                            AgentMode.PLAN -> "Describe what to plan..."
                            AgentMode.BUILD -> "Describe what to build..."
                        }
                    )},
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        viewModel.sendMessage(inputText, currentFileContent, currentFileName, projectFileTree)
                        inputText = ""
                    },
                    enabled = inputText.isNotBlank() && !uiState.isLoading
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageUI,
    onApplyCode: (String, String?) -> Unit,
    autoApply: Boolean = false
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            )
        ) {
            Column(Modifier.padding(12.dp).widthIn(max = 280.dp)) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium
                )

                message.codeBlocks.forEach { block ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    block.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (block.filePath != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        block.filePath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                block.code.take(200),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                maxLines = 10
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { onApplyCode(block.code, block.filePath) },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = if (autoApply) ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                ) else ButtonDefaults.buttonColors()
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null,
                                    modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (autoApply) "Auto" else "Apply", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeySetupScreen(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("API Key", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Get your key from console.opencode.ai",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(key) },
                enabled = key.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Continue")
            }
        }
    }
}

// --- File tree helpers ---

private fun buildFileTreeString(projectPath: String): String {
    if (projectPath.isBlank()) return ""
    val root = File(projectPath)
    if (!root.exists()) return ""
    val sb = StringBuilder()
    sb.appendLine("Project structure (${root.name}):")
    buildFileTreeRecursive(root, sb, "", 0, maxDepth = 3)
    return sb.toString()
}

private fun buildFileTreeRecursive(dir: File, sb: StringBuilder, indent: String, depth: Int, maxDepth: Int) {
    if (depth > maxDepth) return
    dir.listFiles()?.sortedBy { !it.isDirectory }?.forEach { file ->
        val prefix = if (file.isDirectory) "+ " else "  "
        sb.appendLine("$indent$prefix${file.name}")
        if (file.isDirectory) {
            buildFileTreeRecursive(file, sb, "$indent  ", depth + 1, maxDepth)
        }
    }
}

private fun parseReadDirectives(content: String): List<String> {
    val regex = Regex("@read\\s+(\\S+)")
    return regex.findAll(content).map { it.groupValues[1] }.toList()
}
