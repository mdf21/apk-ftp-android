package com.edukasi.ftpmanager.data.model

sealed class TransferStatus {
    object Idle : TransferStatus()
    data class InProgress(
        val fileName: String,
        val isUpload: Boolean,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val percent: Int
    ) : TransferStatus()
    data class Success(val message: String) : TransferStatus()
    data class Error(val error: String) : TransferStatus()
}

