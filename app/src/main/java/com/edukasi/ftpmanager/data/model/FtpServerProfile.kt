package com.edukasi.ftpmanager.data.model

import java.util.UUID

data class FtpServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 21,
    val username: String = "anonymous",
    val password: String = "",
    val isAnonymous: Boolean = false,
    val passiveMode: Boolean = true,
    val encoding: String = "UTF-8",
    val initialPath: String = "/"
)

