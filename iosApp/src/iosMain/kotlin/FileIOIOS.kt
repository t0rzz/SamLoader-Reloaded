import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.size_t

object FileIOIOS {
    data class IOSInput(val stream: NSInputStream, val stopAccess: () -> Unit)
    data class IOSOutput(val stream: NSOutputStream, val stopAccess: () -> Unit)

    fun fileLength(urlStr: String): Long? {
        val url = NSURL.URLWithString(urlStr) ?: return null
        val path = url.path ?: return null
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = err.ptr)
            val size = attrs?.get(NSFileSize) as? NSNumber
            return size?.longLongValue
        }
    }

    private fun parseUrl(urlStr: String?): NSURL? = urlStr?.let { NSURL.URLWithString(it) }

    fun childUrl(folderUrlStr: String, filename: String): String? {
        val base = parseUrl(folderUrlStr) ?: return null
        val child = base.URLByAppendingPathComponent(filename)
        return child?.absoluteString
    }

    fun openInput(urlStr: String): IOSInput? {
        val url = parseUrl(urlStr) ?: return null
        val needStop = if (url.startAccessingSecurityScopedResource() == true) true else false
        val stream = NSInputStream.inputStreamWithURL(url) ?: run { if (needStop) url.stopAccessingSecurityScopedResource(); return null }
        stream.open()
        val stopper = { if (needStop) url.stopAccessingSecurityScopedResource(); stream.close() }
        return IOSInput(stream, stopper)
    }

    fun openOutput(urlStr: String, append: Boolean = false): IOSOutput? {
        val url = parseUrl(urlStr) ?: return null
        val needStop = if (url.startAccessingSecurityScopedResource() == true) true else false
        // Ensure file exists
        val fm = NSFileManager.defaultManager
        val path = url.path
        if (path != null && !fm.fileExistsAtPath(path)) {
            fm.createFileAtPath(path, contents = null, attributes = null)
        }
        val stream = NSOutputStream.outputStreamWithURL(url, append = append) ?: run { if (needStop) url.stopAccessingSecurityScopedResource(); return null }
        stream.open()
        val stopper = { if (needStop) url.stopAccessingSecurityScopedResource(); stream.close() }
        return IOSOutput(stream, stopper)
    }

    fun readChunk(stream: NSInputStream, maxLen: Int = 4096): ByteArray? {
        if (maxLen <= 0) return null
        val buf = ByteArray(maxLen)
        var readCount = 0
        buf.usePinned { pinned ->
            readCount = stream.read(pinned.addressOf(0), maxLen.toULong().toInt())
        }
        if (readCount <= 0) return null
        return if (readCount == buf.size) buf else buf.copyOf(readCount)
    }

    fun writeChunk(stream: NSOutputStream, data: ByteArray): Int {
        var written = 0
        data.usePinned { pinned ->
            written = stream.write(pinned.addressOf(0), data.size.toULong().toInt())
        }
        return written
    }
}
