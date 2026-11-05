package com.edukasi.ftpmanager.data.client

import com.edukasi.ftpmanager.data.model.FtpFileItem
import com.edukasi.ftpmanager.data.model.FtpServerProfile
import com.edukasi.ftpmanager.data.server.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class FtpClientManager {

    private var ftpClient: FTPClient? = null
    var activeProfile: FtpServerProfile? = null
        private set

    val isConnected: Boolean
        get() = ftpClient?.isConnected == true

    suspend fun connect(profile: FtpServerProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()

            val client = FTPClient().apply {
                defaultTimeout = 15000
                connectTimeout = 15000
                setDataTimeout(30000)
                controlEncoding = profile.encoding
            }

            client.connect(profile.host, profile.port)
            val replyCode = client.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                client.disconnect()
                return@withContext Result.failure(Exception("Server menolak koneksi (kode: $replyCode)"))
            }

            val loggedIn = if (profile.isAnonymous) {
                client.login("anonymous", "guest@example.com")
            } else {
                client.login(profile.username, profile.password)
            }

            if (!loggedIn) {
                client.disconnect()
                return@withContext Result.failure(Exception("Login gagal: Username atau password salah"))
            }

            if (profile.passiveMode) {
                client.enterLocalPassiveMode()
            } else {
                client.enterLocalActiveMode()
            }

            client.setFileType(FTP.BINARY_FILE_TYPE)

            if (profile.initialPath.isNotBlank() && profile.initialPath != "/") {
                client.changeWorkingDirectory(profile.initialPath)
            }

            ftpClient = client
            activeProfile = profile
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            if (ftpClient?.isConnected == true) {
                try {
                    ftpClient?.logout()
                } catch (_: Exception) {}
                ftpClient?.disconnect()
            }
        } catch (_: Exception) {
        } finally {
            ftpClient = null
            activeProfile = null
        }
    }

    suspend fun getCurrentDirectory(): String = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext "/"
        try {
            client.printWorkingDirectory() ?: "/"
        } catch (e: Exception) {
            "/"
        }
    }

    suspend fun listFiles(path: String? = null): Result<List<FtpFileItem>> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            val target = path ?: client.printWorkingDirectory() ?: "/"
            val ftpFiles: Array<FTPFile> = client.listFiles(target) ?: emptyArray()

            val items = ftpFiles
                .filter { it.name != "." && it.name != ".." }
                .map { f ->
                    val isDir = f.isDirectory
                    val size = if (isDir) 0L else f.size
                    val timeMillis = f.timestamp?.timeInMillis ?: 0L
                    val itemPath = if (target.endsWith("/")) "$target${f.name}" else "$target/${f.name}"

                    FtpFileItem(
                        name = f.name,
                        path = itemPath,
                        isDirectory = isDir,
                        size = size,
                        formattedSize = if (isDir) "" else NetworkUtils.formatFileSize(size),
                        lastModified = timeMillis,
                        formattedDate = NetworkUtils.formatDate(timeMillis),
                        permissions = formatPermissions(f)
                    )
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changeDirectory(path: String): Result<String> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            val success = client.changeWorkingDirectory(path)
            if (success) {
                Result.success(client.printWorkingDirectory() ?: path)
            } else {
                Result.failure(Exception("Gagal membuka folder: $path"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changeToParent(): Result<String> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            val success = client.changeToParentDirectory()
            if (success) {
                Result.success(client.printWorkingDirectory() ?: "/")
            } else {
                Result.failure(Exception("Sudah di root direktori"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        item: FtpFileItem,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            destinationFile.parentFile?.mkdirs()

            val totalBytes = item.size
            var downloaded = 0L

            client.retrieveFileStream(item.name)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress(downloaded, totalBytes)
                    }
                    outputStream.flush()
                }
            } ?: return@withContext Result.failure(Exception("Gagal mengambil file stream dari FTP"))

            if (!client.completePendingCommand()) {
                return@withContext Result.failure(Exception("Server FTP tidak menyelesaikan perintah transfer"))
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            try { destinationFile.delete() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        inputStream: InputStream,
        remoteFileName: String,
        totalBytes: Long,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            var uploaded = 0L
            client.storeFileStream(remoteFileName)?.use { outputStream ->
                inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        uploaded += bytesRead
                        onProgress(uploaded, totalBytes)
                    }
                    outputStream.flush()
                }
            } ?: return@withContext Result.failure(Exception("Gagal membuka upload stream di server FTP"))

            if (!client.completePendingCommand()) {
                return@withContext Result.failure(Exception("Server FTP gagal menyelesaikan upload"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(dirName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            val ok = client.makeDirectory(dirName)
            if (ok) Result.success(Unit) else Result.failure(Exception("Gagal membuat folder $dirName"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            val ok = client.deleteFile(remotePath)
            if (ok) Result.success(Unit) else Result.failure(Exception("Gagal menghapus file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            val ok = client.removeDirectory(remotePath)
            if (ok) Result.success(Unit) else Result.failure(Exception("Gagal menghapus folder (pastikan folder kosong)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = ftpClient ?: return@withContext Result.failure(Exception("Klien belum terhubung"))
        try {
            val ok = client.rename(oldPath, newPath)
            if (ok) Result.success(Unit) else Result.failure(Exception("Gagal mengubah nama"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatPermissions(f: FTPFile): String {
        val sb = StringBuilder()
        sb.append(if (f.isDirectory) "d" else "-")
        sb.append(if (f.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) "r" else "-")
        sb.append(if (f.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) "w" else "-")
        sb.append(if (f.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION)) "x" else "-")
        return sb.toString()
    }
}

