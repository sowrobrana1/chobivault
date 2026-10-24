package com.sowrobrana.chobivault.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sowrobrana.chobivault.data.FavoritesStore
import com.sowrobrana.chobivault.data.MediaItem
import com.sowrobrana.chobivault.data.MediaStoreRepository

/**
 * Gallery tab: every device photo + video, newest first.
 * MediaStore is the source — installing the app makes all existing
 * photos appear automatically, and new shots appear on next resume.
 */
@Composable
fun GalleryScreen(
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember { MediaStoreRepository(context) }
    val favoritesStore = remember { FavoritesStore(context) }
    val favorites by favoritesStore.favorites.collectAsState(initial = emptySet())

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(checkGalleryPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasPermission = granted.values.all { it } || checkGalleryPermission(context)
        if (hasPermission) {
            loadItems(repo) { items = it }
        }
    }

    // Reload on every resume: new photos taken meanwhile show up at once.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasPermission) {
                loadItems(repo) { items = it }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasPermission) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ছবি দেখতে অনুমতি দরকার", style = MaterialTheme.typography.bodyLarge)
                Button(
                    modifier = Modifier.padding(top = 16.dp),
                    onClick = { permissionLauncher.launch(galleryPermissions()) },
                ) { Text("অনুমতি দিন") }
            }
        }
        return
    }

    val visible = items.filter { item ->
        (query.isBlank() || item.displayName.contains(query, ignoreCase = true)) &&
            (!favoritesOnly || favorites.contains(item.uri.toString()))
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("খুঁজুন…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { favoritesOnly = !favoritesOnly }) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "ফেভারিট",
                        tint = if (favoritesOnly) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        },
                    )
                }
            },
            singleLine = true,
        )
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো ছবি পাওয়া যায়নি")
            }
        } else {
            // weight(1f) guarantees the grid a bounded height so it can
            // actually scroll (v0.1.0 bug report: grid did not scroll).
            MediaGrid(
                items = visible,
                onOpen = onOpen,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Shared square-cell grid used by the gallery tab and album detail. */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onOpen(item) },
            ) {
                // Small explicit thumbnail request: full-res decodes made
                // the v0.1.0 grid janky on large libraries.
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(400)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (item.isVideo) {
                    Text(
                        text = formatDuration(item.durationMs),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun galleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun checkGalleryPermission(context: android.content.Context): Boolean =
    galleryPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun loadItems(
    repo: MediaStoreRepository,
    onLoaded: (List<MediaItem>) -> Unit,
) {
    // Fire-and-forget on a plain thread: the screen is simple enough that
    // a full ViewModel would be ceremony; results land via state.
    Thread {
        try {
            val result = kotlinx.coroutines.runBlocking { repo.loadAll() }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onLoaded(result) }
        } catch (_: Exception) {
            // Empty grid on failure; a resume retry covers transient errors.
        }
    }.start()
}
