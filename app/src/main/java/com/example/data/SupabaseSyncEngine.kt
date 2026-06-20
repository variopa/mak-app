package com.example.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object SupabaseSyncEngine {
    private const val TAG = "SupabaseSyncEngine"

    private const val BASE_URL = "https://fsbsxczwtyqbozguuliv.supabase.co"
    private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZzYnN4Y3p3dHlxYm96Z3V1bGl2Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MTgwNTI0OCwiZXhwIjoyMDk3MzgxMjQ4fQ.EkaBu6ZXrDagQuoSNBQKZB05sLwEPEJpN0Qb2dKRtYA"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Authenticate user with email and password via Supabase Auth API
     */
    suspend fun loginWithSupabase(emailOrUsername: String, password: String): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = "$BASE_URL/auth/v1/token?grant_type=password"
        
        // Supabase Auth usually requires an email. If the user joins with a raw username (e.g. admin),
        // we can automatically turn it into an email to provide seamless login, or try email directly.
        val email = if (emailOrUsername.contains("@")) emailOrUsername else "$emailOrUsername@makstockmanager.com"

        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.i(TAG, "Supabase sign in successful!")
                    Result(true, "Authentication successful")
                } else {
                    Log.e(TAG, "Supabase sign in failed: $bodyStr")
                    val errorDesc = try {
                        JSONObject(bodyStr).optString("error_description", "Invalid login credentials")
                    } catch (e: Exception) {
                        val errorMsg = try {
                            JSONObject(bodyStr).optString("msg", "Invalid credentials")
                        } catch (ex: Exception) {
                            "Invalid credentials or connection error"
                        }
                        errorMsg
                    }
                    Result(false, errorDesc)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase login exception", e)
            Result(false, e.message ?: "Network error. Please check your connection.")
        }
    }

    /**
     * Sync local Room DB elements up to Supabase REST schemas
     */
    suspend fun syncLocalToRemote(
        localCategories: List<Category>,
        localItems: List<Item>,
        localTransactions: List<StockTransaction>
    ): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 1. Sync Categories
            val categoriesArray = JSONArray()
            for (cat in localCategories) {
                val obj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("image_url", cat.imageUrl)
                    put("prefix", cat.prefix)
                }
                categoriesArray.put(obj)
            }
            val catRes = upsertTable("categories", categoriesArray)
            if (!catRes.isSuccess) return@withContext catRes

            // 2. Sync Items
            val itemsArray = JSONArray()
            for (item in localItems) {
                val obj = JSONObject().apply {
                    put("custom_id", item.customId)
                    put("category_id", item.categoryId)
                    put("name", item.name)
                    put("unit", item.unit)
                    put("current_stock", item.currentStock)
                    put("par_level", item.parLevel)
                    put("cost_price", item.costPrice)
                }
                itemsArray.put(obj)
            }
            val itemRes = upsertTable("items", itemsArray)
            if (!itemRes.isSuccess) return@withContext itemRes

            // 3. Sync Transactions
            val txsArray = JSONArray()
            for (tx in localTransactions) {
                val obj = JSONObject().apply {
                    put("id", tx.id)
                    put("item_id", tx.itemId)
                    put("type", tx.type)
                    put("quantity", tx.quantity)
                    put("timestamp", tx.timestamp)
                }
                txsArray.put(obj)
            }
            val txRes = upsertTable("transactions", txsArray)
            if (!txRes.isSuccess) return@withContext txRes

            Result(true, "Cloud Database updated! Sync complete.")
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result(false, "Sync failed: ${e.message}")
        }
    }

    private fun upsertTable(tableName: String, jsonArray: JSONArray): Result {
        if (jsonArray.length() == 0) return Result(true, "No records for $tableName")

        val url = "$BASE_URL/rest/v1/$tableName"
        val payload = jsonArray.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates") // standard PostgREST upsert
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result(true, "Success")
                } else {
                    Log.e(TAG, "Failed to upsert table $tableName: $bodyStr")
                    Result(false, "Database issue syncing $tableName. Please verify table rules.")
                }
            }
        } catch (e: Exception) {
            Result(false, "Network issue: ${e.message}")
        }
    }

    private fun fetchTable(tableName: String): JSONArray? {
        val url = "$BASE_URL/rest/v1/$tableName"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    JSONArray(bodyStr)
                } else {
                    Log.e(TAG, "Failed to fetch table $tableName: $bodyStr")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network issue fetching $tableName", e)
            null
        }
    }

    suspend fun syncFullBidirectional(
        dao: InventoryDao,
        localCategories: List<Category>,
        localItems: List<Item>,
        localTransactions: List<StockTransaction>
    ): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // First push local changes (sync up)
            val pushResult = syncLocalToRemote(localCategories, localItems, localTransactions)
            if (!pushResult.isSuccess) {
                Log.w(TAG, "Sync Push warning: ${pushResult.message}")
            }

            // Pull categories from Supabase
            val categoriesJson = fetchTable("categories")
            if (categoriesJson != null) {
                for (i in 0 until categoriesJson.length()) {
                    val obj = categoriesJson.getJSONObject(i)
                    val cat = Category(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        imageUrl = obj.optString("image_url", "restaurant"),
                        prefix = obj.optString("prefix", "GEN")
                    )
                    dao.insertCategory(cat)
                }
            }

            // Pull items from Supabase
            val itemsJson = fetchTable("items")
            val remoteItemIds = mutableSetOf<String>()
            if (itemsJson != null) {
                for (i in 0 until itemsJson.length()) {
                    val obj = itemsJson.getJSONObject(i)
                    val customId = obj.getString("custom_id")
                    remoteItemIds.add(customId)
                    val item = Item(
                        customId = customId,
                        categoryId = obj.getString("category_id"),
                        name = obj.getString("name"),
                        unit = obj.getString("unit"),
                        currentStock = obj.optDouble("current_stock", 0.0).toFloat(),
                        parLevel = obj.optDouble("par_level", 0.0).toFloat(),
                        costPrice = obj.optDouble("cost_price", 0.0).toFloat()
                    )
                    dao.insertItem(item)
                }

                // If items do not exist in supabase, automatically delete them locally
                val localItems = dao.getAllItemsList()
                for (localItem in localItems) {
                    if (!remoteItemIds.contains(localItem.customId)) {
                        Log.i(TAG, "Deleting orphaned local item: ${localItem.customId}")
                        dao.deleteItem(localItem.customId)
                    }
                }
            }

            // Pull transactions from Supabase
            val txsJson = fetchTable("transactions")
            if (txsJson != null) {
                for (i in 0 until txsJson.length()) {
                    val obj = txsJson.getJSONObject(i)
                    val tx = StockTransaction(
                        id = obj.getString("id"),
                        itemId = obj.getString("item_id"),
                        type = obj.getString("type"),
                        quantity = obj.optDouble("quantity", 0.0).toFloat(),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        synced = true
                    )
                    dao.insertTransaction(tx)
                }
            }

            Result(true, "Database live-synced in both directions successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Full bidirectional sync failed", e)
            Result(false, "Sync failed: ${e.message}")
        }
    }

    data class Result(val isSuccess: Boolean, val message: String)
}
