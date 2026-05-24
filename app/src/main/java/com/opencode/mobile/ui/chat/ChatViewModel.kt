package com.opencode.mobile.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AgentMode { CHAT, PLAN, BUILD }

data class ChatMessageUI(
    val role: String,
    val content: String,
    val codeBlocks: List<CodeBlock> = emptyList()
)

data class CodeBlock(
    val language: String,
    val code: String,
    val filePath: String? = null
)

data class ChatUiState(
    val messages: List<ChatMessageUI> = emptyList(),
    val isLoading: Boolean = false,
    val apiKey: String = "",
    val error: String? = null,
    val agentMode: AgentMode = AgentMode.CHAT,
    val selectedModel: String = "big-pickle",
    val availableModels: List<String> = emptyList(),
    val autoApply: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("opencode_prefs", 0)
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        val savedKey = prefs.getString("api_key", "") ?: ""
        val savedModel = prefs.getString("selected_model", "big-pickle") ?: "big-pickle"
        _uiState.update {
            it.copy(apiKey = savedKey, selectedModel = savedModel)
        }
        if (savedKey.isNotBlank()) fetchModels()
    }

    fun fetchModels() {
        viewModelScope.launch {
            try {
                val apiKey = prefs.getString("api_key", "") ?: ""
                if (apiKey.isBlank()) return@launch
                val service = OpenCodeApiService.create()
                val response = withContext(Dispatchers.IO) {
                    service.listModels("Bearer $apiKey")
                }
                if (response.isSuccessful) {
                    val models = response.body()?.data?.map { it.id } ?: emptyList()
                    _uiState.update { it.copy(availableModels = models) }
                }
            } catch (_: Exception) {
                // silently ignore — models list is non-critical
            }
        }
    }

    fun setAgentMode(mode: AgentMode) {
        _uiState.update { it.copy(agentMode = mode, autoApply = mode == AgentMode.BUILD) }
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString("selected_model", model).apply()
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun sendMessage(text: String, currentFileContent: String = "", currentFileName: String = "", projectFileTree: String = "") {
        val freshKey = prefs.getString("api_key", "") ?: ""
        if (_uiState.value.apiKey != freshKey) {
            _uiState.update { it.copy(apiKey = freshKey) }
        }
        if (text.isBlank() || _uiState.value.apiKey.isBlank()) return

        val userMessage = ChatMessageUI(role = "user", content = text)
        _uiState.update {
            it.copy(messages = it.messages + userMessage, isLoading = true, error = null)
        }

        viewModelScope.launch {
            try {
                val agentMode = _uiState.value.agentMode
                val systemPrompt = buildSystemPrompt(agentMode)

                val contextInfo = buildString {
                    if (projectFileTree.isNotBlank()) {
                        appendLine(projectFileTree)
                        appendLine()
                    }
                    if (currentFileContent.isNotBlank()) {
                        appendLine("Current file: $currentFileName")
                        appendLine("```")
                        appendLine(currentFileContent)
                        appendLine("```")
                    }
                    appendLine()
                    appendLine(text)
                }

                val apiMessages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = contextInfo)
                )

                val model = _uiState.value.selectedModel
                val apiKey = _uiState.value.apiKey
                val service = OpenCodeApiService.create()
                val response = service.chatCompletion(
                    authHeader = "Bearer $apiKey",
                    request = ChatRequest(
                        model = model,
                        messages = apiMessages
                    )
                )

                val aiContent = if (response.isSuccessful) {
                    response.body()?.choices?.firstOrNull()?.message?.content
                        ?: "Sorry, I couldn't process that."
                } else {
                    "Error ${response.code()}: ${response.errorBody()?.string() ?: "Unknown error"}"
                }

                val codeBlocks = extractCodeBlocks(aiContent)
                val aiMessage = ChatMessageUI(
                    role = "assistant",
                    content = aiContent,
                    codeBlocks = codeBlocks
                )

                _uiState.update {
                    it.copy(messages = it.messages + aiMessage, isLoading = false)
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun buildSystemPrompt(mode: AgentMode): String = when (mode) {
        AgentMode.CHAT -> "You are a coding assistant. Help the user with code editing, fixing bugs, and answering questions. When providing code, format it with markdown code blocks."

        AgentMode.PLAN -> "You are a planning agent. Analyze the code and context provided by the user. Think step by step and produce a clear, detailed plan for implementing the requested changes. Evaluate trade-offs, suggest architecture improvements, and list specific files that need modification. Do NOT write any code — only output the plan."

        AgentMode.BUILD -> "You are a build agent. Implement the requested changes by writing actual code. The project file tree is provided in the user message so you know what files exist. Output complete file contents in markdown code blocks. Use the format ```lang:relative/path/to/file\ncode\n``` to specify the target file path relative to the project root. Each code block will be automatically applied to the correct location. Write production-quality code. If you need to read a file, use @read path/to/file in your response."
    }

    private fun extractCodeBlocks(content: String): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()
        val regex = Regex("```([^\\n]*)\\n([\\s\\S]*?)```")
        regex.findAll(content).forEach { match ->
            val langLine = match.groupValues[1]
            val code = match.groupValues[2]
            val (language, filePath) = if (langLine.contains(":")) {
                val parts = langLine.split(":", limit = 2)
                parts[0] to (parts.getOrNull(1)?.trim())
            } else {
                langLine to null
            }
            blocks.add(CodeBlock(language = language.ifBlank { "text" }, code = code, filePath = filePath))
        }
        return blocks
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
        _uiState.update { it.copy(apiKey = key) }
        fetchModels()
    }

    fun clearMessages() {
        _uiState.update { it.copy(messages = emptyList(), error = null) }
    }
}
