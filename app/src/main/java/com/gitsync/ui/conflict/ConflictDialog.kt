package com.gitsync.ui.conflict

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitsync.ui.theme.ErrorRed
import com.gitsync.ui.theme.Warning

@Composable
fun ConflictDialog(
    modifiedFiles: List<String>,
    onForce: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("本地有未提交的修改") },
        text = {
            Column {
                Text(
                    "以下文件有本地修改。如果继续，将丢失这些修改：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(modifiedFiles) { file ->
                        Text(
                            "• $file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Warning,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onForce,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("覆盖本地")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onSkip) {
                Text("保留本地，跳过")
            }
        }
    )
}
