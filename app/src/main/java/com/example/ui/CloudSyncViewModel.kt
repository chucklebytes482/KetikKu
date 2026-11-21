package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DriveAuthExpiredException
import com.example.data.FirestoreSyncRepository
import com.example.data.GoogleDriveClient
import com.example.data.SettingsRepository
import com.example.data.SyncDirection
import com.example.model.BackupSnapshot
import com.example.model.UserSyncProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

sealed interface SyncActionState {
    data object Idle : SyncActionState
    data object Syncing : SyncActionState
    data class Done(val message: String) : SyncActionState
    data class NeedsDriveConsent(val reason: String) : SyncActionState
    data class Error(val message: String) : SyncActionState
}

class CloudSyncViewModel(
    private val currentUserId: String,
    private val userEmail: String,
    private val settingsRepository: SettingsRepository,
    private val firestoreRepo: FirestoreSyncRepository,
    private val driveClient: GoogleDriveClient,
    appContext: Context
) : ViewModel() {

    private val appContext = appContext.applicationContext
    private val connectivityManager =
        this.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _syncActionState = MutableStateFlow<SyncActionState>(SyncActionState.Idle)
    val syncActionState: StateFlow<SyncActionState> = _syncActionState.asStateFlow()

    private var cachedDriveToken: String? = null

    val syncProfileState: StateFlow<CloudUiState<UserSyncProfile?>> =
        firestoreRepo.observeSyncProfile(currentUserId)
            .map<UserSyncProfile?, CloudUiState<UserSyncProfile?>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing sync profile", root)
                emit(CloudUiState.Error(root.message ?: "Gagal memuat profil sinkronisasi"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    val backupSnapshotsState: StateFlow<CloudUiState<List<BackupSnapshot>>> =
        firestoreRepo.observeBackupSnapshots(currentUserId)
            .map<List<BackupSnapshot>, CloudUiState<List<BackupSnapshot>>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing backup snapshots", root)
                emit(CloudUiState.Error(root.message ?: "Gagal memuat riwayat cadangan"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkOnlineNow()
        }
    }

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    fun setDriveTokenAndSync(accessToken: String, forceDirection: SyncDirection? = null) {
        cachedDriveToken = accessToken
        viewModelScope.launch {
            performDriveAndCloudSync(accessToken, forceDirection)
        }
    }

    fun hasCachedDriveToken(): Boolean = !cachedDriveToken.isNullOrBlank()

    fun syncWithCachedTokenOrRequestConsent(forceDirection: SyncDirection? = null) {
        val token = cachedDriveToken
        if (token.isNullOrBlank()) {
            _syncActionState.value = SyncActionState.NeedsDriveConsent("Meminta otorisasi Google Drive...")
        } else {
            viewModelScope.launch {
                performDriveAndCloudSync(token, forceDirection)
            }
        }
    }

    fun syncCloudMetadataOnly() {
        viewModelScope.launch {
            _syncActionState.value = SyncActionState.Syncing
            val settings = settingsRepository.getCurrentSettings()
            val dictionary = settingsRepository.getCurrentDictionary()
            val now = System.currentTimeMillis()
            val settingsJson = settingsRepository.encodeCompactSettingsJson(settings)

            val profileRes = firestoreRepo.upsertSyncProfile(
                userId = currentUserId,
                email = userEmail,
                autoSync = settings.autoSyncDriveEnabled,
                driveFolderId = null,
                driveFolderName = settings.driveFolderName,
                lastSyncedAtMillis = now
            )
            val snapRes = firestoreRepo.recordBackupSnapshot(
                userId = currentUserId,
                driveFileId = null,
                settingsJson = settingsJson,
                dictionaryCount = dictionary.count { !it.deleted },
                syncSource = "CLOUD_ONLY",
                syncedAtMillis = now
            )

            if (profileRes.isSuccess && snapRes.isSuccess) {
                settingsRepository.updateSyncState(
                    lastSyncedAtMillis = now,
                    message = "Kamus & pengaturan tersinkronisasi ke Cloud • Baru saja",
                    pendingChanges = false
                )
                _syncActionState.value = SyncActionState.Done("Cadangan kamus & pengaturan Cloud berhasil disimpan.")
            } else {
                val err = profileRes.exceptionOrNull() ?: snapRes.exceptionOrNull()
                _syncActionState.value = SyncActionState.Error(err?.message ?: "Gagal menyimpan ke Cloud.")
            }
        }
    }

    fun resetActionState() {
        _syncActionState.value = SyncActionState.Idle
    }

    fun clearCachedDriveToken() {
        cachedDriveToken = null
        viewModelScope.launch {
            settingsRepository.clearDriveIdsOnDisconnect()
        }
    }

    private suspend fun performDriveAndCloudSync(
        accessToken: String,
        forceDirection: SyncDirection?
    ) {
        if (!_isOnline.value) {
            _syncActionState.value = SyncActionState.Error("Perangkat sedang offline. Perubahan akan disinkronkan saat online.")
            return
        }
        _syncActionState.value = SyncActionState.Syncing

        val localSettings = settingsRepository.getCurrentSettings()
        val localDictionary = settingsRepository.getCurrentDictionary()
        val localFolders = settingsRepository.getCurrentFolders()

        val driveResult = driveClient.syncTwoWay(
            accessToken = accessToken,
            folderName = localSettings.driveFolderName,
            shortcutFolderIdOverride = localSettings.driveShortcutFolderId,
            localSettings = localSettings,
            localDictionary = localDictionary,
            settingsRepository = settingsRepository,
            forceDirection = forceDirection,
            localFolders = localFolders
        )

        driveResult.onSuccess { outcome ->
            firestoreRepo.upsertSyncProfile(
                userId = currentUserId,
                email = userEmail,
                autoSync = outcome.syncedSettings.autoSyncDriveEnabled,
                driveFolderId = outcome.folderId,
                driveFolderName = outcome.syncedSettings.driveFolderName,
                lastSyncedAtMillis = outcome.syncedAtMillis
            )
            firestoreRepo.recordBackupSnapshot(
                userId = currentUserId,
                driveFileId = outcome.dictionaryFileId.ifBlank { outcome.settingsFileId },
                settingsJson = settingsRepository.encodeCompactSettingsJson(outcome.syncedSettings),
                dictionaryCount = outcome.syncedDictionary.count { !it.deleted },
                syncSource = "DRIVE_AND_CLOUD",
                syncedAtMillis = outcome.syncedAtMillis
            )
            _syncActionState.value = SyncActionState.Done(outcome.message)
        }.onFailure { err ->
            if (err is DriveAuthExpiredException) {
                cachedDriveToken = null
                _syncActionState.value = SyncActionState.NeedsDriveConsent(err.message ?: "Otorisasi Drive diperlukan")
            } else {
                _syncActionState.value = SyncActionState.Error(err.localizedMessage ?: "Gagal sinkronisasi ke Google Drive")
            }
        }
    }

    private fun checkOnlineNow(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
    }

    private companion object {
        const val TAG = "CloudSyncVM"
    }
}
