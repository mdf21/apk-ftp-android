package com.edukasi.ftpmanager.ui.screens.client

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edukasi.ftpmanager.data.model.FtpFileItem
import com.edukasi.ftpmanager.data.model.TransferStatus
import com.edukasi.ftpmanager.ui.components.ConfirmDialog
import com.edukasi.ftpmanager.ui.components.FileInfoDialog
import com.edukasi.ftpmanager.ui.components.FileItemRow
import com.edukasi.ftpmanager.ui.components.InputTextDialog
import com.edukasi.ftpmanager.ui.components.TransferProgressDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: ClientViewModel
) {
    val context = LocalContext.current
    val currentPath by viewModel.currentPath.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val isLoading by viewModel.isLoadingFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val transferStatus by viewModel.transferStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val activeProfile = viewModel.activeProfile

    var isSearchVisible by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FtpFileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FtpFileItem?>(null) }
    var itemForInfo by remember { mutableStateOf<FtpFileItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // File picker launcher for uploading
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = getFileNameAndSize(context, uri)
            viewModel.uploadFile(uri, context.contentResolver, name, size)
        }
    }

    val filteredList = remember(fileList, searchQuery) {
        if (searchQuery.isBlank()) {
            fileList
        } else {
            fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeProfile?.name ?: "FTP Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentPath != "/") {
                            IconButton(onClick = { viewModel.navigateToParent() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Folder Induk")
                            }
                        } else {
                            IconButton(onClick = { /* Root */ }) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                            Icon(Icons.Default.Search, contentDescription = "Cari")
                        }
                        IconButton(onClick = { viewModel.refreshDirectory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                        }
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "Putuskan Koneksi",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (isSearchVisible) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Cari file atau folder...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus")
                                    }
                                }
                            }
                        )
                    }
                }
                HorizontalDivider()
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (fabExpanded) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            showCreateFolderDialog = true
                        },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        text = { Text("Buat Folder") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ExtendedFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            filePickerLauncher.launch("*/*")
                        },
                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        text = { Text("Upload File") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded }
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Menu Aksi"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading && fileList.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                filteredList.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.height(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Tidak ada file yang cocok" else "Folder ini kosong",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredList, key = { it.path }) { item ->
                            FileItemRow(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        viewModel.navigateToDirectory(item.name)
                                    } else {
                                        itemForInfo = item
                                    }
                                },
                                onDownload = {
                                    viewModel.downloadFile(item)
                                },
                                onRename = {
                                    itemToRename = item
                                },
                                onDelete = {
                                    itemToDelete = item
                                },
                                onInfo = {
                                    itemForInfo = item
                                }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Folder
    if (showCreateFolderDialog) {
        InputTextDialog(
            title = "Buat Folder Baru",
            label = "Nama Folder",
            confirmButtonText = "Buat",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                viewModel.createFolder(name)
            }
        )
    }

    // Dialog: Rename
    itemToRename?.let { item ->
        InputTextDialog(
            title = "Ganti Nama",
            initialValue = item.name,
            label = "Nama Baru",
            confirmButtonText = "Ganti",
            onDismiss = { itemToRename = null },
            onConfirm = { newName ->
                itemToRename = null
                viewModel.renameItem(item, newName)
            }
        )
    }

    // Dialog: Delete
    itemToDelete?.let { item ->
        ConfirmDialog(
            title = "Hapus ${if (item.isDirectory) "Folder" else "File"}",
            message = "Apakah Anda yakin ingin menghapus '${item.name}' dari server FTP?",
            confirmText = "Hapus",
            onDismiss = { itemToDelete = null },
            onConfirm = {
                itemToDelete = null
                viewModel.deleteItem(item)
            }
        )
    }

    // Dialog: Info
    itemForInfo?.let { item ->
        FileInfoDialog(
            item = item,
            onDismiss = { itemForInfo = null }
        )
    }

    // Dialog: Transfer Progress
    when (val status = transferStatus) {
        is TransferStatus.InProgress -> {
            TransferProgressDialog(
                fileName = status.fileName,
                isUpload = status.isUpload,
                bytesTransferred = status.bytesTransferred,
                totalBytes = status.totalBytes,
                percent = status.percent
            )
        }
        is TransferStatus.Success -> {
            LaunchedEffect(status) {
                Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                viewModel.dismissTransferStatus()
            }
        }
        is TransferStatus.Error -> {
            LaunchedEffect(status) {
                Toast.makeText(context, status.error, Toast.LENGTH_LONG).show()
                viewModel.dismissTransferStatus()
            }
        }
        TransferStatus.Idle -> {
            // Nothing to show
        }
    }
}

private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "upload_${System.currentTimeMillis()}"
    var size = 0L

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (_: Exception) {}

    return Pair(name, size)
}

