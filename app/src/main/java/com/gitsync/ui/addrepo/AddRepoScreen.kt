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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
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

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val docPath = it.path ?: return@let
            val path = when {
                docPath.contains("/tree/primary:") ->
                    "/storage/emulated/0/" + docPath.substringAfter("/tree/primary:")
                docPath.contains("/tree/") -> {
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

    val intervals = listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 60 to "1 小时", 360 to "6 小时")
    var intervalExpanded by remember { mutableStateOf(false) }
    val selectedLabel = intervals.firstOrNull { it.first == uiState.intervalMinutes }?.second ?: "关闭"

    // per-field touched state for inline validation
    var urlTouched by remember { mutableStateOf(false) }
    var pathTouched by remember { mutableStateOf(false) }

    val urlError = if (urlTouched && uiState.remoteUrl.isNotBlank() &&
        !uiState.remoteUrl.startsWith("https://github.com/"))
        "请输入有效的 GitHub 仓库 URL" else null
    val pathError = if (pathTouched && uiState.localPath.isBlank()) "请选择本地文件夹" else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加仓库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.remoteUrl,
                onValueChange = {
                    viewModel.onUrlChange(it)
                    if (it.isNotBlank()) urlTouched = true
                },
                label = { Text("GitHub 仓库 URL") },
                placeholder = { Text("https://github.com/user/repo") },
                leadingIcon = {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                isError = urlError != null,
                supportingText = {
                    if (urlError != null)
                        Text(urlError, color = MaterialTheme.colorScheme.error)
                    else
                        Text("支持 HTTPS URL", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            )

            OutlinedTextField(
                value = uiState.localPath,
                onValueChange = {
                    viewModel.onPathChange(it)
                    if (it.isBlank()) pathTouched = true
                },
                label = { Text("本地文件夹") },
                placeholder = { Text("/storage/emulated/0/Documents/vault") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                isError = pathError != null,
                supportingText = {
                    if (pathError != null)
                        Text(pathError, color = MaterialTheme.colorScheme.error)
                    else
                        Text("点击右侧图标选择文件夹", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                },
                trailingIcon = {
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "选择文件夹",
                            modifier = Modifier.size(20.dp))
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
                    leadingIcon = {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    supportingText = {
                        Text("设为\"关闭\"则仅手动同步",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    }
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    urlTouched = true
                    pathTouched = true
                    if (urlError != null || pathError != null) return@Button
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Clone 中...")
                } else {
                    Text("添加并 Clone")
                }
            }
        }
    }
}
