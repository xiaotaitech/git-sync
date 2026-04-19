package com.gitsync.ui.repolist

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitsync.data.RepoEntity
import com.gitsync.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RepoCard(
    repo: RepoEntity,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (repo.syncStatus) {
        "idle" -> Success
        "syncing" -> Primary
        "conflict" -> Warning
        "error" -> ErrorRed
        else -> Color.Gray
    }
    val statusLabel = when (repo.syncStatus) {
        "idle" -> "已同步"
        "syncing" -> "同步中"
        "conflict" -> "有冲突"
        "error" -> "失败"
        else -> "未知"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rotation"
    )
    val syncIconModifier = if (isSyncing) Modifier.rotate(rotation) else Modifier

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除仓库") },
            text = { Text("确认删除「${repo.name}」？本地文件夹不会被删除。") },
            // 安全操作（取消）在右，危险操作（删除）在左
            confirmButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDeleteClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(repo.localPath, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                    if (repo.lastSyncTime > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatTime(repo.lastSyncTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            IconButton(
                onClick = onSyncClick,
                enabled = !isSyncing,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = "同步",
                    tint = if (isSyncing) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = syncIconModifier
                )
            }
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}


