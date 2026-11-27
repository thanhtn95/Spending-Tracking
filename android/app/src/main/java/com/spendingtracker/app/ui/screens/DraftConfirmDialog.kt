package com.spendingtracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.data.ConfirmTransactionItem
import com.spendingtracker.app.data.IngestResponse
import com.spendingtracker.app.data.TransactionRecord

private val DEFAULT_CATEGORIES = listOf(
    "Debt Repayment",
    "Dining",
    "Education",
    "Entertainment",
    "Family & Kids",
    "Fees & Interest",
    "Gifts & Donations",
    "Groceries",
    "Health",
    "Housing",
    "Income",
    "Insurance",
    "Investments",
    "Personal Care",
    "Refund",
    "Salary",
    "Shopping",
    "Subscriptions",
    "Transfer",
    "Transport",
    "Travel",
    "Utilities",
    "Uncategorized"
)

private data class EditableDraft(
    val id: String,
    var selected: Boolean,
    var vendor: String,
    var amount: String,
    var txType: String,
    var txDate: String,
    var categoryName: String,
    val currency: String,
    val isDuplicateFlagged: Boolean
)

@Composable
fun DraftConfirmDialog(
    ingest: IngestResponse,
    onDismiss: () -> Unit,
    onConfirm: (confirmed: List<ConfirmTransactionItem>, discardIds: List<String>) -> Unit
) {
    val editableList = remember(ingest.documentId) {
        mutableStateListOf<EditableDraft>().apply {
            addAll(
                ingest.transactions.map { tx ->
                    EditableDraft(
                        id = tx.id,
                        selected = tx.status != "duplicate_flagged",
                        vendor = tx.vendor,
                        amount = tx.amount,
                        txType = tx.txType,
                        txDate = tx.txDate,
                        categoryName = tx.categoryName,
                        currency = tx.currency,
                        isDuplicateFlagged = tx.status == "duplicate_flagged"
                    )
                }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    text = "Review Extracted Transactions",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${ingest.sourceType} · ${ingest.modelUsed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (ingest.openingBalance != null && ingest.closingBalance != null) {
                    val bannerColor = if (ingest.reconciled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                    val bannerText = if (ingest.reconciled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                    Surface(
                        color = bannerColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (ingest.reconciled) {
                                "Statement reconciled (${ingest.openingBalance} → ${ingest.closingBalance})"
                            } else {
                                "Balance discrepancy (diff: ${ingest.reconciliationDiff ?: "unknown"}). Please review."
                            },
                            color = bannerText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (ingest.skippedExactDuplicates > 0) {
                    Text(
                        text = "Skipped ${ingest.skippedExactDuplicates} exact duplicate row(s).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (editableList.isEmpty()) {
                    Text(
                        "No new spending transactions were found in this document.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(editableList, key = { _, item -> item.id }) { idx, item ->
                            DraftItemCard(
                                item = item,
                                onUpdate = { updated -> editableList[idx] = updated }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    val confirmed = editableList
                        .filter { it.selected }
                        .map {
                            ConfirmTransactionItem(
                                id = it.id,
                                txDate = it.txDate,
                                vendor = it.vendor,
                                categoryName = it.categoryName,
                                amount = it.amount,
                                txType = it.txType,
                                currency = it.currency
                            )
                        }
                    val discardIds = editableList
                        .filter { !it.selected }
                        .map { it.id }

                    if (confirmed.isEmpty() && discardIds.isEmpty()) {
                        onDismiss()
                    } else {
                        onConfirm(confirmed, discardIds)
                    }
                }
            ) {
                Text("Confirm (${editableList.count { it.selected }})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Review Later")
            }
        }
    )
}

@Composable
private fun DraftItemCard(
    item: EditableDraft,
    onUpdate: (EditableDraft) -> Unit
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = { onUpdate(item.copy(selected = it)) }
                    )
                    Text(
                        text = "Include",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isCredit = item.txType.equals("credit", ignoreCase = true)
                    Surface(
                        onClick = {
                            onUpdate(item.copy(txType = if (isCredit) "debit" else "credit"))
                        },
                        color = if (isCredit) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(99.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = if (isCredit) "Credit (+)" else "Debit (-)",
                            color = if (isCredit) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (item.isDuplicateFlagged) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Text(
                                text = "Possible duplicate",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = item.vendor,
                onValueChange = { onUpdate(item.copy(vendor = it)) },
                label = { Text("Merchant") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.amount,
                    onValueChange = { onUpdate(item.copy(amount = it)) },
                    label = { Text("Amount (${item.currency})") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.txDate,
                    onValueChange = { onUpdate(item.copy(txDate = it)) },
                    label = { Text("Date") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column {
                OutlinedButton(
                    onClick = { categoryMenuExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Category: ${item.categoryName}")
                }
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    DEFAULT_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                onUpdate(item.copy(categoryName = cat))
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("PDF Password Required", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "This bank statement PDF is encrypted. Your password is used in memory only and never stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("PDF Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = { onSubmit(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Unlock & Extract")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditTransactionDialog(
    tx: TransactionRecord,
    categories: List<String> = DEFAULT_CATEGORIES,
    wallets: List<com.spendingtracker.app.data.WalletRecord> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        txDate: String,
        vendor: String,
        amount: String,
        txType: String,
        currency: String,
        categoryName: String,
        description: String?,
        isRecurring: Boolean,
        walletId: String?,
        tags: List<String>
    ) -> Unit,
    onDuplicate: (
        vendor: String,
        amount: String,
        txType: String,
        currency: String,
        categoryName: String,
        description: String?,
        isRecurring: Boolean,
        walletId: String?,
        tags: List<String>
    ) -> Unit,
    onSplit: () -> Unit = {},
    onDelete: () -> Unit
) {
    var vendor by remember(tx.id) { mutableStateOf(tx.vendor) }
    var amount by remember(tx.id) { mutableStateOf(tx.amount) }
    var txDate by remember(tx.id) { mutableStateOf(tx.txDate) }
    var txType by remember(tx.id) { mutableStateOf(tx.txType) }
    var currency by remember(tx.id) { mutableStateOf(tx.currency) }
    var categoryName by remember(tx.id) { mutableStateOf(tx.categoryName ?: "Uncategorized") }
    var walletId by remember(tx.id) { mutableStateOf(tx.walletId) }
    var tagsText by remember(tx.id) { mutableStateOf(tx.tags.joinToString(", ")) }
    var description by remember(tx.id) { mutableStateOf(tx.description ?: "") }
    var isRecurring by remember(tx.id) { mutableStateOf(tx.isRecurring) }
    var catMenuExpanded by remember { mutableStateOf(false) }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    val parsedTags = tagsText.split(",").map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit Transaction", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val isCredit = txType.equals("credit", ignoreCase = true)
                Surface(
                    onClick = { txType = if (isCredit) "debit" else "credit" },
                    color = if (isCredit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = if (isCredit) "Type: Credit (+) — tap to switch" else "Type: Debit (-) — tap to switch",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Merchant") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val evalEditAmt = com.spendingtracker.app.data.MathEval.evaluate(amount)
                    val editPreview = if (amount.any { it in "+-*/kKmM" } && evalEditAmt != null) " (= ${com.spendingtracker.app.data.MathEval.formatForApi(evalEditAmt)})" else ""
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount$editPreview") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(3) },
                        label = { Text("Curr") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.7f)
                    )
                }
                OutlinedTextField(
                    value = txDate,
                    onValueChange = { txDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    OutlinedButton(
                        onClick = { catMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Category: $categoryName")
                    }
                    DropdownMenu(
                        expanded = catMenuExpanded,
                        onDismissRequest = { catMenuExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    categoryName = cat
                                    catMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (wallets.isNotEmpty()) {
                    Column {
                        val selectedWalletName = wallets.firstOrNull { it.id == walletId }?.name ?: "None"
                        OutlinedButton(
                            onClick = { walletMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Wallet: $selectedWalletName")
                        }
                        DropdownMenu(
                            expanded = walletMenuExpanded,
                            onDismissRequest = { walletMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    walletId = null
                                    walletMenuExpanded = false
                                }
                            )
                            wallets.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(w.name) },
                                    onClick = {
                                        walletId = w.id
                                        walletMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated, e.g. trip, work)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text("Recurring monthly bill", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val evaluated = com.spendingtracker.app.data.MathEval.evaluate(amount)
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    val amtStr = evaluated?.let { com.spendingtracker.app.data.MathEval.formatForApi(it) } ?: amount
                    onSave(txDate, vendor, amtStr, txType, currency.ifBlank { "VND" }, categoryName, description, isRecurring, walletId, parsedTags)
                },
                enabled = vendor.isNotBlank() && evaluated != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Del", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onSplit) {
                    Text("Split")
                }
                TextButton(
                    onClick = {
                        onDuplicate(vendor, amount, txType, currency.ifBlank { "VND" }, categoryName, description, isRecurring, walletId, parsedTags)
                    }
                ) {
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun AddTransactionDialog(
    categories: List<String> = DEFAULT_CATEGORIES,
    wallets: List<com.spendingtracker.app.data.WalletRecord> = emptyList(),
    defaultCurrency: String = "VND",
    defaultTag: String? = null,
    onDismiss: () -> Unit,
    onAdd: (
        txDate: String,
        vendor: String,
        amount: String,
        txType: String,
        currency: String,
        categoryName: String,
        description: String?,
        isRecurring: Boolean,
        walletId: String?,
        tags: List<String>
    ) -> Unit
) {
    var vendor by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var txDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var txType by remember { mutableStateOf("debit") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var categoryName by remember { mutableStateOf(categories.firstOrNull() ?: "Groceries") }
    var walletId by remember { mutableStateOf<String?>(null) }
    var tagsText by remember { mutableStateOf(defaultTag.orEmpty()) }
    var description by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var catMenuExpanded by remember { mutableStateOf(false) }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    val parsedTags = tagsText.split(",").map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add Transaction", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val isCredit = txType == "credit"
                Surface(
                    onClick = { txType = if (isCredit) "debit" else "credit" },
                    color = if (isCredit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = if (isCredit) "Type: Credit (+) — tap to switch" else "Type: Debit (-) — tap to switch",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Merchant / Source") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val evalEditAmt = com.spendingtracker.app.data.MathEval.evaluate(amount)
                    val editPreview = if (amount.any { it in "+-*/kKmM" } && evalEditAmt != null) " (= ${com.spendingtracker.app.data.MathEval.formatForApi(evalEditAmt)})" else ""
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount$editPreview") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(3) },
                        label = { Text("Curr") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.7f)
                    )
                }
                OutlinedTextField(
                    value = txDate,
                    onValueChange = { txDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    OutlinedButton(
                        onClick = { catMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Category: $categoryName")
                    }
                    DropdownMenu(
                        expanded = catMenuExpanded,
                        onDismissRequest = { catMenuExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    categoryName = cat
                                    catMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (wallets.isNotEmpty()) {
                    Column {
                        val selectedWalletName = wallets.firstOrNull { it.id == walletId }?.name ?: "None"
                        OutlinedButton(
                            onClick = { walletMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Wallet: $selectedWalletName")
                        }
                        DropdownMenu(
                            expanded = walletMenuExpanded,
                            onDismissRequest = { walletMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    walletId = null
                                    walletMenuExpanded = false
                                }
                            )
                            wallets.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(w.name) },
                                    onClick = {
                                        walletId = w.id
                                        walletMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated, e.g. trip, work)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text("Recurring monthly bill", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val evaluated = com.spendingtracker.app.data.MathEval.evaluate(amount)
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    val amtStr = evaluated?.let { com.spendingtracker.app.data.MathEval.formatForApi(it) } ?: amount
                    onAdd(txDate, vendor, amtStr, txType, currency.ifBlank { defaultCurrency }, categoryName, description, isRecurring, walletId, parsedTags)
                },
                enabled = vendor.isNotBlank() && evaluated != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SetBudgetDialog(
    categories: List<String> = DEFAULT_CATEGORIES,
    onDismiss: () -> Unit,
    onSave: (categoryName: String, monthlyLimit: String, rollover: Boolean) -> Unit
) {
    var categoryName by remember { mutableStateOf(categories.firstOrNull() ?: "Groceries") }
    var limit by remember { mutableStateOf("") }
    var rollover by remember { mutableStateOf(false) }
    var catMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Set Monthly Budget", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    OutlinedButton(
                        onClick = { catMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Category: $categoryName")
                    }
                    DropdownMenu(
                        expanded = catMenuExpanded,
                        onDismissRequest = { catMenuExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    categoryName = cat
                                    catMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("Monthly Limit") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rollover, onCheckedChange = { rollover = it })
                    Text("Rollover unused budget to next month", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = { onSave(categoryName, limit, rollover) },
                enabled = limit.isNotBlank()
            ) {
                Text("Save Budget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("New Category", style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name (e.g. Pets, Coffee)") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, goalType: String, targetAmount: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var goalType by remember { mutableStateOf("debt_payoff") }
    var target by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add Goal", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { goalType = if (goalType == "debt_payoff") "savings" else "debt_payoff" },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = if (goalType == "debt_payoff") "Type: Debt Payoff — tap to switch" else "Type: Savings — tap to switch",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Goal Name (e.g. Pay off Credit Card)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target Amount") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = { onCreate(name, goalType, target) },
                enabled = name.isNotBlank() && target.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddIouDialog(
    onDismiss: () -> Unit,
    onCreate: (personName: String, direction: String, amount: String) -> Unit
) {
    var person by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("owed_to_me") }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add Split / IOU", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { direction = if (direction == "owed_to_me") "i_owe" else "owed_to_me" },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = if (direction == "owed_to_me") "Owes Me — tap to switch" else "I Owe — tap to switch",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text("Person Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = { onCreate(person, direction, amount) },
                enabled = person.isNotBlank() && amount.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
