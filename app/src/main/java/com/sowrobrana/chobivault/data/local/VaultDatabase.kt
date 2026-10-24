package com.sowrobrana.chobivault.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Vault index (Room). Only metadata lives here — the media bytes live
 * encrypted in app-private files (see VaultRepository). If the app is
 * uninstalled, the Keystore key is gone and these rows are useless
 * BY DESIGN (same honest rule as PhotoLock).
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long,
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: String): VaultItemEntity?

    @Insert
    suspend fun insert(item: VaultItemEntity)

    @Delete
    suspend fun delete(item: VaultItemEntity)
}

@Database(entities = [VaultItemEntity::class], version = 1, exportSchema = false)
abstract class ChobiVaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var instance: ChobiVaultDatabase? = null

        fun get(context: Context): ChobiVaultDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChobiVaultDatabase::class.java,
                    "chobivault.db",
                ).build().also { instance = it }
            }
    }
}
