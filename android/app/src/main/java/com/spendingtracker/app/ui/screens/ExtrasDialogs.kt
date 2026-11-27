package com.spendingtracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.data.SplitItemRequest
import com.spendingtracker.app.data.TransactionRecord
import com.spendingtracker.app.data.WalletRecord
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTemplateDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onCreate: (label: String, amount: String, categoryName: String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull { it == "Dining" } ?: categories.firstOrNull() ?: "Uncategorized") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Quick-Add Template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label / Merchant (e.g. Morning Coffee)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(label, amount, category) },
                enabled = label.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddWalletDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, walletType: String, initialBalance: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var walletType by remember { mutableStateOf("cash") }
    var initial by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Wallet / Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Cash, VCB, MoMo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("cash" to "Cash", "bank" to "Bank", "ewallet" to "E-Wallet").forEach { (key, lbl) ->
                        FilterChip(selected = walletType == key, onClick = { walletType = key }, label = { Text(lbl) })
                    }
                }
                OutlinedTextField(
                    value = initial,
                    onValueChange = { initial = it },
                    label = { Text("Starting Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, walletType, initial) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferWalletDialog(
    wallets: List<WalletRecord>,
    onDismiss: () -> Unit,
    onTransfer: (fromId: String, toId: String, amount: String) -> Unit
) {
    var fromWallet by remember { mutableStateOf(wallets.firstOrNull()) }
    var toWallet by remember { mutableStateOf(wallets.getOrNull(1) ?: wallets.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Internal Wallet Transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = fromExpanded, onExpandedChange = { fromExpanded = !fromExpanded }) {
                    OutlinedTextField(
                        value = fromWallet?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From Wallet") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                        wallets.forEach { w ->
                            DropdownMenuItem(text = { Text(w.name) }, onClick = { fromWallet = w; fromExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = toExpanded, onExpandedChange = { toExpanded = !toExpanded }) {
                    OutlinedTextField(
                        value = toWallet?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To Wallet") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }) {
                        wallets.forEach { w ->
                            DropdownMenuItem(text = { Text(w.name) }, onClick = { toWallet = w; toExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val f = fromWallet?.id ?: return@Button
                    val t = toWallet?.id ?: return@Button
                    onTransfer(f, t, amount)
                },
                enabled = fromWallet != null && toWallet != null && fromWallet?.id != toWallet?.id && (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text("Transfer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (pattern: String, categoryName: String) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull() ?: "Uncategorized") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Categorization Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Merchant name (e.g. Grab, Netflix)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Always Categorize As") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pattern, category) }, enabled = pattern.isNotBlank()) { Text("Save Rule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SplitTransactionDialog(
    tx: TransactionRecord,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSplit: (List<SplitItemRequest>) -> Unit
) {
    val total = tx.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val half1 = total.divideToIntegralValue(BigDecimal(2))
    val half2 = total - half1
    val rows = remember(tx.id) {
        mutableStateListOf(
            (tx.categoryName ?: "Groceries") to half1.toPlainString(),
            "Uncategorized" to half2.toPlainString()
        )
    }
    val currentSum = rows.fold(BigDecimal.ZERO) { acc, pair ->
        acc + (pair.second.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    }
    val remaining = total - currentSum
    val isBalanced = remaining.abs() <= BigDecimal("0.01") &&
        rows.all { (it.second.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ${tx.vendor}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total: ${tx.amount} ${tx.currency} · Remaining: ${remaining.toPlainString()}")
                rows.forEachIndexed { idx, (cat, amt) ->
                    var expanded by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.weight(1f)) {
                            Text(cat, maxLines = 1)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { c ->
                                DropdownMenuItem(text = { Text(c) }, onClick = { rows[idx] = c to amt; expanded = false })
                            }
                        }
                        OutlinedTextField(
                            value = amt,
                            onValueChange = { rows[idx] = cat to it },
                            label = { Text("Amt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                TextButton(onClick = { rows.add("Uncategorized" to "0") }) { Text("+ Add Split Line") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSplit(rows.map { SplitItemRequest(categoryName = it.first, amount = it.second.trim()) }) },
                enabled = isBalanced
            ) { Text("Apply Split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = current.length >= 8 && newPwd.length >= 8 && newPwd == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it },
                    label = { Text("New Password (min 8 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(current, newPwd) }, enabled = valid) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DebtPayoffDialog(
    currency: String,
    onDismiss: () -> Unit
) {
    var balance by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var apr by remember { mutableStateOf("0") }

    val resultText = remember(balance, payment, apr) {
        val bal = balance.toDoubleOrNull() ?: return@remember ""
        val pmt = payment.toDoubleOrNull() ?: return@remember ""
        val rate = (apr.toDoubleOrNull() ?: 0.0) / 100.0 / 12.0
        if (bal <= 0.0 || pmt <= 0.0) return@remember ""
        if (rate > 0.0 && pmt <= bal * rate) return@remember "Monthly payment is too low to cover interest."
        var rem = bal
        var months = 0
        var totalInt = 0.0
        while (rem > 0.01 && months < 600) {
            val interest = rem * rate
            totalInt += interest
            rem = rem + interest - pmt
            months += 1
        }
        val target = YearMonth.now().plusMonths(months.toLong())
        val lbl = target.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
        val intFmt = NumberFormat.getNumberInstance(Locale.getDefault()).format(totalInt.toLong())
        "Debt-free in $months mo ($lbl) · Est. interest: $intFmt $currency"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Debt-Free Date Calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Remaining Debt ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = payment,
                    onValueChange = { payment = it },
                    label = { Text("Monthly Payment") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apr,
                    onValueChange = { apr = it },
                    label = { Text("Interest Rate (APR %, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (resultText.isNotBlank()) {
                    Text(resultText)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
