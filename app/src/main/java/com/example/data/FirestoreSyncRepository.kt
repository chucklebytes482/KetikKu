package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.R
import com.example.model.BackupSnapshot
import com.example.model.UserSyncProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")

    fun observeSyncProfile(userId: String): Flow<UserSyncProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatch signed-in user." }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserSyncProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeBackupSnapshots(userId: String): Flow<List<BackupSnapshot>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatch signed-in user." }
        val colRef = db.collection("users").document(userId).collection("backups")
        emitAll(
            colRef.orderBy("syncedAtMillis", Query.Direction.DESCENDING)
                .limit(10)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(BackupSnapshot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertSyncProfile(
        userId: String,
        email: String,
        autoSync: Boolean,
        driveFolderId: String?,
        driveFolderName: String,
        lastSyncedAtMillis: Long
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Mismatch signed-in user." }
            val safeEmail = email.take(200)
            val safeFolderName = driveFolderName.ifBlank { "KetikKu" }.take(100)
            val safeFolderId = driveFolderId?.takeIf { it.isNotBlank() }?.take(128)
            val docRef = db.collection("users").document(userId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "email" to safeEmail,
                    "autoSync" to autoSync,
                    "driveFolderName" to safeFolderName,
                    "lastSyncedAtMillis" to lastSyncedAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeFolderId != null) {
                    updates["driveFolderId"] = safeFolderId
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", userId)
                    put("email", safeEmail)
                    put("autoSync", autoSync)
                    if (safeFolderId != null) put("driveFolderId", safeFolderId)
                    put("driveFolderName", safeFolderName)
                    put("lastSyncedAtMillis", lastSyncedAtMillis)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun recordBackupSnapshot(
        userId: String,
        driveFileId: String?,
        settingsJson: String,
        dictionaryCount: Int,
        syncSource: String,
        syncedAtMillis: Long
    ): Result<Unit> {
        val snapshotId = "backup_${syncedAtMillis}"
        val path = "users/$userId/backups/$snapshotId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Mismatch signed-in user." }
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }.take(100)
            val safeJson = settingsJson.take(16000).ifBlank { "{}" }
            val safeSource = if (syncSource == "DRIVE_AND_CLOUD") "DRIVE_AND_CLOUD" else "CLOUD_ONLY"
            val safeDriveFileId = driveFileId?.takeIf { it.isNotBlank() }?.take(128)
            val docRef = db.collection("users").document(userId).collection("backups").document(snapshotId)
            val payload = buildMap<String, Any> {
                put("userId", userId)
                put("deviceName", deviceName)
                if (safeDriveFileId != null) put("driveFileId", safeDriveFileId)
                put("settingsJson", safeJson)
                put("dictionaryCount", dictionaryCount.coerceIn(0, 10000))
                put("syncSource", safeSource)
                put("syncedAtMillis", syncedAtMillis.coerceAtLeast(0L))
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(flow: Flow<T>) {
        flow.collect { emit(it) }
    }
}
