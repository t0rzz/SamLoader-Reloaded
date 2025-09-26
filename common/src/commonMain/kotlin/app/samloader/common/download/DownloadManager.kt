package app.samloader.common.download

import app.samloader.common.fus.FusClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlin.math.min

/** Simple shared downloader that streams from FUS cloud and can download in parallel. */
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
        val flowChunks = fus.downloadBinary(modelPathAndName, start, endInclusive).chunks
        var total = 0L
        flowChunks.collect { chunk: ByteArray ->
            write(chunk)
            total += chunk.size.toLong()
            onProgress(chunk.size)
        }
        Result(total)
    }

    /**
     * Parallel segmented downloader that honors the given threads count by performing concurrent Range requests.
     * The provided 'write' lambda is invoked sequentially in file order; no random access is required by the caller.
     *
     * @param size total file size in bytes (required for segmentation)
     * @param threads desired max concurrency (<= 1 falls back to single-threaded download())
     */
    suspend fun downloadWithThreads(
        fus: FusClient,
        modelPathAndName: String,
        size: Long,
        threads: Int,
        write: (ByteArray) -> Unit,
        onProgress: (Int) -> Unit = {},
    ): Result = withContext(Dispatchers.Default) {
        // Fallback to single-thread if threads <= 1 or size unknown/invalid
        if (threads <= 1 || size <= 0L) {
            return@withContext download(fus, modelPathAndName, 0L, null, write, onProgress)
        }

        data class Segment(val start: Long, val endInclusive: Long)

        // Build segment list (reduced segment size to limit per-thread memory usage)
        val segmentSize = 2L * 1024L * 1024L // 2 MiB per segment for safer memory footprint across platforms
        val segments = mutableListOf<Segment>()
        var pos = 0L
        while (pos < size) {
            val end = min(size - 1L, pos + segmentSize - 1L)
            segments.add(Segment(pos, end))
            pos = end + 1L
        }

        val maxConcurrency = threads.coerceAtLeast(1)
        val outChan = Channel<Pair<Long, ByteArray>>(capacity = maxConcurrency * 2)
        val segChan = Channel<Segment>(capacity = maxConcurrency * 4)

        // Feed all segments into a bounded channel
        val producer = launch {
            for (seg in segments) segChan.send(seg)
            segChan.close()
        }

        // Worker coroutines: fetch segments concurrently (up to maxConcurrency)
        val jobs = List(maxConcurrency) {
            async(Dispatchers.IO) {
                for (seg in segChan) {
                    // Stream this segment and accumulate into a single buffer to send once per segment
                    val chunks = fus.downloadBinary(modelPathAndName, seg.start, seg.endInclusive).chunks
                    val totalLenLong = seg.endInclusive - seg.start + 1
                    val expectedLen = if (totalLenLong > Int.MAX_VALUE) Int.MAX_VALUE else totalLenLong.toInt()
                    var buf = ByteArray(expectedLen)
                    var offset = 0
                    chunks.collect { chunk ->
                        // Ensure capacity (should not be needed with expectedLen, but guard anyway)
                        if (offset + chunk.size > buf.size) {
                            buf = buf.copyOf(maxOf(buf.size * 2, offset + chunk.size))
                        }
                        chunk.copyInto(destination = buf, destinationOffset = offset, startIndex = 0, endIndex = chunk.size)
                        offset += chunk.size
                    }
                    val toSend = if (offset == buf.size) buf else buf.copyOf(offset)
                    outChan.send(seg.start to toSend)
                }
            }
        }

        // Writer: ensure in-order writes
        var nextStart = 0L
        var writtenTotal = 0L
        val pending = mutableMapOf<Long, ByteArray>()
        var received = 0
        val expected = segments.size

        while (received < expected) {
            val (segStart, data) = outChan.receive()
            pending[segStart] = data
            received += 1
            // Write any contiguous ready segments
            while (true) {
                val ready = pending.remove(nextStart) ?: break
                write(ready)
                writtenTotal += ready.size
                onProgress(ready.size)
                nextStart += ready.size
            }
        }
        // Ensure all producer jobs completed before closing channel
        jobs.awaitAll()
        outChan.close()

        // In case the last received filled gaps enabling more writes
        while (true) {
            val ready = pending.remove(nextStart) ?: break
            write(ready)
            writtenTotal += ready.size
            onProgress(ready.size)
            nextStart += ready.size
        }

        Result(writtenTotal)
    }
}
