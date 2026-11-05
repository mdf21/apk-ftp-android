package com.edukasi.ftpmanager.ui.screens.client

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edukasi.ftpmanager.data.client.FtpClientManager
import com.edukasi.ftpmanager.data.model.FtpFileItem
import com.edukasi.ftpmanager.data.model.FtpServerProfile
import com.edukasi.ftpmanager.data.model.TransferStatus
import com.edukasi.ftpmanager.data.preferences.ProfilePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ClientViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ProfilePreferences(application)
    private val clientManager = FtpClientManager()

    private val _profiles = MutableStateFlow<List<FtpServerProfile>>(emptyList())
    val profiles: StateFlow<List<FtpServerProfile>> = _profiles.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _fileList = MutableStateFlow<List<FtpFileItem>>(emptyList())
    val fileList: StateFlow<List<FtpFileItem>> = _fileList.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _transferStatus = MutableStateFlow<TransferStatus>(TransferStatus.Idle)
    val transferStatus: StateFlow<TransferStatus> = _transferStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val activeProfile: FtpServerProfile?
        get() = clientManager.activeProfile

    init {
        loadProfiles()
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun dismissTransferStatus() {
        _transferStatus.value = TransferStatus.Idle
    }

    fun loadProfiles() {
        _profiles.value = prefs.getProfiles()
    }

    fun saveProfile(profile: FtpServerProfile) {
        prefs.saveProfile(profile)
        loadProfiles()
    }

    fun deleteProfile(profileId: String) {
        prefs.deleteProfile(profileId)
        loadProfiles()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun connect(profile: FtpServerProfile) {
        viewModelScope.launch {
            _isConnecting.value = true
            _errorMessage.value = null

            val result = clientManager.connect(profile)
            _isConnecting.value = false

            result.onSuccess {
                _isConnected.value = true
                val path = clientManager.getCurrentDirectory()
                _currentPath.value = path
                refreshDirectory(path)
            }.onFailure { err ->
                _isConnected.value = false
                _errorMessage.value = err.message ?: "Koneksi FTP gagal"
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            clientManager.disconnect()
            _isConnected.value = false
            _fileList.value = emptyList()
            _currentPath.value = "/"
            _searchQuery.value = ""
        }
    }

    fun refreshDirectory(path: String? = null) {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val target = path ?: _currentPath.value
            val result = clientManager.listFiles(target)
            _isLoadingFiles.value = false

            result.onSuccess { items ->
                _fileList.value = items
            }.onFailure { err ->
                _errorMessage.value = "Gagal memuat isi direktori: ${err.message}"
            }
        }
    }

    fun navigateToDirectory(dirName: String) {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val result = clientManager.changeDirectory(dirName)
            if (result.isSuccess) {
                val newPath = result.getOrNull() ?: clientManager.getCurrentDirectory()
                _currentPath.value = newPath
                _searchQuery.value = ""
                refreshDirectory(newPath)
            } else {
                _isLoadingFiles.value = false
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal berpindah folder"
            }
        }
    }

    fun navigateToParent() {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val result = clientManager.changeToParent()
            if (result.isSuccess) {
                val newPath = result.getOrNull() ?: clientManager.getCurrentDirectory()
                _currentPath.value = newPath
                _searchQuery.value = ""
                refreshDirectory(newPath)
            } else {
                _isLoadingFiles.value = false
                _toastMessage.value = "Sudah di folder root"
            }
        }
    }

    fun downloadFile(item: FtpFileItem) {
        viewModelScope.launch {
            // Save to Downloads/FTP_Downloads
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FTP_Downloads"
            )
            val destFile = File(downloadDir, item.name)

            _transferStatus.value = TransferStatus.InProgress(
                fileName = item.name,
                isUpload = false,
                bytesTransferred = 0L,
                totalBytes = item.size,
                percent = 0
            )

            val result = clientManager.downloadFile(item, destFile) { transferred, total ->
                val percent = if (total > 0) ((transferred * 100) / total).toInt() else 0
                _transferStatus.value = TransferStatus.InProgress(
                    fileName = item.name,
                    isUpload = false,
                    bytesTransferred = transferred,
                    totalBytes = total,
                    percent = percent
                )
            }

            result.onSuccess {
                _transferStatus.value = TransferStatus.Success("Berhasil mendownload: ${item.name}")
                _toastMessage.value = "Disimpan di: Download/FTP_Downloads/${item.name}"
            }.onFailure { err ->
                _transferStatus.value = TransferStatus.Error("Gagal download: ${err.message}")
            }
        }
    }

    fun uploadFile(uri: Uri, contentResolver: ContentResolver, fileName: String, fileSize: Long) {
        viewModelScope.launch {
            val stream = try {
                contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                _errorMessage.value = "Gagal membaca file lokal: ${e.message}"
                return@launch
            } ?: return@launch

            _transferStatus.value = TransferStatus.InProgress(
                fileName = fileName,
                isUpload = true,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                percent = 0
            )

            val result = clientManager.uploadFile(stream, fileName, fileSize) { transferred, total ->
                val percent = if (total > 0) ((transferred * 100) / total).toInt() else 0
                _transferStatus.value = TransferStatus.InProgress(
                    fileName = fileName,
                    isUpload = true,
                    bytesTransferred = transferred,
                    totalBytes = total,
                    percent = percent
                )
            }

            result.onSuccess {
                _transferStatus.value = TransferStatus.Success("Berhasil mengupload: $fileName")
                _toastMessage.value = "File $fileName berhasil diupload"
                refreshDirectory()
            }.onFailure { err ->
                _transferStatus.value = TransferStatus.Error("Gagal upload: ${err.message}")
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = clientManager.createDirectory(name)
            result.onSuccess {
                _toastMessage.value = "Folder $name berhasil dibuat"
                refreshDirectory()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal membuat folder"
            }
        }
    }

    fun deleteItem(item: FtpFileItem) {
        viewModelScope.launch {
            val result = if (item.isDirectory) {
                clientManager.deleteDirectory(item.name)
            } else {
                clientManager.deleteFile(item.name)
            }

            result.onSuccess {
                _toastMessage.value = "${item.name} berhasil dihapus"
                refreshDirectory()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal menghapus item"
            }
        }
    }

    fun renameItem(item: FtpFileItem, newName: String) {
        viewModelScope.launch {
            val result = clientManager.rename(item.name, newName)
            result.onSuccess {
                _toastMessage.value = "Nama berhasil diubah"
                refreshDirectory()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal mengubah nama"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            clientManager.disconnect()
        }
    }
}

