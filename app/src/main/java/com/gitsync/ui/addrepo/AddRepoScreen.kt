package com.gitsync.ui.addrepo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoScreen(
    onBack: () -> Unit,
    viewModel: AddRepoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.done) {
        if (uiState.done) onBack()
    }

    // Launcher for Android 11+ MANAGE_EXTERNAL_STORAGE permission settings page
    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user returns from settings, re-check will happen on next save */ }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist URI permission so it survives app restarts
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            // Convert SAF content URI to file path:
            // content://com.android.externalstorage.documents/tree/primary:Documents/vault
            //   → /storage/emulated/0/Documents/vault
            val docPath = it.path ?: return@let
            val path = when {
                docPath.contains("/tree/primary:") ->
                    "/storage/emulated/0/" + docPath.substringAfter("/tree/primary:")
                docPath.contains("/tree/") -> {
                    // SD card: /tree/XXXX-XXXX:path → /storage/XXXX-XXXX/path
                    val volumeAndPath = docPath.substringAfter("/tree/")
                    val volume = volumeAndPath.substringBefore(":")
                    val subPath = volumeAndPath.substringAfter(":")
                    "/storage/$volume/$subPath"
                }
                else -> docPath
            }
            viewModel.onPathChange(path)
        }
    }

    val intervals = listOf(0 to "关闭", 15 to "15分钟", 30 to "30分钟", 60 to "1小时", 360 to "6小时")
    var intervalExpanded by remember { mutableStateOf(false) }
    val selectedLabel = intervals.firstOrNull { it.first == uiState.intervalMinutes }?.second ?: "关闭"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加仓库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.remoteUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text("GitHub 仓库 URL") },
                placeholder = { Text("https://github.com/user/vault.git") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.localPath,
                onValueChange = viewModel::onPathChange,
                label = { Text("本地文件夹") },
                placeholder = { Text("/storage/emulated/0/Documents/vault") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择文件夹")
                    }
                }
            )

            ExposedDropdownMenuBox(
                expanded = intervalExpanded,
                onExpandedChange = { intervalExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("同步间隔") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = intervalExpanded,
                    onDismissRequest = { intervalExpanded = false }
                ) {
                    intervals.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.onIntervalChange(minutes)
                                intervalExpanded = false
                            }
                        )
                    }
                }
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    // Android 11+: check MANAGE_EXTERNAL_STORAGE before cloning
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        !Environment.isExternalStorageManager()
                    ) {
                        manageStorageLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else {
                        viewModel.save()
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Clone 中...")
                } else {
                    Text("添加并 Clone")
                }
            }
        }
    }
}
