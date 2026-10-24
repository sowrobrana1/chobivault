package com.sowrobrana.chobivault.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

/**
 * Favorites: just the MediaStore Uri strings, kept in DataStore.
 * (A favorite is a pointer to the real file, never a copy.)
 */
class FavoritesStore(private val context: Context) {

    private val key = stringSetPreferencesKey("favorite_uris")

    val favorites: Flow<Set<String>> =
        context.favoritesDataStore.data.map { it[key] ?: emptySet() }

    suspend fun toggle(uri: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = if (current.contains(uri)) current - uri else current + uri
        }
    }
}
