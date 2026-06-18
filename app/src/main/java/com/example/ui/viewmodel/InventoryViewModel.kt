package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.Category
import com.example.data.Item
import com.example.data.InventoryRepository
import com.example.data.StockTransaction
import com.example.data.SupabaseSyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

enum class UserRole {
    OWNER,
    STAFF
}

enum class ActiveTab {
    DASHBOARD,
    INSERT,
    TAKEOUT,
    HISTORY,
    REPORT
}

data class EodReport(
    val dateString: String = "Today",
    val totalInQty: Float = 0.0f,
    val totalOutQty: Float = 0.0f,
    val totalWasteQty: Float = 0.0f,
    val totalStockUnits: Float = 0.0f,
    val alertItemsCount: Int = 0
)

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("neostock_prefs", Context.MODE_PRIVATE)

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "neostock_db"
    ).fallbackToDestructiveMigration().build()

    private val repository = InventoryRepository(db.inventoryDao())

    // UI state flows
    val categories: StateFlow<List<Category>> = repository.allCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val items: StateFlow<List<Item>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val transactions: StateFlow<List<StockTransaction>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val lowStockCount: StateFlow<Int> = repository.lowStockCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // View state toggles
    private val _currentRole = MutableStateFlow(UserRole.OWNER)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _selectedTab = MutableStateFlow(ActiveTab.DASHBOARD)
    val selectedTab: StateFlow<ActiveTab> = _selectedTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Notification toast messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // PIN Security States
    private val _masterPin = MutableStateFlow(sharedPrefs.getString("master_pin", "") ?: "")
    val masterPin: StateFlow<String> = _masterPin.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // Supabase Cloud Authentication States
    private val _isSupabaseLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_supabase_logged_in", false))
    val isSupabaseLoggedIn: StateFlow<Boolean> = _isSupabaseLoggedIn.asStateFlow()

    private val _isSupabaseAuthLoading = MutableStateFlow(false)
    val isSupabaseAuthLoading: StateFlow<Boolean> = _isSupabaseAuthLoading.asStateFlow()

    // Auto sync configuration state
    private val _isAutoSyncEnabled = MutableStateFlow(sharedPrefs.getBoolean("is_auto_sync_enabled", true))
    val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _isAmharic = MutableStateFlow(sharedPrefs.getBoolean("is_amharic", false))
    val isAmharic: StateFlow<Boolean> = _isAmharic.asStateFlow()

    fun toggleLanguage() {
        val next = !_isAmharic.value
        sharedPrefs.edit().putBoolean("is_amharic", next).apply()
        _isAmharic.value = next
    }

    // Dynamically computed metric: Sum of all item quantities (no $ cost/currency used!)
    val totalStockUnits: StateFlow<Float> = items.map { list ->
        list.sumOf { it.currentStock.toDouble() }.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // EOD Reports data calculated dynamically from transactions
    val eodReport: StateFlow<EodReport> = combine(
        transactions,
        totalStockUnits,
        lowStockCount
    ) { txList, totalUnits, alerts ->
        var inQty = 0f
        var outQty = 0f
        var wasteQty = 0f
        val todayStart = getStartOfToday()
        for (tx in txList) {
            // Only aggregate "Today's" or current session's transactions to be accurate for EOD
            if (tx.timestamp >= todayStart) {
                when (tx.type) {
                    "IN" -> inQty += tx.quantity
                    "OUT" -> outQty += tx.quantity
                    "WASTE" -> wasteQty += tx.quantity
                }
            }
        }
        EodReport(
            dateString = "Today's statement",
            totalInQty = inQty,
            totalOutQty = outQty,
            totalWasteQty = wasteQty,
            totalStockUnits = totalUnits,
            alertItemsCount = alerts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EodReport())

    init {
        // Automatically seed with high-fidelity preset categories & items so the UI pops beautifully.
        viewModelScope.launch {
            repository.seedMockDataIfEmpty()
            if (isNetworkAvailable() && _isAutoSyncEnabled.value && _isSupabaseLoggedIn.value) {
                triggerSync()
            }
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun getStartOfToday(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // --- Authentication Actions ---
    fun setupPin(pinCode: String) {
        if (pinCode.length < 4) {
            showError("PIN code must be at least 4 digits")
            return
        }
        sharedPrefs.edit().putString("master_pin", pinCode).apply()
        _masterPin.value = pinCode
        _isAuthenticated.value = true
        showSuccess("Security PIN set up successfully!")
    }

    fun verifyPin(pinCode: String): Boolean {
        return if (pinCode == _masterPin.value) {
            _isAuthenticated.value = true
            showSuccess("Access Granted. Welcome back.")
            true
        } else {
            showError("Invalid PIN Code. Please try again.")
            false
        }
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (oldPin != _masterPin.value) {
            showError("Current PIN is incorrect")
            return false
        }
        if (newPin.length < 4) {
            showError("New PIN must be at least 4 digits")
            return false
        }
        sharedPrefs.edit().putString("master_pin", newPin).apply()
        _masterPin.value = newPin
        showSuccess("Security PIN updated successfully!")
        return true
    }

    fun loginSupabase(emailOrUsername: String, password: String) {
        if (emailOrUsername.isBlank() || password.isBlank()) {
            showError("Username and Password are required.")
            return
        }
        _isSupabaseAuthLoading.value = true
        viewModelScope.launch {
            val result = SupabaseSyncEngine.loginWithSupabase(emailOrUsername, password)
            _isSupabaseAuthLoading.value = false
            if (result.isSuccess) {
                sharedPrefs.edit().putBoolean("is_supabase_logged_in", true).apply()
                _isSupabaseLoggedIn.value = true
                showSuccess("Authenticated with Supabase Cloud successfully!")
                // Automatically pull/push sync to bring online tables
                triggerSync()
            } else {
                showError(result.message)
            }
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_auto_sync_enabled", enabled).apply()
        _isAutoSyncEnabled.value = enabled
        showSuccess("Auto-Sync is now ${if (enabled) "Enabled" else "Disabled"}")
    }

    fun logout() {
        sharedPrefs.edit()
            .putBoolean("is_supabase_logged_in", false)
            .putString("master_pin", "")
            .apply()
        _isSupabaseLoggedIn.value = false
        _masterPin.value = ""
        _isAuthenticated.value = false
        showSuccess("Logged out of session. Access terminated.")
    }

    // --- Display Format Preferred Indicator Storage (Whole standard numeric vs Decimal Quantity) ---
    // True: formatted as whole number (1, 2, 3...)
    // False: formatted as quantity metric (e.g. 24.50 kg, 3.12 L)
    fun getItemDisplayMode(itemId: String): Boolean {
        return sharedPrefs.getBoolean("display_mode_whole_$itemId", false)
    }

    fun setItemDisplayMode(itemId: String, isWholeOnly: Boolean) {
        sharedPrefs.edit().putBoolean("display_mode_whole_$itemId", isWholeOnly).apply()
        // Simple trick to force trigger UI states
        viewModelScope.launch {
            showSuccess("Display style configured for $itemId")
        }
    }

    // Role Toggler
    fun toggleRole() {
        _currentRole.value = if (_currentRole.value == UserRole.OWNER) UserRole.STAFF else UserRole.OWNER
        showSuccess("Changed active role to: ${_currentRole.value.name}")
    }

    fun selectTab(tab: ActiveTab) {
        _selectedTab.value = tab
    }

    fun selectCategory(category: Category?) {
        _selectedCategory.value = category
    }

    // Dismiss custom toasts
    fun clearToast() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun showError(msg: String) {
        _errorMessage.value = msg
    }

    fun showSuccess(msg: String) {
        _successMessage.value = msg
    }

    // Operations
    fun addCategory(name: String, prefix: String, icon: String) {
        if (name.isBlank() || prefix.isBlank()) {
            showError("Category name and prefix cannot be empty")
            return
        }
        val cleanPrefix = prefix.trim().uppercase()
        if (cleanPrefix.length != 3) {
            showError("Prefix must be exactly 3 letters (e.g. MET)")
            return
        }

        viewModelScope.launch {
            val uppercaseName = name.trim()
            val newCat = Category(
                name = uppercaseName,
                prefix = cleanPrefix,
                imageUrl = icon
            )
            repository.addCategory(newCat)
            showSuccess("Category '$uppercaseName' initialized successfully!")

            if (_isAutoSyncEnabled.value && _isSupabaseLoggedIn.value) {
                repository.syncWithSupabaseReal()
            }
        }
    }

    fun editCategory(id: String, name: String, prefix: String, icon: String) {
        if (name.isBlank() || prefix.isBlank()) {
            showError("Category name and prefix cannot be empty")
            return
        }
        val cleanPrefix = prefix.trim().uppercase()
        if (cleanPrefix.length != 3) {
            showError("Prefix must be exactly 3 letters (e.g. MET)")
            return
        }

        viewModelScope.launch {
            val uppercaseName = name.trim()
            val updatedCat = Category(
                id = id,
                name = uppercaseName,
                prefix = cleanPrefix,
                imageUrl = icon
            )
            repository.addCategory(updatedCat) // room @Insert(onConflict = REPLACE) handles update perfectly
            showSuccess("Category '$uppercaseName' updated successfully!")

            if (_isAutoSyncEnabled.value && _isSupabaseLoggedIn.value) {
                repository.syncWithSupabaseReal()
            }
        }
    }

    fun registerItem(categoryId: String, name: String, unit: String, initialStock: Float, parLevel: Float, displayAsWhole: Boolean, localImagePath: String? = null) {
        if (name.isBlank() || unit.isBlank()) {
            showError("Item Name and Unit cannot be empty")
            return
        }

        viewModelScope.launch {
            val cats = categories.value
            val currentCat = cats.find { it.id == categoryId } ?: return@launch

            // Auto increment ID (DRK-001, DRK-002, etc.)
            val allExistingItems = items.value
            val catPrefixItemsCount = allExistingItems.filter { it.categoryId == categoryId }.size
            val paddedNum = String.format("%03d", catPrefixItemsCount + 1)
            val generatedCustomId = "${currentCat.prefix}-$paddedNum"

            val newItem = Item(
                customId = generatedCustomId,
                categoryId = categoryId,
                name = name.trim(),
                unit = unit.trim(),
                currentStock = initialStock,
                parLevel = parLevel,
                costPrice = 0.0f, // Pricing is eliminated as we track quantities only
                localImagePath = localImagePath
            )

            repository.registerItem(newItem)
            setItemDisplayMode(generatedCustomId, displayAsWhole)
            showSuccess("Registered item $generatedCustomId: ${newItem.name}")

            if (initialStock < 3.0f) {
                triggerLowStockNotification(newItem.name, initialStock)
            }

            if (_isAutoSyncEnabled.value && _isSupabaseLoggedIn.value) {
                repository.syncWithSupabaseReal()
            }
        }
    }

    fun deleteItem(customId: String) {
        viewModelScope.launch {
            repository.deleteItem(customId)
            showSuccess("Removed Item $customId from records.")

            if (_isAutoSyncEnabled.value && _isSupabaseLoggedIn.value) {
                repository.syncWithSupabaseReal()
            }
        }
    }

    fun logTransaction(itemId: String, type: String, quantity: Float) {
        if (quantity <= 0) {
            showError("Quantity must be greater than 0")
            return
        }

        viewModelScope.launch {
            val itemBefore = repository.getItemByCustomId(itemId) ?: return@launch
            val computedStock = when (type) {
                "IN" -> itemBefore.currentStock + quantity
                "OUT", "WASTE" -> itemBefore.currentStock - quantity
                else -> itemBefore.currentStock
            }

            val success = repository.logMovement(itemId, type, quantity)
            if (success) {
                showSuccess("Successfully logged $quantity $type movement for $itemId")

                // Notification warning when stock is low (below 3)
                if (computedStock < 3.0f) {
                    triggerLowStockNotification(itemBefore.name, computedStock)
                }

                if (_isAutoSyncEnabled.value && _isSupabaseLoggedIn.value) {
                    repository.syncWithSupabaseReal()
                }
            } else {
                showError("Error: Item with ID $itemId not found.")
            }
        }
    }

    fun triggerLowStockNotification(itemName: String, stockLeft: Float) {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "low_stock_alerts_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mak Stock Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers real alert notifications when an item stock falls below 3 units"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Critical Low Stock Alert")
            .setContentText("Warning: '$itemName' is low on stock! Only $stockLeft remaining.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun triggerSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            val result = repository.syncWithSupabaseReal()
            _isSyncing.value = false
            if (result.isSuccess) {
                showSuccess(result.message)
            } else {
                showError(result.message)
            }
        }
    }

    fun clearAllData(pinEntered: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentPin = _masterPin.value
        if (currentPin.isNotEmpty() && pinEntered != currentPin) {
            onFailure(if (_isAmharic.value) "የተሳሳተ የይለፍ ቃል ያስገቡት!" else "Incorrect passcode/PIN entered.")
            return
        }
        viewModelScope.launch {
            try {
                repository.clearAllData()
                showSuccess(if (_isAmharic.value) "ሁሉም መረጃዎች በተሳካ ሁኔታ ተሰርዘዋል።" else "All local records cleared successfully!")
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to clear database.")
            }
        }
    }
}
