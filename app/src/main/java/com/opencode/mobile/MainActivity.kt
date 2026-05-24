package com.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencode.mobile.ui.chat.ChatScreen
import com.opencode.mobile.ui.editor.EditorScreen
import com.opencode.mobile.ui.github.GitHubScreen
import com.opencode.mobile.ui.home.HomeScreen
import com.opencode.mobile.ui.settings.SettingsScreen
import com.opencode.mobile.ui.theme.OpenCodeMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = getSharedPreferences("opencode_prefs", 0)
            var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }

            OpenCodeMobileTheme(darkTheme = darkMode) {
                OpenCodeMobileApp(
                    onThemeChanged = { darkMode = it }
                )
            }
        }
    }
}

@Composable
fun OpenCodeMobileApp(
    onThemeChanged: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    var currentFileContent by remember { mutableStateOf("") }
    var currentFileName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("home") {
                HomeScreen(
                    onProjectClick = { projectId, path ->
                        navController.navigate("editor/$projectId/${java.net.URLEncoder.encode(path, "UTF-8")}")
                    },
                    onGitHubClick = {
                        navController.navigate("github")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }

            composable(
                route = "editor/{projectId}/{projectPath}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.LongType },
                    navArgument("projectPath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
                val projectPath = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("projectPath") ?: "", "UTF-8"
                )

                val showChat = remember { mutableStateOf(false) }
                val editorViewModelRef = remember { mutableStateOf<com.opencode.mobile.ui.editor.EditorViewModel?>(null) }

                if (showChat.value) {
                    ChatScreen(
                        currentFileContent = currentFileContent,
                        currentFileName = currentFileName,
                        projectPath = projectPath,
                        onApplyCode = { code, filePath ->
                            val vm = editorViewModelRef.value
                            if (vm != null) {
                                val resolvedPath = if (filePath != null) {
                                    var cleaned = filePath.trim().trimStart('.', '/', '\\')
                                    val f = java.io.File(cleaned)
                                    if (f.isAbsolute) cleaned
                                    else java.io.File(projectPath, cleaned).absolutePath
                                } else {
                                    vm.getActiveFile()?.path
                                }
                                if (resolvedPath != null) {
                                    vm.saveFileAtPath(resolvedPath, code)
                                }
                            }
                        },
                        onDismiss = { showChat.value = false }
                    )
                }

                EditorScreen(
                    projectId = projectId,
                    projectPath = projectPath,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { showChat.value = true },
                    onViewModelCreated = { editorViewModelRef.value = it }
                )

                val vm = editorViewModelRef.value
                LaunchedEffect(vm) {
                    if (vm != null) {
                        vm.uiState.collect { state ->
                            if (state.activeFileIndex in state.openFiles.indices) {
                                val file = state.openFiles[state.activeFileIndex]
                                currentFileContent = file.content
                                currentFileName = file.name
                            }
                        }
                    }
                }
            }

            composable("github") {
                GitHubScreen(
                    onRepoCloned = { name, path ->
                        navController.popBackStack()
                        navController.navigate("editor/0/${java.net.URLEncoder.encode(path, "UTF-8")}")
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onThemeChanged = onThemeChanged
                )
            }
        }
    }
}
