package com.sowrobrana.chobivault.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sowrobrana.chobivault.crypto.CryptoManager
import com.sowrobrana.chobivault.data.local.ChobiVaultDatabase
import com.sowrobrana.chobivault.data.local.VaultItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Vault: AES-256/GCM-encrypted files in app-private storage + Room index.
 * Reuses PhotoLock's proven CryptoManager (Keystore key, streaming API).
 *
 * Rules (same honest rules as PhotoLock):
 *  - Decrypted bytes only ever live in memory or a cache temp file that is
 *    deleted the moment viewing/exporting ends.
 *  - Uninstall = permanent loss (Keystore key dies with the app).
 */
class VaultRepository(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = CryptoManager()
    private val dao = ChobiVaultDatabase.get(appContext).vaultDao()
    private val vaultDir = File(appContext.filesDir, "vault").apply { mkdirs() }
    private val cacheDir = File(appContext.cacheDir, "vault_tmp").apply { mkdirs() }

    fun observeItems(): Flow<List<VaultItemEntity>> = dao.observeAll()

    /** Import any content Uri (Photo Picker, gallery viewer) into the vault. */
    suspend fun importUri(uri: Uri, displayName: String, mimeType: String): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val blob = File(vaultDir, "$id.enc")
            var size = 0L
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(blob).use { output ->
                    crypto.encrypt(input, output)
                }
            } ?: error("ফাইল খোলা যায়নি")
            size = blob.length()
            // Decrypt-verify: a blob that cannot be decrypted is not kept.
            FileInputStream(blob).use { input ->
                crypto.decrypt(input, NullOutputStream)
            }
            dao.insert(
                VaultItemEntity(
                    id = id,
                    fileName = displayName,
                    mimeType = mimeType,
                    size = size,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            id
        }

    /** Decrypt one item into a cache temp file. Caller MUST delete it. */
    suspend fun decryptToTemp(id: String): File = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: error("ভল্ট আইটেম পাওয়া যায়নি")
        val blob = File(vaultDir, "$id.enc")
        val tmp = File(cacheDir, "${id}_${entity.fileName}")
        FileInputStream(blob).use { input ->
            FileOutputStream(tmp).use { output ->
                crypto.decrypt(input, output)
            }
        }
        tmp
    }

    /** Decrypt an item's image bytes into memory (for grid thumbnails). */
    suspend fun decryptBytes(id: String): ByteArray = withContext(Dispatchers.IO) {
        val blob = File(vaultDir, "$id.enc")
        FileInputStream(blob).use { input ->
            ByteArrayOutputStream().also { crypto.decrypt(input, it) }.toByteArray()
        }
    }

    /** Export back to the shared gallery (MediaStore, IS_PENDING flow). */
    suspend fun exportToGallery(id: String): Uri = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: error("ভল্ট আইটেম পাওয়া যায়নি")
        val tmp = decryptToTemp(id)
        try {
            val isVideo = entity.mimeType.startsWith("video")
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, entity.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, entity.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val outUri = appContext.contentResolver.insert(collection, values)
                ?: error("গ্যালারিতে লেখা যায়নি")
            appContext.contentResolver.openOutputStream(outUri)?.use { output ->
                FileInputStream(tmp).use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }.also { appContext.contentResolver.update(outUri, it, null, null) }
            }
            outUri
        } finally {
            tmp.delete()
        }
    }

    /** Permanently delete a vault item (blob + index row). */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { dao.delete(it) }
        File(vaultDir, "$id.enc").delete()
        File(cacheDir).listFiles()
            ?.filter { it.name.startsWith(id) }
            ?.forEach { it.delete() }
    }

    private object NullOutputStream : java.io.OutputStream() {
        override fun write(b: Int) = Unit
    }

    private class ByteArrayOutputStream : java.io.ByteArrayOutputStream()
}
