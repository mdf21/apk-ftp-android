package com.edukasi.ftpmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.edukasi.ftpmanager.ui.screens.MainScreen
import com.edukasi.ftpmanager.ui.screens.client.ClientViewModel
import com.edukasi.ftpmanager.ui.screens.server.ServerViewModel
import com.edukasi.ftpmanager.ui.theme.FtpManagerTheme

class MainActivity : ComponentActivity() {

    private val serverViewModel: ServerViewModel by viewModels()
    private val clientViewModel: ClientViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FtpManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        serverViewModel = serverViewModel,
                        clientViewModel = clientViewModel
                    )
                }
            }
        }
    }
}

