package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SyncDirection
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.model.SyncLocalState
import com.example.ui.AuthHelper
import com.example.ui.CloudSyncViewModel
import com.example.ui.MainViewModel
import com.example.ui.SyncActionState
import com.example.ui.findActivity
import com.example.ui.theme.AmberPending
import com.google.android.gms.auth.api.identity.Identity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriveBackupUnauthenticatedSheetContent(
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
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
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    text = "Opsional • Belum terhubung ke akun Google",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Simpan dan sinkronkan otomatis ke folder \"${settings.driveFolderName}\" di Google Drive Anda agar berkas bisa langsung dilihat di Drive.",
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
                .heightIn(min = 54.dp)
                .testTag("drive_connect_google_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Masuk dengan Google (Drive Sync)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DriveBackupSheetContent(
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
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val isOnline by cloudViewModel.isOnline.collectAsStateWithLifecycle()
    val syncActionState by cloudViewModel.syncActionState.collectAsStateWithLifecycle()

    var pendingDirection by remember { mutableStateOf<SyncDirection?>(null) }

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

    fun triggerDriveSync(direction: SyncDirection? = null) {
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
        is SyncActionState.Syncing -> "Menyinkronkan kamus..."
        is SyncActionState.Done -> st.message
        is SyncActionState.Error -> st.message
        else -> if (syncLocalState.lastSyncedAtMillis > 0L) {
            "Tersinkronisasi • ${formatRelativeTime(syncLocalState.lastSyncedAtMillis)}"
        } else {
            syncLocalState.lastSyncMessage
        }
    }

    DriveBackupConnectedContent(
        userEmail = userEmail,
        driveFolderName = settings.driveFolderName,
        autoSyncEnabled = settings.autoSyncDriveEnabled,
        isOnline = isOnline,
        pendingChanges = syncLocalState.pendingChanges,
        statusHeadline = statusHeadline,
        statusDotColor = statusDotColor,
        isSyncing = isSyncing,
        onToggleAutoSync = { enabled ->
            mainViewModel.updateSettings { it.copy(autoSyncDriveEnabled = enabled) }
        },
        onDisconnect = {
            cloudViewModel.clearCachedDriveToken()
            AuthHelper.signOut(credentialManager, scope) {
                onSignedOut()
            }
        },
        onSyncNow = { triggerDriveSync(direction = null) },
        modifier = modifier
    )
}

@Composable
internal fun DriveBackupConnectedContent(
    userEmail: String,
    driveFolderName: String,
    autoSyncEnabled: Boolean,
    isOnline: Boolean,
    pendingChanges: Boolean,
    statusHeadline: String,
    statusDotColor: androidx.compose.ui.graphics.Color,
    isSyncing: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
            text = "Simpan dan sinkronkan otomatis ke folder \"$driveFolderName\" di Google Drive Anda agar berkas bisa langsung dilihat di Drive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("drive_sync_status_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
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
                        text = if (pendingChanges) {
                            "Perubahan menunggu sinkron"
                        } else {
                            "Semua perubahan tersimpan"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Folder Drive: $driveFolderName (Sinkron 2 arah)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleAutoSync(!autoSyncEnabled) }
                .padding(vertical = 4.dp),
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
                checked = autoSyncEnabled,
                onCheckedChange = onToggleAutoSync,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("drive_auto_sync_switch")
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDisconnect,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp)
                    .testTag("drive_disconnect_button")
            ) {
                Text(
                    text = "Putuskan Akun",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
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
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .heightIn(min = 54.dp)
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
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isSyncing) "Menyinkron..." else "Sinkronkan Sekarang",
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
            }
        }
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
