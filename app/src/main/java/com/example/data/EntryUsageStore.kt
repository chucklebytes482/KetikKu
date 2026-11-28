package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Jumlah tap per entri akun A2F, disimpan di SQLite lokal. Hanya ID entri, tanpa isi kode atau email.
 * Dipakai untuk mengurutkan kolom A2F: paling sering diketuk dulu.
 * Cache di memori dibaca saat render; penulisan ke SQLite dilakukan di thread terpisah.
 */
class EntryUsageStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val cache = ConcurrentHashMap<String, Int>()

    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "entry-usage-writer").apply { isDaemon = true }
    }

    init {
        runCatching {
            readableDatabase.rawQuery("SELECT entry_id, uses FROM entry_usage", null).use { c ->
                while (c.moveToNext()) cache[c.getString(0)] = c.getInt(1)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS entry_usage (" +
                "entry_id TEXT PRIMARY KEY NOT NULL, " +
                "uses INTEGER NOT NULL, " +
                "last_used INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versi pertama, belum ada migrasi.
    }

    /** Catat satu tap pada entri. Cache langsung berubah, tulis SQLite di thread penulis. */
    fun record(entryId: String, nowMillis: Long = System.currentTimeMillis()) {
        val id = entryId.trim()
        if (id.isEmpty()) return
        cache.merge(id, 1) { a, b -> a + b }
        writer.execute { runCatching { upsert(id, nowMillis) } }
    }

    /** Jumlah tap entri ini. 0 jika belum pernah diketuk. */
    fun usesOf(entryId: String): Int = cache[entryId.trim()] ?: 0

    /** Internal agar bisa dipanggil langsung dari tes tanpa thread penulis. */
    internal fun upsert(entryId: String, nowMillis: Long) {
        writableDatabase.execSQL(
            "INSERT INTO entry_usage(entry_id, uses, last_used) VALUES(?, 1, ?) " +
                "ON CONFLICT(entry_id) DO UPDATE SET uses = uses + 1, last_used = excluded.last_used",
            arrayOf<Any>(entryId, nowMillis)
        )
    }

    companion object {
        private const val DB_NAME = "ketikku_entry_usage.db"
        private const val DB_VERSION = 1

        @Volatile
        private var instance: EntryUsageStore? = null

        fun get(context: Context): EntryUsageStore {
            return instance ?: synchronized(this) {
                instance ?: EntryUsageStore(context).also { instance = it }
            }
        }
    }
}
