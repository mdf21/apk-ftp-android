package com.edukasi.ftpmanager.data.server

import com.edukasi.ftpmanager.data.model.ServerConfig
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.StringTokenizer

class FtpSessionHandler(
    private val controlSocket: Socket,
    private val serverConfig: ServerConfig,
    private val onLog: (String) -> Unit
) : Runnable {

    private val rootDir = File(serverConfig.rootPath)
    private var currentDir = rootDir
    private var isAuthenticated = false
    private var pendingUsername: String? = null
    private var dataServerSocket: ServerSocket? = null
    private var activeDataSocketAddress: Pair<InetAddress, Int>? = null
    private var renameFromFile: File? = null
    private var restartOffset: Long = 0L

    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter

    override fun run() {
        val clientIp = controlSocket.inetAddress.hostAddress ?: "Unknown"
        onLog("Koneksi masuk dari $clientIp")

        try {
            reader = BufferedReader(InputStreamReader(controlSocket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(controlSocket.getOutputStream(), Charsets.UTF_8))

            sendReply(220, "Android FTP Manager Service Ready")

            while (!controlSocket.isClosed) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val spaceIdx = trimmed.indexOf(' ')
                val command = (if (spaceIdx != -1) trimmed.substring(0, spaceIdx) else trimmed).uppercase(Locale.ROOT)
                val arg = if (spaceIdx != -1) trimmed.substring(spaceIdx + 1).trim() else ""

                handleCommand(command, arg)
            }
        } catch (e: Exception) {
            onLog("Sesi terputus [$clientIp]: ${e.message}")
        } finally {
            closeDataConnection()
            try { controlSocket.close() } catch (_: Exception) {}
            onLog("Klien $clientIp keluar")
        }
    }

    private fun handleCommand(command: String, arg: String) {
        when (command) {
            "USER" -> handleUser(arg)
            "PASS" -> handlePass(arg)
            "QUIT" -> {
                sendReply(221, "Goodbye.")
                controlSocket.close()
            }
            "NOOP" -> sendReply(200, "NOOP ok.")
            "SYST" -> sendReply(215, "UNIX Type: L8")
            "FEAT" -> {
                writer.write("211-Features:\r\n")
                writer.write(" PASV\r\n")
                writer.write(" EPSV\r\n")
                writer.write(" UTF8\r\n")
                writer.write(" SIZE\r\n")
                writer.write(" MDTM\r\n")
                writer.write(" MLSD\r\n")
                writer.write(" REST STREAM\r\n")
                writer.write("211 End\r\n")
                writer.flush()
            }
            "OPTS" -> {
                if (arg.uppercase(Locale.ROOT).startsWith("UTF8")) {
                    sendReply(200, "Always in UTF8 mode.")
                } else {
                    sendReply(200, "OPTS OK.")
                }
            }
            "AUTH" -> sendReply(502, "Command not implemented (Plain FTP only).")
            else -> {
                if (!isAuthenticated) {
                    sendReply(530, "Please login with USER and PASS.")
                    return
                }
                handleAuthenticatedCommand(command, arg)
            }
        }
    }

    private fun handleUser(username: String) {
        pendingUsername = username
        if (serverConfig.isAnonymous && username.equals("anonymous", ignoreCase = true)) {
            isAuthenticated = true
            sendReply(230, "User logged in anonymously.")
            onLog("Login Anonim berhasil")
        } else {
            sendReply(331, "Password required for $username")
        }
    }

    private fun handlePass(password: String) {
        val user = pendingUsername
        if (serverConfig.isAnonymous) {
            isAuthenticated = true
            sendReply(230, "User logged in.")
            onLog("Login Anonim berhasil")
        } else if (user == serverConfig.username && password == serverConfig.password) {
            isAuthenticated = true
            sendReply(230, "User logged in, proceed.")
            onLog("Login berhasil: $user")
        } else {
            isAuthenticated = false
            sendReply(530, "Authentication failed.")
            onLog("Login gagal untuk $user")
        }
    }

    private fun handleAuthenticatedCommand(command: String, arg: String) {
        when (command) {
            "PWD", "XPWD" -> {
                val relPath = getFtpPath(currentDir)
                sendReply(257, "\"$relPath\" is current directory.")
            }
            "CWD", "XCWD" -> {
                val target = resolveFile(arg)
                if (target.exists() && target.isDirectory) {
                    currentDir = target
                    sendReply(250, "Directory changed to \"${getFtpPath(currentDir)}\".")
                } else {
                    sendReply(550, "Failed to change directory: Not a directory.")
                }
            }
            "CDUP", "XCUP" -> {
                val parent = currentDir.parentFile
                if (parent != null && isChildOf(parent, rootDir)) {
                    currentDir = parent
                } else {
                    currentDir = rootDir
                }
                sendReply(250, "Directory changed to \"${getFtpPath(currentDir)}\".")
            }
            "TYPE" -> {
                sendReply(200, "Type set to $arg.")
            }
            "PASV" -> handlePasv()
            "EPSV" -> handleEpsv()
            "PORT" -> handlePort(arg)
            "LIST", "NLST" -> handleList(arg, command == "NLST")
            "MLSD" -> handleMlsd(arg)
            "RETR" -> handleRetr(arg)
            "STOR" -> handleStor(arg)
            "DELE" -> handleDelete(arg)
            "MKD", "XMKD" -> handleMakeDir(arg)
            "RMD", "XRMD" -> handleRemoveDir(arg)
            "RNFR" -> {
                if (serverConfig.isReadOnly) {
                    sendReply(550, "Permission denied (Read-Only).")
                    return
                }
                val f = resolveFile(arg)
                if (f.exists()) {
                    renameFromFile = f
                    sendReply(350, "File exists, ready for destination name.")
                } else {
                    sendReply(550, "File not found.")
                }
            }
            "RNTO" -> {
                if (serverConfig.isReadOnly) {
                    sendReply(550, "Permission denied (Read-Only).")
                    return
                }
                val from = renameFromFile
                if (from == null) {
                    sendReply(503, "Bad sequence of commands (use RNFR first).")
                    return
                }
                val to = resolveFile(arg)
                if (from.renameTo(to)) {
                    sendReply(250, "Rename successful.")
                    onLog("Ganti nama: ${from.name} -> ${to.name}")
                } else {
                    sendReply(550, "Rename failed.")
                }
                renameFromFile = null
            }
            "SIZE" -> {
                val f = resolveFile(arg)
                if (f.exists() && f.isFile) {
                    sendReply(213, f.length().toString())
                } else {
                    sendReply(550, "File not found or is a directory.")
                }
            }
            "MDTM" -> {
                val f = resolveFile(arg)
                if (f.exists()) {
                    val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                    sendReply(213, sdf.format(Date(f.lastModified())))
                } else {
                    sendReply(550, "File not found.")
                }
            }
            "REST" -> {
                restartOffset = arg.toLongOrNull() ?: 0L
                sendReply(350, "Restarting at $restartOffset. Send STORE or RETRIEVE.")
            }
            else -> sendReply(502, "Command not implemented.")
        }
    }

    private fun handlePasv() {
        closeDataConnection()
        try {
            dataServerSocket = ServerSocket(0, 1, controlSocket.localAddress)
            val port = dataServerSocket!!.localPort
            val addr = controlSocket.localAddress.address
            val ipParts = addr.map { it.toInt() and 0xff }
            val p1 = port shr 8
            val p2 = port and 0xff
            val pasvReply = "(${ipParts[0]},${ipParts[1]},${ipParts[2]},${ipParts[3]},$p1,$p2)"
            sendReply(227, "Entering Passive Mode $pasvReply")
        } catch (e: Exception) {
            sendReply(425, "Cannot open passive data connection: ${e.message}")
        }
    }

    private fun handleEpsv() {
        closeDataConnection()
        try {
            dataServerSocket = ServerSocket(0, 1, controlSocket.localAddress)
            val port = dataServerSocket!!.localPort
            sendReply(229, "Entering Extended Passive Mode (|||$port|)")
        } catch (e: Exception) {
            sendReply(425, "Cannot open extended passive connection: ${e.message}")
        }
    }

    private fun handlePort(arg: String) {
        closeDataConnection()
        try {
            val tokens = StringTokenizer(arg, ",")
            val ip = "${tokens.nextToken()}.${tokens.nextToken()}.${tokens.nextToken()}.${tokens.nextToken()}"
            val port = (tokens.nextToken().toInt() shl 8) or tokens.nextToken().toInt()
            activeDataSocketAddress = Pair(InetAddress.getByName(ip), port)
            sendReply(200, "PORT command successful.")
        } catch (e: Exception) {
            sendReply(501, "Syntax error in PORT parameters.")
        }
    }

    private fun openDataSocket(): Socket? {
        val server = dataServerSocket
        if (server != null) {
            server.soTimeout = 15000
            val sock = server.accept()
            closeDataConnection()
            return sock
        }
        val active = activeDataSocketAddress
        if (active != null) {
            val sock = Socket(active.first, active.second)
            activeDataSocketAddress = null
            return sock
        }
        return null
    }

    private fun closeDataConnection() {
        try { dataServerSocket?.close() } catch (_: Exception) {}
        dataServerSocket = null
    }

    private fun handleList(arg: String, namesOnly: Boolean) {
        val target = if (arg.isEmpty() || arg.startsWith("-")) currentDir else resolveFile(arg)
        val dataSocket = openDataSocket()
        if (dataSocket == null) {
            sendReply(425, "Unable to establish data connection.")
            return
        }

        sendReply(150, "Opening ASCII mode data connection for file list.")
        try {
            BufferedWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8)).use { dataWriter ->
                val files = target.listFiles() ?: emptyArray()
                val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)

                for (f in files) {
                    if (namesOnly) {
                        dataWriter.write("${f.name}\r\n")
                    } else {
                        val isDir = f.isDirectory
                        val perms = if (isDir) "drwxr-xr-x" else "-rw-r--r--"
                        val size = if (isDir) 0L else f.length()
                        val dateStr = sdf.format(Date(f.lastModified()))
                        dataWriter.write(String.format(Locale.US, "%s 1 owner group %10d %s %s\r\n", perms, size, dateStr, f.name))
                    }
                }
                dataWriter.flush()
            }
            sendReply(226, "Directory listing complete.")
        } catch (e: Exception) {
            sendReply(426, "Connection closed, transfer aborted: ${e.message}")
        } finally {
            try { dataSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleMlsd(arg: String) {
        val target = if (arg.isEmpty()) currentDir else resolveFile(arg)
        val dataSocket = openDataSocket()
        if (dataSocket == null) {
            sendReply(425, "Unable to establish data connection.")
            return
        }

        sendReply(150, "Opening data connection for MLSD.")
        try {
            BufferedWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8)).use { dataWriter ->
                val files = target.listFiles() ?: emptyArray()
                val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

                for (f in files) {
                    val type = if (f.isDirectory) "dir" else "file"
                    val modify = sdf.format(Date(f.lastModified()))
                    val size = if (f.isDirectory) 0L else f.length()
                    dataWriter.write("type=$type;size=$size;modify=$modify; ${f.name}\r\n")
                }
                dataWriter.flush()
            }
            sendReply(226, "Transfer complete.")
        } catch (e: Exception) {
            sendReply(426, "Transfer aborted: ${e.message}")
        } finally {
            try { dataSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleRetr(filename: String) {
        val file = resolveFile(filename)
        if (!file.exists() || !file.isFile) {
            sendReply(550, "File not found or is a directory.")
            return
        }

        val dataSocket = openDataSocket()
        if (dataSocket == null) {
            sendReply(425, "Unable to build data connection.")
            return
        }

        sendReply(150, "Opening BINARY mode data connection for ${file.name} (${file.length()} bytes).")
        onLog("Mulai download: ${file.name}")

        try {
            FileInputStream(file).use { fis ->
                if (restartOffset > 0) {
                    fis.skip(restartOffset)
                    restartOffset = 0L
                }
                val out = dataSocket.getOutputStream()
                val buffer = ByteArray(32768)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
            sendReply(226, "Transfer complete.")
            onLog("Selesai download: ${file.name}")
        } catch (e: Exception) {
            sendReply(426, "Transfer aborted: ${e.message}")
        } finally {
            try { dataSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleStor(filename: String) {
        if (serverConfig.isReadOnly) {
            sendReply(550, "Permission denied (Read-Only).")
            return
        }

        val file = resolveFile(filename)
        val dataSocket = openDataSocket()
        if (dataSocket == null) {
            sendReply(425, "Unable to build data connection.")
            return
        }

        sendReply(150, "Ok to send data.")
        onLog("Mulai upload: ${file.name}")

        try {
            val append = restartOffset > 0
            FileOutputStream(file, append).use { fos ->
                val input = dataSocket.getInputStream()
                val buffer = ByteArray(32768)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                }
                fos.flush()
            }
            restartOffset = 0L
            sendReply(226, "Transfer complete.")
            onLog("Selesai upload: ${file.name}")
        } catch (e: Exception) {
            sendReply(426, "Transfer aborted: ${e.message}")
        } finally {
            try { dataSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleDelete(filename: String) {
        if (serverConfig.isReadOnly) {
            sendReply(550, "Permission denied (Read-Only).")
            return
        }
        val file = resolveFile(filename)
        if (file.exists() && file.isFile && file.delete()) {
            sendReply(250, "File deleted.")
            onLog("Hapus file: ${file.name}")
        } else {
            sendReply(550, "Could not delete file.")
        }
    }

    private fun handleMakeDir(dirname: String) {
        if (serverConfig.isReadOnly) {
            sendReply(550, "Permission denied (Read-Only).")
            return
        }
        val dir = resolveFile(dirname)
        if (!dir.exists() && dir.mkdirs()) {
            sendReply(257, "\"${getFtpPath(dir)}\" created.")
            onLog("Buat folder: ${dir.name}")
        } else {
            sendReply(550, "Failed to create directory.")
        }
    }

    private fun handleRemoveDir(dirname: String) {
        if (serverConfig.isReadOnly) {
            sendReply(550, "Permission denied (Read-Only).")
            return
        }
        val dir = resolveFile(dirname)
        if (dir.exists() && dir.isDirectory && dir.deleteRecursively()) {
            sendReply(250, "Directory removed.")
            onLog("Hapus folder: ${dir.name}")
        } else {
            sendReply(550, "Could not remove directory.")
        }
    }

    private fun sendReply(code: Int, text: String) {
        writer.write("$code $text\r\n")
        writer.flush()
    }

    private fun resolveFile(path: String): File {
        val target = if (path.startsWith("/")) {
            File(rootDir, path.removePrefix("/"))
        } else {
            File(currentDir, path)
        }
        val canonical = target.canonicalFile
        return if (isChildOf(canonical, rootDir.canonicalFile)) {
            canonical
        } else {
            rootDir
        }
    }

    private fun getFtpPath(file: File): String {
        val rootPath = rootDir.canonicalPath
        val filePath = file.canonicalPath
        if (filePath == rootPath) return "/"
        val relative = filePath.removePrefix(rootPath).replace('\\', '/')
        return if (relative.startsWith("/")) relative else "/$relative"
    }

    private fun isChildOf(file: File, parent: File): Boolean {
        var current: File? = file
        while (current != null) {
            if (current == parent) return true
            current = current.parentFile
        }
        return false
    }
}

