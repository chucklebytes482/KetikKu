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
    fun readBackup(persistedTreeUri: String?): BackupReadResult
    fun writeBackup(json: String, persistedTreeUri: String?): BackupWriteResult
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

    val backupFile: File
        get() = File(targetDirectory, DeviceDictionaryBackupManager.BACKUP_FILE_NAME)

    override fun readBackup(persistedTreeUri: String?): BackupReadResult {
        return runCatching {
            val file = backupFile
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

    override fun writeBackup(json: String, persistedTreeUri: String?): BackupWriteResult {
        return runCatching {
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs()
            }
            val file = backupFile
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

    private fun legacyPublicBackupFile(): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(docs, DeviceDictionaryBackupManager.BACKUP_FILE_NAME)
    }

    override fun readBackup(persistedTreeUri: String?): BackupReadResult {
        // Tier 1: Persisted SAF folder URI (bebas memilih jalur di dalam Documents/)
        if (!persistedTreeUri.isNullOrBlank()) {
            val safResult = readViaSafTree(Uri.parse(persistedTreeUri))
            if (safResult != null) return safResult
        }

        // Tier 2: Android 10+ (API 29+) MediaStore.Files langsung di Documents/ (tanpa folder otomatis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return readViaMediaStore()
        }

        // Tier 3: Legacy Android 7-9 (API 24-28) langsung di Documents/ketikku_dictionary.json
        return readViaLegacyFile()
    }

    override fun writeBackup(json: String, persistedTreeUri: String?): BackupWriteResult {
        // Tier 1: Persisted SAF folder URI -> always overwrites the single target file in-place
        if (!persistedTreeUri.isNullOrBlank()) {
            val safResult = writeViaSafTree(Uri.parse(persistedTreeUri), json)
            if (safResult != null) return safResult
        }

        // Tier 2: Android 10+ (API 29+) MediaStore.Files langsung di Documents/ (strict in-place replace)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeViaMediaStore(json)
        }

        // Tier 3: Legacy Android 7-9 (API 24-28) direct file overwrite
        return writeViaLegacyFile(json)
    }

    private fun readViaSafTree(treeUri: Uri): BackupReadResult? {
        return runCatching {
            val matches = querySafMatchingFiles(treeUri)
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

    private fun writeViaSafTree(treeUri: Uri, json: String): BackupWriteResult? {
        return runCatching {
            val matches = querySafMatchingFiles(treeUri)
            val targetUri = if (matches.isNotEmpty()) {
                val primary = matches.maxByOrNull { it.lastModifiedMillis }!!.uri
                // Remove any stray duplicate entries with the same display name so strictly 1 file remains
                matches.filter { it.uri != primary }.forEach { dup ->
                    runCatching { DocumentsContract.deleteDocument(resolver, dup.uri) }
                }
                primary
            } else {
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                DocumentsContract.createDocument(
                    resolver,
                    parentDocUri,
                    "application/json",
                    DeviceDictionaryBackupManager.BACKUP_FILE_NAME
                ) ?: return BackupWriteResult.Error("Gagal membuat file ketikku_dictionary.json di folder terpilih.")
            }

            writeTextTruncating(targetUri, json)
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

    private fun querySafMatchingFiles(treeUri: Uri): List<SafFileMatch> {
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
                if (name.equals(DeviceDictionaryBackupManager.BACKUP_FILE_NAME, ignoreCase = true)) {
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

    private fun queryMediaStoreMatches(): List<MediaStoreMatch> {
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
        val selectionArgs = arrayOf(DeviceDictionaryBackupManager.BACKUP_FILE_NAME, relSlash, relNoSlash)

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

    private fun readViaMediaStore(): BackupReadResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return BackupReadResult.NotFound
        return runCatching {
            val matches = queryMediaStoreMatches()
            if (matches.isNotEmpty()) {
                val latest = matches.first()
                val text = resolver.openInputStream(latest.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: return BackupReadResult.NeedsFolderAccess(
                        "File cadangan lama ditemukan. Pilih folder di dalam Documents sekali untuk sinkronisasi penuh."
                    )
                return BackupReadResult.Found(StoredBackupFile(text, latest.lastModifiedMillis))
            }

            // Detect pre-existing file from a previous install or external editor that Scoped Storage hides from MediaStore
            val diskFile = legacyPublicBackupFile()
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
                        "File ketikku_dictionary.json ditemukan di Documents. Pilih folder di dalam Documents untuk memulihkan & menimpa file yang sama."
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

    private fun writeViaMediaStore(json: String): BackupWriteResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return writeViaLegacyFile(json)
        }
        return runCatching {
            val matches = queryMediaStoreMatches()
            if (matches.isNotEmpty()) {
                val primary = matches.first().uri
                // Remove any extra owned duplicates so strictly 1 file remains
                matches.drop(1).forEach { extra ->
                    runCatching { resolver.delete(extra.uri, null, null) }
                }
                writeTextTruncating(primary, json)
                return BackupWriteResult.Success(System.currentTimeMillis())
            }

            // Guard against MediaStore auto-renaming to "ketikku_dictionary (1).json" if a pre-existing foreign file exists on disk
            val diskFile = legacyPublicBackupFile()
            if (diskFile.exists()) {
                return if (diskFile.canWrite()) {
                    diskFile.writeText(json, Charsets.UTF_8)
                    BackupWriteResult.Success(diskFile.lastModified().coerceAtLeast(System.currentTimeMillis()))
                } else {
                    BackupWriteResult.NeedsFolderAccess(
                        "File ketikku_dictionary.json lama ada di Documents. Pilih folder di dalam Documents agar file diganti tanpa duplikat."
                    )
                }
            }

            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, DeviceDictionaryBackupManager.BACKUP_FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DeviceDictionaryBackupManager.BACKUP_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val insertedUri = resolver.insert(collection, values)
                ?: return BackupWriteResult.Error("Gagal membuat file di Documents.")
            try {
                writeTextTruncating(insertedUri, json)
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

    private fun readViaLegacyFile(): BackupReadResult {
        return runCatching {
            val file = legacyPublicBackupFile()
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

    private fun writeViaLegacyFile(json: String): BackupWriteResult {
        return runCatching {
            val file = legacyPublicBackupFile()
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

    private fun writeTextTruncating(uri: Uri, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
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

    /**
     * Dipanggil seketika setiap kali ada perubahan kamus atau folder di dalam aplikasi
     * agar berkas tunggal ketikku_dictionary.json di perangkat langsung ter-replace bersih
     * (folder yang dihapus/diubah nama tidak pernah kembali lagi).
     *
     * Pengaman anti-timpa: jika aplikasi belum pernah sinkron sama sekali (lastSyncedAtMillis == 0L)
     * dan di perangkat sudah ada berkas dengan entri aktif LEBIH BANYAK daripada lokal,
     * penimpaan buta diblokir dan entri perangkat diselamatkan terlebih dahulu lewat penggabungan aman.
     */
    suspend fun pushLocalToDevice(nowMillis: Long = System.currentTimeMillis()): DeviceSyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val localEntries = settingsRepository.getCurrentDictionary()
            val localFolders = settingsRepository.getCurrentFolders()
            val syncState = settingsRepository.getCurrentSyncLocalState()
            val treeUri = syncState.deviceBackupTreeUri.takeIf { it.isNotBlank() }
            val localActive = localEntries.filter { !it.deleted }

            // Pagar pengaman instal baru: jangan pernah menimpa berkas perangkat yang punya entri lebih banyak
            // ketika aplikasi belum pernah menyelesaikan sinkronisasi awal (lastSyncedAtMillis == 0L).
            if (syncState.lastSyncedAtMillis == 0L) {
                when (val existingRead = storage.readBackup(treeUri)) {
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
                                    writeResult = storage.writeBackup(mergedJson, treeUri),
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

                    is BackupReadResult.NotFound, is BackupReadResult.Error -> Unit
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
                writeResult = storage.writeBackup(json, treeUri),
                activeCount = localActive.size,
                restoredFromFile = false,
                nowMillis = writeTs,
                previousSyncedAt = syncState.lastSyncedAtMillis,
                previousPending = true
            )
        }
    }

    suspend fun syncNow(nowMillis: Long = System.currentTimeMillis()): DeviceSyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val localEntries = settingsRepository.getCurrentDictionary()
            val localFolders = settingsRepository.getCurrentFolders()
            val syncState = settingsRepository.getCurrentSyncLocalState()
            val treeUri = syncState.deviceBackupTreeUri.takeIf { it.isNotBlank() }

            val localActive = localEntries.filter { !it.deleted }
            val localCustomFolders = localFolders.filterNot { SettingsRepository.isPrimaryFolder(it) }
            val localIsEmpty = localEntries.isEmpty() && localCustomFolders.isEmpty() && !syncState.pendingChanges

            when (val readResult = storage.readBackup(treeUri)) {
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
                        writeResult = storage.writeBackup(json, treeUri),
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
                            writeResult = storage.writeBackup(json, treeUri),
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
                            writeResult = storage.writeBackup(json, treeUri),
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
                                writeResult = storage.writeBackup(normalizedJson, treeUri),
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
                                writeResult = storage.writeBackup(mergedJson, treeUri),
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
                                writeResult = storage.writeBackup(normalizedJson, treeUri),
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
                            writeResult = storage.writeBackup(localJson, treeUri),
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
                            writeResult = storage.writeBackup(mergedJson, treeUri),
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
    }

    /**
     * Menggabungkan entri lokal dan eksternal sembari menjaga agar entri aktif di lokal
     * tidak pernah berubah menjadi terhapus (deleted = true) akibat berkas eksternal
     * yang lebih sedikit/terpotong, namun tetap mengambil entri baru & pembaruan aktif.
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
            when {
                existing == null -> byId[remoteItem.id] = remoteItem
                !existing.deleted && remoteItem.deleted -> {
                    // Lindungi entri aktif lokal dari penghapusan tak sengaja saat berkas eksternal menyusut
                }
                remoteItem.updatedAtMillis >= existing.updatedAtMillis -> {
                    byId[remoteItem.id] = remoteItem
                }
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
        const val BACKUP_FILE_NAME = "ketikku_dictionary.json"
        const val BACKUP_SHORT_PATH = "Documents/ketikku_dictionary.json"
        const val BACKUP_DISPLAY_PATH = "/storage/emulated/0/Documents/ketikku_dictionary.json"
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val PRIMARY_DOCUMENTS_PREFIX = "primary:Documents"

        val INITIAL_DOCUMENTS_URI: Uri =
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")

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

        fun formatBackupShortFilePath(treeUriString: String?): String =
            "${formatBackupShortFolder(treeUriString)}/$BACKUP_FILE_NAME"

        fun formatBackupDisplayPath(treeUriString: String?): String =
            "/storage/emulated/0/${formatBackupShortFilePath(treeUriString)}"
    }
}
