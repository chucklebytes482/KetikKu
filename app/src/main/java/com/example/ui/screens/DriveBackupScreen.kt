package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SettingsRepository
import com.example.data.SyncDirection
import com.example.model.BackupSnapshot
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.model.SyncLocalState
import com.example.ui.AuthHelper
import com.example.ui.CloudSyncViewModel
import com.example.ui.CloudUiState
import com.example.ui.MainViewModel
import com.example.ui.SyncActionState
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanOnAccent
import com.example.ui.theme.CyanOnContainer
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MintSynced
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.google.android.gms.auth.api.identity.Identity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriveBackupUnauthenticatedScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SlateSurface,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(1.dp, SlateOutline, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CyanContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Google Drive",
                            tint = CyanOnContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Auto-Sinkronisasi Google Drive",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Belum terhubung ke akun Google",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Masuk dengan akun Google untuk mencadangkan seluruh setelan keyboard (tema, tinggi, baris angka, koreksi otomatis) dan kamus pribadi sebagai berkas JSON di Google Drive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = CoralAlert,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        errorMsg = null
                        isLoading = true
                        AuthHelper.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMsg = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isLoading = false }
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = CyanOnAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("drive_connect_google_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CyanOnAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Masuk dengan Google", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DriveBackupScreen(
    userEmail: String,
    mainViewModel: MainViewModel,
    cloudViewModel: CloudSyncViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    syncLocalState: SyncLocalState,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val isOnline by cloudViewModel.isOnline.collectAsStateWithLifecycle()
    val syncActionState by cloudViewModel.syncActionState.collectAsStateWithLifecycle()
    val snapshotsState by cloudViewModel.backupSnapshotsState.collectAsStateWithLifecycle()

    var pendingDirection by remember { mutableStateOf<SyncDirection?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showJsonDialog by remember { mutableStateOf(false) }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        runCatching {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(result.data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                cloudViewModel.setDriveTokenAndSync(token, pendingDirection)
            } else {
                cloudViewModel.syncCloudMetadataOnly()
            }
        }.onFailure {
            cloudViewModel.syncCloudMetadataOnly()
        }
        pendingDirection = null
    }

    fun triggerDriveSync(direction: SyncDirection?) {
        pendingDirection = direction
        if (cloudViewModel.hasCachedDriveToken()) {
            cloudViewModel.syncWithCachedTokenOrRequestConsent(direction)
        } else if (activity != null) {
            AuthHelper.authorizeGoogleDrive(
                activity = activity,
                resolutionLauncher = driveAuthLauncher,
                onAccessToken = { token ->
                    cloudViewModel.setDriveTokenAndSync(token, direction)
                },
                onError = {
                    // Fallback gracefully to Firestore cloud backup if Drive Play Services UI is unavailable
                    cloudViewModel.syncCloudMetadataOnly()
                }
            )
        } else {
            cloudViewModel.syncCloudMetadataOnly()
        }
    }

    LaunchedEffect(syncActionState) {
        if (syncActionState is SyncActionState.NeedsDriveConsent && activity != null) {
            AuthHelper.authorizeGoogleDrive(
                activity = activity,
                resolutionLauncher = driveAuthLauncher,
                onAccessToken = { token ->
                    cloudViewModel.setDriveTokenAndSync(token, pendingDirection)
                },
                onError = {
                    cloudViewModel.syncCloudMetadataOnly()
                }
            )
        }
    }

    val isSyncing = syncActionState is SyncActionState.Syncing
    val statusDotColor = when {
        !isOnline -> CoralAlert
        syncLocalState.pendingChanges -> AmberPending
        syncLocalState.lastSyncedAtMillis > 0L -> MintSynced
        else -> AmberPending
    }

    val statusHeadline = when (val st = syncActionState) {
        is SyncActionState.Syncing -> "Menyinkronkan ke Google Drive..."
        is SyncActionState.Done -> st.message
        is SyncActionState.Error -> st.message
        else -> if (syncLocalState.lastSyncedAtMillis > 0L) {
            "Tersinkronisasi • ${formatRelativeTime(syncLocalState.lastSyncedAtMillis)}"
        } else {
            syncLocalState.lastSyncMessage
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                // Primary Auto-Sinkronisasi Google Drive Card (matching user's screenshot design)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SlateSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateOutline, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CyanContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Auto-Sinkronisasi Google Drive",
                                    tint = CyanOnContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Sinkronisasi Google Drive",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = userEmail.ifBlank { "Akun Google Terhubung" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = "Simpan dan sinkronkan otomatis ke folder \"${settings.driveFolderName}\" di Google Drive Anda agar berkas pengaturan bisa langsung dilihat di Drive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Status Box
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SlateSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SlateOutline, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
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
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isOnline) "Jaringan: Online" else "Jaringan: Offline",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (syncLocalState.pendingChanges) {
                                            "Perubahan menunggu sinkron"
                                        } else {
                                            "Semua perubahan tersimpan"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "Folder Drive: ${settings.driveFolderName} (Sinkron 2 arah)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showFolderDialog = true }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }

                        // Auto-Sync Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mainViewModel.updateSettings {
                                        it.copy(autoSyncDriveEnabled = !it.autoSyncDriveEnabled)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "Sinkronisasi Otomatis",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Unggah otomatis setiap ada perubahan atau saat kembali online",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoSyncDriveEnabled,
                                onCheckedChange = { enabled ->
                                    mainViewModel.updateSettings { it.copy(autoSyncDriveEnabled = enabled) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SlateSurface,
                                    checkedTrackColor = CyanAccent
                                ),
                                modifier = Modifier.testTag("drive_auto_sync_switch")
                            )
                        }

                        HorizontalDivider(color = SlateOutline)

                        // Bottom Action Pills: Putuskan Akun & Sinkronkan Sekarang
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    cloudViewModel.clearCachedDriveToken()
                                    AuthHelper.signOut(credentialManager, scope) {
                                        onSignedOut()
                                    }
                                },
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("drive_disconnect_button")
                            ) {
                                Text(
                                    text = "Putuskan Akun",
                                    color = CoralAlert,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = { triggerDriveSync(direction = null) },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanAccent,
                                    contentColor = CyanOnAccent
                                ),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(52.dp)
                                    .testTag("drive_sync_now_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = CyanOnAccent,
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
                                    text = if (isSyncing) "Menyinkron..." else "Sinkronkan Sekarang",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Secondary Drive tools: Restore from Drive & JSON Preview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { triggerDriveSync(direction = SyncDirection.DOWNLOADED) },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pulihkan Drive")
                    }

                    OutlinedButton(
                        onClick = { showJsonDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lihat JSON")
                    }
                }
            }

            // Cloud Backup Snapshots History from Firestore
            item {
                Text(
                    text = "Riwayat Cadangan Cloud",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            when (val st = snapshotsState) {
                is CloudUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CyanAccent)
                        }
                    }
                }
                is CloudUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SlateSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = st.message,
                                color = CoralAlert,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is CloudUiState.Success -> {
                    if (st.data.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SlateSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Belum ada riwayat cadangan. Tekan \"Sinkronkan Sekarang\" untuk membuat cadangan pertama.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(st.data, key = { it.id.ifEmpty { it.syncedAtMillis.toString() } }) { snap ->
                            BackupHistoryItem(snap)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showFolderDialog) {
        FolderConfigDialog(
            currentFolderName = settings.driveFolderName,
            currentShortcutId = settings.driveShortcutFolderId,
            onDismiss = { showFolderDialog = false },
            onSave = { name, shortcutId ->
                mainViewModel.updateSettings {
                    it.copy(
                        driveFolderName = name.ifBlank { "KetikKu" },
                        driveShortcutFolderId = shortcutId.trim()
                    )
                }
                showFolderDialog = false
            }
        )
    }

    if (showJsonDialog) {
        val repo = remember(context) { SettingsRepository(context) }
        val jsonPreview = remember(settings, dictionary) {
            repo.encodeSettingsBackupJson(settings)
        }
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = { Text("ketikku_settings.json (${dictionary.count { !it.deleted }} kata kamus)") },
            text = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateCardElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = jsonPreview,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanOnContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun BackupHistoryItem(snap: BackupSnapshot) {
    val dateStr = remember(snap.syncedAtMillis) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(snap.syncedAtMillis))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SlateSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateOutline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snap.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$dateStr • ${snap.dictionaryCount} kata kamus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CyanContainer
            ) {
                Text(
                    text = if (snap.syncSource == "DRIVE_AND_CLOUD") "Drive + Cloud" else "Cloud",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanOnContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderConfigDialog(
    currentFolderName: String,
    currentShortcutId: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var folderName by remember { mutableStateOf(currentFolderName) }
    var shortcutId by remember { mutableStateOf(currentShortcutId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfigurasi Folder Google Drive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nama Folder Drive") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shortcutId,
                    onValueChange = { shortcutId = it },
                    label = { Text("ID Folder / Shortcut Berbagi (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(folderName, shortcutId) }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
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
