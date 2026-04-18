package com.gitsync.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
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

    // Re-check every time the screen becomes active (user may have just granted it)
    var isBatteryOptimized by remember {
        mutableStateOf(!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    // Refresh on recompose (e.g. returning from settings)
    LaunchedEffect(Unit) {
        isBatteryOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    val intervals = listOf(0 to "关闭", 15 to "15分钟", 30 to "30分钟", 60 to "1小时", 360 to "6小时")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium)

            // --- Battery optimization card ---
            BatteryOptimizationCard(
                isOptimized = isBatteryOptimized,
                onRequestExemption = {
                    context.startActivity(BatteryOptimizationHelper.requestIgnoreIntent(context))
                }
            )

            OutlinedTextField(
                value = patInput,
                onValueChange = { patInput = it },
                label = { Text("GitHub Personal Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPat) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPat = !showPat }) {
                        Text(if (showPat) "隐藏" else "显示")
                    }
                }
            )

            Button(
                onClick = {
                    viewModel.savePat(patInput)
                    scope.launch { snackbarHostState.showSnackbar("Token 已保存") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存 Token")
            }

            ExposedDropdownMenuBox(
                expanded = intervalExpanded,
                onExpandedChange = { intervalExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("默认同步间隔") },
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
                                viewModel.saveDefaultInterval(minutes)
                                intervalExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(
    isOptimized: Boolean,
    onRequestExemption: () -> Unit
) {
    val containerColor = if (isOptimized)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    val contentColor = if (isOptimized)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isOptimized) Icons.Outlined.BatteryAlert else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isOptimized) "后台同步可能受限" else "后台同步正常",
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Text(
                    if (isOptimized) "此设备对本 app 启用了电池优化，定时同步可能被延迟或跳过"
                    else "已豁免电池优化，定时同步不受系统限制",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
            if (isOptimized) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRequestExemption,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
                ) {
                    Text("去设置", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
