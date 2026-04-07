package com.youkhainda.viewsync.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.youkhainda.viewsync.util.DebugLogger

/**
 * Debug Overlay - Displays real-time debug logs in-app
 * Can be toggled on/off and shows the latest log entries
 */
@Composable
fun DebugOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logEntries by DebugLogger.logBuffer.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Header
                    DebugHeader(
                        logCount = logEntries.size,
                        onClear = { DebugLogger.clear() },
                        onExport = {
                            val logs = DebugLogger.exportLogs()
                            shareLogs(context, logs)
                        },
                        onCopy = {
                            val logs = DebugLogger.exportLogs()
                            copyToClipboard(context, logs)
                        },
                        onClose = onDismiss,
                    )
                    
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    
                    // Log entries
                    if (logEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No debug logs yet",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(logEntries) { entry ->
                                LogEntryItem(entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugHeader(
    logCount: Int,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = Color(0xFF00FF00),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Debug Logs ($logCount)",
                color = Color(0xFF00FF00),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy logs",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export logs",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear logs",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: DebugLogger.LogEntry) {
    val levelColor = when (entry.level) {
        DebugLogger.LogLevel.VERBOSE -> Color.Gray
        DebugLogger.LogLevel.DEBUG -> Color(0xFF60A5FA) // Blue
        DebugLogger.LogLevel.INFO -> Color(0xFF34D399) // Green
        DebugLogger.LogLevel.WARN -> Color(0xFFFBBF24) // Yellow
        DebugLogger.LogLevel.ERROR -> Color(0xFFF87171) // Red
    }
    
    val levelIcon = when (entry.level) {
        DebugLogger.LogLevel.VERBOSE -> "V"
        DebugLogger.LogLevel.DEBUG -> "D"
        DebugLogger.LogLevel.INFO -> "I"
        DebugLogger.LogLevel.WARN -> "W"
        DebugLogger.LogLevel.ERROR -> "E"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF2D2D2D),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Level indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(levelColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = levelIcon,
                color = levelColor,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        
        // Log content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.timestamp,
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "[${entry.tag}]",
                    color = Color(0xFFA78BFA), // Purple
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = entry.message,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Floating debug button that can be placed anywhere to toggle debug overlay
 */
@Composable
fun DebugToggleButton(
    isVisible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isVisible) 0f else 180f,
        label = "debug_icon_rotation",
    )
    
    FloatingActionButton(
        onClick = onToggle,
        modifier = modifier.size(48.dp),
        containerColor = if (isVisible) Color(0xFF00FF00) else Color.Gray,
    ) {
        Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = if (isVisible) "Hide debug overlay" else "Show debug overlay",
            tint = Color.Black,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation),
        )
    }
}

private fun shareLogs(context: Context, logs: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "ViewSyncApp Debug Logs")
        putExtra(Intent.EXTRA_TEXT, logs)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Debug Logs"))
}

private fun copyToClipboard(context: Context, logs: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("ViewSyncApp Debug Logs", logs)
    clipboard.setPrimaryClip(clip)
}
