package app.samloader.common.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.size_tVar
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CCOptions
import platform.CommonCrypto.CCOperation
import platform.CommonCrypto.CCCrypt
import platform.CommonCrypto.kCCAlgorithmAES
import platform.CommonCrypto.kCCBlockSizeAES128
import platform.CommonCrypto.kCCDecrypt
import platform.CommonCrypto.kCCEncrypt
import platform.CommonCrypto.kCCOptionECBMode
import platform.CommonCrypto.kCCOptionPKCS7Padding
import platform.CommonCrypto.kCCSuccess

@OptIn(ExperimentalForeignApi::class)
private fun ccCrypt(
    operation: CCOperation,
    options: CCOptions,
    input: ByteArray,
    key: ByteArray,
    iv: ByteArray? = null,
): ByteArray = memScoped {
    require(key.isNotEmpty()) { "AES key must not be empty" }
    val outCapacity = input.size + kCCBlockSizeAES128.toInt()
    val out = ByteArray(outCapacity)

    var statusCode = -1
    val outLenVar = alloc<size_tVar>()

    input.usePinned { inPinned ->
        key.usePinned { keyPinned ->
            out.usePinned { outPinned ->
                statusCode = if (iv != null) {
                    iv.usePinned { ivPinned ->
                        CCCrypt(
                            operation,
                            kCCAlgorithmAES.convert(),
                            options,
                            keyPinned.addressOf(0), key.size.convert(),
                            ivPinned.addressOf(0),
                            inPinned.addressOf(0), input.size.convert(),
                            outPinned.addressOf(0), outCapacity.convert(),
                            outLenVar.ptr
                        ).toInt()
                    }
                } else {
                    CCCrypt(
                        operation,
                        kCCAlgorithmAES.convert(),
                        options,
                        keyPinned.addressOf(0), key.size.convert(),
                        null,
                        inPinned.addressOf(0), input.size.convert(),
                        outPinned.addressOf(0), outCapacity.convert(),
                        outLenVar.ptr
                    ).toInt()
                }
            }
        }
    }

    if (statusCode != kCCSuccess.toInt()) {
        throw IllegalStateException("CCCrypt failed with status $statusCode")
    }

    val outLen = outLenVar.value.convert<Int>()
    return ByteArray(outLen).apply { out.copyInto(this, endIndex = outLen) }
}

actual fun aesCbcDecrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    ccCrypt(kCCDecrypt.convert(), (kCCOptionPKCS7Padding).convert(), input, key, iv)

actual fun aesCbcEncrypt(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
    ccCrypt(kCCEncrypt.convert(), (kCCOptionPKCS7Padding).convert(), input, key, iv)

// No padding for ECB here; caller handles PKCS#7 unpadding at the end of the stream
actual fun aesEcbDecrypt(input: ByteArray, key: ByteArray): ByteArray =
    ccCrypt(kCCDecrypt.convert(), (kCCOptionECBMode).convert(), input, key, null)
