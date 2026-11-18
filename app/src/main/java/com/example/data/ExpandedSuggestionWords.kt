package com.example.data

// Additional suggestion-only vocabulary. Never persisted or synced as personal entries.
internal object ExpandedSuggestionWords {
    val indonesian = """
        aku dia kalian seseorang siapa apa kapan bagaimana mengapa dimana ketika selama sebelum setelah sampai sejak
        bahkan namun tetapi supaya agar sehingga meskipun walaupun apabila jika maka sebab tanpa bersama antara melalui
        terhadap tentang seperti sebagai oleh pada kepada daripada sekitar seluruh sebagian beberapa kedua pertama terakhir berikut
        tersebut sendiri langsung segera dahulu dulu sering jarang selalu pernah tetap cukup hampir terlalu sekali memang
        sungguh tentunya sebaiknya sepertinya biasanya akhirnya sebenarnya terutama khusus umum pribadi berbeda masing-masing
        sekolah belajar pelajaran guru murid siswa mahasiswa kampus kuliah universitas pendidikan buku halaman cerita bahasa
        kalimat huruf angka kata suara gambar foto video musik lagu film berita informasi internet aplikasi perangkat
        layar tombol ketikan komputer laptop ponsel telepon baterai jaringan koneksi sinyal data memori penyimpanan unduh
        unggah salin tempel pilih pilihan pengaturan setelan sistem versi pembaruan kesalahan perbaikan responsif lancar lambat
        mengetik membaca menulis melihat mendengar berbicara berjalan berlari duduk berdiri bermain bekerja mencoba membantu mencari
        membuat membawa mengambil memberi menerima mengirim membuka menutup menyimpan menghapus memilih menunggu memulai menyelesaikan
        mengingat memahami menjelaskan bertanya menjawab menghubungi menggunakan membeli menjual membayar memasak mencuci membersihkan
        tidurku bangun istirahat sehat sakit dokter obat rumahsakit olahraga tubuh kepala tangan kaki mata telinga hidung
        mulut gigi rambut wajah hati pikiran perasaan senang sedih marah takut tenang bahagia kecewa khawatir nyaman
        indah menarik hebat keren lucu ramah sabar jujur sopan rajin malas kuat lemah tinggi rendah besar kecil
        panjang pendek lebar sempit tebal tipis berat ringan panas dingin hangat sejuk basah kering bersih kotor
        terang gelap merah biru hijau kuning putih hitam ungu cokelat abu-abu warna air api tanah udara laut
        sungai gunung pantai hutan pohon bunga daun buah sayur nasi roti ayam ikan daging telur susu kopi
        teh
    """.trimIndent().split(Regex("\\s+"))

    val english = """
        i you we they he she it this that these those the a an and or but because if when
        while before after now later yesterday week month year time day hour minute second yes no okay sure
        maybe always never sometimes already still again also only very more less much many some all each every
        first last new old big small long short fast slow easy hard right wrong true false safe clear
        text word letter keyboard typing screen phone computer message call read write copy paste select search find
        create edit change settings language online offline network connection download upload memory storage battery service support
        customer money price buy sell pay paid free cost shopping product store food
    """.trimIndent().split(Regex("\\s+"))
}