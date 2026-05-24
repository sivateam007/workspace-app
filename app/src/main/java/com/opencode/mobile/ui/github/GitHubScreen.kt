package com.opencode.mobile.ui.github

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.mobile.util.StorageUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(
    onRepoCloned: (String, String) -> Unit,
    onBack: () -> Unit,
    viewModel: GitHubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var ownerInput by remember { mutableStateOf("") }
    var repoInput by remember { mutableStateOf("") }
    var branchInput by remember { mutableStateOf("main") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.token.isBlank()) {
                TokenSetup(viewModel, onBack)
                return@Scaffold
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Clone") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Commit") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Pull") })
            }

            when (selectedTab) {
                0 -> CloneTab(uiState, viewModel, onRepoCloned, onBack)
                1 -> CommitTab(uiState, viewModel, ownerInput, repoInput, branchInput,
                    onOwnerChange = { ownerInput = it },
                    onRepoChange = { repoInput = it },
                    onBranchChange = { branchInput = it })
                2 -> PullTab(uiState, viewModel, ownerInput, repoInput, branchInput,
                    onOwnerChange = { ownerInput = it },
                    onRepoChange = { repoInput = it },
                    onBranchChange = { branchInput = it })
            }
        }
    }
}

@Composable
private fun TokenSetup(viewModel: GitHubViewModel, onBack: () -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("GitHub Token", style = MaterialTheme.typography.titleMedium)
        Text(
            "Generate a classic personal access token from GitHub Settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Personal Access Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.saveToken(token) },
            enabled = token.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect")
        }
    }
}

@Composable
private fun CloneTab(
    uiState: GitHubUiState,
    viewModel: GitHubViewModel,
    onRepoCloned: (String, String) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (uiState.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 3)
                }
            }
        }
        if (uiState.cloneProgress.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(uiState.cloneProgress)
                }
            }
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.repos) { repo ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val basePath = StorageUtil.projectsBasePath
                            viewModel.cloneRepo(repo, basePath)
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(repo.name, style = MaterialTheme.typography.titleSmall)
                                Text(repo.cloneUrl, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.CloudDownload, contentDescription = "Clone")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitTab(
    uiState: GitHubUiState,
    viewModel: GitHubViewModel,
    owner: String,
    repo: String,
    branch: String,
    onOwnerChange: (String) -> Unit,
    onRepoChange: (String) -> Unit,
    onBranchChange: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (uiState.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 3)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text("Commit Changes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = owner,
            onValueChange = onOwnerChange,
            label = { Text("Owner") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = repo,
            onValueChange = onRepoChange,
            label = { Text("Repo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = branch,
            onValueChange = onBranchChange,
            label = { Text("Branch") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.commitMessage,
            onValueChange = { viewModel.setCommitMessage(it) },
            label = { Text("Commit message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2, maxLines = 5
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val repoItem = uiState.repos.find { it.name == repo }
                if (repoItem != null) {
                    val localPath = "${StorageUtil.projectsBasePath}/$repo"
                    viewModel.commit(repoItem, owner, repo, branch, localPath)
                }
            },
            enabled = uiState.commitMessage.isNotBlank() && owner.isNotBlank() && repo.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Commit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Commit")
        }
        if (uiState.commitResult.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(uiState.commitResult, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PullTab(
    uiState: GitHubUiState,
    viewModel: GitHubViewModel,
    owner: String,
    repo: String,
    branch: String,
    onOwnerChange: (String) -> Unit,
    onRepoChange: (String) -> Unit,
    onBranchChange: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (uiState.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 3)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text("Pull from Remote", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = owner,
            onValueChange = onOwnerChange,
            label = { Text("Owner") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = repo,
            onValueChange = onRepoChange,
            label = { Text("Repo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val repoItem = uiState.repos.find { it.name == repo }
                if (repoItem != null) {
                    val localPath = "${StorageUtil.projectsBasePath}/$repo"
                    viewModel.pullRepo(repoItem, localPath)
                }
            },
            enabled = owner.isNotBlank() && repo.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Pull Latest")
        }
        if (uiState.pullResult.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(uiState.pullResult, color = MaterialTheme.colorScheme.primary)
        }
    }
}
