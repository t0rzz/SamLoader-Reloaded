package app.samloader.common.crypto

import korlibs.crypto.AES
import korlibs.crypto.Padding

// iOS: use the same Korlibs AES implementation as linux/mingw to avoid CommonCrypto cinterop issues.
actual fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    return AES.decryptAesCbc(input, key, iv, padding = Padding.PKCS7Padding)
}

actual fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    return AES.encryptAesCbc(input, key, iv, padding = Padding.PKCS7Padding)
}

// No padding for ECB here; caller handles PKCS#7 unpadding at the higher level
actual fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray {
    return AES.decryptAesEcb(input, key, padding = Padding.NoPadding)
}
