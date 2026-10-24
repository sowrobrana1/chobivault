package com.sowrobrana.chobivault.ui.gallery

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.sowrobrana.chobivault.data.FavoritesStore
import com.sowrobrana.chobivault.data.MediaItem
import com.sowrobrana.chobivault.data.MediaStoreRepository
import com.sowrobrana.chobivault.data.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen viewer (v0.1.1): swipe left/right through the whole list
 * (gallery or the album it was opened from), double-tap to zoom photos,
 * ExoPlayer for videos. The v0.1.0 single-photo viewer could not swipe,
 * which read as "photos don't scroll".
 *
 * Actions on the currently visible photo: favorite, move-to-vault
 * (encrypt + trash the original with the system's own consent dialog),
 * trash (system consent dialog).
 */
@Composable
fun ViewerScreen(
    uriString: String,
    displayName: String,
    mimeType: String,
    isVideo: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    album: String = "",
) {
    val context = LocalContext.current
    val repo = remember { MediaStoreRepository(context) }
    val favoritesStore = remember { FavoritesStore(context) }
    val vaultRepo = remember { VaultRepository(context) }
    val favorites by favoritesStore.favorites.collectAsState(initial = emptySet())
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(album) {
        Thread {
            try {
                val all = kotlinx.coroutines.runBlocking { repo.loadAll() }
                val filtered = if (album.isBlank()) {
                    all
                } else {
                    all.filter { it.bucketName.ifBlank { "অন্যান্য" } == album }
                }
                val result = filtered.ifEmpty {
                    listOf(
                        MediaItem(
                            uri = Uri.parse(uriString),
                            displayName = displayName,
                            mimeType = mimeType,
                            dateAdded = 0,
                            bucketName = "",
                            size = 0,
                        ),
                    )
                }
                android.os.Handler(android.os.Looper.getMainLooper())
                    .post { items = result }
            } catch (_: Exception) {
            }
        }.start()
    }

    // System consent dialog result (trash / move-to-vault original).
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "সরানো হয়েছে", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    fun requestTrash(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createTrashRequest(
                context.contentResolver, uris, true,
            )
            trashLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            )
        } else {
            try {
                uris.forEach { context.contentResolver.delete(it, null, null) }
                Toast.makeText(context, "সরানো হয়েছে", Toast.LENGTH_SHORT).show()
                onBack()
            } catch (_: Exception) {
                Toast.makeText(context, "সরানো যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val initialIndex = items.indexOfFirst { it.uri.toString() == uriString }
        .takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size },
    )
    val current = items.getOrNull(pagerState.currentPage) ?: items[initialIndex]
    val isFavorite = favorites.contains(current.uri.toString())

    Scaffold(
        bottomBar = {
            Row {
                IconButton(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        favoritesStore.toggle(current.uri.toString())
                    }
                }) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "ফেভারিট",
                    )
                }
                IconButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                vaultRepo.importUri(
                                    current.uri,
                                    current.displayName,
                                    current.mimeType,
                                )
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        "ভল্টে লুকানো হয়েছে — গ্যালারি থেকে সরাতে অনুমতি দিন",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    requestTrash(listOf(current.uri))
                                }
                            } catch (e: Exception) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        "ভল্টে নেওয়া যায়নি: ${e.message}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    busy = false
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "ভল্টে লুকান")
                }
                IconButton(onClick = { requestTrash(listOf(current.uri)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "ট্র্যাশে পাঠান")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "ফিরে যান")
                }
                Text(
                    text = current.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val item = items[page]
                    if (item.isVideo) {
                        if (page == pagerState.currentPage) {
                            VideoPlayerCell(
                                uri = item.uri,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = item.displayName,
                                    modifier = Modifier.padding(),
                                )
                            }
                        }
                    } else {
                        DoubleTapZoomImage(uri = item.uri, name = item.displayName)
                    }
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/**
 * ExoPlayer cell, also used by the vault viewer (decrypted temp file).
 * Only the currently visible page mounts a player; other video pages
 * show a play icon so several players never run at once.
 */
@Composable
fun VideoPlayerCell(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier,
    )
}

/**
 * Double-tap toggles 1x / 2.5x zoom. Drag gestures are deliberately left
 * to the pager, so swiping between photos always works (pinch-zoom
 * gestures used to swallow the swipe in v0.1.0's viewer).
 */
@Composable
private fun DoubleTapZoomImage(uri: Uri, name: String) {
    var scale by remember { mutableStateOf(1f) }
    AsyncImage(
        model = uri,
        contentDescription = name,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                    },
                )
            }
            .graphicsLayer(scaleX = scale, scaleY = scale),
        contentScale = ContentScale.Fit,
    )
}
