package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Kata yang sering diketik, disimpan di SQLite lokal.
 * Bukan kamus pribadi. Hanya kata utuh berisi huruf, 8 sampai 30 karakter.
 * Maksimal [MAX_ROWS] baris; yang paling jarang dipakai dibuang lebih dulu.
 */
class TypedWordStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "typed-word-writer").apply { isDaemon = true }
    }

    /**
     * Salinan kata paling sering dipakai (maks [MAX_ROWS], urut uses lalu last_used). Diperbarui di thread penulis
     * setiap kali tabel berubah, sehingga pencarian saran di jalur ketik tidak membaca SQLite.
     */
    @Volatile
    private var habitSnapshot: List<Pair<String, Int>> = emptyList()

    init {
        writer.execute { runCatching { refreshSnapshot(readableDatabase) } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS typed_words (" +
                "word TEXT PRIMARY KEY NOT NULL, " +
                "uses INTEGER NOT NULL, " +
                "last_used INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_typed_words_uses ON typed_words(uses, last_used)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versi pertama, belum ada migrasi.
    }

    /** Catat satu kata yang selesai diketik. Penulisan dilakukan di thread terpisah, tidak di jalur ketik. */
    fun recordAsync(word: String, nowMillis: Long = System.currentTimeMillis()) {
        val clean = normalize(word) ?: return
        writer.execute {
            runCatching { upsert(clean, nowMillis) }
        }
    }

    /** Internal agar bisa dipanggil langsung dari tes tanpa thread penulis. */
    internal fun upsert(word: String, nowMillis: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO typed_words(word, uses, last_used) VALUES(?, 1, ?) " +
                    "ON CONFLICT(word) DO UPDATE SET uses = uses + 1, last_used = excluded.last_used",
                arrayOf<Any>(word, nowMillis)
            )
            val total = db.rawQuery("SELECT COUNT(*) FROM typed_words", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            val excess = total - MAX_ROWS
            if (excess > 0) {
                db.execSQL(
                    "DELETE FROM typed_words WHERE word IN (" +
                        "SELECT word FROM typed_words ORDER BY uses ASC, last_used ASC LIMIT ?)",
                    arrayOf<Any>(excess)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshSnapshot(db)
    }

    private fun refreshSnapshot(db: SQLiteDatabase) {
        val rows = ArrayList<Pair<String, Int>>(MAX_ROWS)
        db.rawQuery(
            "SELECT word, uses FROM typed_words ORDER BY uses DESC, last_used DESC LIMIT ?",
            arrayOf(MAX_ROWS.toString())
        ).use { c ->
            while (c.moveToNext()) rows.add(c.getString(0) to c.getInt(1))
        }
        habitSnapshot = rows
    }

    /**
     * Kata tersimpan yang diawali [prefix] (minimal [MIN_PREFIX_LEN] huruf), beserta jumlah pemakaiannya.
     * Urut dari yang paling sering dipakai.
     */
    fun suggestPrefix(prefix: String, limit: Int = SUGGEST_LIMIT): Map<String, Int> {
        val p = prefix.trim().lowercase(Locale.ROOT)
        if (p.length < MIN_PREFIX_LEN || !LETTERS.matches(p)) return emptyMap()
        val result = LinkedHashMap<String, Int>()
        for ((word, uses) in habitSnapshot) {
            if (result.size >= limit) break
            if (word.startsWith(p)) result[word] = uses
        }
        return result
    }

    companion object {
        const val MIN_WORD_LEN = 8
        const val MAX_WORD_LEN = 30
        const val MAX_ROWS = 500
        const val MIN_PREFIX_LEN = 3
        const val SUGGEST_LIMIT = 20

        private const val DB_NAME = "ketikku_typed_words.db"
        private const val DB_VERSION = 1
        private val LETTERS = Regex("^\\p{L}+$")

        @Volatile
        private var instance: TypedWordStore? = null

        fun get(context: Context): TypedWordStore {
            return instance ?: synchronized(this) {
                instance ?: TypedWordStore(context).also { instance = it }
            }
        }

        /** Kata yang lolos aturan simpan (huruf saja, 8-30 karakter), dalam huruf kecil. Null jika ditolak. */
        fun normalize(word: String): String? {
            val clean = word.trim().lowercase(Locale.ROOT)
            if (clean.length !in MIN_WORD_LEN..MAX_WORD_LEN || !LETTERS.matches(clean)) return null
            return clean
        }
    }
}
