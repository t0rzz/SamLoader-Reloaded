package app.samloader.common.crypto

import korlibs.crypto.AES
import korlibs.crypto.Padding

actual fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    // PKCS7 padding per server contract
    return AES.decryptCbc(input, key, iv, padding = Padding.PKCS7)
}

actual fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    return AES.encryptCbc(input, key, iv, padding = Padding.PKCS7)
}

actual fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray {
    // No padding at this layer; caller handles PKCS7 for last block
    return AES.decryptEcb(input, key)
}
