package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.model.PersonalDictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class StoredBackupFile(
    val content: String,
    val lastModifiedMillis: Long
)

sealed interface BackupReadResult {
    data object NotFound : BackupReadResult
    data class Found(val file: StoredBackupFile) : BackupReadResult
    data class NeedsFolderAccess(val reason: String) : BackupReadResult
    data class Error(val message: String) : BackupReadResult
}

sealed interface BackupWriteResult {
    data class Success(val writtenAtMillis: Long) : BackupWriteResult
    data class NeedsFolderAccess(val reason: String) : BackupWriteResult
    data class Error(val message: String) : BackupWriteResult
}

interface DeviceBackupStorage {
    fun readBackup(fileName: String, persistedTreeUri: String?): BackupReadResult
    fun writeBackup(fileName: String, json: String, persistedTreeUri: String?): BackupWriteResult
    /** Menghapus berkas dari semua jalur penyimpanan. Berkas yang tidak ada dianggap sudah terhapus. */
    fun deleteBackup(fileName: String, persistedTreeUri: String?): BackupWriteResult
}

sealed interface DeviceSyncResult {
    data class Synced(
        val activeEntryCount: Int,
        val restoredFromFile: Boolean,
        val wroteToFile: Boolean,
        val syncedAtMillis: Long,
        val message: String
    ) : DeviceSyncResult

    data class NeedsFolderAccess(val message: String) : DeviceSyncResult
    data class Error(val message: String) : DeviceSyncResult
}

class DirectoryDeviceBackupStorage(
    private val targetDirectory: File
) : DeviceBackupStorage {

    fun fileFor(fileName: String): File = File(targetDirectory, fileName)

    override fun readBackup(fileName: String, persistedTreeUri: String?): BackupReadResult {
        return runCatching {
            val file = fileFor(fileName)
            if (!file.exists()) return BackupReadResult.NotFound
            if (!file.canRead()) {
                return BackupReadResult.NeedsFolderAccess(
                    "File cadangan ditemukan. Pilih folder di dalam Documents untuk memberi akses."
                )
            }
            BackupReadResult.Found(
                StoredBackupFile(
                    content = file.readText(Charsets.UTF_8),
                    lastModifiedMillis = file.lastModified().coerceAtLeast(0L)
                )
            )
        }.getOrElse { err ->
            BackupReadResult.Error("Gagal membaca file cadangan: ${err.message.orEmpty()}")
        }
    }

    override fun deleteBackup(fileName: String, persistedTreeUri: String?): BackupWriteResult {
        return runCatching {
            val file = fileFor(fileName)
            if (file.exists() && !file.delete()) {
                return BackupWriteResult.Error("Gagal menghapus $fileName")
            }
            BackupWriteResult.Success(System.currentTimeMillis())
        }.getOrElse { err ->
            BackupWriteResult.Error("Gagal menghapus $fileName: ${err.message.orEmpty()}")
        }
    }

    override fun writeBackup(fileName: String, json: String, persistedTreeUri: String?): BackupWriteResult {
        return runCatching {
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs()
            }
            val file = fileFor(fileName)
            // Overwrite the exact same file in-place so strictly 1 backup file exists.
            file.writeText(json, Charsets.UTF_8)
            BackupWriteResult.Success(file.lastModified().coerceAtLeast(System.currentTimeMillis()))
        }.getOrElse { err ->
            BackupWriteResult.Error("Gagal menulis file cadangan: ${err.message.orEmpty()}")
        }
    }
}

class AndroidDeviceBackupStorage(
    private val context: Context
) : DeviceBackupStorage {

    private val resolver get() = context.contentResolver

    private fun legacyPublicBackupFile(fileName: String): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(docs, fileName)
    }

    override fun readBackup(fileName: String, persistedTreeUri: String?): BackupReadResult {
        // Tier 1: Persisted SAF folder URI (bebas memilih jalur di dalam Documents/)
        if (!persistedTreeUri.isNullOrBlank()) {
            val safResult = readViaSafTree(Uri.parse(persistedTreeUri), fileName)
            if (safResult != null) return safResult
        }

        // Tier 2: Android 10+ (API 29+) MediaStore.Files langsung di Documents/ (tanpa folder otomatis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return readViaMediaStore(fileName)
        }

        // Tier 3: Legacy Android 7-9 (API 24-28) langsung di Documents/
        return readViaLegacyFile(fileName)
    }

    override fun writeBackup(fileName: String, json: String, persistedTreeUri: String?): BackupWriteResult {
        // Tier 1: Persisted SAF folder URI -> always overwrites the single target file in-place
        if (!persistedTreeUri.isNullOrBlank()) {
            val safResult = writeViaSafTree(Uri.parse(persistedTreeUri), fileName, json)
            if (safResult != null) return safResult
        }

        // Tier 2: Android 10+ (API 29+) MediaStore.Files langsung di Documents/ (strict in-place replace)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeViaMediaStore(fileName, json)
        }

        // Tier 3: Legacy Android 7-9 (API 24-28) direct file overwrite
        return writeViaLegacyFile(fileName, json)
    }

    override fun deleteBackup(fileName: String, persistedTreeUri: String?): BackupWriteResult {
        val errors = mutableListOf<String>()
        // Semua jalur dicoba: berkas yang sama bisa ada di lebih dari satu tempat setelah pindah izin folder.
        if (!persistedTreeUri.isNullOrBlank()) {
            runCatching {
                querySafMatchingFiles(Uri.parse(persistedTreeUri), fileName).forEach { match ->
                    DocumentsContract.deleteDocument(resolver, match.uri)
                }
            }.onFailure { err -> if (err !is SecurityException) errors += err.message.orEmpty() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                queryMediaStoreMatches(fileName).forEach { match -> resolver.delete(match.uri, null, null) }
            }.onFailure { err -> if (err !is SecurityException) errors += err.message.orEmpty() }
        }
        runCatching {
            val legacy = legacyPublicBackupFile(fileName)
            if (legacy.exists() && !legacy.delete()) errors += "tidak bisa menghapus ${legacy.name}"
        }.onFailure { err -> if (err !is SecurityException) errors += err.message.orEmpty() }
        return if (errors.isEmpty()) {
            BackupWriteResult.Success(System.currentTimeMillis())
        } else {
            BackupWriteResult.Error("Gagal menghapus $fileName: ${errors.joinToString("; ")}")
        }
    }

    private fun readViaSafTree(treeUri: Uri, fileName: String): BackupReadResult? {
        return runCatching {
            val matches = querySafMatchingFiles(treeUri, fileName)
            val target = matches.maxByOrNull { it.lastModifiedMillis } ?: return BackupReadResult.NotFound
            val text = resolver.openInputStream(target.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return BackupReadResult.Error("Gagal membuka file cadangan dari folder terpilih.")
            BackupReadResult.Found(StoredBackupFile(text, target.lastModifiedMillis))
        }.getOrElse { err ->
            if (err is SecurityException) {
                // Permission may have been revoked; fall back to MediaStore or request access cleanly.
                null
            } else {
                BackupReadResult.Error("Gagal membaca cadangan folder: ${err.message.orEmpty()}")
            }
        }
    }

    private fun writeViaSafTree(treeUri: Uri, fileName: String, json: String): BackupWriteResult? {
        return runCatching {
            val matches = querySafMatchingFiles(treeUri, fileName)
            val targetUri = if (matches.isNotEmpty()) {
                val primary = matches.maxByOrNull { it.lastModifiedMillis }!!.uri
                // Duplikat nama tidak dihapus (isinya mungkin belum ikut tergabung): cukup ganti nama
                // agar tidak lagi dianggap berkas cadangan, lalu pengguna bisa memeriksanya manual.
                val stamp = System.currentTimeMillis()
                matches.filter { it.uri != primary }.forEach { dup ->
                    runCatching {
                        DocumentsContract.renameDocument(resolver, dup.uri, "duplikat-$stamp-$fileName")
                    }
                }
                primary
            } else {
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                DocumentsContract.createDocument(
                    resolver,
                    parentDocUri,
                    "application/json",
                    fileName
                ) ?: return BackupWriteResult.Error("Gagal membuat file $fileName di folder terpilih.")
            }

            writeTextTruncating(targetUri, json, Charsets.UTF_8)
            BackupWriteResult.Success(System.currentTimeMillis())
        }.getOrElse { err ->
            if (err is SecurityException) {
                null
            } else {
                BackupWriteResult.Error("Gagal menulis cadangan folder: ${err.message.orEmpty()}")
            }
        }
    }

    private data class SafFileMatch(val uri: Uri, val lastModifiedMillis: Long)

    private fun querySafMatchingFiles(treeUri: Uri, fileName: String): List<SafFileMatch> {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val result = mutableListOf<SafFileMatch>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                if (idIdx < 0 || nameIdx < 0) continue
                val name = cursor.getString(nameIdx).orEmpty()
                if (name.equals(fileName, ignoreCase = true)) {
                    val docId = cursor.getString(idIdx)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val mod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                    result.add(SafFileMatch(docUri, mod))
                }
            }
        }
        return result
    }

    private data class MediaStoreMatch(val uri: Uri, val lastModifiedMillis: Long)

    private fun queryMediaStoreMatches(fileName: String): List<MediaStoreMatch> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val relSlash = "${DeviceDictionaryBackupManager.BACKUP_RELATIVE_PATH}/"
        val relNoSlash = DeviceDictionaryBackupManager.BACKUP_RELATIVE_PATH
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND (${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)"
        val selectionArgs = arrayOf(fileName, relSlash, relNoSlash)

        val matches = mutableListOf<MediaStoreMatch>()
        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                if (idIdx < 0) continue
                val id = cursor.getLong(idIdx)
                val uri = ContentUris.withAppendedId(collection, id)
                val modSec = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                matches.add(MediaStoreMatch(uri, modSec * 1000L))
            }
        }
        return matches
    }

    private fun readViaMediaStore(fileName: String): BackupReadResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return BackupReadResult.NotFound
        return runCatching {
            val matches = queryMediaStoreMatches(fileName)
            if (matches.isNotEmpty()) {
                val latest = matches.first()
                val text = resolver.openInputStream(latest.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: return BackupReadResult.NeedsFolderAccess(
                        "File cadangan lama ditemukan. Pilih folder di dalam Documents sekali untuk sinkronisasi penuh."
                    )
                return BackupReadResult.Found(StoredBackupFile(text, latest.lastModifiedMillis))
            }

            // Detect pre-existing file from a previous install or external editor that Scoped Storage hides from MediaStore
            val diskFile = legacyPublicBackupFile(fileName)
            if (diskFile.exists()) {
                return if (diskFile.canRead()) {
                    BackupReadResult.Found(
                        StoredBackupFile(
                            content = diskFile.readText(Charsets.UTF_8),
                            lastModifiedMillis = diskFile.lastModified().coerceAtLeast(0L)
                        )
                    )
                } else {
                    BackupReadResult.NeedsFolderAccess(
                        "File $fileName ditemukan di Documents. Pilih folder di dalam Documents untuk memulihkan & menimpa file yang sama."
                    )
                }
            }
            BackupReadResult.NotFound
        }.getOrElse { err ->
            if (err is SecurityException) {
                BackupReadResult.NeedsFolderAccess(
                    "Pilih folder di dalam Documents sekali agar KetikKu dapat membaca & menimpa file cadangan yang ada."
                )
            } else {
                BackupReadResult.Error("Gagal membaca cadangan perangkat: ${err.message.orEmpty()}")
            }
        }
    }

    private fun writeViaMediaStore(fileName: String, json: String): BackupWriteResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return writeViaLegacyFile(fileName, json)
        }
        return runCatching {
            val matches = queryMediaStoreMatches(fileName)
            if (matches.isNotEmpty()) {
                val primary = matches.first().uri
                // Remove any extra owned duplicates so strictly 1 file remains
                matches.drop(1).forEach { extra ->
                    runCatching { resolver.delete(extra.uri, null, null) }
                }
                writeTextTruncating(primary, json, Charsets.UTF_8)
                return BackupWriteResult.Success(System.currentTimeMillis())
            }

            // Guard against MediaStore auto-renaming (mis. "pw (1).json") if a pre-existing foreign file exists on disk
            val diskFile = legacyPublicBackupFile(fileName)
            if (diskFile.exists()) {
                return if (diskFile.canWrite()) {
                    diskFile.writeText(json, Charsets.UTF_8)
                    BackupWriteResult.Success(diskFile.lastModified().coerceAtLeast(System.currentTimeMillis()))
                } else {
                    BackupWriteResult.NeedsFolderAccess(
                        "File $fileName lama ada di Documents. Pilih folder di dalam Documents agar file diganti tanpa duplikat."
                    )
                }
            }

            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DeviceDictionaryBackupManager.BACKUP_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val insertedUri = resolver.insert(collection, values)
                ?: return BackupWriteResult.Error("Gagal membuat file di Documents.")
            try {
                writeTextTruncating(insertedUri, json, Charsets.UTF_8)
                val ready = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(insertedUri, ready, null, null)
            } catch (e: Throwable) {
                runCatching { resolver.delete(insertedUri, null, null) }
                throw e
            }
            BackupWriteResult.Success(System.currentTimeMillis())
        }.getOrElse { err ->
            if (err is SecurityException) {
                BackupWriteResult.NeedsFolderAccess(
                    "Pilih folder di dalam Documents sekali agar KetikKu dapat menimpa file cadangan lama tanpa duplikat."
                )
            } else {
                BackupWriteResult.Error("Gagal menyimpan ke Documents: ${err.message.orEmpty()}")
            }
        }
    }

    private fun readViaLegacyFile(fileName: String): BackupReadResult {
        return runCatching {
            val file = legacyPublicBackupFile(fileName)
            if (!file.exists()) return BackupReadResult.NotFound
            if (!file.canRead()) {
                return BackupReadResult.NeedsFolderAccess(
                    "Pilih folder di dalam Documents sekali untuk memberi akses file cadangan."
                )
            }
            BackupReadResult.Found(
                StoredBackupFile(
                    content = file.readText(Charsets.UTF_8),
                    lastModifiedMillis = file.lastModified().coerceAtLeast(0L)
                )
            )
        }.getOrElse { err ->
            if (err is SecurityException) {
                BackupReadResult.NeedsFolderAccess(
                    "Pilih folder di dalam Documents sekali untuk memberi akses file cadangan."
                )
            } else {
                BackupReadResult.Error("Gagal membaca file cadangan: ${err.message.orEmpty()}")
            }
        }
    }

    private fun writeViaLegacyFile(fileName: String, json: String): BackupWriteResult {
        return runCatching {
            val file = legacyPublicBackupFile(fileName)
            file.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            file.writeText(json, Charsets.UTF_8)
            BackupWriteResult.Success(file.lastModified().coerceAtLeast(System.currentTimeMillis()))
        }.getOrElse { err ->
            if (err is SecurityException) {
                BackupWriteResult.NeedsFolderAccess(
                    "Pilih folder di dalam Documents sekali untuk menyimpan cadangan kamus."
                )
            } else {
                BackupWriteResult.Error("Gagal menyimpan file cadangan: ${err.message.orEmpty()}")
            }
        }
    }

    private fun writeTextTruncating(uri: Uri, content: String, charset: java.nio.charset.Charset) {
        val bytes = content.toByteArray(charset)
        val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: runCatching { resolver.openOutputStream(uri, "rwt") }.getOrNull()
            ?: resolver.openOutputStream(uri)
            ?: error("Tidak dapat membuka aliran tulis untuk file cadangan.")
        out.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }
}

class DeviceDictionaryBackupManager(
    private val settingsRepository: SettingsRepository,
    private val storage: DeviceBackupStorage
) {
    private val syncMutex = Mutex()

    /** Setelah sinkron kamus berhasil, perbarui berkas A2F otomatis (selalu digabung dulu dengan isi berkas). */
    private suspend fun afterDictionaryWrite(result: DeviceSyncResult): DeviceSyncResult {
        if (result !is DeviceSyncResult.Synced) return result
        val treeUri = settingsRepository.getCurrentSyncLocalState().deviceBackupTreeUri.takeIf { it.isNotBlank() }
        val error = try {
            syncA2fAuto(treeUri)
        } catch (e: Exception) {
            e.localizedMessage ?: "Gagal memperbarui cadangan A2F"
        }
        if (error == null) return result
        settingsRepository.updateSyncState(
            lastSyncedAtMillis = result.syncedAtMillis,
            message = error,
            pendingChanges = true
        )
        return DeviceSyncResult.Error(error)
    }

    /**
     * Berkas A2F otomatis. Akun dari berkas yang belum ada lokal ditambahkan dulu, lalu himpunan A2F aktif lokal
     * ditulis. Himpunan lokal kosong tidak pernah menimpa berkas.
     * Mengembalikan pesan error, atau null jika berhasil.
     */
    private suspend fun syncA2fAuto(treeUri: String?): String? = withContext(Dispatchers.IO) {
        // Selalu gabungkan dulu: akun yang ada di berkas tapi belum ada di HP tidak boleh hilang saat ditulis ulang.
        run {
            when (val read = storage.readBackup(BACKUP_FILE_A2F_AUTO, treeUri)) {
                is BackupReadResult.NeedsFolderAccess -> return@withContext read.reason
                is BackupReadResult.Error -> return@withContext read.message
                is BackupReadResult.NotFound -> Unit
                is BackupReadResult.Found -> {
                    val payload = try {
                        BackupCodec.decode(read.file.content)
                    } catch (_: Exception) {
                        return@withContext corruptFileMessage(BACKUP_FILE_A2F_AUTO)
                    }
                    settingsRepository.addMissingA2fAccounts(A2fBackup.entriesFromPayloadJson(payload))
                }
            }
        }
        val localA2f = settingsRepository.getCurrentDictionary()
        if (localA2f.none { DictionaryEngine.isA2fEntry(it) && !it.deleted }) return@withContext null
        val encoded = BackupCodec.encode(A2fBackup.payloadJson(localA2f))
        when (val write = storage.writeBackup(BACKUP_FILE_A2F_AUTO, encoded, treeUri)) {
            is BackupWriteResult.Success -> null
            is BackupWriteResult.NeedsFolderAccess -> write.reason
            is BackupWriteResult.Error -> write.message
        }
    }

    /**
     * Dipanggil seketika setiap kali ada perubahan kamus atau folder di dalam aplikasi
     * agar berkas cadangan di perangkat langsung ter-replace bersih
     * (folder yang dihapus/diubah nama tidak pernah kembali lagi).
     *
     * Pengaman anti-timpa: jika aplikasi belum pernah sinkron sama sekali (lastSyncedAtMillis == 0L)
     * dan di perangkat sudah ada berkas dengan entri aktif LEBIH BANYAK daripada lokal,
     * penimpaan buta diblokir dan entri perangkat diselamatkan terlebih dahulu lewat penggabungan aman.
     */
    suspend fun pushLocalToDevice(nowMillis: Long = System.currentTimeMillis()): DeviceSyncResult = syncMutex.withLock {
        val result: DeviceSyncResult = withContext(Dispatchers.IO) {
            val localEntries = settingsRepository.getCurrentDictionary()
            val localFolders = settingsRepository.getCurrentFolders()
            val syncState = settingsRepository.getCurrentSyncLocalState()
            val treeUri = syncState.deviceBackupTreeUri.takeIf { it.isNotBlank() }
            val localActive = localEntries.filter { !it.deleted }

            // Pagar pengaman instal baru: jangan pernah menimpa berkas perangkat yang punya entri lebih banyak
            // ketika aplikasi belum pernah menyelesaikan sinkronisasi awal (lastSyncedAtMillis == 0L).
            if (syncState.lastSyncedAtMillis == 0L) {
                when (val existingRead = readAll(treeUri)) {
                    is BackupReadResult.NeedsFolderAccess -> {
                        settingsRepository.updateSyncState(
                            lastSyncedAtMillis = syncState.lastSyncedAtMillis,
                            message = "Perlu izin folder ${formatBackupShortFolder(treeUri)}",
                            pendingChanges = true
                        )
                        return@withContext DeviceSyncResult.NeedsFolderAccess(existingRead.reason)
                    }

                    is BackupReadResult.Found -> {
                        val decoded = settingsRepository.decodeDictionaryBackupJson(existingRead.file.content)
                        if (decoded != null) {
                            val fileActive = decoded.entries.filter { !it.deleted }
                            if (fileActive.size > localActive.size) {
                                val mergedEntries = mergePreservingActiveLocal(localEntries, decoded.entries)
                                val mergedFolders = SettingsRepository.normalizeOrderedFolders(
                                    explicitFolders = localFolders,
                                    entries = mergedEntries
                                )
                                settingsRepository.replaceDictionaryAndFolders(mergedEntries, mergedFolders)
                                val syncTs = maxOf(nowMillis, decoded.updatedAtMillis, existingRead.file.lastModifiedMillis)
                                val mergedJson = settingsRepository.encodeDictionaryBackupJson(
                                    entries = mergedEntries,
                                    updatedAtMillis = syncTs,
                                    folders = mergedFolders
                                )
                                val activeCount = mergedEntries.count { !it.deleted }
                                return@withContext commitWriteResult(
                                    writeResult = writeAll(mergedJson, treeUri),
                                    activeCount = activeCount,
                                    restoredFromFile = true,
                                    nowMillis = syncTs,
                                    previousSyncedAt = syncState.lastSyncedAtMillis,
                                    previousPending = true,
                                    customSuccessMessage = "Digabung aman (entri perangkat dipertahankan) • $activeCount kata"
                                )
                            }
                        }
                    }

                    is BackupReadResult.Error -> {
                        // Berkas tidak terbaca: jangan timpa.
                        settingsRepository.updateSyncState(
                            lastSyncedAtMillis = syncState.lastSyncedAtMillis,
                            message = existingRead.message,
                            pendingChanges = true
                        )
                        return@withContext DeviceSyncResult.Error(existingRead.message)
                    }

                    is BackupReadResult.NotFound -> Unit
                }
            }

            // Kondisi normal (sudah pernah sinkron atau lokal >= perangkat): langsung replace berkas perangkat
            val writeTs = maxOf(nowMillis, syncState.lastSyncedAtMillis + 1L)
            val json = settingsRepository.encodeDictionaryBackupJson(
                entries = localEntries,
                updatedAtMillis = writeTs,
                folders = localFolders
            )
            return@withContext commitWriteResult(
                writeResult = writeAll(json, treeUri),
                activeCount = localActive.size,
                restoredFromFile = false,
                nowMillis = writeTs,
                previousSyncedAt = syncState.lastSyncedAtMillis,
                previousPending = true
            )
        }
        afterDictionaryWrite(result)
    }

    /**
     * Gabungkan: hanya menambah id yang belum ada di sisi lain. Entri dengan id sama tetap pakai versi masing-masing;
     * tidak ada penimpaan dan tidak ada penghapusan. Kedua sisi berakhir dengan himpunan id yang sama.
     */
    suspend fun gabungkan(nowMillis: Long = System.currentTimeMillis()): DeviceSyncResult = syncMutex.withLock {
        val result: DeviceSyncResult = withContext(Dispatchers.IO) {
            val localEntries = settingsRepository.getCurrentDictionary()
            val localFolders = settingsRepository.getCurrentFolders()
            val syncState = settingsRepository.getCurrentSyncLocalState()
            val treeUri = syncState.deviceBackupTreeUri.takeIf { it.isNotBlank() }
            when (val read = readAll(treeUri)) {
                is BackupReadResult.NeedsFolderAccess -> {
                    settingsRepository.updateSyncState(
                        lastSyncedAtMillis = syncState.lastSyncedAtMillis,
                        message = "Perlu izin folder ${formatBackupShortFolder(treeUri)}",
                        pendingChanges = syncState.pendingChanges
                    )
                    DeviceSyncResult.NeedsFolderAccess(read.reason)
                }
                is BackupReadResult.Error -> {
                    settingsRepository.updateSyncState(
                        lastSyncedAtMillis = syncState.lastSyncedAtMillis,
                        message = read.message,
                        pendingChanges = syncState.pendingChanges
                    )
                    DeviceSyncResult.Error(read.message)
                }
                is BackupReadResult.NotFound -> {
                    // Belum ada berkas di perangkat: tidak ada yang bisa digabung, tulis data aplikasi sebagai awal.
                    val json = settingsRepository.encodeDictionaryBackupJson(
                        entries = localEntries,
                        updatedAtMillis = nowMillis,
                        folders = localFolders
                    )
                    val activeCount = localEntries.count { !it.deleted }
                    commitWriteResult(
                        writeResult = writeAll(json, treeUri),
                        activeCount = activeCount,
                        restoredFromFile = false,
                        nowMillis = nowMillis,
                        previousSyncedAt = syncState.lastSyncedAtMillis,
                        previousPending = syncState.pendingChanges
                    )
                }
                is BackupReadResult.Found -> {
                    val decoded = settingsRepository.decodeDictionaryBackupJson(read.file.content)
                        ?: return@withContext DeviceSyncResult.Error("Cadangan di perangkat tidak terbaca")
                    val localIds = localEntries.map { it.id }.toSet()
                    val deviceIds = decoded.entries.map { it.id }.toSet()
                    // Sisi aplikasi: tambah entri perangkat yang id-nya belum ada.
                    val mergedLocal = localEntries + decoded.entries.filter { it.id !in localIds }
                    // Sisi perangkat: tambah entri aplikasi yang id-nya belum ada.
                    val mergedFile = decoded.entries + localEntries.filter { it.id !in deviceIds }
                    val mergedFolders = SettingsRepository.normalizeOrderedFolders(
                        explicitFolders = localFolders + decoded.folders,
                        entries = mergedLocal + mergedFile
                    )
                    settingsRepository.replaceDictionaryAndFolders(mergedLocal, mergedFolders)
                    val json = settingsRepository.encodeDictionaryBackupJson(
                        entries = mergedFile,
                        updatedAtMillis = nowMillis,
                        folders = mergedFolders
                    )
                    val activeCount = mergedLocal.count { !it.deleted }
                    commitWriteResult(
                        writeResult = writeAll(json, treeUri),
                        activeCount = activeCount,
                        restoredFromFile = true,
                        nowMillis = nowMillis,
                        previousSyncedAt = syncState.lastSyncedAtMillis,
                        previousPending = syncState.pendingChanges
                    )
                }
            }
        }
        afterDictionaryWrite(result)
    }

    suspend fun syncNow(nowMillis: Long = System.currentTimeMillis()): DeviceSyncResult = syncMutex.withLock {
        val result: DeviceSyncResult = withContext(Dispatchers.IO) {
            val localEntries = settingsRepository.getCurrentDictionary()
            val localFolders = settingsRepository.getCurrentFolders()
            val syncState = settingsRepository.getCurrentSyncLocalState()
            val treeUri = syncState.deviceBackupTreeUri.takeIf { it.isNotBlank() }

            val localActive = localEntries.filter { !it.deleted }
            val localCustomFolders = localFolders.filterNot { SettingsRepository.isPrimaryFolder(it) }
            val localIsEmpty = localEntries.isEmpty() && localCustomFolders.isEmpty() && !syncState.pendingChanges

            when (val readResult = readAll(treeUri)) {
                is BackupReadResult.NeedsFolderAccess -> {
                    settingsRepository.updateSyncState(
                        lastSyncedAtMillis = syncState.lastSyncedAtMillis,
                        message = "Perlu izin folder ${formatBackupShortFolder(treeUri)}",
                        pendingChanges = syncState.pendingChanges
                    )
                    return@withContext DeviceSyncResult.NeedsFolderAccess(readResult.reason)
                }

                is BackupReadResult.Error -> {
                    settingsRepository.updateSyncState(
                        lastSyncedAtMillis = syncState.lastSyncedAtMillis,
                        message = readResult.message,
                        pendingChanges = syncState.pendingChanges
                    )
                    return@withContext DeviceSyncResult.Error(readResult.message)
                }

                is BackupReadResult.NotFound -> {
                    val json = settingsRepository.encodeDictionaryBackupJson(
                        entries = localEntries,
                        updatedAtMillis = nowMillis,
                        folders = localFolders
                    )
                    return@withContext commitWriteResult(
                        writeResult = writeAll(json, treeUri),
                        activeCount = localActive.size,
                        restoredFromFile = false,
                        nowMillis = nowMillis,
                        previousSyncedAt = syncState.lastSyncedAtMillis,
                        previousPending = syncState.pendingChanges
                    )
                }

                is BackupReadResult.Found -> {
                    val decoded = settingsRepository.decodeDictionaryBackupJson(readResult.file.content)
                    if (decoded == null) {
                        // File exists but is empty/invalid JSON -> replace cleanly with current local dictionary
                        val json = settingsRepository.encodeDictionaryBackupJson(
                            entries = localEntries,
                            updatedAtMillis = nowMillis,
                            folders = localFolders
                        )
                        return@withContext commitWriteResult(
                            writeResult = writeAll(json, treeUri),
                            activeCount = localActive.size,
                            restoredFromFile = false,
                            nowMillis = nowMillis,
                            previousSyncedAt = syncState.lastSyncedAtMillis,
                            previousPending = syncState.pendingChanges
                        )
                    }

                    val fileEntries = decoded.entries
                    val fileFolders = decoded.folders
                    val fileActive = fileEntries.filter { !it.deleted }
                    val fileCustomFolders = fileFolders.filterNot { SettingsRepository.isPrimaryFolder(it) }
                    val fileHasData = fileActive.isNotEmpty() || fileCustomFolders.isNotEmpty()
                    val fileEffectiveTs = maxOf(decoded.updatedAtMillis, readResult.file.lastModifiedMillis)

                    // Case 0: Pengaman berkas kosong — berkas perangkat kosong padahal kamus lokal punya entri aktif ->
                    // tolak menimpa lokal dan pulihkan berkas perangkat dari lokal.
                    if (fileActive.isEmpty() && localActive.isNotEmpty()) {
                        val safeTs = maxOf(nowMillis, fileEffectiveTs + 1L)
                        val json = settingsRepository.encodeDictionaryBackupJson(
                            entries = localEntries,
                            updatedAtMillis = safeTs,
                            folders = localFolders
                        )
                        return@withContext commitWriteResult(
                            writeResult = writeAll(json, treeUri),
                            activeCount = localActive.size,
                            restoredFromFile = false,
                            nowMillis = safeTs,
                            previousSyncedAt = syncState.lastSyncedAtMillis,
                            previousPending = syncState.pendingChanges,
                            customSuccessMessage = "Dilindungi dari berkas kosong • ${localActive.size} kata"
                        )
                    }

                    // Case 1: Local dictionary is empty (e.g. fresh install) & backup file has data -> restore from file
                    if (localIsEmpty && fileHasData) {
                        val (appliedEntries, appliedFolders) = settingsRepository.replaceDictionaryAndFolders(
                            entries = fileEntries,
                            folders = fileFolders
                        )
                        val normalizedTs = maxOf(nowMillis, fileEffectiveTs)
                        val normalizedJson = settingsRepository.encodeDictionaryBackupJson(
                            entries = appliedEntries,
                            updatedAtMillis = normalizedTs,
                            folders = appliedFolders
                        )
                        val needsRewrite = !isDictionaryContentEquivalent(
                            leftEntries = fileEntries,
                            leftFolders = fileFolders,
                            rightEntries = appliedEntries,
                            rightFolders = appliedFolders
                        )
                        if (needsRewrite) {
                            return@withContext commitWriteResult(
                                writeResult = writeAll(normalizedJson, treeUri),
                                activeCount = appliedEntries.count { !it.deleted },
                                restoredFromFile = true,
                                nowMillis = normalizedTs,
                                previousSyncedAt = syncState.lastSyncedAtMillis,
                                previousPending = false
                            )
                        }
                        val activeCount = appliedEntries.count { !it.deleted }
                        val msg = "Dipulihkan otomatis • $activeCount kata"
                        settingsRepository.updateSyncState(
                            lastSyncedAtMillis = normalizedTs,
                            message = msg,
                            pendingChanges = false
                        )
                        return@withContext DeviceSyncResult.Synced(
                            activeEntryCount = activeCount,
                            restoredFromFile = true,
                            wroteToFile = false,
                            syncedAtMillis = normalizedTs,
                            message = msg
                        )
                    }

                    // Case 2: External file was updated after last sync and user has NO pending local edits
                    val externalFileIsNewer = syncState.lastSyncedAtMillis > 0L &&
                        fileEffectiveTs > syncState.lastSyncedAtMillis &&
                        !syncState.pendingChanges
                    if (externalFileIsNewer) {
                        // Pengaman entri terbanyak: jika berkas eksternal punya entri aktif LEBIH SEDIKIT daripada lokal
                        // (misalnya JSON hasil edit AI terpotong), JANGAN replace penuh lokal — gabungkan secara aman
                        // agar seluruh entri aktif lokal tetap selamat dan entri baru dari file ikut masuk.
                        if (fileActive.size < localActive.size) {
                            val mergedEntries = mergePreservingActiveLocal(localEntries, fileEntries)
                            val mergedFolders = SettingsRepository.normalizeOrderedFolders(
                                explicitFolders = localFolders,
                                entries = mergedEntries
                            )
                            val (appliedEntries, appliedFolders) = settingsRepository.replaceDictionaryAndFolders(
                                entries = mergedEntries,
                                folders = mergedFolders
                            )
                            val safeTs = maxOf(nowMillis, fileEffectiveTs + 1L)
                            val mergedJson = settingsRepository.encodeDictionaryBackupJson(
                                entries = appliedEntries,
                                updatedAtMillis = safeTs,
                                folders = appliedFolders
                            )
                            val activeCount = appliedEntries.count { !it.deleted }
                            val localGotNewData = !isDictionaryContentEquivalent(
                                leftEntries = localEntries,
                                leftFolders = localFolders,
                                rightEntries = appliedEntries,
                                rightFolders = appliedFolders
                            )
                            return@withContext commitWriteResult(
                                writeResult = writeAll(mergedJson, treeUri),
                                activeCount = activeCount,
                                restoredFromFile = localGotNewData,
                                nowMillis = safeTs,
                                previousSyncedAt = syncState.lastSyncedAtMillis,
                                previousPending = false,
                                customSuccessMessage = "Digabung aman (entri lokal dipertahankan) • $activeCount kata"
                            )
                        }

                        // Berkas eksternal lebih baru dan jumlah entri aktif >= lokal -> pulihkan penuh (edit AI normal)
                        val (appliedEntries, appliedFolders) = settingsRepository.replaceDictionaryAndFolders(
                            entries = fileEntries,
                            folders = fileFolders
                        )
                        val normalizedTs = maxOf(nowMillis, fileEffectiveTs)
                        val normalizedJson = settingsRepository.encodeDictionaryBackupJson(
                            entries = appliedEntries,
                            updatedAtMillis = normalizedTs,
                            folders = appliedFolders
                        )
                        val needsRewrite = !isDictionaryContentEquivalent(
                            leftEntries = fileEntries,
                            leftFolders = fileFolders,
                            rightEntries = appliedEntries,
                            rightFolders = appliedFolders
                        )
                        if (needsRewrite) {
                            return@withContext commitWriteResult(
                                writeResult = writeAll(normalizedJson, treeUri),
                                activeCount = appliedEntries.count { !it.deleted },
                                restoredFromFile = true,
                                nowMillis = normalizedTs,
                                previousSyncedAt = syncState.lastSyncedAtMillis,
                                previousPending = false
                            )
                        }
                        val activeCount = appliedEntries.count { !it.deleted }
                        val msg = "Dipulihkan dari perangkat • $activeCount kata"
                        settingsRepository.updateSyncState(
                            lastSyncedAtMillis = normalizedTs,
                            message = msg,
                            pendingChanges = false
                        )
                        return@withContext DeviceSyncResult.Synced(
                            activeEntryCount = activeCount,
                            restoredFromFile = true,
                            wroteToFile = false,
                            syncedAtMillis = normalizedTs,
                            message = msg
                        )
                    }

                    // Case 3A: Ada perubahan lokal tertunda pada aplikasi yang sudah pernah sinkron
                    // dan berkas perangkat TIDAK berubah lebih baru -> kondisi lokal otoritatif,
                    // langsung replace perangkat tanpa menghidupkan kembali folder yang dihapus/diubah nama.
                    val localPendingAuthoritative = syncState.pendingChanges &&
                        syncState.lastSyncedAtMillis > 0L &&
                        fileEffectiveTs <= syncState.lastSyncedAtMillis
                    if (localPendingAuthoritative) {
                        val writeTs = maxOf(nowMillis, fileEffectiveTs + 1L)
                        val localJson = settingsRepository.encodeDictionaryBackupJson(
                            entries = localEntries,
                            updatedAtMillis = writeTs,
                            folders = localFolders
                        )
                        return@withContext commitWriteResult(
                            writeResult = writeAll(localJson, treeUri),
                            activeCount = localActive.size,
                            restoredFromFile = false,
                            nowMillis = writeTs,
                            previousSyncedAt = syncState.lastSyncedAtMillis,
                            previousPending = true
                        )
                    }

                    // Case 3B: Sinkronisasi awal dengan data di kedua sisi (lastSyncedAtMillis == 0L)
                    // atau konflik dua sisi bersamaan -> gabungkan entri (lindungi entri terbanyak),
                    // dan JANGAN lakukan union buta fileFolders bila lokal punya pendingChanges agar folder terhapus tidak kembali.
                    val mergedEntries = if (syncState.lastSyncedAtMillis == 0L && fileActive.size > localActive.size) {
                        mergePreservingActiveLocal(localEntries, fileEntries)
                    } else {
                        SettingsRepository.mergeDictionaryLists(localEntries, fileEntries)
                    }
                    val baseExplicitFolders = if (syncState.pendingChanges) {
                        localFolders
                    } else {
                        localFolders + fileFolders
                    }
                    val mergedFolders = SettingsRepository.normalizeOrderedFolders(
                        explicitFolders = baseExplicitFolders,
                        entries = mergedEntries
                    )
                    val localChanged = !isDictionaryContentEquivalent(
                        leftEntries = localEntries,
                        leftFolders = localFolders,
                        rightEntries = mergedEntries,
                        rightFolders = mergedFolders
                    )
                    if (localChanged) {
                        settingsRepository.replaceDictionaryAndFolders(mergedEntries, mergedFolders)
                    }

                    val fileChanged = !isDictionaryContentEquivalent(
                        leftEntries = fileEntries,
                        leftFolders = fileFolders,
                        rightEntries = mergedEntries,
                        rightFolders = mergedFolders
                    )
                    val syncTs = maxOf(nowMillis, fileEffectiveTs)
                    val activeCount = mergedEntries.count { !it.deleted }

                    if (fileChanged || syncState.pendingChanges || syncState.lastSyncedAtMillis == 0L) {
                        val mergedJson = settingsRepository.encodeDictionaryBackupJson(
                            entries = mergedEntries,
                            updatedAtMillis = syncTs,
                            folders = mergedFolders
                        )
                        return@withContext commitWriteResult(
                            writeResult = writeAll(mergedJson, treeUri),
                            activeCount = activeCount,
                            restoredFromFile = localChanged,
                            nowMillis = syncTs,
                            previousSyncedAt = syncState.lastSyncedAtMillis,
                            previousPending = syncState.pendingChanges
                        )
                    }

                    val msg = "Tersinkronisasi • $activeCount kata"
                    settingsRepository.updateSyncState(
                        lastSyncedAtMillis = syncTs,
                        message = msg,
                        pendingChanges = false
                    )
                    return@withContext DeviceSyncResult.Synced(
                        activeEntryCount = activeCount,
                        restoredFromFile = false,
                        wroteToFile = false,
                        syncedAtMillis = syncTs,
                        message = msg
                    )
                }
            }
        }
        afterDictionaryWrite(result)
    }

    /**
     * Membaca berkas tiap jenis (`.json`, isi Base64) dan menggabungkannya menjadi satu envelope JSON.
     * Berkas `.vx2` lama dan amplop terenkripsi lama tidak dibaca: dilewati, tidak dimigrasi.
     */
    private fun readAll(treeUri: String?): BackupReadResult {
        val entries = mutableListOf<PersonalDictionaryEntry>()
        val folders = mutableListOf<String>()
        var updatedAt = 0L
        var lastModified = 0L
        var foundAny = false
        for (kind in DeviceDictionaryBackupManager.BACKUP_KINDS) {
            // A2F is restored only via the explicit password import, never by auto-sync.
            if (kind == "aut") continue
            val fileName = DeviceDictionaryBackupManager.backupFileNameFor(kind)
            when (val read = storage.readBackup(fileName, treeUri)) {
                is BackupReadResult.NeedsFolderAccess -> return read
                is BackupReadResult.Error -> return read
                is BackupReadResult.NotFound -> Unit
                is BackupReadResult.Found -> {
                    foundAny = true
                    lastModified = maxOf(lastModified, read.file.lastModifiedMillis)
                    val plain = try {
                        BackupCodec.decode(read.file.content)
                    } catch (_: Exception) {
                        // Isi berkas rusak: sebutkan nama berkasnya agar bisa dihapus satu per satu.
                        return BackupReadResult.Error(corruptFileMessage(fileName))
                    }
                    if (BackupCodec.isLegacyEncryptedEnvelope(plain)) continue
                    val decoded = settingsRepository.decodeDictionaryBackupJson(plain) ?: continue
                    entries += decoded.entries.filterNot { DictionaryEngine.isA2fEntry(it) }
                    folders += decoded.folders
                    updatedAt = maxOf(updatedAt, decoded.updatedAtMillis)
                }
            }
        }
        if (!foundAny) return BackupReadResult.NotFound
        val byId = LinkedHashMap<String, PersonalDictionaryEntry>()
        for (entry in entries) {
            val existing = byId[entry.id]
            if (existing == null || entry.updatedAtMillis >= existing.updatedAtMillis) byId[entry.id] = entry
        }
        val content = settingsRepository.encodeDictionaryBackupJson(
            entries = byId.values.toList(),
            updatedAtMillis = updatedAt,
            folders = folders.distinct()
        )
        return BackupReadResult.Found(StoredBackupFile(content, lastModified))
    }

    /** Pesan untuk satu berkas perangkat yang rusak. Penghapusan berkas itu saja aman: berkas dibuat ulang dari data aplikasi. */
    private fun corruptFileMessage(fileName: String): String =
        "Berkas $fileName tidak valid (isinya rusak). Hapus berkas $fileName saja dari folder cadangan. " +
            "Aplikasi akan membuatnya ulang otomatis dari data di aplikasi, dan tidak ada entri yang terhapus."

    /**
     * Menulis berkas tiap jenis dari satu envelope JSON. Setiap berkas ditulis sebagai satu baris Base64.
     * Jenis tanpa isi juga ditulis (isinya `[]` yang di-Base64).
     */
    private fun writeAll(json: String, treeUri: String?): BackupWriteResult {
        val incoming = settingsRepository.decodeDictionaryBackupJson(json)
            ?: return BackupWriteResult.Error("Gagal menyiapkan data cadangan.")
        // Gabungkan dulu dengan isi berkas yang sudah ada: entri tidak boleh hilang karena penulisan berikutnya.
        // Jika berkas tidak bisa dibaca, jangan menulis sama sekali.
        val existing = when (val current = readAll(treeUri)) {
            is BackupReadResult.Found -> settingsRepository.decodeDictionaryBackupJson(current.file.content)
                ?: return BackupWriteResult.Error("Berkas cadangan tidak terbaca; tidak ada yang ditulis.")
            is BackupReadResult.NotFound -> null
            is BackupReadResult.NeedsFolderAccess -> return BackupWriteResult.NeedsFolderAccess(current.reason)
            is BackupReadResult.Error -> return BackupWriteResult.Error(current.message)
        }
        val decoded = if (existing == null) incoming else incoming.copy(
            // Folder mengikuti keputusan lokal (folder yang dihapus tidak kembali); entri digabung per id.
            entries = unionByIdNewestWins(incoming.entries, existing.entries),
            updatedAtMillis = maxOf(incoming.updatedAtMillis, existing.updatedAtMillis)
        )
        var latest = 0L
        for (kind in DeviceDictionaryBackupManager.BACKUP_KINDS) {
            // Never write device-bound seeds or overwrite an existing portable A2F backup.
            if (kind == "aut") continue
            val kindEntries = decoded.entries.filter { SettingsRepository.backupKindFor(it.folder) == kind }
            val kindFolders = decoded.folders.filter { SettingsRepository.backupKindFor(it) == kind }
            val kindJson = if (kindEntries.isEmpty() && kindFolders.isEmpty()) {
                "[]"
            } else {
                settingsRepository.encodeDictionaryBackupJson(
                    entries = kindEntries,
                    updatedAtMillis = decoded.updatedAtMillis,
                    folders = kindFolders
                )
            }
            val fileName = DeviceDictionaryBackupManager.backupFileNameFor(kind)
            // Aturan standar 06 §2: isi sama dengan berkas di perangkat -> dilewati (tidak ditulis ulang).
            if (isKindUnchanged(fileName, kindEntries, kindFolders, treeUri)) continue
            val payload = BackupCodec.encode(kindJson)
            when (val result = storage.writeBackup(fileName, payload, treeUri)) {
                is BackupWriteResult.Success -> {
                    latest = maxOf(latest, result.writtenAtMillis)
                }
                is BackupWriteResult.NeedsFolderAccess -> return result
                is BackupWriteResult.Error -> return result
            }
        }
        return BackupWriteResult.Success(maxOf(latest, System.currentTimeMillis()))
    }

    /** True jika berkas ini sudah berisi data yang sama persis (entri dan folder). */
    private fun isKindUnchanged(
        fileName: String,
        entries: List<PersonalDictionaryEntry>,
        folders: List<String>,
        treeUri: String?
    ): Boolean {
        val read = storage.readBackup(fileName, treeUri) as? BackupReadResult.Found ?: return false
        val plain = try {
            BackupCodec.decode(read.file.content)
        } catch (_: Exception) {
            return false
        }
        val current = settingsRepository.decodeDictionaryBackupJson(plain)
        return isDictionaryContentEquivalent(
            leftEntries = current?.entries.orEmpty(),
            leftFolders = current?.folders.orEmpty(),
            rightEntries = entries,
            rightFolders = folders
        )
    }

    /**
     * Menggabungkan entri lokal dan eksternal berdasarkan updatedAtMillis terbaru per id.
     * Entri yang hilang dari berkas eksternal tidak dihitung sebagai hapus; tombstone menang kalau lebih baru.
     */
    private fun mergePreservingActiveLocal(
        local: List<PersonalDictionaryEntry>,
        remote: List<PersonalDictionaryEntry>
    ): List<PersonalDictionaryEntry> {
        val byId = linkedMapOf<String, PersonalDictionaryEntry>()
        for (item in local) {
            byId[item.id] = item
        }
        for (remoteItem in remote) {
            val existing = byId[remoteItem.id]
            // Penanda hapus (tombstone, termasuk hapus permanen) ikut menang kalau lebih baru.
            // Entri yang lebih lama tidak pernah menimpa entri lokal yang lebih baru.
            if (existing == null || remoteItem.updatedAtMillis >= existing.updatedAtMillis) {
                byId[remoteItem.id] = remoteItem
            }
        }
        return byId.values.toList()
    }

    private suspend fun commitWriteResult(
        writeResult: BackupWriteResult,
        activeCount: Int,
        restoredFromFile: Boolean,
        nowMillis: Long,
        previousSyncedAt: Long,
        previousPending: Boolean,
        customSuccessMessage: String? = null
    ): DeviceSyncResult {
        return when (writeResult) {
            is BackupWriteResult.Success -> {
                val effectiveTs = maxOf(nowMillis, writeResult.writtenAtMillis)
                val msg = customSuccessMessage ?: if (restoredFromFile) {
                    "Disinkronkan dua arah • $activeCount kata"
                } else {
                    "Tersinkronisasi • $activeCount kata"
                }
                settingsRepository.updateSyncState(
                    lastSyncedAtMillis = effectiveTs,
                    message = msg,
                    pendingChanges = false
                )
                DeviceSyncResult.Synced(
                    activeEntryCount = activeCount,
                    restoredFromFile = restoredFromFile,
                    wroteToFile = true,
                    syncedAtMillis = effectiveTs,
                    message = msg
                )
            }

            is BackupWriteResult.NeedsFolderAccess -> {
                settingsRepository.updateSyncState(
                    lastSyncedAtMillis = previousSyncedAt,
                    message = "Perlu izin folder Documents",
                    pendingChanges = previousPending
                )
                DeviceSyncResult.NeedsFolderAccess(writeResult.reason)
            }

            is BackupWriteResult.Error -> {
                settingsRepository.updateSyncState(
                    lastSyncedAtMillis = previousSyncedAt,
                    message = writeResult.message,
                    pendingChanges = previousPending
                )
                DeviceSyncResult.Error(writeResult.message)
            }
        }
    }

    private fun isDictionaryContentEquivalent(
        leftEntries: List<PersonalDictionaryEntry>,
        leftFolders: List<String>,
        rightEntries: List<PersonalDictionaryEntry>,
        rightFolders: List<String>
    ): Boolean {
        val normLeftFolders = SettingsRepository.normalizeOrderedFolders(leftFolders, leftEntries)
        val normRightFolders = SettingsRepository.normalizeOrderedFolders(rightFolders, rightEntries)
        if (normLeftFolders != normRightFolders) return false
        if (leftEntries.size != rightEntries.size) return false

        val leftMap = leftEntries.associateBy { it.id }
        val rightMap = rightEntries.associateBy { it.id }
        if (leftMap.keys != rightMap.keys) return false

        return leftMap.all { (id, l) ->
            val r = rightMap[id] ?: return@all false
            l.word == r.word &&
                l.shortcut == r.shortcut &&
                SettingsRepository.normalizeFolderPath(l.folder) == SettingsRepository.normalizeFolderPath(r.folder) &&
                l.deleted == r.deleted
        }
    }

    companion object {
        const val BACKUP_RELATIVE_PATH = "Documents"
        const val KIND_PW = "pw"
        const val KIND_KRED = "kred"
        /** Nama berkas cadangan per jenis. Isi berkas: JSON dalam Base64 satu baris. */
        const val BACKUP_FILE_UMUM = "umum.json"
        const val BACKUP_FILE_KATA_SANDI = "pw.json"
        const val BACKUP_FILE_KREDENSIAL = "kred.json"
        const val BACKUP_FILE_A2F = "aut.json"
        /** Cadangan otomatis A2F (Base64, bukan enkripsi). Berkas `aut.json` tetap hanya untuk impor manual. */
        const val BACKUP_FILE_A2F_AUTO = "aut_otomatis.json"
        const val BACKUP_DISPLAY_PATH = "/storage/emulated/0/Documents"
        val BACKUP_KINDS = listOf("umum", "pw", "kred", "aut")

        /** Nama berkas satu jenis. */
        fun backupFileNameFor(kind: String): String = when (kind) {
            KIND_PW -> BACKUP_FILE_KATA_SANDI
            KIND_KRED -> BACKUP_FILE_KREDENSIAL
            "aut" -> BACKUP_FILE_A2F
            else -> BACKUP_FILE_UMUM
        }
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /** Lazy agar `Uri.parse` tidak dipanggil saat companion diinisialisasi (tes JVM biasa tanpa Android). */
        val INITIAL_DOCUMENTS_URI: Uri by lazy {
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")
        }

        /**
         * Mengekstrak jalur folder relatif di dalam `/storage/emulated/0/Documents/` dari SAF tree URI.
         * Mengembalikan `"Documents"` untuk root Documents atau `"Documents/Subfolder/..."` untuk subfolder,
         * atau `null` jika URI berada di luar `/storage/emulated/0/Documents/`.
         */
        fun extractDocumentsRelativeFolder(treeUriString: String?): String? {
            val raw = treeUriString?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
            if (!EXTERNAL_STORAGE_AUTHORITY.equals(uri.authority, ignoreCase = true)) return null

            val rawDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    val segments = uri.pathSegments
                    val treeIndex = segments.indexOf("tree")
                    if (treeIndex >= 0 && treeIndex + 1 < segments.size) {
                        segments[treeIndex + 1]
                    } else {
                        null
                    }
                }
                ?: return null

            val decodedId = Uri.decode(rawDocId).trim()
            if (!decodedId.startsWith("primary:", ignoreCase = true)) return null
            val relativeRaw = decodedId.substringAfter(':').trim('/')
            if (relativeRaw.isEmpty()) return null

            val isRootDocs = relativeRaw.equals("Documents", ignoreCase = true)
            val isSubfolderOfDocs = relativeRaw.startsWith("Documents/", ignoreCase = true)
            if (!isRootDocs && !isSubfolderOfDocs) return null

            val normalizedSegments = relativeRaw.split('/').map { it.trim() }
            if (normalizedSegments.any { it.isEmpty() || it == "." || it == ".." }) return null

            return if (normalizedSegments.size == 1) {
                BACKUP_RELATIVE_PATH
            } else {
                "$BACKUP_RELATIVE_PATH/${normalizedSegments.drop(1).joinToString("/")}"
            }
        }

        fun isValidDocumentsTreeUri(treeUriString: String?): Boolean =
            extractDocumentsRelativeFolder(treeUriString) != null

        fun formatBackupShortFolder(treeUriString: String?): String =
            extractDocumentsRelativeFolder(treeUriString) ?: BACKUP_RELATIVE_PATH

        fun formatBackupDisplayPath(treeUriString: String?): String =
            "/storage/emulated/0/${formatBackupShortFolder(treeUriString)}"
    }
}
