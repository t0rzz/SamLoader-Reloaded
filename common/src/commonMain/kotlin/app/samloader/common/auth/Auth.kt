package app.samloader.common.auth

import app.samloader.common.request.RequestBuilder
import korlibs.crypto.MD5
import korlibs.crypto.AES
import korlibs.crypto.Padding
import korlibs.encoding.Base64

/**
 * KMP Auth helpers (nonce decrypt, signature derivation), ported from Python auth.py.
 */
object Auth {
    // Constants from Python version
    private const val KEY_1 = "vicopx7dqu06emacgpnpy8j8zwhduwlh"
    private const val KEY_2 = "9u7qab84rpc16gvk"

    /**
     * Calculate the AES key from the FUS input nonce (string), per Python derive_key().
     * First 16 bytes are offsets into KEY_1, then KEY_2 (16 bytes) appended.
     */
    fun deriveKey(nonce: String): ByteArray {
        val sb = StringBuilder()
        for (i in 0 until 16) {
            val idx = nonce[i].code % 16
            sb.append(KEY_1[idx])
        }
        sb.append(KEY_2)
        return sb.toString().encodeToByteArray()
    }

    /**
     * Decrypt the server NONCE (Base64) to a 32-char string.
     */
    fun decryptNonceBase64(encB64: String): String {
        val enc = Base64.decode(encB64)
        val key = KEY_1.encodeToByteArray()
        val iv = key.copyOf(16)
        val dec = AES.decryptCbc(enc, key, iv, padding = Padding.PKCS7)
        return dec.decodeToString()
    }

    /**
     * Build the Authorization signature using AES-CBC over the plain nonce and return Base64 string.
     */
    fun getAuthSignature(noncePlain: String): String {
        val nkey = deriveKey(noncePlain)
        val iv = nkey.copyOf(16)
        val enc = AES.encryptCbc(noncePlain.encodeToByteArray(), nkey, iv, padding = Padding.PKCS7)
        return Base64.encode(enc)
    }

    /**
     * Calculate the V4 decryption key: MD5(getlogiccheck(fwver, logicVal)).
     */
    fun v4KeyFromServer(fwVersion: String, logicVal: String): ByteArray {
        val logic = RequestBuilder.getLogicCheck(fwVersion, logicVal)
        return MD5.digest(logic.encodeToByteArray()).bytes
    }

    /**
     * Calculate the V2 key: MD5("region:model:version").
     */
    fun v2Key(version: String, model: String, region: String): ByteArray {
        val s = "$region:$model:$version"
        return MD5.digest(s.encodeToByteArray()).bytes
    }
}
