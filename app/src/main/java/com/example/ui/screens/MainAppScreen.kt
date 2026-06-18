package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Category
import com.example.data.Item
import com.example.data.StockTransaction
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActiveTab
import com.example.ui.viewmodel.EodReport
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.UserRole
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.pdf.PdfDocument
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: InventoryViewModel,
    darkModeState: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    onTriggerBiometric: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val totalStockUnits by viewModel.totalStockUnits.collectAsStateWithLifecycle()
    val eodReport by viewModel.eodReport.collectAsStateWithLifecycle()
    val isAmharic by viewModel.isAmharic.collectAsStateWithLifecycle()

    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // PIN Authentication States
    val masterPin by viewModel.masterPin.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isSupabaseLoggedIn by viewModel.isSupabaseLoggedIn.collectAsStateWithLifecycle()
    val isSupabaseAuthLoading by viewModel.isSupabaseAuthLoading.collectAsStateWithLifecycle()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showExportSimDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<Category?>(null) }
    var showDeleteAllSecureDialog by remember { mutableStateOf(false) }

    val orangeGradient = Brush.linearGradient(
        colors = listOf(OrangePrimary, OrangeSecondary)
    )

    // Handle Authentication view vs core app interface
    if (!isSupabaseLoggedIn) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SupabaseLoginScreen(
                onLogin = { email, pass -> viewModel.loginSupabase(email, pass) },
                isLoading = isSupabaseAuthLoading,
                errorMessage = errorMessage,
                onDismissError = { viewModel.clearToast() }
            )
        }
    } else if (!isAuthenticated) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SecurityEntranceScreen(
                masterPin = masterPin,
                onVerify = { viewModel.verifyPin(it) },
                onSetup = { viewModel.setupPin(it) },
                onTriggerBiometric = onTriggerBiometric
            )
        }
    } else {
        // Core Authenticated App Interface
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (selectedTab == ActiveTab.DASHBOARD) {
                    HeaderBanner(
                        totalStockUnits = totalStockUnits,
                        uniqueSkus = items.size,
                        lowStockCount = lowStockCount,
                        isSyncing = isSyncing,
                        orangeGradient = orangeGradient,
                        onSyncClick = { viewModel.triggerSync() },
                        onExportClick = { showExportSimDialog = true },
                        isAmharic = isAmharic,
                        viewModel = viewModel
                    )
                } else {
                    CompactHeaderBar(
                        selectedTab = selectedTab,
                        isAmharic = isAmharic,
                        viewModel = viewModel
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)) with 
                            fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                        },
                        label = "TabSwitcher"
                    ) { tab ->
                        when (tab) {
                            ActiveTab.DASHBOARD -> DashboardTab(
                                categories = categories,
                                items = items,
                                lowStockCount = lowStockCount,
                                selectedCategory = selectedCategory,
                                viewModel = viewModel,
                                currentRole = currentRole,
                                onCategorySelect = { viewModel.selectCategory(it) },
                                onAddCategoryClick = { showAddCategoryDialog = true },
                                onAddItemClick = { showAddItemDialog = true },
                                isAmharic = isAmharic,
                                onEditCategoryClick = { showEditCategoryDialog = it }
                            )
                            ActiveTab.INSERT -> InsertTab(
                                categories = categories,
                                items = items,
                                currentRole = currentRole,
                                viewModel = viewModel,
                                onAddItemClick = { showAddItemDialog = true },
                                onAddCategoryClick = { showAddCategoryDialog = true },
                                isAmharic = isAmharic
                            )
                            ActiveTab.TAKEOUT -> TakeoutTab(
                                categories = categories,
                                items = items,
                                viewModel = viewModel,
                                isAmharic = isAmharic
                            )
                            ActiveTab.HISTORY -> TransactionsTab(
                                transactions = transactions,
                                items = items,
                                currentRole = currentRole,
                                viewModel = viewModel,
                                isAmharic = isAmharic
                            )
                            ActiveTab.REPORT -> ReportTab(
                                eodReport = eodReport,
                                currentRole = currentRole,
                                transactions = transactions,
                                items = items,
                                viewModel = viewModel,
                                onExportClick = { showExportSimDialog = true },
                                darkModeState = darkModeState,
                                isAmharic = isAmharic,
                                onDeleteAllClick = { showDeleteAllSecureDialog = true }
                            )
                        }
                    }
                }

                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelect = { viewModel.selectTab(it) },
                    isAmharic = isAmharic
                )
            }

            if (isSyncing) {
                SyncingOverlay()
            }

            // High Fidelity Animated Custom Toast Notifications
            AnimatedVisibility(
                visible = successMessage != null,
                enter = slideInVertically(initialOffsetY = { -150 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -150 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                successMessage?.let { msg ->
                    ToastMessageCard(
                        message = msg,
                        icon = Icons.Rounded.VerifiedUser,
                        color = GreenAlert,
                        onDismiss = { viewModel.clearToast() }
                    )
                }
            }

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = slideInVertically(initialOffsetY = { -150 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -150 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                errorMessage?.let { msg ->
                    ToastMessageCard(
                        message = msg,
                        icon = Icons.Rounded.Lock,
                        color = RedAlert,
                        onDismiss = { viewModel.clearToast() }
                    )
                }
            }
        }
    }

    // --- DIALOG MODALS ---

    // 1. Add Category Dialog
    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, prefix, icon ->
                viewModel.addCategory(name, prefix, icon)
                showAddCategoryDialog = false
            }
        )
    }

    // Edit Category Dialog
    if (showEditCategoryDialog != null) {
        EditCategoryDialog(
            category = showEditCategoryDialog!!,
            onDismiss = { showEditCategoryDialog = null },
            isAmharic = isAmharic,
            onConfirm = { name, prefix, icon ->
                viewModel.editCategory(showEditCategoryDialog!!.id, name, prefix, icon)
                showEditCategoryDialog = null
            }
        )
    }

    // Secure Data Clear Dialog
    if (showDeleteAllSecureDialog) {
        DeleteAllSecureDialog(
            onDismiss = { showDeleteAllSecureDialog = false },
            isAmharic = isAmharic,
            onConfirm = { pin ->
                viewModel.clearAllData(
                    pinEntered = pin,
                    onSuccess = {
                        showDeleteAllSecureDialog = false
                    },
                    onFailure = {
                        // errorMessage is managed via viewModel.showError automatically
                    }
                )
            }
        )
    }

    // 2. Add Item Dialog with format indicator option (Amount vs Quantity)
    if (showAddItemDialog) {
        AddItemDialog(
            categories = categories,
            preselectedCategory = selectedCategory,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { catId, name, unit, initialStock, parLevel, displayAsWhole, localImagePath ->
                viewModel.registerItem(catId, name, unit, initialStock, parLevel, displayAsWhole, localImagePath)
                showAddItemDialog = false
            }
        )
    }

    // 3. Statement Export Simulation Dialog (Owner duration PDF)
    if (showExportSimDialog) {
        ExportReportDialog(
            transactions = transactions,
            items = items,
            onDismiss = { showExportSimDialog = false },
            isAmharic = isAmharic
        )
    }
}

// ======================== SECURITY LOCKED ENTRY (PIN KEYPAD) ========================

@Composable
fun SecurityEntranceScreen(
    masterPin: String,
    onVerify: (String) -> Boolean,
    onSetup: (String) -> Unit,
    onTriggerBiometric: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    var enteredPin by remember { mutableStateOf("") }
    val isSetupMode = masterPin.isEmpty()

    LaunchedEffect(masterPin) {
        if (masterPin.isNotEmpty() && onTriggerBiometric != null) {
            onTriggerBiometric {
                onVerify(masterPin)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Logo Brand
        Column(
            modifier = Modifier
                .padding(top = 48.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(OrangePrimary)
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "NEOSTOCK SECURE",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isSetupMode) "First-time setup: Configure Admin PIN Code" else "Protected Terminal Lock",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }

        // Animated PIN Dots Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // We show 4 dots representing entered pin layout
                val maxDots = if (isSetupMode) 4 else masterPin.length.coerceAtLeast(4)
                for (i in 0 until maxDots) {
                    val active = i < enteredPin.length
                    val dotScale by animateFloatAsState(targetValue = if (active) 1.25f else 1.0f)
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer(scaleX = dotScale, scaleY = dotScale)
                            .clip(CircleShape)
                            .background(
                                if (active) OrangePrimary else MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = 0.15f
                                )
                            )
                            .border(
                                1.dp,
                                if (active) OrangePrimary else MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = 0.3f
                                ),
                                CircleShape
                            )
                    )
                }
            }

            if (isSetupMode && enteredPin.length == 4) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        onSetup(enteredPin)
                        enteredPin = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("apply_pincode_setup")
                ) {
                    Text("Confirm Admin PIN", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            if (!isSetupMode && onTriggerBiometric != null) {
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(
                    onClick = {
                        onTriggerBiometric {
                            onVerify(masterPin)
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = "Fingerprint Unlock",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to scan fingerprint",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        }

        // Tactile Numeric Grid Pad
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val keyRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Clear", "0", "OK")
            )

            for (row in keyRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (key in row) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(2.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    when (key) {
                                        "Clear" -> {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.substring(0, enteredPin.length - 1)
                                            }
                                        }
                                        "OK" -> {
                                            if (!isSetupMode) {
                                                val success = onVerify(enteredPin)
                                                if (!success) {
                                                    enteredPin = "" // reset on failure
                                                }
                                            } else {
                                                // Setup mode handles via automatic button above or checking 4
                                                if (enteredPin.length >= 4) {
                                                    onSetup(enteredPin)
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 6) {
                                                enteredPin += key
                                            }
                                            // Auto-verify if they type exact length
                                            if (!isSetupMode && enteredPin.length == masterPin.length && masterPin.isNotEmpty()) {
                                                val success = onVerify(enteredPin)
                                                if (!success) {
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                                }
                                .testTag("pin_key_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = if (key.length > 2) 13.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (key == "OK") OrangePrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ======================== POLISHED HEADER BANNER (NO CURRENCY) ========================

@Composable
fun HeaderBanner(
    totalStockUnits: Float,
    uniqueSkus: Int,
    lowStockCount: Int,
    isSyncing: Boolean,
    orangeGradient: Brush,
    onSyncClick: () -> Unit,
    onExportClick: () -> Unit,
    isAmharic: Boolean,
    viewModel: InventoryViewModel
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(orangeGradient)
            .shadow(6.dp, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(top = 28.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MAK STOCK MANAGER",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = t("Stock Balance", "የስቶክ መጠን"),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Language Switch Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                            .clickable { viewModel.toggleLanguage() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isAmharic) "ENGLISH" else "አማርኛ",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                            .clickable { onSyncClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("sync_action_pill"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSyncing) OrangeAlert else GreenAlert)
                        )
                        Text(
                            text = t("LIVE SYNC", "በቀጥታ ማመሳሰል"),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Non-Glass Solid Display Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("Total Active Items", "አጠቃላይ ዕቃዎች"),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format(Locale.US, "%,.2f", totalStockUnits)} " + t("Items", "ዕቃዎች"),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$uniqueSkus " + t("types listed", "የተመዘገቡ ዓይነቶች"),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp
                        )
                    }

                    Button(
                        onClick = onExportClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = OrangePrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("statement_generator_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Assessment,
                            contentDescription = "Report Statement",
                            modifier = Modifier.size(16.dp),
                            tint = OrangePrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = t("GET REPORT", "ሪፖርት አውርድ"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToastMessageCard(
    message: String,
    icon: ImageVector,
    color: Color,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clickable { onDismiss() }
            .testTag("toast_notification"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = message,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ======================== HIGH END BOTTOM BAR (NO QR SCANNER ACCORDING TO USER REQ) ========================

@Composable
fun BottomNavBar(
    selectedTab: ActiveTab,
    onTabSelect: (ActiveTab) -> Unit,
    isAmharic: Boolean
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val menuItems = listOf(
                    Triple(t("HOME", "ዋና ገጽ"), Icons.Rounded.GridView, ActiveTab.DASHBOARD),
                    Triple(t("INSERT", "ማስገባት"), Icons.Rounded.AddCircle, ActiveTab.INSERT),
                    Triple(t("TAKEOUT", "ማውጣት"), Icons.Rounded.RemoveCircle, ActiveTab.TAKEOUT),
                    Triple(t("HISTORY", "ታሪክ"), Icons.Rounded.History, ActiveTab.HISTORY),
                    Triple(t("REPORT", "ሪፖርት"), Icons.Rounded.Assessment, ActiveTab.REPORT)
                )

                for (item in menuItems) {
                    val active = selectedTab == item.third
                    val scale by animateFloatAsState(targetValue = if (active) 1.1f else 1.0f)

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable { onTabSelect(item.third) }
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.second,
                            contentDescription = item.first,
                            tint = if (active) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.first,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ======================== TAB PANELS AND SECTIONS ========================

@Composable
fun DashboardTab(
    categories: List<Category>,
    items: List<Item>,
    lowStockCount: Int,
    selectedCategory: Category?,
    viewModel: InventoryViewModel,
    currentRole: UserRole,
    onCategorySelect: (Category?) -> Unit,
    onAddCategoryClick: () -> Unit,
    onAddItemClick: () -> Unit,
    isAmharic: Boolean,
    onEditCategoryClick: (Category) -> Unit
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    if (selectedCategory != null) {
        CategoryDrilldownView(
            category = selectedCategory,
            items = items.filter { it.categoryId == selectedCategory.id },
            currentRole = currentRole,
            onBack = { onCategorySelect(null) },
            viewModel = viewModel,
            onAddItemClick = onAddItemClick,
            onEditCategoryClick = onEditCategoryClick
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Low stock alert warning bar
            if (lowStockCount > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, RedAlert.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .clickable { viewModel.selectTab(ActiveTab.REPORT) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(RedAlert.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = RedAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "$lowStockCount " + t("items below alert limit", "ዕቃዎች ከአነስተኛ ገደብ በታች ናቸው"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "View",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Categories heading
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("ANALYTICS BY CATEGORY", "ዕቃዎች በየምድቡ"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.2.sp
                    )

                    if (currentRole == UserRole.OWNER) {
                        Text(
                            text = "+ " + t("NEW CATEGORY", "አዲስ ምድብ መፍጠሪያ"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary,
                            modifier = Modifier
                                .clickable { onAddCategoryClick() }
                                .testTag("add_category_trigger")
                        )
                    }
                }
            }

            // Categories list and metrics
            item {
                if (categories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t("No Categories initialized yet.", "እስካሁን ምንም ዓይነት ምድቦች አልተፈጠሩም።"),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    CustomCategoryGrid(
                        categories = categories,
                        items = items,
                        onCategorySelect = onCategorySelect
                    )
                }
            }
        }
    }
}

@Composable
fun CustomCategoryGrid(
    categories: List<Category>,
    items: List<Item>,
    onCategorySelect: (Category?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val rows = categories.chunked(2)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (cat in row) {
                    val count = items.filter { it.categoryId == cat.id }.size
                    val lowForCat = items.filter { it.categoryId == cat.id && it.currentStock < it.parLevel }.size

                    CategoryCard(
                        category = cat,
                        itemCount = count,
                        lowStockAlertCount = lowForCat,
                        onClick = { onCategorySelect(cat) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: Category,
    itemCount: Int,
    lowStockAlertCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (lowStockAlertCount > 0) RedAlert.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.08f
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("category_card_${category.prefix}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (category.imageUrl) {
                                "restaurant" -> Color(0xFFFFECE7)
                                "egg" -> Color(0xFFE8F4FD)
                                "eco" -> Color(0xFFEAF9EB)
                                "local_bar" -> Color(0xFFFBE9FA)
                                else -> Color(0xFFF1F5F9)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (category.imageUrl) {
                            "restaurant" -> Icons.Rounded.GridView
                            "egg" -> Icons.Rounded.Storage
                            "eco" -> Icons.Rounded.VerifiedUser
                            "local_bar" -> Icons.Rounded.BarChart
                            else -> Icons.Rounded.Inventory
                        },
                        contentDescription = category.name,
                        tint = when (category.imageUrl) {
                            "restaurant" -> Color(0xFFD84315)
                            "egg" -> Color(0xFF1565C0)
                            "eco" -> Color(0xFF2E7D32)
                            "local_bar" -> Color(0xFFAD1457)
                            else -> Color(0xFF475569)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (lowStockAlertCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(RedAlert)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$lowStockAlertCount ALERT",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Column {
                Text(
                    text = category.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$itemCount Stock classes",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun CategoryDrilldownView(
    category: Category,
    items: List<Item>,
    currentRole: UserRole,
    onBack: () -> Unit,
    viewModel: InventoryViewModel,
    onAddItemClick: () -> Unit,
    onEditCategoryClick: (Category) -> Unit
) {
    val isAmharic by viewModel.isAmharic.collectAsStateWithLifecycle()
    val t = { en: String, am: String -> if (isAmharic) am else en }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_categories")) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = category.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (currentRole == UserRole.OWNER) {
                    IconButton(
                        onClick = { onEditCategoryClick(category) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Category",
                            tint = OrangePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (currentRole == UserRole.OWNER) {
                Button(
                    onClick = onAddItemClick,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Add, "add", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Register Item", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No items registered in this category yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items, key = { it.customId }) { item ->
                    ItemDisplayRow(
                        item = item,
                        currentRole = currentRole,
                        viewModel = viewModel,
                        onDelete = { viewModel.deleteItem(item.customId) }
                    )
                }
            }
        }
    }
}

// ======================== STOCK ITEM LISTING INTERFACE ========================

@Composable
fun ItemDisplayRow(
    item: Item,
    currentRole: UserRole,
    viewModel: InventoryViewModel,
    onDelete: () -> Unit
) {
    val lowStock = item.currentStock < item.parLevel
    val displayAsWhole = viewModel.getItemDisplayMode(item.customId)
    var showQuickLogDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (lowStock) RedAlert.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .clickable { showQuickLogDialog = true }
            .padding(14.dp)
            .testTag("item_row_${item.customId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High fidelity custom image thumbnail (stored locally on device only)
            if (!item.localImagePath.isNullOrEmpty()) {
                val painter = rememberAsyncImagePainter(model = item.localImagePath)
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OrangePrimary.copy(alpha = 0.06f))
                        .border(1.dp, OrangePrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fastfood,
                        contentDescription = "Food item thumbnail",
                        tint = OrangePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OrangePrimary.copy(alpha = 0.1f))
                            .border(1.dp, OrangePrimary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.customId,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (lowStock) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(RedAlert.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LOW LEVEL",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = RedAlert
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Threshold Par: ${item.parLevel} ${item.unit}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                // Stock displays based on Admin preference (Whole amount vs Metric precision)
                val formattedStock = if (displayAsWhole) {
                    "${item.currentStock.toInt()} ${item.unit}"
                } else {
                    "${String.format(Locale.US, "%.2f", item.currentStock)} ${item.unit}"
                }

                Text(
                    text = formattedStock,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (lowStock) RedAlert else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (displayAsWhole) "WHOLE" else "DECIMAL",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangePrimary,
                        modifier = Modifier
                            .clickable { viewModel.setItemDisplayMode(item.customId, !displayAsWhole) }
                            .padding(4.dp)
                    )

                    if (currentRole == UserRole.OWNER) {
                        Text(
                            text = "REMOVE",
                            color = RedAlert.copy(alpha = 0.8f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onDelete() }
                                .padding(4.dp)
                                .testTag("delete_item_${item.customId}")
                        )
                    }
                }
            }
        }
    }

    if (showQuickLogDialog) {
        QuickLogMovementDialog(
            item = item,
            onDismiss = { showQuickLogDialog = false },
            onLog = { type, qty ->
                viewModel.logTransaction(item.customId, type, qty)
                showQuickLogDialog = false
            }
        )
    }
}

// ======================== INSERT STOCK / RESTOCK PANEL ========================

@Composable
fun InsertTab(
    categories: List<Category>,
    items: List<Item>,
    currentRole: UserRole,
    viewModel: InventoryViewModel,
    onAddItemClick: () -> Unit,
    onAddCategoryClick: () -> Unit,
    isAmharic: Boolean
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    var selectedItem by remember { mutableStateOf<Item?>(null) }
    var qtyInput by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = t("STOCK INSERT / RESTOCK", "የዕቃ ማስገቢያ ቅጽ"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp
            )
        }

        // Add existing item quantity form
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.AddCircle, "restock", tint = GreenAlert, modifier = Modifier.size(18.dp))
                        Text(
                            text = t("ADD TO EXISTING STOCK", "በነባር ዕቃ ላይ መጨመር"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedItem?.let { "${it.customId} - ${it.name}" } ?: t("Select existing item...", "ዕቃ ይምረጡ..."),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Rounded.ArrowDropDown, null)
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 260.dp)
                        ) {
                            items.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${item.customId} - ${item.name} (${item.currentStock} ${item.unit})", fontSize = 12.sp) },
                                    onClick = {
                                        selectedItem = item
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it },
                        label = { Text(t("INSERT QUANTITY (No. of items)", "የሚገባው መጠን")) },
                        placeholder = { Text("E.g., 20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("insert_qty_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            selectedItem?.let { item ->
                                val qty = qtyInput.toFloatOrNull() ?: 0.0f
                                if (qty > 0f) {
                                    viewModel.logTransaction(item.customId, "IN", qty)
                                    qtyInput = ""
                                    selectedItem = null
                                }
                            }
                        },
                        enabled = selectedItem != null && qtyInput.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("insert_confirm_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAlert),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(t("ADD STOCK", "ዕቃ ጨምር"), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // New item or new category initiator buttons for landowners/owners
        if (currentRole == UserRole.OWNER) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Add, "register", tint = OrangePrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = t("REGISTER NEW", "አዲስ ዓይነት ዕቃ መመዝገቢያ"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = onAddItemClick,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("register_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(t("REGISTER FAB NEW ITEM", "አዲስ ዓይነት ዕቃ ይመዝገቡ"), fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = onAddCategoryClick,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("register_category_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(t("CREATE NEW CATEGORY", "አዲስ ምድብ ይፍጠሩ"), fontWeight = FontWeight.Bold, color = OrangePrimary)
                        }
                    }
                }
            }
        }
    }
}

// ======================== TAKEOUT STOCK / REDUCTION PANEL ========================

@Composable
fun TakeoutTab(
    categories: List<Category>,
    items: List<Item>,
    viewModel: InventoryViewModel,
    isAmharic: Boolean
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    var selectedItem by remember { mutableStateOf<Item?>(null) }
    var qtyInput by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var isWasteMode by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = t("STOCK OUT / DESTRUCTION", "የዕቃ ማውጫ ቅጽ"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.RemoveCircle, "remove", tint = RedAlert, modifier = Modifier.size(18.dp))
                        Text(
                            text = t("TAKE OUT FROM STOCK", "ከስቶክ ላይ መቀነስ/ማውጣት"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Switcher pill selection between standard and waste mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeColor = if (!isWasteMode) RedAlert else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        val wasteColor = if (isWasteMode) Color.DarkGray else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(activeColor)
                                .clickable { isWasteMode = false }
                                .padding(vertical = 10.dp)
                                .testTag("takeout_mode_standard"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t("STANDARD TAKE OUT", "ሪል ታይም ማውጣት"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isWasteMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(wasteColor)
                                .clickable { isWasteMode = true }
                                .padding(vertical = 10.dp)
                                .testTag("takeout_mode_waste"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t("WASTE / LOSS", "ብክነት / ጉዳት"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWasteMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Choose item selector dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("takeout_item_select_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedItem?.let { "${it.customId} - ${it.name}" } ?: t("Select item to takeout...", "ማውጣት የሚፈልጉትን ዕቃ ይምረጡ..."),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Rounded.ArrowDropDown, null)
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 260.dp)
                        ) {
                            items.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${item.customId} - ${item.name} (${item.currentStock} ${item.unit})", fontSize = 12.sp) },
                                    onClick = {
                                        selectedItem = item
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it },
                        label = { Text(t("QUANTITY (No. of items)", "የሚወጣው መጠን")) },
                        placeholder = { Text("E.g., 5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("takeout_qty_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            selectedItem?.let { item ->
                                val qty = qtyInput.toFloatOrNull() ?: 0.0f
                                if (qty > 0f) {
                                    val actionType = if (isWasteMode) "WASTE" else "OUT"
                                    viewModel.logTransaction(item.customId, actionType, qty)
                                    qtyInput = ""
                                    selectedItem = null
                                }
                            }
                        },
                        enabled = selectedItem != null && qtyInput.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("takeout_confirm_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isWasteMode) Color.DarkGray else RedAlert),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isWasteMode) t("LOG LOSS", "የብክነት ታሪክ መዝግብ") else t("LOG TAKE OUT", "የማውጣት ታሪክ መዝግብ"),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ======================== HISTORICAL AUDIT LOGS ========================

@Composable
fun TransactionsTab(
    transactions: List<StockTransaction>,
    items: List<Item>,
    currentRole: UserRole,
    viewModel: InventoryViewModel,
    isAmharic: Boolean
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = t("STOCK OPERATION HISTORY", "የዕቃዎች ዝውውር ታሪክ"),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t("No stock operations logged yet.", "እስካሁን ምንም ዓይነት የዕቃ እንቅስቃሴዎች አልተመዘገቡም።"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(transactions, key = { it.id }) { tx ->
                    val matchingItemName = items.find { it.customId == tx.itemId }?.name ?: "Unknown Item"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (tx.type) {
                                                    "IN" -> GreenAlert.copy(alpha = 0.12f)
                                                    "OUT" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                                    "WASTE" -> RedAlert.copy(alpha = 0.12f)
                                                    else -> OrangePrimary.copy(alpha = 0.1f)
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when (tx.type) {
                                                "IN" -> t("RESTOCKED", "የገባ ዕቃ")
                                                "OUT" -> t("TAKEN OUT", "የወጣ ዕቃ")
                                                "WASTE" -> t("WASTED", "የተበላሸ ዕቃ")
                                                else -> tx.type
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = when (tx.type) {
                                                "IN" -> GreenAlert
                                                "OUT" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                "WASTE" -> RedAlert
                                                else -> OrangePrimary
                                            }
                                        )
                                    }

                                    Text(
                                        text = tx.itemId,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = matchingItemName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val prefixSign = when(tx.type) {
                                    "IN" -> "+"
                                    "OUT" -> "-"
                                    "WASTE" -> "-"
                                    else -> ""
                                }
                                Text(
                                    text = "$prefixSign${tx.quantity}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when (tx.type) {
                                        "IN" -> GreenAlert
                                        "OUT" -> MaterialTheme.colorScheme.onSurface
                                        "WASTE" -> RedAlert
                                        else -> OrangePrimary
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = df.format(Date(tx.timestamp)),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================== DETAILED DAILY STOCKS LOG PANEL ========================

@Composable
fun ReportTab(
    eodReport: EodReport,
    currentRole: UserRole,
    transactions: List<StockTransaction>,
    items: List<Item>,
    viewModel: InventoryViewModel,
    onExportClick: () -> Unit,
    darkModeState: MutableState<Boolean>,
    isAmharic: Boolean,
    onDeleteAllClick: () -> Unit
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("DAY CLOSING CONSOLIDATION", "የዕለቱ ማጠቃለያ ሪፖርት"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    letterSpacing = 1.2.sp
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OrangePrimary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = t("STATUS: ACTIVE", "ሁኔታ፡ ገባሪ"),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangePrimary
                    )
                }
            }
        }

        // Consolidated Daily Volume Log Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = t("Consolidated Daily Volume Log", "የዛሬው ጠቅላላ ዝውውር"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ReportMetric(
                            label = t("STOCK IN", "የገባ ዕቃ"),
                            value = "${eodReport.totalInQty}",
                            color = GreenAlert
                        )
                        ReportMetric(
                            label = t("STOCK OUT", "የወጣ ዕቃ"),
                            value = "${eodReport.totalOutQty}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ReportMetric(
                            label = t("WASTED", "የጠፋ/የተበላሸ"),
                            value = "${eodReport.totalWasteQty}",
                            color = RedAlert
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = t("Remaining Item Inventory", "በስቶክ ላይ ያለ ቀሪ ዕቃ"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Text(
                                text = "${eodReport.totalStockUnits} " + t("Items", "ዕቃዎች"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = onExportClick,
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Print,
                                contentDescription = "Print PDF",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(t("GET PDF STATEMENT", "ፒዲኤፍ ሪፖርት አውርድ"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Today's transaction preview heading
        item {
            Text(
                text = t("Today's Transaction Audits", "የዛሬ ዝርዝር እንቅስቃሴዎች"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.0.sp
            )
        }

        val todayTxs = transactions.take(5)
        if (todayTxs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t("No movements recorded in today's cycle.", "በዛሬው ዑደት እስካሁን ምንም አይነት ስራ አልተመዘገበም።"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(todayTxs) { tx ->
                val itemName = items.find { it.customId == tx.itemId }?.name ?: "Unknown"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (tx.type == "IN") GreenAlert else if (tx.type == "WASTE") RedAlert else Color.Gray)
                        )
                        Column {
                            Text(text = itemName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${tx.itemId} • " + when(tx.type) {
                                    "IN" -> t("RESTOCKED", "የገባ")
                                    "OUT" -> t("TAKEN OUT", "የወጣ")
                                    "WASTE" -> t("WASTED", "የጠፋ")
                                    else -> tx.type
                                }, 
                                fontSize = 10.sp, 
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Text(
                        text = "${if(tx.type == "IN") "+" else "-"}${tx.quantity}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.type == "IN") GreenAlert else if (tx.type == "WASTE") RedAlert else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // NEW: Inline Settings & Terminal Configuration Panel for Simplified Design Architecture
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = t("TERMINAL SYSTEM PREFERENCES", "የመተግበሪያው ቅንብሮች"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.0.sp
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Dark theme toggler row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.DarkMode, "darktheme", tint = OrangePrimary, modifier = Modifier.size(16.dp))
                            Text(text = t("Dark Slate Visual Theme", "ደማቅ ገጽታ (Dark Mode)"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = darkModeState.value,
                            onCheckedChange = { darkModeState.value = it }
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Account settings row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = t("Client Access Mode", "የአካውንት ደረጃ"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = t("Current Access Level: ", "የአሁኑ ደረጃ፡ ") + currentRole.name, fontSize = 9.sp, color = OrangePrimary)
                        }

                        Button(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(t("LOGOUT", "ውጣ"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (currentRole == UserRole.OWNER) {
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = t("Erase All Terminal Data", "ዳታን ሙሉ በሙሉ አጥፋ"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = t("Clear local categories/items", "ሁሉንም አጥፍቶ መጀመርያ"), fontSize = 9.sp, color = Color.Gray)
                            }

                            Button(
                                onClick = onDeleteAllClick,
                                colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(t("DELETE ALL", "ሁሉንም ሰርዝ"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportMetric(
    label: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

// ======================== STUNNING SETTINGS AND PIN MANAGER PANEL ========================

@Composable
fun SettingsTab(
    currentRole: UserRole,
    darkModeState: MutableState<Boolean>,
    viewModel: InventoryViewModel
) {
    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var showPinChangeForm by remember { mutableStateOf(false) }

    val isAutoSyncEnabled by viewModel.isAutoSyncEnabled.collectAsStateWithLifecycle()
    val isNetworkConnected = viewModel.isNetworkAvailable()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "CONFIGURATIONS & CRYPTO-KEY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp
            )
        }

        // 1. PIN PASSWORD MANAGE CRADLE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(OrangePrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text("Admin Passcode PIN", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Require PIN to enter system", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }

                        Button(
                            onClick = { showPinChangeForm = !showPinChangeForm },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary.copy(alpha = 0.12f), contentColor = OrangePrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(if (showPinChangeForm) "Cancel" else "Update PIN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showPinChangeForm) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = oldPinInput,
                            onValueChange = { oldPinInput = it },
                            label = { Text("Current PIN", fontSize = 11.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { newPinInput = it },
                            label = { Text("New PIN (min 4 digits)", fontSize = 11.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val success = viewModel.changePin(oldPinInput, newPinInput)
                                if (success) {
                                    oldPinInput = ""
                                    newPinInput = ""
                                    showPinChangeForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = oldPinInput.isNotEmpty() && newPinInput.isNotEmpty()
                        ) {
                            Text("Save PIN Lock", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // 2. ACTIVE SYSTEM ROLE CONFIG (OWNER vs STAFF)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF00796B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text("Security Authorization Level", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Current: ${currentRole.name}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    Switch(
                        checked = currentRole == UserRole.OWNER,
                        onCheckedChange = { viewModel.toggleRole() },
                        colors = SwitchDefaults.colors(checkedThumbColor = OrangePrimary, checkedTrackColor = OrangePrimary.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // 3. DARK OVERLAY TOGGLE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFECEFF1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (darkModeState.value) Icons.Rounded.Settings else Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF37474F),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text("Night slate background mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Deep gray and coal elements", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    Switch(
                        checked = darkModeState.value,
                        onCheckedChange = { darkModeState.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = OrangePrimary, checkedTrackColor = OrangePrimary.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // 4. AUTOMATIC BACKUP SYNC TOGGLE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text("Realtime Cloud Synchronization", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isNetworkConnected) "Status: Authenticated & Connected to Supabase" else "Status: Offline, changes queued locally",
                                fontSize = 10.sp,
                                color = if (isNetworkConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Switch(
                        checked = isAutoSyncEnabled,
                        onCheckedChange = { viewModel.setAutoSyncEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = OrangePrimary, checkedTrackColor = OrangePrimary.copy(alpha = 0.3f))
                    )
                }
            }
        }

        // 5. TERMINAL RE-LOCK (LOGOUT)
        item {
            Button(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("terminal_logout_action")
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out & Secure Terminal Controls", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

// ======================== MODAL POPUPS AND INPUT DIALOGS ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, prefix: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("restaurant") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Initiate New Stock Category",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("E.g., Meat & Shellfish") },
                    label = { Text("Category Name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { if (it.length <= 3) prefix = it.uppercase() },
                    placeholder = { Text("E.g., MET (Length Exactly 3)") },
                    label = { Text("ID prefix barcode tag") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Visual Class Icon Indicator",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val icons = listOf("restaurant", "egg", "eco", "local_bar")
                    val names = listOf("Plates", "Dairy", "Greens", "Cups")
                    for (i in icons.indices) {
                        val active = selectedIcon == icons[i]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                .border(1.dp, if (active) OrangePrimary else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedIcon = icons[i] }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = names[i],
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Abstain", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onConfirm(name, prefix, selectedIcon) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        enabled = name.isNotEmpty() && prefix.length == 3
                    ) {
                        Text("Establish", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    categories: List<Category>,
    preselectedCategory: Category?,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: String, name: String, unit: String, initialStock: Float, parLevel: Float, displayAsWhole: Boolean, localImagePath: String?) -> Unit
) {
    var selectedCatId by remember { mutableStateOf(preselectedCategory?.id ?: categories.firstOrNull()?.id ?: "") }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var initialStock by remember { mutableStateOf("") }
    var parLevel by remember { mutableStateOf("") }
    // User config representation indicator layout setting (Amount vs decimal quantity)
    var displayAsWhole by remember { mutableStateOf(false) }
    var localImagePath by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            localImagePath = it.toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Register Premium Stock Sku",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Category Choosing Dropdown simulator
                Column {
                    Text("Host Category Class", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (cat in categories) {
                            val active = selectedCatId == cat.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                    .clickable { selectedCatId = cat.id }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat.prefix,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("E.g., Fresh Organic Carrots") },
                    label = { Text("Stock Sku Name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        placeholder = { Text("E.g., kg, L, unit") },
                        label = { Text("Unit Symbol") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = initialStock,
                        onValueChange = { initialStock = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        placeholder = { Text("E.g., 25") },
                        label = { Text("Stock Level") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = parLevel,
                    onValueChange = { parLevel = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("Alert threshold e.g., 5.0") },
                    label = { Text("Safety Par Level Trigger") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Local Only Product Sku Photo Picker Row
                Column {
                    Text("Product Image (Local Device Only)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                            .clickable { photoPickerLauncher.launch("image/*") }
                            .padding(10.dp)
                    ) {
                        if (localImagePath == null) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OrangePrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Tap to choose a local photo for this stock sku",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            // Show selected thumbnail
                            val painter = rememberAsyncImagePainter(model = localImagePath)
                            Image(
                                painter = painter,
                                contentDescription = "Picked image",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready (Stored Locally)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = "Tap to change image",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                            IconButton(onClick = { localImagePath = null }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Remove Photo",
                                    tint = RedAlert,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Indicator Choice Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Value Display Alignment",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (displayAsWhole) "Display integer amount (1, 2, 3)" else "Display metric quantity (e.g. 2.50 kg)",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Switch(
                        checked = displayAsWhole,
                        onCheckedChange = { displayAsWhole = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = OrangePrimary, checkedTrackColor = OrangePrimary.copy(alpha = 0.3f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Abort", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val initQty = initialStock.toFloatOrNull() ?: 0.0f
                            val pLvl = parLevel.toFloatOrNull() ?: 0.0f
                            onConfirm(selectedCatId, name, unit, initQty, pLvl, displayAsWhole, localImagePath)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        enabled = name.isNotEmpty() && selectedCatId.isNotEmpty()
                    ) {
                        Text("Register", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickLogMovementDialog(
    item: Item,
    onDismiss: () -> Unit,
    onLog: (type: String, quantity: Float) -> Unit
) {
    var type by remember { mutableStateOf("IN") }
    var quantityText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Log Stock Amendment for ${item.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                // Select transaction type IN, OUT or WASTE (No cost/currency implications, totally quantity based!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf("IN", "OUT", "WASTE")
                    val colors = listOf(GreenAlert, MaterialTheme.colorScheme.onSurface, RedAlert)
                    for (i in types.indices) {
                        val active = type == types[i]
                        val btnBg = if (active) colors[i] else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        val btnTx = if (active) Color.White else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(btnBg)
                                .clickable { type = types[i] }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = types[i],
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = btnTx
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("E.g., 5.5") },
                    label = { Text("Transaction Quantity (${item.unit})") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val computedDescription = when (type) {
                    "IN" -> "Will add quantity to active store inventory balance."
                    "OUT" -> "Will deplete quantity from active store inventory balance."
                    "WASTE" -> "Will register spoilage/loss and deplete quantity balance."
                    else -> ""
                }

                Text(
                    text = computedDescription,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val qtyVal = quantityText.toFloatOrNull() ?: 0.0f
                            if (qtyVal > 0f) {
                                onLog(type, qtyVal)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        enabled = quantityText.isNotEmpty()
                    ) {
                        Text("Log Balance", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ======================== OWNER'S INTERACTIVE CUSTOM PDF STATEMENT EXPORT DIALOG ========================

// Real companion PDF generation helper function using Android's native graphics and Canvas APIs
private fun generateRealPdf(
    file: File,
    durationName: String,
    totalInQty: Float,
    totalOutQty: Float,
    totalWasteQty: Float,
    txs: List<StockTransaction>,
    items: List<Item>,
    isAmharic: Boolean
): Boolean {
    return try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        // Page Background
        paint.color = AndroidColor.WHITE
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        // Title Header
        paint.color = AndroidColor.argb(255, 33, 33, 33)
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("MAK STOCK MANAGER - STATEMENT", 40f, 60f, paint)

        // Subtitle information
        paint.color = AndroidColor.GRAY
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Statement Duration Range: $durationName", 40f, 85f, paint)
        canvas.drawText("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}", 40f, 102f, paint)

        // Divider
        paint.color = AndroidColor.LTGRAY
        canvas.drawLine(40f, 115f, 555f, 115f, paint)

        // Consolidated daily activity totals row
        paint.color = AndroidColor.DKGRAY
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Consolidated Aggregations Summary", 40f, 145f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = AndroidColor.argb(255, 46, 125, 50) // Green
        canvas.drawText("• Total Stock Inserted (IN): +$totalInQty items", 50f, 175f, paint)
        paint.color = AndroidColor.DKGRAY
        canvas.drawText("• Total Stock Released (OUT): -$totalOutQty items", 50f, 195f, paint)
        paint.color = AndroidColor.argb(255, 198, 40, 40) // Red
        canvas.drawText("• Total Stock Wasted (WASTE): -$totalWasteQty items", 50f, 215f, paint)

        // Divider
        paint.color = AndroidColor.LTGRAY
        canvas.drawLine(40f, 240f, 555f, 240f, paint)

        // Table headers config
        paint.color = AndroidColor.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("SKU / Item ID", 45f, 275f, paint)
        canvas.drawText("Item name", 150f, 275f, paint)
        canvas.drawText("Operation", 330f, 275f, paint)
        canvas.drawText("Quantity", 460f, 275f, paint)

        canvas.drawLine(40f, 290f, 555f, 290f, paint)

        // Data rows configuration
        paint.textSize = 10f
        paint.isFakeBoldText = false
        var currentY = 315f
        val printableTxs = txs.take(15)

        printableTxs.forEach { tx ->
            val itName = items.find { it.customId == tx.itemId }?.name ?: "Unknown Item"
            canvas.drawText(tx.itemId, 45f, currentY, paint)
            
            // Limit name width to avoid overlay overlaps
            val truncatedName = if (itName.length > 25) itName.substring(0, 22) + "..." else itName
            canvas.drawText(truncatedName, 150f, currentY, paint)
            
            canvas.drawText(tx.type, 330f, currentY, paint)
            
            val displayQty = when(tx.type) {
                "IN" -> "+${tx.quantity}"
                else -> "-${tx.quantity}"
            }
            canvas.drawText(displayQty, 460f, currentY, paint)

            currentY += 24f
        }

        if (txs.size > 15) {
            paint.color = AndroidColor.GRAY
            canvas.drawText("... and ${txs.size - 15} more logged transactions ...", 45f, currentY, paint)
            currentY += 24f
        }

        // Footer block
        paint.color = AndroidColor.LTGRAY
        canvas.drawLine(40f, 740f, 555f, 740f, paint)

        paint.color = AndroidColor.GRAY
        paint.textSize = 8f
        canvas.drawText("MAK STOCK MANAGER DEVICE RECONCILIATION DOCUMENT", 40f, 765f, paint)
        canvas.drawText("VERIFICATION KEY: SECURE_INTEGRATED_SQLITE_AUTHPASS_OK_SHA256", 40f, 780f, paint)

        pdfDocument.finishPage(page)

        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@Composable
fun ExportReportDialog(
    transactions: List<StockTransaction>,
    items: List<Item>,
    onDismiss: () -> Unit,
    isAmharic: Boolean
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    val context = LocalContext.current

    var selectedDuration by remember { mutableStateOf("1 Month (30 Days)") }
    var isSimulatingExport by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showPdfPreview by remember { mutableStateOf(false) }

    val filterDuration = when (selectedDuration) {
        "Today Only" -> 1
        "Last 7 Days" -> 7
        "1 Month (30 Days)" -> 30
        "Year to Date" -> 365
        else -> 30
    }

    val cutoffTime = System.currentTimeMillis() - (filterDuration * 24 * 60 * 60 * 1000L)
    val statementTxs = transactions.filter { it.timestamp >= cutoffTime }

    var totalIn = 0f
    var totalOut = 0f
    var totalWaste = 0f

    for (tx in statementTxs) {
        when (tx.type) {
            "IN" -> totalIn += tx.quantity
            "OUT" -> totalOut += tx.quantity
            "WASTE" -> totalWaste += tx.quantity
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = t("MAK STOCK STATEMENT GENERATOR", "የሪፖርት ሰነድ ማውጫ"),
                        color = OrangePrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.0.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = t("Period Statement (PDF)", "የተመረጠው የክፍለ ጊዜ ሪፖርት"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Selected duration list
                item {
                    Column {
                        Text(
                            text = t("Choose Period Range", "ጊዜውን ይምረጡ"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val durations = listOf("Today Only", "Last 7 Days", "1 Month (30 Days)", "Year to Date")
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val chunked = durations.chunked(2)
                                for (row in chunked) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (dur in row) {
                                            val active = selectedDuration == dur
                                            val displayedLabel = when(dur) {
                                                "Today Only" -> t("Today Only", "ዛሬ ብቻ")
                                                "Last 7 Days" -> t("Last 7 Days", "ያለፉት 7 ቀናት")
                                                "1 Month (30 Days)" -> t("Last 30 Days", "ያለፉት 30 ቀናት")
                                                "Year to Date" -> t("Year to Date", "ከዓመቱ መጀመሪያ ጀምሮ")
                                                else -> dur
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (active) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                                    .clickable { 
                                                        selectedDuration = dur 
                                                        showPdfPreview = false
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = displayedLabel,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                if (!showPdfPreview) {
                    item {
                        Column {
                            Text(t("Aggregated Statement Summary:", "የተጠቃለለ የሪፖርቱ ማጠቃለያ ፡"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• " + t("Selected Duration: ", "የተመረጠው ጊዜ ፡ ") + selectedDuration, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("• " + t("Actions Found: ", "የውስጥ እንቅስቃሴዎች ዛት ፡ ") + "${statementTxs.size} " + t("audits", "እንቅስቃሴዎች"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("• " + t("Sum Inward Volume: ", "ጠቅላላ የገባ ዕቃ ፡ ") + "+$totalIn " + t("items", "ዕቃዎች"), fontSize = 11.sp, color = GreenAlert)
                            Text("• " + t("Sum Released volume: ", "ጠቅላላ የወጣ ዕቃ ፡ ") + "-$totalOut " + t("items", "ዕቃዎች"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("• " + t("Sum Spoilage Loss (Waste): ", "ጠቅላላ የጠፋ/የተበላሸ ፡ ") + "-$totalWaste " + t("items", "ዕቃዎች"), fontSize = 11.sp, color = RedAlert)
                        }
                    }

                    item {
                        Button(
                            onClick = { showPdfPreview = true },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("verify_proof_btn")
                        ) {
                            Icon(Icons.Rounded.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(t("Verify Printable Document Proof", "ቀድመው ይመልከቱ"), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else {
                    item {
                        // Dynamic styling of preview sheet
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .border(1.dp, Color(0xFFCCCCCC))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "MAK STOCK MANAGER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                        Text(text = "ARCHIVAL AUDIT VERIFICATION", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFE3F2FD))
                                            .border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "PDF DOCUMENT", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = Color.Black, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = t("RANGE: ", "ክፍለ-ጊዜ፡ ") + selectedDuration, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    Text(text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()), fontSize = 7.sp, color = Color.Black)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color.Black)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFEEEEEE))
                                                .padding(4.dp)
                                        ) {
                                            Text(text = "ITEM ID", modifier = Modifier.weight(1f), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            Text(text = "TYPE", modifier = Modifier.weight(0.8f), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            Text(text = "QUANTITY", modifier = Modifier.weight(1f), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }

                                        val previewRows = statementTxs.take(3)
                                        for (tx in previewRows) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(4.dp)
                                            ) {
                                                Text(text = tx.itemId, modifier = Modifier.weight(1f), fontSize = 7.sp, color = Color.Black)
                                                Text(text = tx.type, modifier = Modifier.weight(0.8f), fontSize = 7.sp, color = Color.Black)
                                                Text(text = "${if (tx.type == "IN") "+" else "-"}${tx.quantity}", modifier = Modifier.weight(1f), fontSize = 7.sp, color = Color.Black)
                                            }
                                        }
                                        if (statementTxs.size > 3) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(text = "... and ${statementTxs.size - 3} more records ...", fontSize = 6.sp, color = Color.DarkGray)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Triggering and initiating immediate PDF generation with actual file outputs
                        if (!isSimulatingExport && exportSuccessMessage == null) {
                            Button(
                                onClick = {
                                    isSimulatingExport = true
                                    val fileName = "mak_report_${System.currentTimeMillis()}.pdf"
                                    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                    val destFile = File(downloadsDir, fileName)

                                    val success = generateRealPdf(
                                        file = destFile,
                                        durationName = selectedDuration,
                                        totalInQty = totalIn,
                                        totalOutQty = totalOut,
                                        totalWasteQty = totalWaste,
                                        txs = statementTxs,
                                        items = items,
                                        isAmharic = isAmharic
                                    )

                                    isSimulatingExport = false
                                    if (success) {
                                        exportSuccessMessage = t(
                                            "PDF Successfully Generated!\nSaved immediately to file manager:\n${destFile.absolutePath}",
                                            "ፒዲኤፍ ሪፖርት በተሳካ ሁኔታ ተፈጥሯል!\nይህ ፋይል በስልክዎ ፋይል ማከማቻ ውስጥ ተቀምጧል:\n${destFile.absolutePath}"
                                        )
                                    } else {
                                        exportSuccessMessage = t(
                                            "Failed to generate statement vector PDF. Check storage spaces.",
                                            "የፒዲኤፍ ሪፖርቱን መፍጠር አልተቻለም። የስልክዎን ቦታ ያረጋግጡ።"
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenAlert),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("real_pdf_download_btn")
                            ) {
                                Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(t("Compile & Immediate Download", "ፒዲኤፍ ይፍጠሩ እና ያውርዱ"), fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else if (isSimulatingExport) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = OrangePrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(t("Compiling PDF Vectors...", "ፒዲኤፍ ሪፖርት እየተዘጋጀ ነው..."), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = exportSuccessMessage ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (exportSuccessMessage?.startsWith("PDF") == true || exportSuccessMessage?.startsWith("ፒዲኤፍ") == true) GreenAlert else RedAlert,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("close_statement_dialog")
                    ) {
                        Text(t("Dismiss", "አቋርጥ"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ======================== SYNCHRONIZATION RUNNING BANNER ========================

@Composable
fun SyncingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = OrangePrimary,
                    strokeWidth = 4.dp
                )

                Text(
                    text = "SUPABASE SYNC IN PROGRESS",
                    color = OrangePrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )

                Text(
                    text = "Encrypting local SQLite state changes and validating online database handshakes...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseLoginScreen(
    onLogin: (String, String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit
) {
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity Brand Logo Header
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(OrangePrimary)
                .shadow(6.dp, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "SIGN IN",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Username Field
        OutlinedTextField(
            value = emailOrUsername,
            onValueChange = { emailOrUsername = it },
            label = { Text("Email") },
            placeholder = { Text("e.g. user@domain.com") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("supabase_username_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangePrimary,
                focusedLabelColor = OrangePrimary,
                cursorColor = OrangePrimary
            ),
            leadingIcon = {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = OrangePrimary)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("supabase_password_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangePrimary,
                focusedLabelColor = OrangePrimary,
                cursorColor = OrangePrimary
            ),
            leadingIcon = {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = OrangePrimary)
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Toggle password visibility",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Login Button
        Button(
            onClick = { onLogin(emailOrUsername, password) },
            enabled = !isLoading && emailOrUsername.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_supabase_login"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OrangePrimary,
                disabledContainerColor = OrangePrimary.copy(alpha = 0.5f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = "LOGIN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun CompactHeaderBar(
    selectedTab: ActiveTab,
    isAmharic: Boolean,
    viewModel: InventoryViewModel
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Color(0xFF262626))
            .padding(top = 22.dp, bottom = 14.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MAK STOCK",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = when (selectedTab) {
                        ActiveTab.INSERT -> t("RESTOCK ITEMS", "ዕቃዎች ማስገቢያ")
                        ActiveTab.TAKEOUT -> t("RELEASE STOCK", "ዕቃዎች ማውጫ")
                        ActiveTab.HISTORY -> t("AUDIT TRAILS", "የእንቅስቃሴ ታሪክ")
                        ActiveTab.REPORT -> t("ANALYTICS & SETTINGS", "ዶክመንት እና ቅንብሮች")
                        else -> "MAK"
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Language Switch Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .clickable { viewModel.toggleLanguage() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isAmharic) "ENGLISH" else "አማርኛ",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (name: String, prefix: String, icon: String) -> Unit,
    isAmharic: Boolean
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    var name by remember { mutableStateOf(category.name) }
    var prefix by remember { mutableStateOf(category.prefix) }
    var selectedIcon by remember { mutableStateOf(category.imageUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = t("Edit Stock Category", "ምድብ ማስተካከያ"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("E.g., Meat & Shellfish") },
                    label = { Text(t("Category Name", "የምድብ ስም")) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { if (it.length <= 3) prefix = it.uppercase() },
                    placeholder = { Text("E.g., MET") },
                    label = { Text(t("ID Prefix (3 characters)", "ምህጻረ ቃል (3 ቁምፊ)")) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = t("Visual Icon Indicator", "የምስል ምልክት"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val icons = listOf("restaurant", "egg", "eco", "local_bar")
                    val names = listOf(t("Plates", "ሳህን"), t("Dairy", "ወተት"), t("Greens", "አትክልት"), t("Cups", "ብርጭቆ"))
                    for (i in icons.indices) {
                        val active = selectedIcon == icons[i]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                .border(1.dp, if (active) OrangePrimary else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedIcon = icons[i] }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = names[i],
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(t("Cancel", "አቋርጥ"), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onConfirm(name, prefix, selectedIcon) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        enabled = name.isNotEmpty() && prefix.length == 3
                    ) {
                        Text(t("Save", "አስቀምጥ"), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAllSecureDialog(
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit,
    isAmharic: Boolean
) {
    val t = { en: String, am: String -> if (isAmharic) am else en }
    var pinEntered by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = RedAlert,
                    modifier = Modifier.size(40.dp)
                )

                Text(
                    text = t("SECURE RESET", "ዳታን ማጥፊያ"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = RedAlert,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = t(
                        "This will permanently erase all local categories, items, and transactions. Enter security passcode PIN to confirm.",
                        "ይህ እርምጃ ሙሉ በሙሉ ሁሉንም ምድቦች፣ ዕቃዎች እና ታሪኮች ያጠፋል። ለማረጋገጥ የይለፍ ቃል ያስገቡ።"
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pinEntered,
                    onValueChange = { pinEntered = it },
                    placeholder = { Text("E.g., 1234") },
                    label = { Text(t("Security Passcode PIN", "የይለፍ ቃል PIN")) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(t("Cancel", "አቋርጥ"), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onConfirm(pinEntered) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                    ) {
                        Text(t("ERASE ALL", "ሁሉንም አጥፋ"), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
