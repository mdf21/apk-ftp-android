package com.edukasi.ftpmanager.data.server

import com.edukasi.ftpmanager.data.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors

class SimpleFtpServer(
    private val config: ServerConfig,
    private val onLog: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val threadPool = Executors.newCachedThreadPool()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return

        val rootDir = File(config.rootPath)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        try {
            serverSocket = ServerSocket(config.port)
            isRunning = true
            onLog("Server FTP aktif di port ${config.port}")
            onLog("Root direktori: ${config.rootPath}")

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        val handler = FtpSessionHandler(clientSocket, config, onLog)
                        threadPool.execute(handler)
                    } catch (e: Exception) {
                        if (isRunning) {
                            onLog("Error menerima koneksi: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isRunning = false
            onLog("Gagal memulai server di port ${config.port}: ${e.message}")
            throw e
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        serverJob?.cancel()
        serverJob = null

        threadPool.shutdownNow()
        onLog("Server FTP telah dinonaktifkan")
    }
}

