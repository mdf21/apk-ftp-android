package com.edukasi.ftpmanager.data.model

data class ServerConfig(
    val port: Int = 2121,
    val username: String = "admin",
    val password: String = "123456",
    val isAnonymous: Boolean = false,
    val isReadOnly: Boolean = false,
    val rootPath: String = ""
)

