package app.samloader.common.crypto

// Expect/Actual shims for AES operations to avoid API differences across platforms.

expect fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
expect fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
expect fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray
