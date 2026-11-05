package com.edukasi.ftpmanager.ui.screens.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edukasi.ftpmanager.data.model.ServerConfig
import com.edukasi.ftpmanager.data.preferences.ProfilePreferences
import com.edukasi.ftpmanager.data.server.FtpServerService
import com.edukasi.ftpmanager.data.server.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ProfilePreferences(application)

    val isRunning: StateFlow<Boolean> = FtpServerService.isRunningFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val logs: StateFlow<List<String>> = FtpServerService.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _serverConfig = MutableStateFlow(prefs.getServerConfig())
    val serverConfig: StateFlow<ServerConfig> = _serverConfig.asStateFlow()

    private val _ipAddress = MutableStateFlow(NetworkUtils.getLocalIpAddress(application))
    val ipAddress: StateFlow<String?> = _ipAddress.asStateFlow()

    fun refreshIp() {
        _ipAddress.value = NetworkUtils.getLocalIpAddress(getApplication())
    }

    fun updateConfig(config: ServerConfig) {
        _serverConfig.value = config
        prefs.saveServerConfig(config)
    }

    fun toggleServer() {
        val app = getApplication<Application>()
        if (isRunning.value) {
            FtpServerService.stopService(app)
        } else {
            refreshIp()
            FtpServerService.startService(app)
        }
    }

    fun clearLogs() {
        FtpServerService.clearLogs()
    }
}

