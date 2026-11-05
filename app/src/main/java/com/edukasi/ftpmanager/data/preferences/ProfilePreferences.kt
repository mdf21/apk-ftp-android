package com.edukasi.ftpmanager.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.edukasi.ftpmanager.data.model.FtpServerProfile
import com.edukasi.ftpmanager.data.model.ServerConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProfilePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ftp_manager_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PROFILES = "saved_ftp_profiles"
        private const val KEY_SERVER_PORT = "ftp_server_port"
        private const val KEY_SERVER_USER = "ftp_server_username"
        private const val KEY_SERVER_PASS = "ftp_server_password"
        private const val KEY_SERVER_ANON = "ftp_server_anonymous"
        private const val KEY_SERVER_READ_ONLY = "ftp_server_read_only"
        private const val KEY_SERVER_ROOT = "ftp_server_root_dir"
    }

    // Client profiles
    fun getProfiles(): List<FtpServerProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FtpServerProfile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfile(profile: FtpServerProfile) {
        val list = getProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            list[index] = profile
        } else {
            list.add(0, profile)
        }
        prefs.edit().putString(KEY_PROFILES, gson.toJson(list)).apply()
    }

    fun deleteProfile(profileId: String) {
        val list = getProfiles().filterNot { it.id == profileId }
        prefs.edit().putString(KEY_PROFILES, gson.toJson(list)).apply()
    }

    // Server configuration
    fun getServerConfig(): ServerConfig {
        val defaultRoot = Environment.getExternalStorageDirectory().absolutePath
        return ServerConfig(
            port = prefs.getInt(KEY_SERVER_PORT, 2121),
            username = prefs.getString(KEY_SERVER_USER, "admin") ?: "admin",
            password = prefs.getString(KEY_SERVER_PASS, "123456") ?: "123456",
            isAnonymous = prefs.getBoolean(KEY_SERVER_ANON, false),
            isReadOnly = prefs.getBoolean(KEY_SERVER_READ_ONLY, false),
            rootPath = prefs.getString(KEY_SERVER_ROOT, defaultRoot) ?: defaultRoot
        )
    }

    fun saveServerConfig(config: ServerConfig) {
        prefs.edit()
            .putInt(KEY_SERVER_PORT, config.port)
            .putString(KEY_SERVER_USER, config.username)
            .putString(KEY_SERVER_PASS, config.password)
            .putBoolean(KEY_SERVER_ANON, config.isAnonymous)
            .putBoolean(KEY_SERVER_READ_ONLY, config.isReadOnly)
            .putString(KEY_SERVER_ROOT, config.rootPath)
            .apply()
    }
}

