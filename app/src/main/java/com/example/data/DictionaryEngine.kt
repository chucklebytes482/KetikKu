package com.example.data

import com.example.model.KeyboardLanguage
import com.example.model.PersonalDictionaryEntry
import kotlin.math.abs
import kotlin.math.min

data class SuggestionResult(
    val primaryAutoCorrect: String? = null,
    val suggestions: List<String> = emptyList()
)

object DictionaryEngine {

    private val indonesianWords: List<String> = listOf(
        "yang", "dan", "di", "ke", "dari", "ini", "itu", "untuk", "dengan", "tidak",
        "ada", "pada", "dalam", "saya", "aku", "kamu", "anda", "kita", "kami", "mereka",
        "akan", "sudah", "belum", "bisa", "dapat", "juga", "atau", "karena", "jika", "kalau",
        "tetapi", "tapi", "namun", "sebagai", "oleh", "saat", "ketika", "setelah", "sebelum",
        "apa", "siapa", "mana", "kapan", "mengapa", "kenapa", "bagaimana", "berapa",
        "selamat", "pagi", "siang", "sore", "malam", "terima", "kasih", "sama", "maaf", "tolong",
        "mohon", "silakan", "baik", "bagus", "hebat", "keren", "mantap", "siap", "oke", "ya",
        "halo", "hai", "kabar", "sehat", "semoga", "sukses", "lancar", "berhasil",
        "rumah", "kantor", "sekolah", "kampus", "jalan", "kota", "negara", "indonesia",
        "hari", "besok", "lusa", "kemarin", "sekarang", "nanti", "minggu", "bulan", "tahun",
        "jam", "menit", "detik", "waktu", "jadwal", "rapat", "pertemuan", "acara",
        "kerja", "bekerja", "belajar", "membaca", "menulis", "mengetik", "makan", "minum",
        "tidur", "bangun", "pergi", "pulang", "datang", "sampai", "kirim", "terima", "buka",
        "tutup", "simpan", "hapus", "ubah", "ganti", "cari", "pilih", "atur", "pengaturan",
        "setelan", "cadangan", "cadangkan", "pulihkan", "sinkron", "sinkronisasi", "otomatis",
        "keyboard", "papan", "ketik", "tombol", "huruf", "angka", "simbol", "kata", "kalimat",
        "bahasa", "kamus", "pribadi", "clipboard", "salin", "tempel", "potong", "kursor",
        "layar", "aplikasi", "ponsel", "perangkat", "akun", "masuk", "keluar", "google", "drive",
        "berkas", "dokumen", "catatan", "pesan", "email", "nomor", "telepon", "alamat", "nama",
        "banyak", "sedikit", "semua", "beberapa", "setiap", "baru", "lama", "cepat", "lambat",
        "besar", "kecil", "tinggi", "pendek", "panjang", "mudah", "sulit", "penting", "benar",
        "salah", "jelas", "lengkap", "aman", "nyaman", "bersih", "rapi", "ringan", "gratis",
        "sangat", "terlalu", "lebih", "kurang", "paling", "sekali", "hanya", "saja", "lagi",
        "masih", "pernah", "selalu", "sering", "jarang", "kadang", "mungkin", "pasti", "tentu",
        "harus", "perlu", "ingin", "mau", "suka", "cinta", "sayang", "rindu", "senang",
        "bahagia", "sedih", "marah", "takut", "tenang", "sabar", "semangat", "kuat",
        "teman", "sahabat", "keluarga", "orang", "anak", "ibu", "ayah", "kakak", "adik",
        "bapak", "saudara", "rekan", "tim", "proyek", "tugas", "laporan", "hasil", "data",
        "sistem", "jaringan", "internet", "online", "offline", "koneksi", "server", "awan",
        "tema", "warna", "gelap", "terang", "ukuran", "posisi", "kiri", "kanan", "tengah",
        "atas", "bawah", "depan", "belakang", "dalam", "luar", "awal", "akhir", "mulai",
        "selesai", "lanjut", "berhenti", "coba", "tes", "uji", "periksa", "lihat", "dengar",
        "tanya", "jawab", "cerita", "berita", "info", "informasi", "bantuan", "panduan",
        "fitur", "versi", "pembaruan", "perbaikan", "kualitas", "performa", "kecepatan",
        "menunggu", "sedang", "sedikit", "sebentar", "langsung", "segera", "akhirnya",
        "misalnya", "contoh", "seperti", "antara", "tentang", "mengenai", "terhadap", "kepada",
        "supaya", "agar", "meskipun", "walaupun", "padahal", "sedangkan", "sehingga", "karena",
        "kirimkan", "tolong", "beritahu", "kabari", "hubungi", "diskusi", "sepakat", "setuju"
    )

    private val englishWords: List<String> = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "hello", "thanks", "thank", "please", "sorry", "morning", "afternoon", "evening",
        "night", "today", "tomorrow", "yesterday", "meeting", "schedule", "project",
        "keyboard", "typing", "swipe", "glide", "autocorrect", "settings", "backup",
        "restore", "sync", "cloud", "google", "drive", "account", "file", "folder",
        "save", "delete", "edit", "copy", "paste", "cut", "select", "search", "find",
        "language", "english", "indonesian", "dictionary", "personal", "shortcut",
        "clipboard", "theme", "dark", "light", "height", "layout", "number", "symbol",
        "message", "email", "phone", "address", "name", "ready", "done", "great", "awesome",
        "quick", "fast", "clean", "simple", "smart", "secure", "private", "local", "online",
        "offline", "update", "version", "change", "check", "test", "start", "finish",
        "always", "never", "sometimes", "often", "maybe", "sure", "definitely", "really",
        "better", "best", "more", "less", "very", "much", "many", "every", "each",
        "should", "could", "might", "must", "need", "help", "support", "question", "answer"
    )

    private val indonesianBigrams: Map<String, List<String>> = mapOf(
        "selamat" to listOf("pagi", "siang", "sore", "malam", "datang"),
        "terima" to listOf("kasih", "pesan", "berkas"),
        "terimakasih" to listOf("banyak", "kembali", "ya"),
        "apa" to listOf("kabar", "yang", "itu"),
        "saya" to listOf("akan", "sudah", "tidak", "mau", "sedang"),
        "aku" to listOf("sudah", "akan", "tidak", "mau"),
        "kamu" to listOf("sudah", "mau", "di", "lagi"),
        "tidak" to listOf("ada", "bisa", "apa", "tahu"),
        "sedang" to listOf("bekerja", "dalam", "menunggu", "makan"),
        "google" to listOf("drive", "keyboard", "akun"),
        "mohon" to listOf("maaf", "bantuan", "tunggu"),
        "sampai" to listOf("jumpa", "nanti", "besok")
    )

    private val englishBigrams: Map<String, List<String>> = mapOf(
        "thank" to listOf("you", "god"),
        "thanks" to listOf("for", "a", "so"),
        "good" to listOf("morning", "afternoon", "evening", "night", "job"),
        "how" to listOf("are", "to", "is", "much"),
        "i" to listOf("am", "will", "have", "need", "think"),
        "on" to listOf("the", "my", "time"),
        "google" to listOf("drive", "account", "keyboard"),
        "see" to listOf("you", "the", "that")
    )

    fun getSuggestions(
        currentWord: String,
        previousWord: String,
        language: KeyboardLanguage,
        personalEntries: List<PersonalDictionaryEntry>
    ): SuggestionResult {
        val activePersonal = personalEntries.filter { !it.deleted }
        val lowerWord = currentWord.lowercase()

        if (lowerWord.isEmpty()) {
            val prevLower = previousWord.lowercase()
            val bigrams = if (language == KeyboardLanguage.INDONESIAN) {
                indonesianBigrams[prevLower]
            } else {
                englishBigrams[prevLower]
            }
            val defaults = if (language == KeyboardLanguage.INDONESIAN) {
                listOf("Saya", "Tidak", "Terima")
            } else {
                listOf("I", "The", "Thanks")
            }
            return SuggestionResult(
                primaryAutoCorrect = null,
                suggestions = (bigrams ?: defaults).take(3)
            )
        }

        // Exact shortcut match from personal dictionary
        val shortcutMatch = activePersonal.firstOrNull {
            it.shortcut.isNotBlank() && it.shortcut.equals(lowerWord, ignoreCase = true)
        }

        val personalWords = activePersonal
            .filter { it.word.isNotBlank() && !it.word.contains(" ") }
            .map { it.word }

        val lexicon = if (language == KeyboardLanguage.INDONESIAN) {
            (personalWords + indonesianWords + englishWords).distinct()
        } else {
            (personalWords + englishWords + indonesianWords).distinct()
        }

        val exactExists = lexicon.any { it.equals(lowerWord, ignoreCase = true) }

        val prefixMatches = lexicon
            .filter { it.lowercase().startsWith(lowerWord) && !it.equals(lowerWord, ignoreCase = true) }
            .take(4)

        val fuzzyMatches = if (!exactExists && lowerWord.length >= 2) {
            lexicon
                .asSequence()
                .filter { abs(it.length - lowerWord.length) <= 2 }
                .map { candidate -> candidate to editDistance(lowerWord, candidate.lowercase()) }
                .filter { (_, dist) -> dist in 1..2 }
                .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { abs(it.first.length - lowerWord.length) })
                .map { it.first }
                .take(3)
                .toList()
        } else {
            emptyList()
        }

        val preserveCaps = currentWord.firstOrNull()?.isUpperCase() == true
        fun applyCase(w: String): String =
            if (preserveCaps && w.isNotEmpty()) w.replaceFirstChar { it.uppercaseChar() } else w

        val candidates = linkedSetOf<String>()
        if (shortcutMatch != null) {
            candidates.add(shortcutMatch.word)
        }
        val shortcutTarget = shortcutMatch?.word

        if (exactExists) {
            candidates.add(currentWord)
        } else if (shortcutTarget != null) {
            candidates.add(shortcutTarget)
        }

        prefixMatches.forEach { candidates.add(applyCase(it)) }
        fuzzyMatches.forEach { candidates.add(applyCase(it)) }

        if (candidates.size < 3 && !exactExists) {
            candidates.add(currentWord)
        }

        return SuggestionResult(
            primaryAutoCorrect = shortcutTarget,
            suggestions = candidates.take(3)
        )
    }

    fun editDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    dp[i][j] = min(dp[i][j], dp[i - 2][j - 2] + cost)
                }
            }
        }
        return dp[m][n]
    }
}
