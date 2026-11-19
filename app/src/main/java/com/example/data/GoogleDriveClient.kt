package com.example.data

import android.util.Log
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

enum class SyncDirection {
    UPLOADED,
    DOWNLOADED,
    MERGED,
    UP_TO_DATE
}

data class DriveFileMeta(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTimeMillis: Long,
    val shortcutTargetId: String? = null
)

data class DriveSyncOutcome(
    val direction: SyncDirection,
    val folderId: String,
    val settingsFileId: String,
    val dictionaryFileId: String,
    val syncedSettings: KeyboardSettings,
    val syncedDictionary: List<PersonalDictionaryEntry>,
    val syncedFolders: List<String> = emptyList(),
    val syncedAtMillis: Long,
    val message: String
)

class DriveAuthExpiredException(message: String) : Exception(message)

class GoogleDriveClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GoogleDriveClient"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val SHORTCUT_MIME = "application/vnd.google-apps.shortcut"
        const val SETTINGS_FILE_NAME = "ketikku_settings.json"
        const val DICTIONARY_FILE_NAME = "ketikku_dictionary.json"
    }

    suspend fun syncTwoWay(
        accessToken: String,
        folderName: String,
        shortcutFolderIdOverride: String,
        localSettings: KeyboardSettings,
        localDictionary: List<PersonalDictionaryEntry>,
        settingsRepository: SettingsRepository,
        forceDirection: SyncDirection? = null,
        localFolders: List<String> = emptyList()
    ): Result<DriveSyncOutcome> = withContext(Dispatchers.IO) {
        try {
            val folderId = resolveTargetFolderId(
                accessToken = accessToken,
                folderName = folderName.ifBlank { "KetikKu" },
                shortcutFolderIdOverride = shortcutFolderIdOverride.trim()
            )

            val remoteFiles = listFilesInFolder(accessToken, folderId)
            val remoteDictMeta = remoteFiles.firstOrNull { it.name == DICTIONARY_FILE_NAME }
            val remoteSettingsMeta = remoteFiles.firstOrNull { it.name == SETTINGS_FILE_NAME }

            // 1. Sinkronisasi kamus pribadi (ketikku_dictionary.json)
            var finalDictionary = localDictionary
            var finalFolders = SettingsRepository.normalizeOrderedFolders(localFolders, localDictionary)
            var dictFileId = remoteDictMeta?.id ?: ""
            var dictAction = SyncDirection.UP_TO_DATE

            if (remoteDictMeta == null) {
                if (forceDirection != SyncDirection.DOWNLOADED) {
                    val now = System.currentTimeMillis()
                    val payload = settingsRepository.encodeDictionaryBackupJson(localDictionary, now, finalFolders)
                    dictFileId = upsertJsonFile(
                        accessToken = accessToken,
                        folderId = folderId,
                        existingFileId = null,
                        fileName = DICTIONARY_FILE_NAME,
                        jsonContent = payload
                    )
                    dictAction = SyncDirection.UPLOADED
                }
            } else {
                val remoteDictJson = downloadFileContent(accessToken, remoteDictMeta.id)
                val remoteDictEnv = settingsRepository.decodeDictionaryBackupJson(remoteDictJson)

                if (forceDirection == SyncDirection.UPLOADED) {
                    val now = System.currentTimeMillis()
                    val mergedForUpload = if (remoteDictEnv != null) {
                        settingsRepository.mergeRemoteDictionary(remoteDictEnv.entries, remoteDictEnv.folders)
                    } else {
                        localDictionary
                    }
                    finalDictionary = mergedForUpload
                    finalFolders = settingsRepository.getCurrentFolders()
                    val payload = settingsRepository.encodeDictionaryBackupJson(mergedForUpload, now, finalFolders)
                    dictFileId = upsertJsonFile(
                        accessToken = accessToken,
                        folderId = folderId,
                        existingFileId = remoteDictMeta.id,
                        fileName = DICTIONARY_FILE_NAME,
                        jsonContent = payload
                    )
                    dictAction = SyncDirection.UPLOADED
                } else if (remoteDictEnv != null) {
                    val merged = settingsRepository.mergeRemoteDictionary(remoteDictEnv.entries, remoteDictEnv.folders)
                    finalDictionary = merged
                    finalFolders = settingsRepository.getCurrentFolders()
                    val remoteFoldersNorm = SettingsRepository.normalizeOrderedFolders(
                        remoteDictEnv.folders,
                        remoteDictEnv.entries
                    )
                    val localFoldersNorm = SettingsRepository.normalizeOrderedFolders(
                        localFolders,
                        localDictionary
                    )
                    val localUpdated = dictionaryContentsDiffer(merged, localDictionary) ||
                        finalFolders != localFoldersNorm
                    val remoteNeedsUpdate = forceDirection != SyncDirection.DOWNLOADED &&
                        (dictionaryContentsDiffer(merged, remoteDictEnv.entries) || finalFolders != remoteFoldersNorm)

                    if (remoteNeedsUpdate) {
                        val now = System.currentTimeMillis()
                        val payload = settingsRepository.encodeDictionaryBackupJson(merged, now, finalFolders)
                        dictFileId = upsertJsonFile(
                            accessToken = accessToken,
                            folderId = folderId,
                            existingFileId = remoteDictMeta.id,
                            fileName = DICTIONARY_FILE_NAME,
                            jsonContent = payload
                        )
                        dictAction = if (localUpdated) SyncDirection.MERGED else SyncDirection.UPLOADED
                    } else if (localUpdated) {
                        dictAction = SyncDirection.DOWNLOADED
                    }
                } else if (forceDirection != SyncDirection.DOWNLOADED) {
                    val now = System.currentTimeMillis()
                    val payload = settingsRepository.encodeDictionaryBackupJson(localDictionary, now, finalFolders)
                    dictFileId = upsertJsonFile(
                        accessToken = accessToken,
                        folderId = folderId,
                        existingFileId = remoteDictMeta.id,
                        fileName = DICTIONARY_FILE_NAME,
                        jsonContent = payload
                    )
                    dictAction = SyncDirection.UPLOADED
                }
            }

            // 2. Sinkronisasi pengaturan keyboard (ketikku_settings.json)
            var finalSettings = localSettings
            var settingsFileId = remoteSettingsMeta?.id ?: ""
            var settingsAction = SyncDirection.UP_TO_DATE

            if (remoteSettingsMeta == null) {
                if (forceDirection != SyncDirection.DOWNLOADED) {
                    val settingsToUpload = if (localSettings.updatedAtMillis > 0L) {
                        localSettings
                    } else {
                        localSettings.copy(updatedAtMillis = System.currentTimeMillis())
                    }
                    finalSettings = settingsToUpload
                    val payload = settingsRepository.encodeSettingsBackupJson(settingsToUpload)
                    settingsFileId = upsertJsonFile(
                        accessToken = accessToken,
                        folderId = folderId,
                        existingFileId = null,
                        fileName = SETTINGS_FILE_NAME,
                        jsonContent = payload
                    )
                    settingsAction = SyncDirection.UPLOADED
                }
            } else {
                val remoteSettingsJson = downloadFileContent(accessToken, remoteSettingsMeta.id)
                val remoteSettingsEnv = settingsRepository.decodeSettingsBackupJson(remoteSettingsJson)

                if (remoteSettingsEnv == null) {
                    if (forceDirection != SyncDirection.DOWNLOADED) {
                        val settingsToUpload = if (localSettings.updatedAtMillis > 0L) {
                            localSettings
                        } else {
                            localSettings.copy(updatedAtMillis = System.currentTimeMillis())
                        }
                        finalSettings = settingsToUpload
                        val payload = settingsRepository.encodeSettingsBackupJson(settingsToUpload)
                        settingsFileId = upsertJsonFile(
                            accessToken = accessToken,
                            folderId = folderId,
                            existingFileId = remoteSettingsMeta.id,
                            fileName = SETTINGS_FILE_NAME,
                            jsonContent = payload
                        )
                        settingsAction = SyncDirection.UPLOADED
                    }
                } else {
                    val remoteEffectiveTs = maxOf(
                        remoteSettingsEnv.updatedAtMillis,
                        remoteSettingsEnv.settings.updatedAtMillis
                    )
                    val normalizedRemote = remoteSettingsEnv.settings.copy(
                        updatedAtMillis = remoteEffectiveTs
                    )
                    val differ = !settingsContentEquals(localSettings, normalizedRemote)

                    if (forceDirection == SyncDirection.UPLOADED) {
                        val settingsToUpload = localSettings.copy(
                            updatedAtMillis = maxOf(localSettings.updatedAtMillis, System.currentTimeMillis())
                        )
                        finalSettings = settingsToUpload
                        val payload = settingsRepository.encodeSettingsBackupJson(settingsToUpload)
                        settingsFileId = upsertJsonFile(
                            accessToken = accessToken,
                            folderId = folderId,
                            existingFileId = remoteSettingsMeta.id,
                            fileName = SETTINGS_FILE_NAME,
                            jsonContent = payload
                        )
                        settingsAction = SyncDirection.UPLOADED
                    } else if (forceDirection == SyncDirection.DOWNLOADED) {
                        if (differ) {
                            settingsRepository.applyRemoteSettings(normalizedRemote)
                            finalSettings = normalizedRemote
                            settingsAction = SyncDirection.DOWNLOADED
                        }
                    } else if (differ) {
                        val localTs = localSettings.updatedAtMillis
                        val remoteNewer = remoteEffectiveTs > localTs ||
                            (localTs == 0L && remoteEffectiveTs == 0L && remoteSettingsMeta.modifiedTimeMillis > 0L)
                        if (remoteNewer) {
                            settingsRepository.applyRemoteSettings(normalizedRemote)
                            finalSettings = normalizedRemote
                            settingsAction = SyncDirection.DOWNLOADED
                        } else {
                            val settingsToUpload = localSettings.copy(
                                updatedAtMillis = maxOf(localTs, System.currentTimeMillis())
                            )
                            finalSettings = settingsToUpload
                            val payload = settingsRepository.encodeSettingsBackupJson(settingsToUpload)
                            settingsFileId = upsertJsonFile(
                                accessToken = accessToken,
                                folderId = folderId,
                                existingFileId = remoteSettingsMeta.id,
                                fileName = SETTINGS_FILE_NAME,
                                jsonContent = payload
                            )
                            settingsAction = SyncDirection.UPLOADED
                        }
                    }
                }
            }

            val direction = when {
                dictAction == SyncDirection.MERGED -> SyncDirection.MERGED
                dictAction == SyncDirection.UPLOADED && settingsAction == SyncDirection.DOWNLOADED -> SyncDirection.MERGED
                dictAction == SyncDirection.DOWNLOADED && settingsAction == SyncDirection.UPLOADED -> SyncDirection.MERGED
                dictAction == SyncDirection.UPLOADED || settingsAction == SyncDirection.UPLOADED -> SyncDirection.UPLOADED
                dictAction == SyncDirection.DOWNLOADED || settingsAction == SyncDirection.DOWNLOADED -> SyncDirection.DOWNLOADED
                else -> SyncDirection.UP_TO_DATE
            }

            val now = System.currentTimeMillis()
            val message = when (direction) {
                SyncDirection.UPLOADED -> "Kamus & pengaturan tersinkronisasi • Diunggah ke Drive"
                SyncDirection.DOWNLOADED -> "Kamus & pengaturan tersinkronisasi • Dipulihkan dari Drive"
                SyncDirection.MERGED -> "Kamus & pengaturan tersinkronisasi • Gabungan 2 arah"
                SyncDirection.UP_TO_DATE -> if (
                    remoteDictMeta == null &&
                    remoteSettingsMeta == null &&
                    forceDirection == SyncDirection.DOWNLOADED
                ) {
                    "Belum ada cadangan di Google Drive"
                } else {
                    "Kamus & pengaturan tersinkronisasi • Baru saja"
                }
            }

            settingsRepository.updateSyncState(
                lastSyncedAtMillis = now,
                message = message,
                driveFolderId = folderId,
                driveSettingsFileId = settingsFileId,
                driveDictFileId = dictFileId,
                pendingChanges = false
            )

            Result.success(
                DriveSyncOutcome(
                    direction = direction,
                    folderId = folderId,
                    settingsFileId = settingsFileId,
                    dictionaryFileId = dictFileId,
                    syncedSettings = finalSettings,
                    syncedDictionary = finalDictionary,
                    syncedFolders = finalFolders,
                    syncedAtMillis = now,
                    message = message
                )
            )
        } catch (e: DriveAuthExpiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Drive sync failed", e)
            Result.failure(e)
        }
    }

    private fun dictionaryContentsDiffer(
        a: List<PersonalDictionaryEntry>,
        b: List<PersonalDictionaryEntry>
    ): Boolean {
        if (a.size != b.size) return true
        val bById = b.associateBy { it.id }
        return a.any { entry ->
            val other = bById[entry.id] ?: return@any true
            entry.word != other.word ||
                entry.shortcut != other.shortcut ||
                entry.folder != other.folder ||
                entry.updatedAtMillis != other.updatedAtMillis ||
                entry.deleted != other.deleted
        }
    }

    private fun settingsContentEquals(a: KeyboardSettings, b: KeyboardSettings): Boolean =
        a.heightPreset == b.heightPreset &&
            a.floatingModeEnabled == b.floatingModeEnabled &&
            kotlin.math.abs(a.floatingPosX - b.floatingPosX) < 0.0001f &&
            kotlin.math.abs(a.floatingPosY - b.floatingPosY) < 0.0001f &&
            a.hapticEnabled == b.hapticEnabled &&
            a.clipboardHistoryEnabled == b.clipboardHistoryEnabled &&
            a.arialItalicEnabled == b.arialItalicEnabled &&
            a.serifMiringEnabled == b.serifMiringEnabled &&
            a.swapPeriodCommaEnabled == b.swapPeriodCommaEnabled &&
            a.swapSymbolPanelsEnabled == b.swapSymbolPanelsEnabled &&
            a.autoSyncDriveEnabled == b.autoSyncDriveEnabled &&
            a.driveFolderName == b.driveFolderName &&
            a.driveShortcutFolderId == b.driveShortcutFolderId

    private fun resolveTargetFolderId(
        accessToken: String,
        folderName: String,
        shortcutFolderIdOverride: String
    ): String {
        if (shortcutFolderIdOverride.isNotEmpty()) {
            val meta = fetchFileMeta(accessToken, shortcutFolderIdOverride)
            if (meta != null) {
                return if (meta.mimeType == SHORTCUT_MIME && !meta.shortcutTargetId.isNullOrBlank()) {
                    meta.shortcutTargetId
                } else {
                    meta.id
                }
            }
        }

        val escapedName = folderName.replace("'", "\\'")
        val query = "name = '$escapedName' and trashed = false and " +
            "(mimeType = '$FOLDER_MIME' or mimeType = '$SHORTCUT_MIME')"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_FILES_URL?q=$encodedQuery&fields=files(id,name,mimeType,modifiedTime,shortcutDetails)&supportsAllDrives=true&includeItemsFromAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val json = executeRequest(request)
        val files = parseFileList(json)
        val existing = files.firstOrNull()
        if (existing != null) {
            return if (existing.mimeType == SHORTCUT_MIME && !existing.shortcutTargetId.isNullOrBlank()) {
                existing.shortcutTargetId
            } else {
                existing.id
            }
        }

        // Create dedicated folder in Drive root
        val metadata = JSONObject().apply {
            put("name", folderName)
            put("mimeType", FOLDER_MIME)
        }
        val createRequest = Request.Builder()
            .url("$DRIVE_FILES_URL?fields=id")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val createdJson = executeRequest(createRequest)
        return JSONObject(createdJson).getString("id")
    }

    private fun fetchFileMeta(accessToken: String, fileId: String): DriveFileMeta? {
        val url = "$DRIVE_FILES_URL/$fileId?fields=id,name,mimeType,modifiedTime,shortcutDetails&supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return runCatching {
            val body = executeRequest(request)
            parseFileObject(JSONObject(body))
        }.getOrNull()
    }

    private fun listFilesInFolder(accessToken: String, folderId: String): List<DriveFileMeta> {
        val query = "'$folderId' in parents and trashed = false"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_FILES_URL?q=$encoded&fields=files(id,name,mimeType,modifiedTime,shortcutDetails)&supportsAllDrives=true&includeItemsFromAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return parseFileList(executeRequest(request))
    }

    private fun downloadFileContent(accessToken: String, fileId: String): String {
        val url = "$DRIVE_FILES_URL/$fileId?alt=media&supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return executeRequest(request)
    }

    private fun upsertJsonFile(
        accessToken: String,
        folderId: String,
        existingFileId: String?,
        fileName: String,
        jsonContent: String
    ): String {
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/json")
            if (existingFileId == null) {
                put("parents", JSONArray().apply { put(folderId) })
            }
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(
                metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .addPart(
                jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()

        val requestBuilder = Request.Builder()
            .addHeader("Authorization", "Bearer $accessToken")

        val request = if (existingFileId != null) {
            requestBuilder
                .url("$DRIVE_UPLOAD_URL/$existingFileId?uploadType=multipart&fields=id&supportsAllDrives=true")
                .patch(multipartBody)
                .build()
        } else {
            requestBuilder
                .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id&supportsAllDrives=true")
                .post(multipartBody)
                .build()
        }

        val responseBody = executeRequest(request)
        return JSONObject(responseBody).getString("id")
    }

    private fun executeRequest(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw DriveAuthExpiredException("Akses Google Drive perlu otorisasi ulang (${response.code})")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive API error ${response.code}: $bodyStr")
            }
            return bodyStr
        }
    }

    private fun parseFileList(jsonStr: String): List<DriveFileMeta> {
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("files") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                add(parseFileObject(arr.getJSONObject(i)))
            }
        }
    }

    private fun parseFileObject(obj: JSONObject): DriveFileMeta {
        val modifiedStr = obj.optString("modifiedTime", "")
        val modifiedMillis = runCatching {
            if (modifiedStr.isNotEmpty()) Instant.parse(modifiedStr).toEpochMilli() else 0L
        }.getOrDefault(0L)
        val shortcutDetails = obj.optJSONObject("shortcutDetails")
        val targetId = shortcutDetails?.optString("targetId")?.takeIf { it.isNotBlank() }
        return DriveFileMeta(
            id = obj.getString("id"),
            name = obj.optString("name"),
            mimeType = obj.optString("mimeType"),
            modifiedTimeMillis = modifiedMillis,
            shortcutTargetId = targetId
        )
    }
}
