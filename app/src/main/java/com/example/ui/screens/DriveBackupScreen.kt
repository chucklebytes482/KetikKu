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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
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
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MintSynced
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriveBackupUnauthenticatedScreen(
    mainViewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    syncLocalState: SyncLocalState,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

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
                // Metode 1: Sinkronisasi Google Drive (Opsional)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
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
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Google Drive",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Metode 1 • Sinkronisasi Google Drive",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Opsional • Belum terhubung ke akun Google",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Masuk dengan akun Google untuk sinkronisasi otomatis 2 arah ke folder \"${settings.driveFolderName}\" di Google Drive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (errorMsg != null) {
                            Text(
                                text = errorMsg.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
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
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("drive_connect_google_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Masuk dengan Google (Drive Sync)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                // Metode 2: Cadangan Lokal JSON Tanpa Login Drive
                LocalJsonBackupCard(
                    title = "Metode 2 • Cadangan Lokal (Tanpa Login Drive)",
                    mainViewModel = mainViewModel,
                    settings = settings,
                    dictionary = dictionary,
                    syncLocalState = syncLocalState
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
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
        !isOnline -> MaterialTheme.colorScheme.error
        syncLocalState.pendingChanges -> AmberPending
        syncLocalState.lastSyncedAtMillis > 0L -> MaterialTheme.colorScheme.secondary
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
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
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
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Auto-Sinkronisasi Google Drive",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
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
                                    color = MaterialTheme.colorScheme.primary,
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
                                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("drive_auto_sync_switch")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

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
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = { triggerDriveSync(direction = null) },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(52.dp)
                                    .testTag("drive_sync_now_button")
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

            // Metode 2: Cadangan Lokal JSON (Offline / Ekspor & Impor Perangkat)
            item {
                LocalJsonBackupCard(
                    title = "Cadangan Lokal JSON (Offline)",
                    mainViewModel = mainViewModel,
                    settings = settings,
                    dictionary = dictionary,
                    syncLocalState = syncLocalState
                )
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
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is CloudUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = st.message,
                                color = MaterialTheme.colorScheme.error,
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
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
        JsonPreviewDialog(
            settings = settings,
            dictionary = dictionary,
            onDismiss = { showJsonDialog = false }
        )
    }
}

@Composable
private fun LocalJsonBackupCard(
    title: String,
    mainViewModel: MainViewModel,
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    syncLocalState: SyncLocalState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localStatus by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showJsonDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val payload = mainViewModel.buildFullBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Tidak dapat membuka berkas tujuan.")
                }
            }.onSuccess {
                localError = null
                localStatus = "Cadangan JSON lokal berhasil disimpan ke perangkat."
            }.onFailure { err ->
                localStatus = null
                localError = err.localizedMessage ?: "Gagal mengekspor berkas JSON."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val rawResult = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: error("Tidak dapat membaca berkas yang dipilih.")
                }
            }
            rawResult.onSuccess { jsonText ->
                mainViewModel.importLocalBackupJson(jsonText) { result ->
                    result.onSuccess { msg ->
                        localError = null
                        localStatus = msg
                    }.onFailure { err ->
                        localStatus = null
                        localError = err.localizedMessage ?: "Format JSON cadangan tidak valid."
                    }
                }
            }.onFailure { err ->
                localStatus = null
                localError = err.localizedMessage ?: "Gagal membuka berkas JSON."
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ekspor & pulihkan setelan + ${dictionary.count { !it.deleted }} kata kamus langsung ke file JSON tanpa akun Google.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (syncLocalState.lastSyncedAtMillis > 0L && localStatus == null) {
                Text(
                    text = "Status terakhir: ${syncLocalState.lastSyncMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (localStatus != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = localStatus.orEmpty(),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (localError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = localError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("ketikku_backup_$stamp.json")
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("local_export_json_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ekspor JSON", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("local_import_json_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pulihkan JSON", maxLines = 1)
                }
            }

            TextButton(
                onClick = { showJsonDialog = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("local_preview_json_button")
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lihat Pratinjau JSON")
            }
        }
    }

    if (showJsonDialog) {
        JsonPreviewDialog(
            settings = settings,
            dictionary = dictionary,
            onDismiss = { showJsonDialog = false }
        )
    }
}

@Composable
private fun JsonPreviewDialog(
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember(context) { SettingsRepository(context) }
    val jsonPreview = remember(settings, dictionary) {
        repo.encodeFullBackupJson(settings, dictionary)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ketikku_backup.json (${dictionary.count { !it.deleted }} kata kamus)") },
        text = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = jsonPreview,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun BackupHistoryItem(snap: BackupSnapshot) {
    val dateStr = remember(snap.syncedAtMillis) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(snap.syncedAtMillis))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
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
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = if (snap.syncSource == "DRIVE_AND_CLOUD") "Drive + Cloud" else "Cloud",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
