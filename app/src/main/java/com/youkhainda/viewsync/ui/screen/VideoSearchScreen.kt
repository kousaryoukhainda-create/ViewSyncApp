package com.youkhainda.viewsync.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.youkhainda.viewsync.data.model.YouTubeVideo
import com.youkhainda.viewsync.ui.viewmodel.SearchViewModel

@Composable
fun VideoSearchScreen(
    onSessionCreated: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val createdSessionId by viewModel.createdSessionId.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val selectedVideos = remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var sessionName by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Navigate when session is created
    LaunchedEffect(createdSessionId) {
        createdSessionId?.let { sessionId ->
            onSessionCreated(sessionId)
            viewModel.clearCreatedSessionId()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { viewModel.searchVideos(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )

        // Error Message
        if (!error.isNullOrEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Loading Indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        // Search Results
        if (searchResults.isNotEmpty()) {
            Text(
                text = "Results (${searchResults.size})",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(16.dp, 8.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(searchResults) { video ->
                    VideoSearchResultItem(
                        video = video,
                        isSelected = selectedVideos.value.any { it.videoId == video.videoId },
                        onSelect = {
                            selectedVideos.value = if (selectedVideos.value.any { it.videoId == video.videoId }) {
                                selectedVideos.value.filter { it.videoId != video.videoId }
                            } else {
                                selectedVideos.value + video
                            }
                        },
                    )
                }
            }
        }

        // Selected Videos Summary
        if (selectedVideos.value.isNotEmpty()) {
            SelectedVideosSummary(
                videos = selectedVideos.value,
                onRemove = { videoId ->
                    selectedVideos.value = selectedVideos.value.filter { it.videoId != videoId }
                },
                onCreateSession = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Create Session Dialog
    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createSyncSession(name, selectedVideos.value)
                showCreateDialog = false
            },
            sessionName = sessionName,
            onNameChange = { sessionName = it },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search YouTube videos...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch(query) },
        ),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun VideoSearchResultItem(
    video: YouTubeVideo,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Thumbnail
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )

            // Video Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = video.channelTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "Duration: ${formatDuration(video.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                modifier = Modifier.align(Alignment.Top),
            )
        }
    }
}

@Composable
private fun SelectedVideosSummary(
    videos: List<YouTubeVideo>,
    onRemove: (String) -> Unit,
    onCreateSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Selected Videos (${videos.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                videos.forEach { video ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )

                        IconButton(
                            onClick = { onRemove(video.videoId) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onCreateSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Sync Session")
            }
        }
    }
}

@Composable
private fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    sessionName: String,
    onNameChange: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Sync Session") },
        text = {
            OutlinedTextField(
                value = sessionName,
                onValueChange = onNameChange,
                label = { Text("Session name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., UHC Season 25") },
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(sessionName) },
                enabled = sessionName.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "$hours:${String.format("%02d", minutes)}"
        else -> "$minutes min"
    }
}
