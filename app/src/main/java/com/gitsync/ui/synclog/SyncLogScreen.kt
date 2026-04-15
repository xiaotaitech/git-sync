package com.gitsync.ui.synclog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.data.SyncLogEntity
import com.gitsync.ui.theme.ErrorRed
import com.gitsync.ui.theme.Success
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncLogScreen(
    contentPadding: PaddingValues,
    viewModel: SyncLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Text(
            "同步日志",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无同步记录", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(logs, key = { it.id }) { log ->
                    SyncLogItem(log)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncLogItem(log: SyncLogEntity) {
    val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(log.repoName, style = MaterialTheme.typography.titleMedium)
            Text(log.message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(sdf.format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = (if (log.success) Success else ErrorRed).copy(alpha = 0.15f)
            ) {
                Text(
                    if (log.success) "成功" else "失败",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.success) Success else ErrorRed
                )
            }
        }
    }
}
