package dev.t0rzz.samloaderreloaded.ui.downloader

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object CscLoader {
    data class Csc(val code: String, val countries: String?, val carriers: String?)

    fun load(context: Context): List<Csc> {
        return try {
            context.assets.open("cscs.csv").use { input ->
                val reader = BufferedReader(InputStreamReader(input))
                reader.lineSequence()
                    .drop(1) // skip header
                    .mapNotNull { line ->
                        val parts = line.split(',')
                        if (parts.isEmpty()) null else {
                            val code = parts.getOrNull(0)?.trim().orEmpty()
                            if (code.isEmpty()) null else Csc(
                                code,
                                parts.getOrNull(1)?.trim(),
                                parts.getOrNull(2)?.trim()
                            )
                        }
                    }
                    .toList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
