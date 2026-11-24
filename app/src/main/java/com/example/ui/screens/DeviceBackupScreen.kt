package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceDictionaryBackupManager
import com.example.model.PersonalDictionaryEntry
import com.example.model.SyncLocalState
import com.example.ui.DeviceSyncUiState
import com.example.ui.theme.GoogleYellow700
import com.example.ui.theme.GoogleGreen700
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceBackupSheetContent(
    dictionary: List<PersonalDictionaryEntry>,
    syncLocalState: SyncLocalState,
    deviceSyncUiState: DeviceSyncUiState,
    onSyncNow: () -> Unit,
    onFolderAuthorized: (String) -> Unit,
    onResetFolderToDefault: () -> Unit = {},
    modifier: Modifier = Modifier,
    a2fContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val activeWordCount = remember(dictionary) { dictionary.count { !it.deleted } }
    val hasCustomFolder = remember(syncLocalState.deviceBackupTreeUri) {
        DeviceDictionaryBackupManager.isValidDocumentsTreeUri(syncLocalState.deviceBackupTreeUri)
    }
    val backupDisplayPath = remember(syncLocalState.deviceBackupTreeUri) {
        DeviceDictionaryBackupManager.formatBackupDisplayPath(syncLocalState.deviceBackupTreeUri)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            val uriString = selectedUri.toString()
            if (DeviceDictionaryBackupManager.isValidDocumentsTreeUri(uriString)) {
                runCatching {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(selectedUri, flags)
                }
            }
            onFolderAuthorized(uriString)
        }
    }

    val isSyncing = deviceSyncUiState is DeviceSyncUiState.Syncing
    val needsFolderAccess = deviceSyncUiState is DeviceSyncUiState.NeedsFolderAccess
    val statusDotColor = when (deviceSyncUiState) {
        is DeviceSyncUiState.Syncing -> MaterialTheme.colorScheme.primary
        is DeviceSyncUiState.NeedsFolderAccess -> GoogleYellow700
        is DeviceSyncUiState.Error -> MaterialTheme.colorScheme.error
        is DeviceSyncUiState.Idle -> when {
            syncLocalState.pendingChanges -> GoogleYellow700
            syncLocalState.lastSyncedAtMillis > 0L -> GoogleGreen700
            else -> GoogleYellow700
        }
    }

    val statusHeadline = when (deviceSyncUiState) {
        is DeviceSyncUiState.Syncing -> "Menyinkronkan kamus..."
        is DeviceSyncUiState.NeedsFolderAccess -> deviceSyncUiState.message
        is DeviceSyncUiState.Error -> deviceSyncUiState.message
        is DeviceSyncUiState.Idle -> if (syncLocalState.lastSyncedAtMillis > 0L) {
            "Tersinkronisasi • ${formatRelativeTime(syncLocalState.lastSyncedAtMillis)}"
        } else {
            "Belum dicadangkan"
        }
    }

    DeviceBackupSheetBody(
        activeWordCount = activeWordCount,
        backupDisplayPath = backupDisplayPath,
        hasCustomFolder = hasCustomFolder,
        statusHeadline = statusHeadline,
        statusDotColor = statusDotColor,
        isSyncing = isSyncing,
        needsFolderAccess = needsFolderAccess,
        onSyncNow = onSyncNow,
        onRequestFolderAccess = {
            val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DeviceDictionaryBackupManager.INITIAL_DOCUMENTS_URI
            } else {
                null
            }
            folderPickerLauncher.launch(initialUri)
        },
        onResetFolderToDefault = onResetFolderToDefault,
        a2fContent = a2fContent,
        modifier = modifier
    )
}

@Composable
internal fun DeviceBackupSheetBody(
    activeWordCount: Int,
    statusHeadline: String,
    statusDotColor: Color,
    isSyncing: Boolean,
    needsFolderAccess: Boolean,
    onSyncNow: () -> Unit,
    onRequestFolderAccess: () -> Unit,
    backupDisplayPath: String = DeviceDictionaryBackupManager.BACKUP_DISPLAY_PATH,
    hasCustomFolder: Boolean = false,
    onResetFolderToDefault: () -> Unit = {},
    modifier: Modifier = Modifier,
    a2fContent: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Cadangan Perangkat",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cadangan Perangkat",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Otomatis saat dibuka & saat diubah • Hanya Kamus Pribadi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Status 1 baris (dimensi kartu tetap konsisten)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("device_sync_status_card")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Text(
                    text = statusHeadline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Lokasi file tunggal di dalam /storage/emulated/0/Documents/
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("device_backup_path_card")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lokasi file tunggal ($activeWordCount kata aktif)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasCustomFolder) {
                        TextButton(
                            onClick = onResetFolderToDefault,
                            enabled = !isSyncing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                                .testTag("device_reset_folder_button")
                        ) {
                            Text(
                                text = "Bawaan",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    text = backupDisplayPath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("device_backup_path_text")
                )
                Text(
                    text = "Bebas pilih folder di dalam Documents tanpa dibuatkan folder otomatis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

        // Selalu tampil dengan ukuran konsisten agar pengguna bebas memilih jalur di dalam Documents kapan saja
        OutlinedButton(
            onClick = onRequestFolderAccess,
            enabled = !isSyncing,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("device_grant_folder_button")
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (needsFolderAccess) {
                    "Izinkan & Pilih Folder Documents"
                } else {
                    "Pilih Folder di Documents"
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onSyncNow,
            enabled = !isSyncing,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("device_sync_now_button")
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isSyncing) "Menyinkron..." else "Sinkron Sekarang",
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        a2fContent()
    }
}

private fun formatRelativeTime(timestampMillis: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Baru saja"
        diffSec < 3600L -> "${diffSec / 60} menit lalu"
        diffSec < 86400L -> "${diffSec / 3600} jam lalu"
        else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestampMillis))
    }
}
