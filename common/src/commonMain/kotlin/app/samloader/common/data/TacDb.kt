package app.samloader.common.data

import app.samloader.common.crypt.Crypt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

object TacDb {
    private const val TACS_URL = "https://raw.githubusercontent.com/zacharee/SamloaderKotlin/refs/heads/master/common/src/commonMain/moko-resources/files/tacs.csv"

    private val modelToTacs: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val initMutex = Mutex()

    private suspend fun ensureLoaded() {
        if (modelToTacs.isNotEmpty()) return
        initMutex.withLock {
            if (modelToTacs.isNotEmpty()) return
            val client = HttpClient {
                install(Logging)
                install(ContentNegotiation) { json() }
                install(HttpTimeout) {
                    requestTimeoutMillis = 10000
                    connectTimeoutMillis = 5000
                    socketTimeoutMillis = 10000
                }
                expectSuccess = true
            }
            try {
                val text: String = client.get(TACS_URL).body()
                val rows = text.split('\n')
                if (rows.isNotEmpty()) {
                    val header = rows.first().split(',').map { it.trim().lowercase() }
                    val iModel = header.indexOf("model")
                    val iTac = header.indexOf("tac")
                    if (iModel >= 0 && iTac >= 0) {
                        val seen = mutableSetOf<Pair<String,String>>()
                        rows.drop(1).forEach { line ->
                            val cols = line.split(',')
                            if (cols.size <= maxOf(iModel, iTac)) return@forEach
                            val model = cols[iModel].trim().uppercase()
                            val tac = cols[iTac].trim()
                            if (tac.length < 8 || tac.any { it !in '0'..'9' }) return@forEach
                            val key = model to tac
                            if (!seen.add(key)) return@forEach
                            modelToTacs.getOrPut(model) { mutableListOf() }.add(tac.take(8))
                        }
                    }
                }
            } catch (_: Throwable) {
                // ignore; remains empty (no TAC available)
            } finally {
                client.close()
            }
        }
    }

    suspend fun availableTacsForModel(model: String): List<String> {
        ensureLoaded()
        return modelToTacs[model.trim().uppercase()].orEmpty()
    }

    suspend fun generateImeiFromModel(model: String): String? {
        val tacs = availableTacsForModel(model)
        if (tacs.isEmpty()) return null
        val tac = tacs.random()
        val snr = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
        val body = tac.take(8) + snr
        val chk = Crypt.luhnChecksum(body)
        return body + chk.toString()
    }
}