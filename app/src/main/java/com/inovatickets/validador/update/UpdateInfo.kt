package com.inovatickets.validador.update

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val artifactName: String,
    val size: Long,
    val sha256: String,
    val download_url: String,
    val release_tag: String? = null,
    val repo: String? = null
)