package com.sowrobrana.chobivault.ui.vault

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sowrobrana.chobivault.data.VaultRepository
import com.sowrobrana.chobivault.data.local.VaultItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Vault tab: encrypted items only. Thumbnails are decrypted into memory
 * (never written to shared storage); full viewing decrypts to a cache
 * temp file that is deleted the moment the viewer closes.
 */
@Composable
fun VaultScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { VaultRepository(context) }
    val items by repo.observeItems().collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<VaultItemEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<VaultItemEntity?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        CoroutineScope(Dispatchers.IO).launch {
            var done = 0
            for (uri in uris) {
                try {
                    val (name, mime) = queryDisplayName(context, uri)
                    repo.importUri(uri, name, mime)
                    done++
                } catch (_: Exception) {
                }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                busy = false
                Toast.makeText(context, "$doneটি ভল্টে নেওয়া হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ভল্ট",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                enabled = !busy,
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                        ),
                    )
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "ভল্টে যোগ করুন")
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ভল্ট খালি — + চেপে ছবি/ভিডিও লুকান")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    VaultThumb(
                        item = item,
                        repo = repo,
                        onClick = { selected = item },
                    )
                }
            }
        }
        Text(
            "সতর্কতা: অ্যাপ আনইনস্টল করলে ভল্টের ছবি চিরতরে যাবে — আগেই গ্যালারিতে এক্সপোর্ট করে রাখুন।",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }

    selected?.let { item ->
        VaultViewerDialog(
            item = item,
            repo = repo,
            onClose = { selected = null },
            onExport = {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repo.exportToGallery(item.id)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "গ্যালারিতে ফেরত দেওয়া হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "এক্সপোর্ট হয়নি", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDelete = { confirmDelete = item },
        )
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("ভল্ট থেকে মুছবেন?") },
            text = { Text("এটা চিরতরে মুছে যাবে — আর ফেরত পাওয়া যাবে না।") },
            confirmButton = {
                TextButton(onClick = {
                    CoroutineScope(Dispatchers.IO).launch { repo.delete(item.id) }
                    confirmDelete = null
                    selected = null
                }) { Text("মুছুন") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("না") }
            },
        )
    }
}

@Composable
private fun VaultThumb(
    item: VaultItemEntity,
    repo: VaultRepository,
    onClick: () -> Unit,
) {
    val bitmapState = remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    if (!item.mimeType.startsWith("video")) {
        LaunchedEffect(item.id) {
            try {
                val bytes = repo.decryptBytes(item.id)
                bitmapState.value = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
            }
        }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = item.fileName,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun VaultViewerDialog(
    item: VaultItemEntity,
    repo: VaultRepository,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var tempFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(item.id) {
        try {
            tempFile = repo.decryptToTemp(item.id)
        } catch (_: Exception) {
        }
    }
    // Temp file dies with the dialog.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { tempFile?.delete() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন")
                }
            }
            val file = tempFile
            if (file != null) {
                if (item.mimeType.startsWith("video")) {
                    com.sowrobrana.chobivault.ui.gallery.VideoPlayerCell(
                        uri = Uri.fromFile(file),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    AsyncImage(
                        model = file,
                        contentDescription = item.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) { Text("খোলা হচ্ছে…") }
            }
            Row {
                Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Text("গ্যালারিতে ফেরত")
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text("মুছুন")
                }
            }
        }
    }
}

private fun queryDisplayName(
    context: android.content.Context,
    uri: Uri,
): Pair<String, String> {
    var name = "vault_${System.currentTimeMillis()}"
    var mime = "image/*"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
            }
        }
        context.contentResolver.getType(uri)?.let { mime = it }
    } catch (_: Exception) {
    }
    return name to mime
}
