package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.ui.ScreenRoute
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface

@Composable
fun ContentSelectionScreen(
    stagedItems: List<ContentItem>,
    onImportUris: (List<Uri>) -> Unit,
    onAddText: (String) -> Unit,
    onAddUrl: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearAll: () -> Unit,
    onNavigate: (ScreenRoute) -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var previewedItem by remember { mutableStateOf<ContentItem?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onImportUris(uris)
        }
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onImportUris(uris)
        }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("content_selection_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Import Content",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { documentPickerLauncher.launch(arrayOf("*/*")) }
                        .testTag("saf_import_button"),
                    cornerRadius = 16.dp,
                    borderAccent = palette.accentPrimary.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .glassSurface(
                                    cornerRadius = 20.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentPrimary.copy(alpha = 0.55f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "SAF Files",
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SAF Files / PDF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Open Document",
                            fontSize = 10.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                        .testTag("photo_picker_button"),
                    cornerRadius = 16.dp,
                    borderAccent = palette.accentSecondary.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .glassSurface(
                                    cornerRadius = 20.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentSecondary.copy(alpha = 0.55f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Photos & Videos",
                                tint = palette.accentSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Media Picker",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Photos & Videos",
                            fontSize = 10.sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTextInputDialog = true }
                        .testTag("text_note_button"),
                    cornerRadius = 14.dp,
                    fillColor = palette.glassFillElevated,
                    borderAccent = palette.accentPrimary.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = "Text Note",
                            tint = palette.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add Text Note",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                    }
                }

                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showUrlInputDialog = true }
                        .testTag("url_link_button"),
                    cornerRadius = 14.dp,
                    fillColor = palette.glassFillElevated,
                    borderAccent = palette.accentSecondary.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "URL Link",
                            tint = palette.accentSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Share Web Link",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Staged Content (${stagedItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )

                if (stagedItems.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("clear_staged_button")
                    ) {
                        Text(
                            text = "Clear All",
                            color = palette.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (stagedItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(cornerRadius = 18.dp)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .glassSurface(
                                    cornerRadius = 28.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.accentPrimary.copy(alpha = 0.45f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No files selected yet",
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import files above or share from any app via Android Sharesheet",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(stagedItems, key = { it.id }) { item ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { previewedItem = item }
                        .testTag("content_item_${item.id}"),
                    cornerRadius = 16.dp,
                    borderAccent = palette.accentPrimary.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .glassSurface(cornerRadius = 12.dp, fillColor = palette.glassFillInset),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.thumbnailUri != null) {
                                AsyncImage(
                                    model = item.thumbnailUri,
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = when (item.type) {
                                        ContentType.IMAGE -> Icons.Default.Image
                                        ContentType.VIDEO -> Icons.Default.VideoFile
                                        ContentType.DOCUMENT_PDF -> Icons.Default.PictureAsPdf
                                        ContentType.TEXT -> Icons.Default.Description
                                        ContentType.URL -> Icons.Default.Link
                                        ContentType.GENERIC_FILE -> Icons.Default.Description
                                    },
                                    contentDescription = item.type.name,
                                    tint = when (item.type) {
                                        ContentType.IMAGE -> palette.accentPrimary
                                        ContentType.VIDEO -> palette.accentSecondary
                                        ContentType.DOCUMENT_PDF -> palette.error
                                        ContentType.TEXT -> palette.warning
                                        ContentType.URL -> palette.accentPrimary
                                        ContentType.GENERIC_FILE -> palette.textSecondary
                                    },
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = item.mimeType,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = "· ${formatSize(item.sizeBytes)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accentPrimary
                                )
                            }
                            if (!item.sha256Hash.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "SHA-256 Ready",
                                        tint = palette.success,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "SHA-256: ${item.sha256Hash.take(14)}...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.success
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onRemoveItem(item.id) },
                            modifier = Modifier.testTag("remove_item_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onNavigate(ScreenRoute.GESTURE_CONTROL) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("aim_gesture_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentPrimary,
                            contentColor = palette.onAccentPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Aim Gesture to Transfer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onNavigate(ScreenRoute.NEARBY_DEVICES) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("select_device_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, palette.accentSecondary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Send Directly via Nearby Devices",
                                color = palette.accentSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = palette.accentSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Rich Staged Content Preview Modal
    previewedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { previewedItem = null },
            containerColor = palette.dialogSurface,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassStatusBadge(label = item.type.name, color = palette.accentPrimary)
                    Text(
                        text = item.name,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.thumbnailUri != null) {
                        AsyncImage(
                            model = item.thumbnailUri,
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (!item.textContent.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(
                                    cornerRadius = 10.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.glassBorder
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.textContent,
                                fontSize = 12.sp,
                                color = palette.textPrimary
                            )
                        }
                    }
                    Text(
                        text = "MIME: ${item.mimeType}\nSize: ${formatSize(item.sizeBytes)}\nSHA-256: ${item.sha256Hash ?: "Computed on send"}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewedItem = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentPrimary,
                        contentColor = palette.onAccentPrimary
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showTextInputDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            containerColor = palette.dialogSurface,
            title = { Text("Add Text Note to Share", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Type text or message...") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accentPrimary,
                        unfocusedBorderColor = palette.glassBorder,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            onAddText(noteText)
                            showTextInputDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentPrimary,
                        contentColor = palette.onAccentPrimary
                    )
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }

    if (showUrlInputDialog) {
        var urlText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlInputDialog = false },
            containerColor = palette.dialogSurface,
            title = { Text("Share Website Link", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accentSecondary,
                        unfocusedBorderColor = palette.glassBorder,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlText.isNotBlank()) {
                            onAddUrl(urlText)
                            showUrlInputDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentSecondary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Add Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInputDialog = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}
