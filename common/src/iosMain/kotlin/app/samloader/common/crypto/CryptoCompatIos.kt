package app.samloader.common.crypto

import korlibs.crypto.AES
import korlibs.crypto.Padding

actual fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    AES.decryptCBC(input, key, iv, padding = Padding.PKCS7Padding)

actual fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    AES.encryptCBC(input, key, iv, padding = Padding.PKCS7Padding)

actual fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray =
    AES.decryptECB(input, key)
