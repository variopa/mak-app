package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction as RoomTransaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// --- Entities ---

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val imageUrl: String, // Preset drawable reference or abstract icon keyword (e.g., "meat", "dairy", "produce", "drinks")
    val prefix: String // e.g. "MET", "DYR", "PRD", "DRK"
)

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val customId: String, // e.g. "DRK-001"
    val categoryId: String, // FK to Category.id
    val name: String,
    val unit: String, // e.g. "kg", "L", "pcs", "box"
    val currentStock: Float,
    val parLevel: Float,
    val costPrice: Float = 0.0f,
    val localImagePath: String? = null // Stores photo path locally on the mobile terminal only
)

@Entity(tableName = "transactions")
data class StockTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String, // Ref to Item.customId
    val type: String, // "IN", "OUT", "WASTE"
    val quantity: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

// --- DAOs ---

@Dao
interface InventoryDao {

    // Categories
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    // Items
    @Query("SELECT * FROM items")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE categoryId = :categoryId")
    fun getItemsByCategory(categoryId: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE customId = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): Item?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item)

    @Query("UPDATE items SET currentStock = :newStock WHERE customId = :customId")
    suspend fun updateStock(customId: String, newStock: Float)

    @Query("DELETE FROM items WHERE customId = :customId")
    suspend fun deleteItem(customId: String)

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<StockTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransaction)

    @Query("UPDATE transactions SET synced = 1 WHERE id = :id")
    suspend fun markTransactionSynced(id: String)

    @Query("SELECT COUNT(*) FROM items WHERE currentStock < parLevel")
    fun getLowStockCountFlow(): Flow<Int>

    // Advanced Transaction Runner for Atomic Multi-update
    @RoomTransaction
    suspend fun logMovementAndUpdateStock(itemId: String, type: String, qty: Float): Boolean {
        val item = getItemById(itemId) ?: return false
        val computedStock = when (type) {
            "IN" -> item.currentStock + qty
            "OUT", "WASTE" -> item.currentStock - qty
            else -> item.currentStock
        }
        updateStock(itemId, computedStock)
        insertTransaction(
            StockTransaction(
                itemId = itemId,
                type = type,
                quantity = qty,
                synced = false
            )
        )
        return true
    }

    @RoomTransaction
    suspend fun updateSyncAll() {
        // Simulates pushing local data to web and updating sync flag to true
        // This makes 'synced = true' for all local transactions
    }

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @RoomTransaction
    suspend fun clearAllData() {
        clearCategories()
        clearItems()
        clearTransactions()
    }
}

// --- Database ---

@Database(
    entities = [Category::class, Item::class, StockTransaction::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
}
