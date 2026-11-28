package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.BackupCodec
import com.example.data.DeviceDictionaryBackupManager
import com.example.ui.DeviceSyncUiState

@Composable
fun DeviceBackupSheetContent(
    folderUri: String,
    state: DeviceSyncUiState,
    snackbarState: SnackbarHostState,
    onFolderAuthorized: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onSync: () -> Unit,
    onLock: () -> Unit,
    onMessage: (String) -> Unit,
    hasSavedPassword: Boolean = false,
    onForgetPassword: () -> Unit = {}
) {
    val resolver = LocalContext.current.contentResolver
    val autosyncStartUri = android.provider.DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", DeviceDictionaryBackupManager.AUTOSYNC_VOLUME_ID
    )
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val chosen = uri.toString()
            if (!DeviceDictionaryBackupManager.isValidFolderTreeUri(chosen)) {
                onMessage("Pilih folder di penyimpanan perangkat.")
            } else {
                try {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    onFolderAuthorized(chosen)
                } catch (_: SecurityException) {
                    onMessage("Izin baca dan tulis folder tidak diberikan.")
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().imePadding()) {
        DeviceBackupSheetBody(
            folder = DeviceDictionaryBackupManager.folderPath(folderUri),
            state = state,
            hasSavedPassword = hasSavedPassword,
            onForgetPassword = onForgetPassword,
            onRequestFolderAccess = {
                folderPicker.launch(autosyncStartUri)
            },
            onUnlock = onUnlock,
            onSync = onSync,
            onLock = onLock,
            modifier = Modifier.weight(1f, fill = false)
        )
        SnackbarHost(snackbarState, modifier = Modifier.fillMaxWidth())
    }
}

private const val SAVED_PASSWORD_MASK = "••••••••"

@Composable
internal fun DeviceBackupSheetBody(
    folder: String?,
    state: DeviceSyncUiState,
    onRequestFolderAccess: () -> Unit,
    onUnlock: (String) -> Unit,
    onSync: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    hasSavedPassword: Boolean = false,
    onForgetPassword: () -> Unit = {}
) {
    val syncing = state == DeviceSyncUiState.Syncing
    val unlocked = state == DeviceSyncUiState.Unlocked
    var password by remember(folder) { mutableStateOf("") }
    val passwordValid = BackupCodec.isValidPassword(password)
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cadangan Perangkat", style = MaterialTheme.typography.titleLarge)
        Text("Satu berkas terenkripsi untuk kamus pribadi dan A2F.", style = MaterialTheme.typography.bodyMedium)
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("device_backup_path_card"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Lokasi berkas", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (folder == null) "Belum dipilih" else "$folder/${DeviceDictionaryBackupManager.BACKUP_FOLDER_NAME}/${DeviceDictionaryBackupManager.FILE_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("device_backup_path_text")
                )
                OutlinedButton(
                    onClick = onRequestFolderAccess, enabled = !syncing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("device_grant_folder_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Pilih folder", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(
            when (state) {
                DeviceSyncUiState.Locked -> "Terkunci"
                DeviceSyncUiState.Syncing -> "Memproses cadangan…"
                DeviceSyncUiState.Unlocked -> "Terbuka · sinkron otomatis"
            },
            style = MaterialTheme.typography.labelLarge
        )
        // Kata sandi selalu tersembunyi: tanpa tombol lihat, tanpa salin dari kolom.
        if (!unlocked) OutlinedTextField(
            value = if (hasSavedPassword) SAVED_PASSWORD_MASK else password,
            onValueChange = { if (!hasSavedPassword) password = it },
            enabled = folder != null && !syncing && !hasSavedPassword,
            modifier = Modifier.fillMaxWidth().testTag("device_backup_password"),
            label = { Text("Kata sandi cadangan") },
            singleLine = true,
            isError = !hasSavedPassword && password.isNotEmpty() && !passwordValid,
            supportingText = {
                Text(
                    when {
                        hasSavedPassword -> "Terisi otomatis dari penyimpanan perangkat."
                        password.isNotEmpty() && !passwordValid -> "Maksimum 128 byte (karakter non-ASCII bisa memakai lebih dari satu byte)."
                        else -> "Spasi diabaikan. Gunakan kata sandi yang panjang dan simpan untuk pemulihan."
                    }
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        if (hasSavedPassword && !unlocked) TextButton(
            onClick = onForgetPassword, enabled = !syncing,
            modifier = Modifier.heightIn(min = 48.dp).testTag("device_forget_password_button")
        ) { Text("Hapus kata sandi tersimpan") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (unlocked) onSync() else {
                        // Kata sandi kosong: sheet memakai kata sandi tersimpan di perangkat.
                        val submitted = if (hasSavedPassword) "" else password
                        password = ""
                        onUnlock(submitted)
                    }
                },
                enabled = folder != null && !syncing && (unlocked || hasSavedPassword || passwordValid),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("device_sync_button")
            ) {
                if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (unlocked) "Sinkronkan sekarang" else "Buka & sinkronkan")
            }
            IconButton(onClick = onLock, enabled = unlocked, modifier = Modifier.size(48.dp).testTag("device_lock_button")) {
                Icon(Icons.Default.Lock, contentDescription = "Kunci cadangan")
            }
        }
        Text(
            "Berkas yang sudah ada digabungkan. Jika belum ada, KetikKu membuatnya. Cadangan berjalan otomatis setelah dibuka, sampai dikunci atau sesi aplikasi berakhir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
