@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.samloader.common.crypto

import kotlinx.cinterop.*
import platform.CommonCrypto.*
import platform.posix.size_tVar

actual fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    ccAesCrypt(op = kCCDecrypt, data = input, key = key, iv = iv, options = kCCOptionPKCS7Padding)

actual fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    ccAesCrypt(op = kCCEncrypt, data = input, key = key, iv = iv, options = kCCOptionPKCS7Padding)

actual fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray =
    ccAesCrypt(op = kCCDecrypt, data = input, key = key, iv = null, options = kCCOptionECBMode)

private fun ccAesCrypt(
    op: CCOperation,
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray?,
    options: CCOptions
): ByteArray {
    require(key.size == 16 || key.size == 24 || key.size == 32) { "Invalid AES key size: ${'$'}{key.size}" }
    memScoped {
        val outCapacity = data.size + kCCBlockSizeAES128.toInt()
        val out = ByteArray(outCapacity)
        val outCount = alloc<size_tVar>()
        var result: ByteArray? = null
        data.usePinned { dataPinned ->
            key.usePinned { keyPinned ->
                out.usePinned { outPinned ->
                    val ivPinned = iv?.pin()
                    try {
                        val status = CCCrypt(
                            op,
                            kCCAlgorithmAES128,
                            options,
                            keyPinned.addressOf(0),
                            key.size.toULong(),
                            ivPinned?.addressOf(0),
                            dataPinned.addressOf(0),
                            data.size.toULong(),
                            outPinned.addressOf(0),
                            out.size.toULong(),
                            outCount.ptr
                        )
                        check(status == kCCSuccess) { "CCCrypt failed: ${'$'}status" }
                        result = out.copyOf(outCount.value.toInt())
                    } finally {
                        ivPinned?.unpin()
                    }
                }
            }
        }
        return result!!
    }
}
