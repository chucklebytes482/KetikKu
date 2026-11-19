package com.example.data

// Additional suggestion-only vocabulary (minimum 8 characters each).
// Never persisted or synced as personal dictionary entries.
internal object ExpandedSuggestionWords {
    val indonesian = """
        bagaimana sementara selanjutnya sebelumnya sebenarnya sepertinya sesudah seharusnya semestinya secepatnya
        sekitarnya seluruhnya semuanya sebanyaknya sesungguhnya setidaknya seandainya sebagaimana sedemikian sebagainya
        kemudian kemarinnya sekarang berkelanjutan berlangganan berpengalaman berpengetahuan berkualitas berkoordinasi
        berkomunikasi berkesempatan berkepentingan berkemampuan berpendapatan berpendidikan berkembang berpartisipasi
        berkonsultasi berterimakasih bersamaan berdiskusi berbelanja beristirahat berolahraga bepergian bersemangat
        berhadapan berdekatan bersebelahan bertetangga bertumbuh berpotensi bersahabat bersedia bersungguh bersyukur
        memberikan membacakan menuliskan memikirkan membalasnya membahasnya membuktikan membangun membandingkan membantu
        membiasakan membersihkan membicarakan membukanya membekukan memberitahu membelikan membawaan membiarkan memelihara
        memerlukan memesanmu memestikan memfokuskan menggantikan menggunakan menghasilkan mengharapkan menghubungi
        menghadiri menghematkan menghindari menghiburkan mengingatkan mengizinkan mengonfirmasi mengirimkan mengoleskan
        mengubahku mengundangku mengunggahnya mengunduhnya menguranginya mengusulkan mengutamakan menjalankan menjelaskan
        menjaminnya menjadwalkan menjadikan menjauhinya menjemputnya menjanjikan menjaganya menjagaan menanyakan menanggapi
        menawarkan menampilkan menandatangani menambahkan menantikan menangkapnya menaruhnya mencari-cari menceritakan
        mencobanya menciptakan mencukupi mendampingi mendengarkan mendapatkan mendaftarkan mendahulukan mendekatkan
        mendukungnya menentukan menerapkan menerimaan meneruskan menertibkan menegaskan mengetahui mengakhiri
        mengakibat mengabarkan mengadakan mengagumkan mengajarkan mengalihkan mengamankan mengambilan mengamati
        mengandalkan mengangkat mengantarkan mengarahkan mengartikan mengatasinya mengaturkan mengawalinya mengevaluasi
        mengetahui menggalang menggambar menganggap mengganggu menggeluti menggerakkan menggugahku menggunkan
        menghentikan menghendaki menghormati mengidentifi mengimbangi mengindikasi menginformas menginisiasi menginstal
        menginstruksi mengintegras menginteraksi menginvestasi mengisikan mengklaimnya mengklikku mengoleksi
        mengomunikas mengondisikan mengontrolku mengoperasikan mengoptimalk mengorganisir mengorbankan mengulanginya
        mengumpulkan mengumumkan mengunci-nya mengunggulkan menguntungkan mengupayakan mengurusinya mengusahakan
        mengusungku mengutarakan menidakkan menimbangku menindaklanj meningkatkan meninjauku menikmati menuliskan
        menunjukkan menunjangku menuntaskan menurunkan menuntutku menutupinya menyadarinya menyakinkan menyalakan
        menyampaikan menyamakan menyambutku menyanggupi menyangkalnya menyarankan menyatakan menyatukan menyediakan
        menyediakan menyeimbangkan menyelaraskan menyelenggara menyelesaikan menyembuhkan menyempatkan menyempurna
        menyenangkan menyerahkan menyertakan menyesalkan menyesuaikan menyetujui menyiapkan menyimakku menyimpan
        menyinggung menyingkat menyisihkan menyukainya menyusulnya menyusunnya merangkum merayakan merencanakan
        merespons meringankan merugikannya merasakan merumuskan masyarakat mahasiswa manajemen matematika mekanisme
        metodologi memaksimalkan mempelajarinya memperbarui memperbaiki memperbesar mempercepat memperdalam
        memperhatikan memperjelas memperkuat memerlukan memperluas mempermainkan memperpanjang mempersiap mempertahan
        mempertimbang mempertinggi memperuncing penawaran pencapaian pencatatan pencegahan pencarian pencerahan
        pendekatan pendapatan pendaftaran pendamping pendukung penelitian penerimaan penerapan penerbitan pengabdian
        pengalaman pengamanan pengamatan pengambilan pengangkatan pengantar pengaturan pengecekan pengelola
        pengeluaran pengembangan pengendalian pengenalan pengetahuan penggunaan penghasilan penghargaan penghematan
        penghentian penghubung pengiklanan pengisian pengiriman pengumuman pengurangan pengukuran pengumpulan
        pengunduhan pengunggahan pengurusan peninggalan peningkatan peninjauan penjadwalan penjelasan penjualan
        penolakan penulisan penunjukan penurunan penutupan penyajian penyaluran penyampaian penyaringan penyediaan
        penyelidikan penyelesaian penyerahan penyertaan penyesuaian penyetoran penyusunan penyuluhan perancangan
        peraturan peralatan perananmu perayaanmu perbankan perbaikan perbedaan perbuatan percakapan percaya
        perencanaan pergantian perhatian perhotelan periklanan perincian perintah peringatan perjalanan
        perjanjian perkiraan perkuliahan perlengkapan perlakuan perlombaan permintaan permainan permohonan
        permulaan pernikahan perolehan perpaduan perpindahan persahabatan persamaan persediaan persekutuan
        persentase persetujuan persiapan persidangan persoalan personalia perspektif pertanian pertambahan
        pertandingan pertanyaan pertukaran pertumbuhan perubahan perumahan perusahaan perwakilan perwujudan
        pesananmu pesertanya petunjukku pelatihan pelaporan pelayanan peluncuran pemakaian pemaksaan
        pemantauan pemasaran pemasangan pemastian pembagian pembaharuan pembahasan pembatalan pembatasan
        pembayaran pembedaan pembekalan pembelaan pembelian pembentuk pemberian pembersihan pembiasaan
        pembicaraan pembinaan pembiayaan pemblokiran pembongkaran pembuatan pembukuan pembukaan pembuktian
        pemelihara pemenuhan pemeriksa pemerintahan pemesanan pemindahan pemilihan pemikiran pemimpin
        pemisahan pemotongan pemrosesan pemulihan pemungutan pemusatan pemutaran penagihan penahanan
    """.trimIndent().split(Regex("\\s+"))
        .map { it.trim().lowercase() }
        .filter { it.length >= 8 && it.all { ch -> ch.isLetter() } }
        .distinct()

    val english = """
        absolutely acceptance accessible accomplish accordance accounting accurately achievement acquisition activation
        activities additional addressing adjustment admiration admission advantage adventure advertise agreement
        algorithm alignment allocation allowance alongside alternative altogether amazingness ambassador amendment
        amounting analysis analytics ancestor announcement annoyance answering anywhere apologize apparent
        appearance appendix application appointment appreciate approach approval architect archive argument
        arrangement articulation assembly assessment assignment assistance assistant associate assumption assurance
        atmosphere attachment attempting attendance attention attitude attorney attractive attribute audience
        authority authorize automated automatic available awareness awesome background balancing bandwidth
        basically basketball beautiful becoming beginning behavior believing benchmark beneficial benefit
        beside-me bettering beverage biography birthday blessing blueprint bookmark boundary brainstorming
        breakfast breakthrough breathing brightest bringing broadcast browsing budgeting building bulletin
        business calendar campaign candidate capability capacity carefully carriage category celebrate
        celebrity certainly challenge champion changing channels character charming checking checklist
        chemical childhood chocolate choosing circular circumstance citizen clarifying classical classroom
        cleaning clearance clearly client-side climbing clinical closing clothing coaching cognitive
        collapse colleague collect collection collector college collision colorful combination comfort
        commanding commerce commercial commission commitment committee commodity communicate community
        company compare comparison compatible compel-to compensate compete competent compile complain
        complaint complete completely completion complex compliance component compose composer composite
        compound comprehensive compromise computer conceive concentrate concept concern concerned conclude
        conclusion concrete condition conduct conference confident confirm confirmation conflict conform
        confront confuse confusion connect connection conquer conscious consensus consent consequence
        consider considerable consist consistent console constant construct consult consultation consume
        consumer consumption contact contain container contemporary content contest context continue
        contract contrast contribute control convenience convention converge conversation convert convince
        cookie-jar cooperate coordinate copyright corporate correct correctly correspond corridor cosmetic
        cost-free counseling countless countryside couple-up coursing courtesy coverage creating creation
        creative creativity creature credibility credit-card criminal criteria critical criticism critique
        crossing crowding crucial crystal cultural currency current currently curriculum customer
        customize cycling-up cylinder database daughter deadline dealings debating debugger december
        decision decisive declaration decrease dedicate deep-dive default defeating defender defense
        deficiency definite definition delegate deletion delicate delivered delivery demanding democracy
        demonstrate department departure dependent deployment deposit depressed describe description
        designer desirable desktop desperate despite-it destination destroy detailed detected developer
        developing development deviation device-id diagnose diagram dialogue diamond dictionary
        different difficult difficulty digital diligence dimension diminish dining-out dinosaur diploma
        directed direction directly director directory disabled disagree disappear disaster discover
        discovery discrete discussed discussion disease-free disorder dispatch display disposal dispute
        distance distinct distribute district disturb diversity dividing division document documentary
        domestic dominate donation double-up doubting download downwards drafting dramatic dramatically
        drawing-in dreaming dressing drilling drinking driving-up dropping duration dynamic-ui
    """.trimIndent().split(Regex("\\s+"))
        .map { it.trim().lowercase() }
        .filter { it.length >= 8 && it.all { ch -> ch.isLetter() } }
        .distinct()
}