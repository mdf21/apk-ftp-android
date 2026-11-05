package com.edukasi.ftpmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.edukasi.ftpmanager.ui.screens.MainScreen
import com.edukasi.ftpmanager.ui.screens.client.ClientViewModel
import com.edukasi.ftpmanager.ui.screens.server.ServerViewModel
import com.edukasi.ftpmanager.ui.theme.FtpManagerTheme

class MainActivity : ComponentActivity() {

    private lateinit var serverViewModel: ServerViewModel
    private lateinit var clientViewModel: ClientViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        serverViewModel = ViewModelProvider(this)[ServerViewModel::class.java]
        clientViewModel = ViewModelProvider(this)[ClientViewModel::class.java]

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

