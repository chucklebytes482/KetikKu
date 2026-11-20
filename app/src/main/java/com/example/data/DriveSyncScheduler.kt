package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.model.SyncScheduleInterval
import com.example.ui.AuthHelper
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (currentUser == null) {
            DriveSyncScheduler.cancel(applicationContext)
            return Result.success()
        }

        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.getCurrentSettings()
        val dictionary = settingsRepo.getCurrentDictionary()
        val folders = settingsRepo.getCurrentFolders()

        val outcomeResult = runCatching {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(AuthHelper.DRIVE_FILE_SCOPE)))
                .build()
            val authResult = Identity.getAuthorizationClient(applicationContext)
                .authorize(authRequest)
                .await()
            val accessToken = if (!authResult.hasResolution()) authResult.accessToken else null

            if (!accessToken.isNullOrBlank()) {
                val driveClient = GoogleDriveClient()
                val syncResult = driveClient.syncTwoWay(
                    accessToken = accessToken,
                    folderName = settings.driveFolderName,
                    shortcutFolderIdOverride = settings.driveShortcutFolderId,
                    localSettings = settings,
                    localDictionary = dictionary,
                    settingsRepository = settingsRepo,
                    forceDirection = null,
                    localFolders = folders
                )
                syncResult.getOrNull()?.let { outcome ->
                    val firestoreRepo = FirestoreSyncRepository(applicationContext)
                    firestoreRepo.upsertSyncProfile(
                        userId = currentUser.uid,
                        email = currentUser.email.orEmpty(),
                        autoSync = true,
                        driveFolderId = outcome.folderId,
                        driveFolderName = outcome.syncedSettings.driveFolderName,
                        lastSyncedAtMillis = outcome.syncedAtMillis
                    )
                }
            } else {
                Log.d(TAG, "Background Drive sync skipped: silent access token unavailable")
            }
        }

        // Re-chain 5-minute interval work (OS PeriodicWorkRequest minimum floor is 15 minutes).
        if (settings.syncIntervalMinutes == SyncScheduleInterval.MINUTES_5.minutes) {
            DriveSyncScheduler.schedule(applicationContext, SyncScheduleInterval.MINUTES_5.minutes)
        }

        return if (outcomeResult.isSuccess) {
            Result.success()
        } else {
            Log.w(TAG, "Background Drive sync failed, will retry", outcomeResult.exceptionOrNull())
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "DriveSyncWorker"
    }
}

object DriveSyncScheduler {
    private const val PERIODIC_WORK_NAME = "ketikku_drive_sync_periodic"
    private const val SHORT_INTERVAL_WORK_NAME = "ketikku_drive_sync_5m"

    fun schedule(context: Context, intervalMinutes: Int) {
        val normalized = SyncScheduleInterval.fromMinutes(intervalMinutes)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        runCatching {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (normalized == SyncScheduleInterval.MINUTES_5) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                val shortRequest = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                    .setInitialDelay(normalized.minutes.toLong(), TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniqueWork(
                    SHORT_INTERVAL_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    shortRequest
                )
            } else {
                workManager.cancelUniqueWork(SHORT_INTERVAL_WORK_NAME)
                val periodicRequest = PeriodicWorkRequestBuilder<DriveSyncWorker>(
                    normalized.minutes.toLong(),
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicRequest
                )
            }
        }
    }

    fun cancel(context: Context) {
        runCatching {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(SHORT_INTERVAL_WORK_NAME)
        }
    }
}
