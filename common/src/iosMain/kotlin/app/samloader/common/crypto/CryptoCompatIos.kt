package app.samloader.common.crypto

import korlibs.crypto.AES
import korlibs.crypto.Padding

actual fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    AES.decryptCbc(input, key, iv, padding = Padding.PKCS7)

actual fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    AES.encryptCbc(input, key, iv, padding = Padding.PKCS7)

actual fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray =
    AES.decryptEcb(input, key)
