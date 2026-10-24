package com.sowrobrana.chobivault

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sowrobrana.chobivault.ui.gallery.AlbumDetailScreen
import com.sowrobrana.chobivault.ui.gallery.AlbumsScreen
import com.sowrobrana.chobivault.ui.gallery.GalleryScreen
import com.sowrobrana.chobivault.ui.gallery.ViewerScreen
import com.sowrobrana.chobivault.ui.theme.ChobiVaultTheme
import com.sowrobrana.chobivault.ui.vault.VaultLockScreen
import com.sowrobrana.chobivault.ui.vault.VaultScreen

/**
 * ChobiVault v0.1.0 — gallery + albums + viewer + vault.
 *
 * Bottom tabs: গ্যালারি / অ্যালবাম / ভল্ট.
 * The vault tab re-locks on process death (safe default): unlocking is a
 * per-launch gate, never persisted.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChobiVaultTheme {
                ChobiVaultNav()
            }
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    GALLERY("gallery", "গ্যালারি", Icons.Filled.PhotoLibrary),
    ALBUMS("albums", "অ্যালবাম", Icons.Filled.Folder),
    VAULT("vault", "ভল্ট", Icons.Filled.Lock),
}

@Composable
private fun ChobiVaultNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var vaultUnlocked by remember { mutableStateOf(false) }
    val showBottomBar = currentRoute in listOf("gallery", "albums", "vault")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo("gallery") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "gallery",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("gallery") {
                GalleryScreen(
                    onOpen = { item ->
                        navController.navigate(
                            "viewer?uri=${Uri.encode(item.uri.toString())}" +
                                "&name=${Uri.encode(item.displayName)}" +
                                "&mime=${Uri.encode(item.mimeType)}" +
                                "&video=${item.isVideo}",
                        )
                    },
                )
            }
            composable("albums") {
                AlbumsScreen(
                    onOpenAlbum = { name ->
                        navController.navigate("album/${Uri.encode(name)}")
                    },
                )
            }
            composable(
                route = "album/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val name = entry.arguments?.getString("name") ?: ""
                AlbumDetailScreen(
                    albumName = name,
                    onBack = { navController.popBackStack() },
                    onOpen = { item ->
                        navController.navigate(
                            "viewer?uri=${Uri.encode(item.uri.toString())}" +
                                "&name=${Uri.encode(item.displayName)}" +
                                "&mime=${Uri.encode(item.mimeType)}" +
                                "&video=${item.isVideo}",
                        )
                    },
                )
            }
            composable(
                route = "viewer?uri={uri}&name={name}&mime={mime}&video={video}",
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("mime") { type = NavType.StringType },
                    navArgument("video") { type = NavType.BoolType },
                ),
            ) { entry ->
                ViewerScreen(
                    uriString = entry.arguments?.getString("uri") ?: "",
                    displayName = entry.arguments?.getString("name") ?: "",
                    mimeType = entry.arguments?.getString("mime") ?: "",
                    isVideo = entry.arguments?.getBoolean("video") ?: false,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("vault") {
                if (vaultUnlocked) {
                    VaultScreen()
                } else {
                    VaultLockScreen(
                        onUnlocked = { vaultUnlocked = true },
                    )
                }
            }
        }
    }
}
