package com.edukasi.ftpmanager.data.model

data class FtpFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val formattedSize: String = "",
    val lastModified: Long = 0L,
    val formattedDate: String = "",
    val permissions: String = ""
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

