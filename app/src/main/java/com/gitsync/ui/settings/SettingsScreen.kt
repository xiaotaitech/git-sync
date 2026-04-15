package com.gitsync.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pat by viewModel.pat.collectAsState()
    val defaultInterval by viewModel.defaultInterval.collectAsState()
    var patInput by remember(pat) { mutableStateOf(pat) }
    var showPat by remember { mutableStateOf(false) }
    var intervalExpanded by remember { mutableStateOf(false) }

    val intervals = listOf(0 to "关闭", 15 to "15分钟", 30 to "30分钟", 60 to "1小时", 360 to "6小时")
    val selectedLabel = intervals.firstOrNull { it.first == defaultInterval }?.second ?: "关闭"

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

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
            onClick = { viewModel.savePat(patInput) },
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
