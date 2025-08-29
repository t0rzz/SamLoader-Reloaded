package dev.t0rzz.samloaderreloaded.ui

import app.samloader.common.version.VersionFetch

internal fun formatLatest(ver: String): String {
    val norm = VersionFetch.normalize(ver)
    val parts = norm.split('/')
    val ap = parts.getOrNull(0) ?: "-"
    val csc = parts.getOrNull(1) ?: "-"
    val cp = parts.getOrNull(2) ?: "-"
    val build = parts.getOrNull(3) ?: "-"
    return "AP: $ap\nCSC: $csc\nCP: $cp\nBuild: $build"
}
