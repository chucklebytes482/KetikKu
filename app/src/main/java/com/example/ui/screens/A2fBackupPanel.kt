package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
internal fun A2fBackupPanel(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var encryptionOn by remember { mutableStateOf(viewModel.isBackupEncryptionConfigured()) }
    var showEncryptDialog by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirm by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { pendingPassword?.fill('\u0000') } }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri == null || json == null) { busy = false; status = "Ekspor dibatalkan" }
        else scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Berkas tidak dapat dibuka")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                "Cadangan A2F berpassword berhasil disimpan"
            }.getOrElse { "Gagal menyimpan cadangan A2F" }
            busy = false
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val secret = pendingPassword
        pendingPassword = null
        if (uri == null || secret == null) { secret?.fill('\u0000'); busy = false; status = "Impor dibatalkan" }
        else scope.launch {
            status = try {
                val raw = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Berkas tidak dapat dibuka")
                    input.use { stream ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(out.size() + count <= 4 * 1024 * 1024) { "Cadangan terlalu besar" }
                            out.write(buffer, 0, count)
                        }
                        out.toString("UTF-8")
                    }
                }
                val count = viewModel.importA2fBackup(raw, secret)
                "$count akun A2F berhasil dipulihkan"
            } catch (error: Exception) {
                "Impor gagal: password, format, atau akun bentrok. Data lama dipertahankan."
            } finally { secret.fill('\u0000'); busy = false }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cadangan A2F", style = MaterialTheme.typography.titleMedium)
        Text("Seed disimpan di Android Keystore. Cadangan otomatis A2F hanya berjalan setelah passphrase cadangan dibuat; berkasnya selalu terenkripsi. Tetap simpan cadangan berpassword sebelum pindah HP atau menghapus aplikasi.")
        if (encryptionOn) {
            Text("Cadangan otomatis terenkripsi: aktif", modifier = Modifier.testTag("a2f_auto_status"))
        } else {
            OutlinedButton(onClick = { showEncryptDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("a2f_auto_enable_button")) { Text("Aktifkan cadangan otomatis terenkripsi") }
        }
        OutlinedButton(onClick = { mode = "export" }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("a2f_export_button")) { Text("Ekspor A2F berpassword") }
        OutlinedButton(onClick = { mode = "import" }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("a2f_import_button")) { Text("Impor A2F") }
        if (status.isNotBlank()) Text(status, modifier = Modifier.testTag("a2f_backup_status"))
        if (busy) Text("Memproses cadangan A2F…")
    }
    if (showEncryptDialog) {
        val valid = passphrase.length >= 12 && passphrase == passphraseConfirm
        AlertDialog(
            onDismissRequest = { showEncryptDialog = false; passphrase = ""; passphraseConfirm = "" },
            title = { Text("Passphrase cadangan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Satu passphrase untuk semua cadangan otomatis (kamus, kata sandi, kredensial, A2F). Minimal 12 karakter. Dibutuhkan untuk memulihkan di HP baru; tidak bisa dipulihkan jika lupa.")
                    OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, singleLine = true,
                        label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.testTag("a2f_auto_passphrase"))
                    OutlinedTextField(value = passphraseConfirm, onValueChange = { passphraseConfirm = it }, singleLine = true,
                        label = { Text("Ulangi passphrase") }, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = { Button(
                enabled = valid && !busy,
                onClick = {
                    val chars = passphrase.toCharArray()
                    passphrase = ""; passphraseConfirm = ""; showEncryptDialog = false
                    busy = true; status = ""
                    viewModel.enableBackupEncryption(chars) { result ->
                        busy = false
                        encryptionOn = result.isSuccess || viewModel.isBackupEncryptionConfigured()
                        status = result.fold(
                            onSuccess = { "Cadangan otomatis terenkripsi diaktifkan" },
                            onFailure = { "Gagal: ${it.message.orEmpty()}" }
                        )
                    }
                }
            ) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { showEncryptDialog = false; passphrase = ""; passphraseConfirm = "" }) { Text("Batal") } }
        )
    }
    if (mode != null) {
        val exporting = mode == "export"
        AlertDialog(
            onDismissRequest = { mode = null; password = ""; confirmation = "" },
            title = { Text(if (exporting) "Password cadangan A2F" else "Impor cadangan A2F") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (exporting) "Minimal 12 karakter. Password tidak disimpan dan tidak dapat dipulihkan." else "Masukkan password cadangan. Untuk JSON A2F versi lama, kosongkan password. Berkas lama belum aman: setelah impor, ekspor ulang berpassword lalu ganti berkas lama secara manual.")
                    OutlinedTextField(value = password, onValueChange = { password = it }, singleLine = true,
                        label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.testTag("a2f_backup_password"))
                    if (exporting) OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, singleLine = true,
                        label = { Text("Ulangi password") }, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = { Button(
                enabled = !exporting || (password.length >= 12 && password == confirmation),
                onClick = {
                    val chars = password.toCharArray()
                    password = ""; confirmation = ""; mode = null; busy = true; status = ""
                    if (exporting) scope.launch {
                        try {
                            pendingJson = viewModel.exportA2fBackup(chars)
                            exportPicker.launch("ketikku_a2f.json")
                        } catch (_: Exception) { busy = false; status = "Ekspor gagal; pastikan semua seed dapat dibaca" }
                        finally { chars.fill('\u0000') }
                    } else {
                        pendingPassword = chars
                        importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }
            ) { Text("Pilih berkas") } },
            dismissButton = { TextButton(onClick = { mode = null; password = ""; confirmation = "" }) { Text("Batal") } }
        )
    }
}
