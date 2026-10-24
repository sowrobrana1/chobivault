package com.sowrobrana.chobivault.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sowrobrana.chobivault.data.Album
import com.sowrobrana.chobivault.data.MediaItem
import com.sowrobrana.chobivault.data.MediaStoreRepository

/**
 * Albums tab: folders (Camera, Downloads, WhatsApp…) with counts.
 * Tapping an album opens its own grid (same MediaGrid as the gallery).
 */
@Composable
fun AlbumsScreen(
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember { MediaStoreRepository(context) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(Unit) {
        Thread {
            try {
                val result = kotlinx.coroutines.runBlocking { repo.loadAlbums() }
                android.os.Handler(android.os.Looper.getMainLooper())
                    .post { albums = result }
            } catch (_: Exception) {
            }
        }.start()
    }

    if (albums.isEmpty()) {
        androidx.compose.foundation.layout.Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("কোনো অ্যালবাম পাওয়া যায়নি") }
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        items(albums) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAlbum(album.name) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = album.thumbUri,
                    contentDescription = album.name,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(album.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${album.count}টি ছবি/ভিডিও",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** One album's own grid, with a back button. */
@Composable
fun AlbumDetailScreen(
    albumName: String,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember { MediaStoreRepository(context) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    LaunchedEffect(albumName) {
        Thread {
            try {
                val result = kotlinx.coroutines.runBlocking { repo.loadAll() }
                    .filter { it.bucketName.ifBlank { "অন্যান্য" } == albumName }
                android.os.Handler(android.os.Looper.getMainLooper())
                    .post { items = result }
            } catch (_: Exception) {
            }
        }.start()
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "ফিরে যান")
            }
            Text(albumName, style = MaterialTheme.typography.titleLarge)
        }
        MediaGrid(items = items, onOpen = onOpen)
    }
}
