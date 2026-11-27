package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.PersonalDictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

internal class BackupFolderException(message: String) : Exception(message)

interface DeviceBackupStorage {
    fun read(folderUri: String): ByteArray?
    fun replace(folderUri: String, bytes: ByteArray, expected: ByteArray?)
    fun release(folderUri: String) = Unit
}

class AndroidDeviceBackupStorage(context: Context) : DeviceBackupStorage {
    private val resolver = context.applicationContext.contentResolver

    override fun release(folderUri: String) {
        resolver.releasePersistableUriPermission(Uri.parse(folderUri),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun read(uri: Uri): ByteArray = resolver.openInputStream(uri)?.use { it.readBounded() }
        ?: error("Berkas cadangan tidak dapat dibaca.")

    override fun read(folderUri: String): ByteArray? = inFolder(folderUri) { tree, parent ->
        val files = lookup(tree, parent)
        (files[DeviceDictionaryBackupManager.FILE_NAME] ?: files[PREVIOUS])?.let(::read)
    }

    override fun replace(folderUri: String, bytes: ByteArray, expected: ByteArray?) = inFolder(folderUri) { tree, parent ->
        val files = lookup(tree, parent)
        val old = files[DeviceDictionaryBackupManager.FILE_NAME]
        var previous = files[PREVIOUS]
        val actual = (old ?: previous)?.let(::read)
        check(if (expected == null) actual == null else actual?.contentEquals(expected) == true) {
            "Cadangan berubah di luar aplikasi. Sinkronkan ulang."
        }
        // Only ciphertext is staged. Never truncate the existing backup to write a new one.
        files[STAGED]?.let { check(DocumentsContract.deleteDocument(resolver, it)) }
        val staged = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", STAGED)
            ?: error("Berkas sementara tidak dapat dibuat.")
        var committed = false
        try {
            resolver.openOutputStream(staged, "wt")?.use { it.write(bytes) }
                ?: error("Berkas sementara tidak dapat ditulis.")
            check(read(staged).contentEquals(bytes)) { "Berkas sementara tidak lengkap." }
            if (old != null) {
                previous?.let { check(DocumentsContract.deleteDocument(resolver, it)) }
                previous = null
                previous = rename(old, PREVIOUS)
            }
            rename(staged, DeviceDictionaryBackupManager.FILE_NAME)
            committed = true
        } finally {
            if (!committed) {
                // If rollback is unavailable, a subsequent read still uses the previous VXENC.
                previous?.let { runCatching { rename(it, DeviceDictionaryBackupManager.FILE_NAME) } }
                runCatching { DocumentsContract.deleteDocument(resolver, staged) }
            }
        }
        previous?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        Unit
    }

    private fun rename(uri: Uri, name: String): Uri = DocumentsContract.renameDocument(resolver, uri, name)
        ?: error("Berkas cadangan tidak dapat diganti dengan aman.")

    private fun lookup(tree: Uri, parent: Uri): Map<String, Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        return resolver.query(children, columns, null, null, null)?.use { rows ->
            buildMap {
                while (rows.moveToNext()) {
                    val name = rows.getString(0)
                    if (name == DeviceDictionaryBackupManager.FILE_NAME || name == STAGED || name == PREVIOUS) {
                        put(name, DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(1)))
                    }
                }
            }
        } ?: throw BackupFolderException("Folder tidak dapat dibaca. Pilih lagi folder cadangan.")
    }

    private inline fun <T> inFolder(folderUri: String, action: (Uri, Uri) -> T): T {
        if (!DeviceDictionaryBackupManager.isValidFolderTreeUri(folderUri)) {
            throw BackupFolderException("Pilih folder cadangan di penyimpanan perangkat.")
        }
        val tree = Uri.parse(folderUri)
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission && it.isWritePermission }) {
            throw BackupFolderException("Izin folder hilang. Pilih lagi folder cadangan.")
        }
        return try {
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            action(tree, backupFolder(tree, root))
        } catch (_: SecurityException) {
            throw BackupFolderException("Akses folder ditolak. Pilih lagi folder cadangan.")
        }
    }

    // Folder cadangan otomatis: <folder pilihan>/ketikku_backup_vxenc. Dibuat jika belum ada.
    private fun backupFolder(tree: Uri, root: Uri): Uri {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(root))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val existing = resolver.query(children, columns, null, null, null)?.use { rows ->
            var found: Uri? = null
            while (rows.moveToNext() && found == null) {
                if (rows.getString(0) == DeviceDictionaryBackupManager.BACKUP_FOLDER_NAME) {
                    found = DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(1))
                }
            }
            found
        } ?: throw BackupFolderException("Folder tidak dapat dibaca. Pilih lagi folder cadangan.")
        return existing ?: DocumentsContract.createDocument(
            resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, DeviceDictionaryBackupManager.BACKUP_FOLDER_NAME
        ) ?: throw BackupFolderException("Folder cadangan tidak dapat dibuat.")
    }

    private fun InputStream.readBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var size = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) return output.toByteArray()
            size += read
            require(size <= BackupCodec.MAX_FILE) { "Berkas VXENC terlalu besar." }
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        const val PREVIOUS = ".ketikku-prev.vxenc"
        const val STAGED = ".ketikku-stage.vxenc"
    }
}

internal data class LocalBackupSnapshot(
    val entries: List<PersonalDictionaryEntry>,
    val folders: List<String>,
    val lastSyncedAt: Long,
    val entriesRevision: String?,
    val foldersRevision: String?
)

sealed interface DeviceSyncResult {
    data object Locked : DeviceSyncResult
    data class Synced(val message: String) : DeviceSyncResult
    data class Error(val message: String) : DeviceSyncResult
}

class DeviceDictionaryBackupManager(
    private val settingsRepository: SettingsRepository,
    private val storage: DeviceBackupStorage
) {
    private val mutex = Mutex()
    private var key: ByteArray? = null

    suspend fun unlockAndSync(password: String): DeviceSyncResult = mutex.withLock {
        clearPassword()
        if (!BackupCodec.isValidPassword(password)) return@withLock DeviceSyncResult.Error("Masukkan kata sandi tanpa spasi (maks. 128 byte).")
        val candidate = password.toByteArray(Charsets.UTF_8)
        try {
            sync(candidate).also { if (it is DeviceSyncResult.Synced) key = candidate }
        } finally {
            if (key !== candidate) candidate.fill(0)
        }
    }

    suspend fun syncNow(): DeviceSyncResult = mutex.withLock {
        val password = key ?: return@withLock DeviceSyncResult.Locked
        sync(password).also { if (it is DeviceSyncResult.Error) clearPassword() }
    }

    suspend fun lock() = mutex.withLock { clearPassword() }

    suspend fun selectFolder(treeUri: String) = mutex.withLock {
        clearPassword()
        val previous = settingsRepository.getCurrentSyncLocalState().deviceBackupTreeUri
        settingsRepository.saveDeviceBackupTreeUri(treeUri)
        if (previous.isNotEmpty() && previous != treeUri) runCatching { storage.release(previous) }
        Unit
    }

    private fun clearPassword() {
        key?.fill(0)
        key = null
    }

    private suspend fun sync(password: ByteArray): DeviceSyncResult = withContext(Dispatchers.IO) {
        try {
            val folder = settingsRepository.getCurrentSyncLocalState().deviceBackupTreeUri
            val existing = storage.read(folder)
            // Authenticate and validate the entire file before changing local data or replacing the file.
            val remote = existing?.let { VxencBackupPayload.decode(BackupCodec.decode(it, password)) }
            val local = settingsRepository.mergeVxencBackup(remote)
            val entries = local.entries.map(PortableBackupEntry::fromLocal).sortedBy { it.entry.id }
            val changed = remote == null || local.folders != remote.folders || entries != remote.entries.sortedBy { it.entry.id }
            val updatedAt = maxOf(System.currentTimeMillis(), local.lastSyncedAt, remote?.updatedAtMillis ?: 0)
            if (changed) {
                val payload = VxencBackupPayload(updatedAt, local.folders, entries)
                storage.replace(folder, BackupCodec.encode(payload.encode(), password), existing)
            }
            val message = when {
                existing == null -> "Cadangan VXENC dibuat."
                changed -> "Cadangan VXENC diperbarui."
                else -> "Cadangan VXENC tersinkron."
            }
            settingsRepository.markVxencSynced(local, if (changed) updatedAt else requireNotNull(remote).updatedAtMillis, message)
            DeviceSyncResult.Synced(message)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = when (e) {
                is BackupFolderException, is BackupCodec.AuthenticationException -> e.message
                is IllegalStateException, is IllegalArgumentException -> e.message?.takeIf { it in SAFE_ERRORS }
                else -> null
            }
            DeviceSyncResult.Error(message ?: "Cadangan tidak dapat disinkronkan. Periksa folder dan coba lagi.")
        }
    }

    companion object {
        const val FILE_NAME = "ketikku.vxenc"
        // Nama folder = nama apk + jenis file + format file.
        const val BACKUP_FOLDER_NAME = "ketikku_backup_vxenc"
        // Lokasi awal pemilih folder: /storage/emulated/0/autosync.
        const val AUTOSYNC_VOLUME_ID = "primary:autosync"
        private const val AUTHORITY = "com.android.externalstorage.documents"
        private val SAFE_ERRORS = setOf(
            "Seed A2F tidak dapat dibaca. Cadangan tidak diubah.",
            "Cadangan berubah di luar aplikasi. Sinkronkan ulang.",
            "Isi VXENC bukan cadangan KetikKu yang didukung atau datanya rusak.",
            "Berkas bukan VXENC versi 1.",
            "Gunakan VXENC AES-256-GCM tanpa keyfile.",
            "Ukuran atau parameter berkas VXENC tidak valid.",
            "Berkas VXENC terlalu besar.",
            "Data terlalu besar untuk cadangan VXENC.",
            "ID cadangan bentrok dengan akun A2F. Data tidak diubah."
        )

        fun folderPath(treeUri: String): String? = runCatching {
            val uri = Uri.parse(treeUri)
            require(uri.scheme == "content" && uri.authority == AUTHORITY && DocumentsContract.isTreeUri(uri))
            val id = DocumentsContract.getTreeDocumentId(uri)
            val colon = id.indexOf(':')
            require(colon > 0)
            val volume = id.substring(0, colon)
            val segments = id.substring(colon + 1).split('/').filter { it.isNotEmpty() }
            require(segments.none { it == "." || it == ".." })
            val relative = segments.joinToString("/")
            if (volume == "primary") {
                relative.ifEmpty { "Penyimpanan internal" }
            } else {
                "Kartu SD ($volume)" + if (relative.isEmpty()) "" else "/$relative"
            }
        }.getOrNull()

        fun isValidFolderTreeUri(treeUri: String): Boolean = folderPath(treeUri) != null
    }
}
