package com.bizzeh.bruce.huggingface

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherDeviceTest {
    private val cipher = KeystoreTokenCipher(alias = "bruce.test_token")
    private val token = "hf_example_token_value".toByteArray()

    @Test
    fun roundTrips() {
        assertArrayEquals(token, cipher.decrypt(cipher.encrypt(token)))
    }

    @Test
    fun ciphertextIsNotThePlainTokenAndUsesAFreshIv() {
        val first = cipher.encrypt(token)
        val second = cipher.encrypt(token)

        assertFalse(first.toString(Charsets.ISO_8859_1).contains("hf_example"))
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun tamperedOrTruncatedDataIsRejected() {
        val sealed = cipher.encrypt(token)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()

        assertNull(cipher.decrypt(sealed))
        assertNull(cipher.decrypt(ByteArray(12)))
    }
}
