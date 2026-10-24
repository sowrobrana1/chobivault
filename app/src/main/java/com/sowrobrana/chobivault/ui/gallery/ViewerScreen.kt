package com.sowrobrana.chobivault.ui.gallery

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.sowrobrana.chobivault.data.FavoritesStore
import com.sowrobrana.chobivault.data.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen viewer: pinch-zoom for photos, ExoPlayer for videos.
 * Actions: favorite, move-to-vault (encrypt + trash original with the
 * system's own consent dialog), trash (system consent dialog).
 */
@Composable
fun ViewerScreen(
    uriString: String,
    displayName: String,
    mimeType: String,
    isVideo: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uri = remember(uriString) { Uri.parse(uriString) }
    val favoritesStore = remember { FavoritesStore(context) }
    val vaultRepo = remember { VaultRepository(context) }
    val favorites by favoritesStore.favorites.collectAsState(initial = emptySet())
    val isFavorite = favorites.contains(uriString)
    var busy by remember { mutableStateOf(false) }

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

    Scaffold(
        bottomBar = {
            Row(modifier = Modifier.padding()) {
                IconButton(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        favoritesStore.toggle(uriString)
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
                                vaultRepo.importUri(uri, displayName, mimeType)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        "ভল্টে লুকানো হয়েছে — আসলটা সরাতে চাইলে অনুমতি দিন",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    requestTrash(listOf(uri))
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
                IconButton(onClick = { requestTrash(listOf(uri)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "ট্র্যাশে পাঠান")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isVideo) {
                VideoPlayer(uri = uri, modifier = Modifier.fillMaxSize())
            } else {
                ZoomableImage(uri = uri, name = displayName)
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "ফিরে যান")
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            Text(
                text = displayName,
                modifier = Modifier.align(Alignment.BottomCenter),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ZoomableImage(uri: Uri, name: String) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    AsyncImage(
        model = uri,
        contentDescription = name,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY,
            ),
    )
}

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

@Composable
private fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    VideoPlayerCell(uri = uri, modifier = modifier)
}
