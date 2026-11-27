package com.spendingtracker.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.spendingtracker.app.data.MediaUtils
import com.spendingtracker.app.data.ReminderWorker
import com.spendingtracker.app.data.TransactionRecord
import com.spendingtracker.app.ui.SpendingViewModel
import com.spendingtracker.app.ui.screens.AddGoalDialog
import com.spendingtracker.app.ui.screens.AddIouDialog
import com.spendingtracker.app.ui.screens.AddTransactionDialog
import com.spendingtracker.app.ui.screens.AuthScreen
import com.spendingtracker.app.ui.screens.DashboardScreen
import com.spendingtracker.app.ui.screens.DraftConfirmDialog
import com.spendingtracker.app.ui.screens.EditTransactionDialog
import androidx.compose.foundation.isSystemInDarkTheme
import com.spendingtracker.app.ui.screens.AddRuleDialog
import com.spendingtracker.app.ui.screens.AddTemplateDialog
import com.spendingtracker.app.ui.screens.AddWalletDialog
import com.spendingtracker.app.ui.screens.NewCategoryDialog
import com.spendingtracker.app.ui.screens.PdfPasswordDialog
import com.spendingtracker.app.ui.screens.SetBudgetDialog
import com.spendingtracker.app.ui.screens.SplitTransactionDialog
import com.spendingtracker.app.ui.screens.TransferWalletDialog
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme

class MainActivity : FragmentActivity() {

    private val viewModel: SpendingViewModel by viewModels()

    // 1. ML Kit Document Scanner launcher (auto-crop & glare removal)
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUris = scanResult?.pages?.map { it.imageUri }.orEmpty()
            if (pageUris.isNotEmpty()) {
                viewModel.uploadMedia(uris = pageUris, sourceType = "bill_image")
            }
        }
    }

    // 2. Android Photo Picker launcher (multi-select banking screenshots / receipts)
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadMedia(uris = uris, sourceType = "screenshot")
        }
    }

    // 3. Document Picker launcher (PDF & CSV bank statements)
    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val sourceType = if (MediaUtils.isPdfUri(this, uri)) {
                "bank_statement_pdf"
            } else {
                "bank_statement_csv"
            }
            viewModel.uploadMedia(uris = listOf(uri), sourceType = sourceType)
        }
    }

    private var pendingExportJson: String? = null
    private var widgetLaunchAction by mutableStateOf<String?>(null)

    // 4. Backup JSON Export & Import launchers
    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                viewModel.logNaturalQuickInput(spoken)
            }
        }
    }

    private fun launchVoiceQuickLog() {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say e.g. \"50k coffee cash\"")
        }
        runCatching { voiceLauncher.launch(intent) }.onFailure {
            Toast.makeText(this, "Voice recognition unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Failed to save backup: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) viewModel.importBackupJson(text)
            }.onFailure {
                Toast.makeText(this, "Failed to read backup: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReminderWorker.scheduleDaily(applicationContext)
        handleShareIntent(intent)
        widgetLaunchAction = intent?.getStringExtra(EXTRA_WIDGET_ACTION)

        setContent {
            val state by viewModel.uiState.collectAsState()
            val useDarkTheme = when (state.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            SpendingTrackerTheme(darkTheme = useDarkTheme) {
                var biometricUnlocked by remember { mutableStateOf(!state.biometricEnabled) }

                LaunchedEffect(state.isAuthenticated, state.biometricEnabled) {
                    if (state.isAuthenticated && state.biometricEnabled && !biometricUnlocked) {
                        promptBiometric(onSuccess = { biometricUnlocked = true })
                    }
                }

                if (!state.isAuthenticated) {
                    AuthScreen(
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        statusMessage = state.statusMessage,
                        onLogin = { email, password ->
                            biometricUnlocked = true
                            viewModel.login(email, password)
                        },
                        onRegister = { email, password, name ->
                            biometricUnlocked = true
                            viewModel.register(email, password, name)
                        }
                    )
                } else if (state.biometricEnabled && !biometricUnlocked) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Spending Tracker Locked", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { promptBiometric(onSuccess = { biometricUnlocked = true }) }) {
                                Text("Unlock with Biometrics")
                            }
                        }
                    }
                } else {
                    var editingTx by remember { mutableStateOf<TransactionRecord?>(null) }
                    var splittingTx by remember { mutableStateOf<TransactionRecord?>(null) }
                    var showAddDialog by remember { mutableStateOf(false) }
                    var showBudgetDialog by remember { mutableStateOf(false) }
                    var showCategoryDialog by remember { mutableStateOf(false) }
                    var showGoalDialog by remember { mutableStateOf(false) }
                    var showIouDialog by remember { mutableStateOf(false) }
                    var showZeroConfirm by remember { mutableStateOf(false) }
                    var showTemplateDialog by remember { mutableStateOf(false) }
                    var showWalletDialog by remember { mutableStateOf(false) }
                    var showTransferDialog by remember { mutableStateOf(false) }
                    var showRuleDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(widgetLaunchAction) {
                        when (widgetLaunchAction) {
                            "scan" -> {
                                widgetLaunchAction = null
                                launchDocumentScanner()
                            }
                            "add" -> {
                                widgetLaunchAction = null
                                showAddDialog = true
                            }
                        }
                    }

                    DashboardScreen(
                        state = state,
                        onRefresh = { viewModel.refreshDashboard() },
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() },
                        onScanBill = { launchDocumentScanner() },
                        onPickScreenshot = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickStatement = {
                            documentPickerLauncher.launch(
                                arrayOf("application/pdf", "text/csv", "text/comma-separated-values")
                            )
                        },
                        onAddTransactionClick = { showAddDialog = true },
                        onSetBudgetClick = { showBudgetDialog = true },
                        onNewCategoryClick = { showCategoryDialog = true },
                        onAddGoalClick = { showGoalDialog = true },
                        onAddIouClick = { showIouDialog = true },
                        onSettleIouClick = { id -> viewModel.toggleSettleIou(id) },
                        onGenerateInsightsClick = { viewModel.generateMonthlyInsights() },
                        onZeroBalanceClick = { showZeroConfirm = true },
                        onUseTemplateClick = { id -> viewModel.useTemplate(id) },
                        onAddTemplateClick = { showTemplateDialog = true },
                        onAddWalletClick = { showWalletDialog = true },
                        onTransferWalletClick = { showTransferDialog = true },
                        onAddRuleClick = { showRuleDialog = true },
                        onDeleteRuleClick = { id -> viewModel.deleteRule(id) },
                        onSetRangeClick = { s, e -> viewModel.setDateRange(s, e) },
                        onClearRangeClick = { viewModel.clearDateRange() },
                        onExportBackupClick = {
                            viewModel.exportBackupJson { json ->
                                pendingExportJson = json
                                exportBackupLauncher.launch("spending-backup.json")
                            }
                        },
                        onImportBackupClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        onCycleThemeClick = { viewModel.cycleThemeMode() },
                        onTogglePrivacy = { viewModel.togglePrivacyMode() },
                        onQuickLogSubmit = { raw -> viewModel.logNaturalQuickInput(raw) },
                        onVoiceQuickLogClick = { launchVoiceQuickLog() },
                        onLogRecurringClick = { bill -> viewModel.logRecurringBillNow(bill) },
                        onBulkDeleteClick = { ids -> viewModel.bulkDeleteTransactions(ids) },
                        onBulkCategoryClick = { ids, cat -> viewModel.bulkUpdateCategory(ids, cat) },
                        onSavePreferences = { day, tag, cur -> viewModel.updatePreferences(day, tag, cur) },
                        onEndEventClick = { viewModel.updatePreferences(null, "", "") },
                        onToggleBankAlerts = {
                            val enabling = !state.bankAlertsEnabled
                            viewModel.setBankAlertsEnabled(enabling)
                            if (enabling) {
                                runCatching {
                                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            }
                        },
                        onReviewPendingClick = { viewModel.openPendingDrafts() },
                        onFlushOfflineClick = { viewModel.flushOfflineQueue() },
                        onToggleBiometric = {
                            if (!state.biometricEnabled) {
                                if (canUseBiometrics()) {
                                    viewModel.setBiometricEnabled(true)
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Set up fingerprint/screen lock in Android Settings first",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                viewModel.setBiometricEnabled(false)
                            }
                        },
                        onTransactionClick = { tx -> editingTx = tx },
                        onLogout = { viewModel.logout() },
                        onClearMessages = { viewModel.clearMessages() }
                    )

                    if (showZeroConfirm) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showZeroConfirm = false },
                            title = { Text("Zero Out Balance?") },
                            text = {
                                Text("This adds a reversible balancing entry to your ledger so your running balance becomes 0 without deleting past transactions.")
                            },
                            confirmButton = {
                                Button(onClick = {
                                    viewModel.zeroOutBalance()
                                    showZeroConfirm = false
                                }) { Text("Zero Out") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { showZeroConfirm = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showTemplateDialog) {
                        AddTemplateDialog(
                            categories = state.categories,
                            onDismiss = { showTemplateDialog = false },
                            onCreate = { label, amt, cat ->
                                viewModel.createTemplate(label, amt, cat)
                                showTemplateDialog = false
                            }
                        )
                    }

                    if (showWalletDialog) {
                        AddWalletDialog(
                            onDismiss = { showWalletDialog = false },
                            onCreate = { name, wType, initial ->
                                viewModel.createWallet(name, wType, initial)
                                showWalletDialog = false
                            }
                        )
                    }

                    if (showTransferDialog) {
                        TransferWalletDialog(
                            wallets = state.wallets,
                            onDismiss = { showTransferDialog = false },
                            onTransfer = { fromId, toId, amt ->
                                viewModel.transferWallet(fromId, toId, amt)
                                showTransferDialog = false
                            }
                        )
                    }

                    if (showRuleDialog) {
                        AddRuleDialog(
                            categories = state.categories,
                            onDismiss = { showRuleDialog = false },
                            onSave = { pattern, cat ->
                                viewModel.upsertRule(pattern, cat)
                                showRuleDialog = false
                            }
                        )
                    }

                    if (showGoalDialog) {
                        AddGoalDialog(
                            onDismiss = { showGoalDialog = false },
                            onCreate = { name, goalType, target ->
                                viewModel.createGoal(name, goalType, target)
                                showGoalDialog = false
                            }
                        )
                    }

                    if (showIouDialog) {
                        AddIouDialog(
                            onDismiss = { showIouDialog = false },
                            onCreate = { person, direction, amount ->
                                viewModel.createIou(person, direction, amount)
                                showIouDialog = false
                            }
                        )
                    }

                    if (showAddDialog) {
                        AddTransactionDialog(
                            categories = state.categories,
                            wallets = state.wallets,
                            defaultCurrency = state.preferences?.activeEventCurrency?.takeIf { it.isNotBlank() }
                                ?: state.monthlySpending?.baseCurrency ?: "VND",
                            defaultTag = state.preferences?.activeEventTag,
                            onDismiss = { showAddDialog = false },
                            onAdd = { txDate, vendor, amount, txType, currency, categoryName, desc, isRecurring, walletId, tags ->
                                viewModel.createManualTransaction(
                                    txDate, vendor, amount, txType, currency, categoryName, desc, isRecurring, walletId, tags
                                )
                                showAddDialog = false
                            }
                        )
                    }

                    if (showBudgetDialog) {
                        SetBudgetDialog(
                            categories = state.categories,
                            onDismiss = { showBudgetDialog = false },
                            onSave = { cat, limit, rollover ->
                                viewModel.upsertBudget(cat, limit, rollover)
                                showBudgetDialog = false
                            }
                        )
                    }

                    if (showCategoryDialog) {
                        NewCategoryDialog(
                            onDismiss = { showCategoryDialog = false },
                            onCreate = { name ->
                                viewModel.createCategory(name)
                                showCategoryDialog = false
                            }
                        )
                    }

                    editingTx?.let { tx ->
                        EditTransactionDialog(
                            tx = tx,
                            categories = state.categories,
                            wallets = state.wallets,
                            onDismiss = { editingTx = null },
                            onSave = { txDate, vendor, amount, txType, currency, categoryName, desc, isRecurring, walletId, tags ->
                                viewModel.updateTransaction(
                                    id = tx.id,
                                    txDate = txDate,
                                    vendor = vendor,
                                    amount = amount,
                                    txType = txType,
                                    currency = currency,
                                    categoryName = categoryName,
                                    description = desc,
                                    isRecurring = isRecurring,
                                    walletId = walletId,
                                    tags = tags
                                )
                                editingTx = null
                            },
                            onDuplicate = { vendor, amount, txType, currency, categoryName, desc, isRecurring, walletId, tags ->
                                viewModel.createManualTransaction(
                                    txDate = java.time.LocalDate.now().toString(),
                                    vendor = vendor,
                                    amount = amount,
                                    txType = txType,
                                    currency = currency,
                                    categoryName = categoryName,
                                    description = desc,
                                    isRecurring = isRecurring,
                                    walletId = walletId,
                                    tags = tags
                                )
                                editingTx = null
                            },
                            onSplit = {
                                splittingTx = tx
                                editingTx = null
                            },
                            onDelete = {
                                viewModel.deleteTransaction(tx.id)
                                editingTx = null
                            }
                        )
                    }

                    splittingTx?.let { tx ->
                        SplitTransactionDialog(
                            tx = tx,
                            categories = state.categories,
                            onDismiss = { splittingTx = null },
                            onSplit = { items ->
                                viewModel.splitTransaction(tx.id, items)
                                splittingTx = null
                            }
                        )
                    }

                    state.pendingDraft?.let { draft ->
                        DraftConfirmDialog(
                            ingest = draft,
                            onDismiss = { viewModel.dismissDraftDialog() },
                            onConfirm = { confirmed, discardIds ->
                                viewModel.confirmDrafts(confirmed, discardIds)
                            }
                        )
                    }

                    state.pendingPasswordPrompt?.let { pending ->
                        PdfPasswordDialog(
                            onDismiss = { viewModel.dismissPasswordPrompt() },
                            onSubmit = { password ->
                                viewModel.uploadMedia(
                                    uris = pending.uris,
                                    sourceType = pending.sourceType,
                                    pdfPassword = password
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        intent.getStringExtra(EXTRA_WIDGET_ACTION)?.let { widgetLaunchAction = it }
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "com.spendingtracker.app.WIDGET_ACTION"
    }

    private fun canUseBiometrics(): Boolean {
        val mgr = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return mgr.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptBiometric(onSuccess: () -> Unit) {
        if (!canUseBiometrics()) {
            onSuccess()
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Spending Tracker")
            .setSubtitle("Verify your identity to view your ledger")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    private fun launchDocumentScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(5)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Scanner unavailable: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = getParcelableExtraCompat(intent, Intent.EXTRA_STREAM, Uri::class.java)
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                getParcelableArrayListExtraCompat(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
            }
            else -> emptyList()
        }

        if (uris.isEmpty()) return

        val mime = intent.type.orEmpty()
        val sourceType = when {
            mime == "application/pdf" || uris.any { MediaUtils.isPdfUri(this, it) } -> "bank_statement_pdf"
            mime.contains("csv") -> "bank_statement_csv"
            else -> "screenshot"
        }

        viewModel.uploadMedia(uris = uris, sourceType = sourceType)
        intent.action = Intent.ACTION_MAIN
    }

    @Suppress("DEPRECATION")
    private fun <T : Parcelable> getParcelableExtraCompat(intent: Intent, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, clazz)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun <T : Parcelable> getParcelableArrayListExtraCompat(
        intent: Intent,
        key: String,
        clazz: Class<T>
    ): ArrayList<T>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, clazz)
        } else {
            intent.getParcelableArrayListExtra(key)
        }
    }
}
