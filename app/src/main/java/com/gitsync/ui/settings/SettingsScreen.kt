package com.gitsync.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.util.BatteryOptimizationHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val pat by viewModel.pat.collectAsState()
    val defaultInterval by viewModel.defaultInterval.collectAsState()
    var patInput by remember(pat) { mutableStateOf(pat) }
    var showPat by remember { mutableStateOf(false) }
    var intervalExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isBatteryOptimized by remember {
        mutableStateOf(!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    LaunchedEffect(Unit) {
        isBatteryOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    val intervals = listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 60 to "1 小时", 360 to "6 小时")
    val selectedLabel = intervals.firstOrNull { it.first == defaultInterval }?.second ?: "关闭"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- System section ---
            SettingsSection(title = "系统") {
                BatteryOptimizationCard(
                    isOptimized = isBatteryOptimized,
                    onRequestExemption = {
                        context.startActivity(BatteryOptimizationHelper.requestIgnoreIntent(context))
                    }
                )
            }

            // --- Authentication section ---
            SettingsSection(title = "认证") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        label = { Text("GitHub Personal Access Token") },
                        placeholder = { Text("ghp_xxxxxxxxxxxx") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = if (showPat) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPat = !showPat }) {
                                Icon(
                                    if (showPat) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPat) "隐藏" else "显示",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        supportingText = {
                            Text("需要 repo 权限", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    )
                    Button(
                        onClick = {
                            viewModel.savePat(patInput)
                            scope.launch { snackbarHostState.showSnackbar("Token 已保存") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("保存 Token")
                    }
                }
            }

            // --- Sync section ---
            SettingsSection(title = "同步") {
                ExposedDropdownMenuBox(
                    expanded = intervalExpanded,
                    onExpandedChange = { intervalExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("自动同步间隔") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                        shape = RoundedCornerShape(16.dp),
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
                                    viewModel.saveDefaultInterval(minutes)
                                    intervalExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(
    isOptimized: Boolean,
    onRequestExemption: () -> Unit
) {
    val iconColor = if (isOptimized) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.tertiary

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isOptimized) Icons.Outlined.BatteryAlert else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isOptimized) "后台同步可能受限" else "后台同步正常",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (isOptimized) "已启用电池优化，定时同步可能被延迟"
                else "已豁免电池优化",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (isOptimized) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRequestExemption,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("去设置", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
