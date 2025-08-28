package app.samloader.common.crypto

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private fun cipher(transformation: String, mode: Int, key: ByteArray, iv: ByteArray? = null): Cipher {
    val c = Cipher.getInstance(transformation)
    val sk = SecretKeySpec(key, "AES")
    if (iv != null) {
        c.init(mode, sk, IvParameterSpec(iv))
    } else {
        c.init(mode, sk)
    }
    return c
}

actual fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    // PKCS5Padding is compatible with PKCS7 for 16-byte block size
    val c = cipher("AES/CBC/PKCS5Padding", Cipher.DECRYPT_MODE, key, iv)
    return c.doFinal(input)
}

actual fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val c = cipher("AES/CBC/PKCS5Padding", Cipher.ENCRYPT_MODE, key, iv)
    return c.doFinal(input)
}

actual fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray {
    // No padding here; higher-level code handles PKCS7 unpad when needed
    val c = cipher("AES/ECB/NoPadding", Cipher.DECRYPT_MODE, key, null)
    return c.doFinal(input)
}
