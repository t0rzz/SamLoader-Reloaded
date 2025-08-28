package app.samloader.common.data

import app.samloader.common.crypt.Crypt
import kotlin.random.Random

object TacDb {
    // Placeholder in-memory map; TODO: load from remote CSV and cache
    private val modelToTacs = mapOf<String, List<String>>()

    fun availableTacsForModel(model: String): List<String> =
        modelToTacs[model.trim().uppercase()].orEmpty()

    fun generateImeiFromModel(model: String): String? {
        val tacs = availableTacsForModel(model)
        if (tacs.isEmpty()) return null
        val tac = tacs.random()
        val snr = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
        val body = tac.take(8) + snr
        val chk = Crypt.luhnChecksum(body)
        return body + chk.toString()
    }
}