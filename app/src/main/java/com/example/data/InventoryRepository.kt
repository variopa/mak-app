package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class InventoryRepository(private val dao: InventoryDao) {

    val allCategories: Flow<List<Category>> = dao.getAllCategories()
    val allItems: Flow<List<Item>> = dao.getAllItems()
    val allTransactions: Flow<List<StockTransaction>> = dao.getAllTransactions()
    val lowStockCount: Flow<Int> = dao.getLowStockCountFlow()

    fun getItemsByCategory(categoryId: String): Flow<List<Item>> {
        return dao.getItemsByCategory(categoryId)
    }

    suspend fun addCategory(category: Category) {
        dao.insertCategory(category)
    }

    suspend fun deleteCategory(id: String) {
        dao.deleteCategory(id)
    }

    suspend fun registerItem(item: Item) {
        dao.insertItem(item)
    }

    suspend fun deleteItem(customId: String) {
        dao.deleteItem(customId)
    }

    suspend fun clearAllData() {
        dao.clearAllData()
    }

    suspend fun logMovement(itemId: String, type: String, qty: Float): Boolean {
        return dao.logMovementAndUpdateStock(itemId, type, qty)
    }

    suspend fun getItemByCustomId(itemId: String): Item? {
        return dao.getItemById(itemId)
    }

    // real live sync connection with Supabase database
    suspend fun syncWithSupabaseReal(): SupabaseSyncEngine.Result {
        val cats = allCategories.first()
        val items = allItems.first()
        val txs = allTransactions.first()
        val result = SupabaseSyncEngine.syncFullBidirectional(dao, cats, items, txs)
        return result
    }

    // Initialize Mock Data if DB is totally empty. Mock data is removed completely as requested.
    suspend fun seedMockDataIfEmpty() {
    }
}
