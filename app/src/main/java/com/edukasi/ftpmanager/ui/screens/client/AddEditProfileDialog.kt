package com.edukasi.ftpmanager.ui.screens.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.edukasi.ftpmanager.data.model.FtpServerProfile
import java.util.UUID

@Composable
fun AddEditProfileDialog(
    initialProfile: FtpServerProfile? = null,
    onDismiss: () -> Unit,
    onSave: (FtpServerProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var host by remember { mutableStateOf(initialProfile?.host ?: "") }
    var portText by remember { mutableStateOf(initialProfile?.port?.toString() ?: "21") }
    var isAnonymous by remember { mutableStateOf(initialProfile?.isAnonymous ?: false) }
    var username by remember { mutableStateOf(initialProfile?.username ?: "anonymous") }
    var password by remember { mutableStateOf(initialProfile?.password ?: "") }
    var passiveMode by remember { mutableStateOf(initialProfile?.passiveMode ?: true) }
    var initialPath by remember { mutableStateOf(initialProfile?.initialPath ?: "/") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProfile == null) "Tambah Server FTP" else "Edit Server FTP") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Server / Label") },
                    placeholder = { Text("contoh: PC Kantor, VPS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Login Anonim")
                    Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                }

                if (!isAnonymous) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mode Pasif (Passive PASV)")
                    Switch(checked = passiveMode, onCheckedChange = { passiveMode = it })
                }

                OutlinedTextField(
                    value = initialPath,
                    onValueChange = { initialPath = it },
                    label = { Text("Path Direktori Awal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = androidx.compose.ui.graphics.Color.Red
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Nama server tidak boleh kosong"
                        return@Button
                    }
                    if (host.isBlank()) {
                        errorMessage = "Host/IP tidak boleh kosong"
                        return@Button
                    }
                    val port = portText.toIntOrNull() ?: 21

                    val profile = FtpServerProfile(
                        id = initialProfile?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        host = host.trim(),
                        port = port,
                        isAnonymous = isAnonymous,
                        username = if (isAnonymous) "anonymous" else username.trim(),
                        password = if (isAnonymous) "" else password,
                        passiveMode = passiveMode,
                        initialPath = if (initialPath.isBlank()) "/" else initialPath.trim()
                    )
                    onSave(profile)
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

