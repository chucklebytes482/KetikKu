package com.example

import com.example.data.Vx2Codec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Vx2CodecTest {
    private val pw = "kata-sandi-uji-12".toByteArray(Charsets.UTF_8)

    @Test
    fun roundTripSmall() {
        val plain = "[{\"kata\":\"halo\"}]".toByteArray(Charsets.UTF_8)
        val sealed = Vx2Codec.encrypt(plain, pw)
        assertEquals("VAULTX04", String(sealed, 0, 8, Charsets.US_ASCII))
        assertArrayEquals(plain, Vx2Codec.decrypt(sealed, pw))
    }

    @Test
    fun roundTripEmpty() {
        assertArrayEquals(ByteArray(0), Vx2Codec.decrypt(Vx2Codec.encrypt(ByteArray(0), pw), pw))
    }

    @Test
    fun roundTripMultipleChunks() {
        val plain = ByteArray((1 shl 20) + 5) { (it * 31).toByte() }
        assertArrayEquals(plain, Vx2Codec.decrypt(Vx2Codec.encrypt(plain, pw), pw))
    }

    @Test
    fun wrongPassphraseFails() {
        val sealed = Vx2Codec.encrypt("[]".toByteArray(), pw)
        assertThrows(IllegalStateException::class.java) {
            Vx2Codec.decrypt(sealed, "passphrase-salah-xx".toByteArray())
        }
    }

    @Test
    fun tamperedBodyFails() {
        val sealed = Vx2Codec.encrypt("[]".toByteArray(), pw)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
        assertThrows(IllegalStateException::class.java) { Vx2Codec.decrypt(sealed, pw) }
    }

    @Test
    fun notVx2Rejected() {
        assertThrows(IllegalArgumentException::class.java) { Vx2Codec.decrypt("[]".toByteArray(), pw) }
    }

    /** Berkas yang dibuat oleh referensi Python VAULTX04 (kata sandi "kata-sandi-uji-12"). */
    @Test
    fun decryptsReferenceFixture() {
        val hex = "5641554c54583034f0edb9101535a79343d964d5a12cf7026239c021593c18d762a193f03a82627f5e1525b21b6513c08e1631f7eab53d2b"
        val data = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        assertEquals("{\"a\":1}é", String(Vx2Codec.decrypt(data, pw), Charsets.UTF_8))
    }
}
