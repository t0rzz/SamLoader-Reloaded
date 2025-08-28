package app.samloader.common.download

import app.samloader.common.fus.FusClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Simple shared downloader that streams from FUS cloud and reports progress. */
object DownloadManager {
    data class Result(val bytes: Long)

    /**
     * Stream a file from FUS cloud into the provided write lambda.
     * - modelPathAndName: path+name from BinaryInform (e.g., MODEL_PATH + BINARY_NAME)
     * - start: starting byte (for resume), default 0
     * - endInclusive: optional range end
     * - onProgress: callback with delta bytes written
     */
    suspend fun download(
        fus: FusClient,
        modelPathAndName: String,
        start: Long = 0L,
        endInclusive: Long? = null,
        write: (ByteArray) -> Unit,
        onProgress: (Int) -> Unit = {},
    ): Result = withContext(Dispatchers.Default) {
        val flow = fus.downloadBinary(modelPathAndName, start, endInclusive)
        var total = 0L
        for (chunk in flow.chunks) {
            write(chunk)
            total += chunk.size
            onProgress(chunk.size)
        }
        Result(total)
    }
}
