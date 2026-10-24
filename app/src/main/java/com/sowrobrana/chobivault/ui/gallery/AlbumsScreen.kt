package com.sowrobrana.chobivault.ui.gallery

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sowrobrana.chobivault.data.Album
import com.sowrobrana.chobivault.data.MediaItem
import com.sowrobrana.chobivault.data.MediaStoreRepository

/**
 * Albums tab — modern card grid (v0.1.1 redesign): rounded-corner covers
 * with the album name + count, built to stand next to Google Photos /
 * Samsung Gallery instead of the plain v0.1.0 rows.
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
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো অ্যালবাম পাওয়া যায়নি")
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums) { album ->
            Card(
                modifier = Modifier.clickable { onOpenAlbum(album.name) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(album.thumbUri)
                            .size(600)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    )
                    Text(
                        text = "${album.count}টি",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
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
        MediaGrid(
            items = items,
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
    }
}
