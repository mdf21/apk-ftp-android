package com.edukasi.ftpmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.edukasi.ftpmanager.data.model.FtpFileItem
import com.edukasi.ftpmanager.data.server.NetworkUtils

@Composable
fun InputTextDialog(
    title: String,
    initialValue: String = "",
    label: String,
    confirmButtonText: String = "Simpan",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textState by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(0, initialValue.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textState.text.isNotBlank()) {
                        onConfirm(textState.text.trim())
                    }
                }
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Hapus",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun FileInfoDialog(
    item: FtpFileItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail File / Folder") },
        text = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("Nama: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Tipe: ${if (item.isDirectory) "Direktori / Folder" else "File (${item.extension})"}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Path: ${item.path}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                if (!item.isDirectory) {
                    Text("Ukuran: ${item.formattedSize} (${item.size} bytes)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text("Terakhir Diubah: ${item.formattedDate}", style = MaterialTheme.typography.bodyMedium)
                if (item.permissions.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Izin: ${item.permissions}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun TransferProgressDialog(
    fileName: String,
    isUpload: Boolean,
    bytesTransferred: Long,
    totalBytes: Long,
    percent: Int,
    onCancel: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss while transferring */ },
        title = { Text(if (isUpload) "Mengupload File..." else "Mendownload File...") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$percent% (${NetworkUtils.formatFileSize(bytesTransferred)} / ${NetworkUtils.formatFileSize(totalBytes)})",
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = NetworkUtils.formatFileSize(bytesTransferred),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text("Batalkan")
                }
            }
        }
    )
}

