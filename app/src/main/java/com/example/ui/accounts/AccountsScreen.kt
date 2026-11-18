package com.example.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.util.FinancialEngine

@Composable
fun AccountsScreen(
    accounts: List<Account>,
    isBangla: Boolean,
    onAddAccount: (Account) -> Unit,
    onUpdateAccount: (Account) -> Unit,
    onOpenTransfer: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    val totalBalance = accounts.filter { !it.isArchived }.sumOf { it.currentBalance }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingAccount = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_account")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Total Balance Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isBangla) "সকল অ্যাকাউন্টের মোট ব্যালেন্স" else "Total Accounts Balance",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FinancialEngine.formatBdt(totalBalance),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Button(
                            onClick = onOpenTransfer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(text = if (isBangla) "ফান্ড ট্রান্সফার" else "Transfer")
                        }
                    }
                }
            }

            // Group by Account Type
            val cashAccounts = accounts.filter { it.type == AccountType.CASH.name }
            val bankAccounts = accounts.filter { it.type == AccountType.BANK.name }
            val mobileAccounts = accounts.filter { it.type == AccountType.MOBILE_BANKING.name }
            val savingsAccounts = accounts.filter { it.type == AccountType.SAVINGS.name || it.type == AccountType.DPS_FDR.name }

            if (cashAccounts.isNotEmpty()) {
                item {
                    AccountSectionHeader(title = if (isBangla) "নগদ টাকা (Cash Accounts)" else "Cash Accounts")
                }
                items(cashAccounts, key = { it.id }) { acc ->
                    AccountRowCard(
                        account = acc,
                        icon = Icons.Default.Payments,
                        tint = Color(0xFF10B981),
                        isBangla = isBangla,
                        onEdit = { editingAccount = acc }
                    )
                }
            }

            if (bankAccounts.isNotEmpty()) {
                item {
                    AccountSectionHeader(title = if (isBangla) "ব্যাংক অ্যাকাউন্ট (Bank Accounts)" else "Bank Accounts")
                }
                items(bankAccounts, key = { it.id }) { acc ->
                    AccountRowCard(
                        account = acc,
                        icon = Icons.Default.AccountBalance,
                        tint = Color(0xFF3B82F6),
                        isBangla = isBangla,
                        onEdit = { editingAccount = acc }
                    )
                }
            }

            if (mobileAccounts.isNotEmpty()) {
                item {
                    AccountSectionHeader(title = if (isBangla) "মোবাইল ব্যাংকিং (bKash / Nagad / Rocket)" else "Mobile Banking")
                }
                items(mobileAccounts, key = { it.id }) { acc ->
                    AccountRowCard(
                        account = acc,
                        icon = Icons.Default.PhoneAndroid,
                        tint = Color(0xFFEC4899),
                        isBangla = isBangla,
                        onEdit = { editingAccount = acc }
                    )
                }
            }

            if (savingsAccounts.isNotEmpty()) {
                item {
                    AccountSectionHeader(title = if (isBangla) "সঞ্চয় ও ডিপিএস/এফডিআর" else "Savings & DPS / FDR")
                }
                items(savingsAccounts, key = { it.id }) { acc ->
                    AccountRowCard(
                        account = acc,
                        icon = Icons.Default.Savings,
                        tint = Color(0xFFF59E0B),
                        isBangla = isBangla,
                        onEdit = { editingAccount = acc }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }

    if (showAddDialog || editingAccount != null) {
        AddAccountDialog(
            isBangla = isBangla,
            existingAccount = editingAccount,
            onSave = { acc ->
                if (editingAccount != null) {
                    onUpdateAccount(acc)
                } else {
                    onAddAccount(acc)
                }
                editingAccount = null
                showAddDialog = false
            },
            onDismiss = {
                editingAccount = null
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AccountSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun AccountRowCard(
    account: Account,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    isBangla: Boolean,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${account.provider ?: account.type}${if (!account.accountNumber.isNullOrBlank()) " • ${account.accountNumber}" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FinancialEngine.formatBdt(account.currentBalance),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (account.currentBalance >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "প্রারম্ভিক: ৳${account.openingBalance.toInt()}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_account_${account.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = if (isBangla) "অ্যাকাউন্ট এডিট করুন" else "Edit Account",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun normalizeAccountNumericInput(input: String): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val idx = bnDigits.indexOf(ch)
        if (idx >= 0) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
    }
    return sb.toString()
}

private fun formatCleanAccountDouble(v: Double): String {
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

@Composable
fun AddAccountDialog(
    isBangla: Boolean,
    existingAccount: Account? = null,
    onSave: (Account) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditMode = existingAccount != null
    var name by remember(existingAccount?.id) { mutableStateOf(existingAccount?.name ?: "") }
    var selectedType by remember(existingAccount?.id) { mutableStateOf(existingAccount?.type ?: AccountType.CASH.name) }
    var provider by remember(existingAccount?.id) { mutableStateOf(existingAccount?.provider ?: "") }
    var accountNumber by remember(existingAccount?.id) { mutableStateOf(existingAccount?.accountNumber ?: "") }
    var openingBalance by remember(existingAccount?.id) {
        mutableStateOf(
            if (existingAccount != null) formatCleanAccountDouble(existingAccount.openingBalance) else "0"
        )
    }
    var currentBalance by remember(existingAccount?.id) {
        mutableStateOf(
            if (existingAccount != null) formatCleanAccountDouble(existingAccount.currentBalance) else "0"
        )
    }
    var currentBalanceEditedManually by remember(existingAccount?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditMode) {
                    if (isBangla) "অ্যাকাউন্ট এডিট করুন" else "Edit Account"
                } else {
                    if (isBangla) "নতুন অ্যাকাউন্ট যোগ করুন" else "Add New Account"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isBangla) "অ্যাকাউন্টের নাম" else "Account Name") },
                    placeholder = { Text("যেমন: ব্র্যাক ব্যাংক, বিকাশ পার্সোনাল") },
                    modifier = Modifier.fillMaxWidth().testTag("add_account_name"),
                    singleLine = true
                )

                Text(
                    if (isBangla) "অ্যাকাউন্টের ধরন:" else "Account Type:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AccountType.CASH.name to if (isBangla) "ক্যাশ" else "Cash",
                        AccountType.BANK.name to if (isBangla) "ব্যাংক" else "Bank",
                        AccountType.MOBILE_BANKING.name to if (isBangla) "মোবাইল" else "Mobile",
                        AccountType.SAVINGS.name to if (isBangla) "সঞ্চয়" else "Savings"
                    ).forEach { (t, l) ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(l, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    label = { Text(if (isBangla) "প্রোভাইডার / ব্যাংকের নাম (ঐচ্ছিক)" else "Provider / Bank (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text(if (isBangla) "অ্যাকাউন্ট / মোবাইল নম্বর (ঐচ্ছিক)" else "Account Number (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { raw ->
                        openingBalance = raw
                        if (isEditMode && !currentBalanceEditedManually && existingAccount != null) {
                            val newOpening = normalizeAccountNumericInput(raw).toDoubleOrNull()
                            if (newOpening != null) {
                                val adjusted = FinancialEngine.computeAdjustedBalanceOnOpeningChange(
                                    oldCurrentBalance = existingAccount.currentBalance,
                                    oldOpeningBalance = existingAccount.openingBalance,
                                    newOpeningBalance = newOpening
                                )
                                currentBalance = formatCleanAccountDouble(adjusted)
                            }
                        }
                    },
                    label = { Text(if (isBangla) "প্রারম্ভিক ব্যালেন্স (৳)" else "Opening Balance (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_account_balance"),
                    singleLine = true
                )

                if (isEditMode) {
                    OutlinedTextField(
                        value = currentBalance,
                        onValueChange = {
                            currentBalance = it
                            currentBalanceEditedManually = true
                        },
                        label = { Text(if (isBangla) "বর্তমান ব্যালেন্স (৳)" else "Current Balance (৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("edit_account_current_balance"),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opening = normalizeAccountNumericInput(openingBalance).toDoubleOrNull() ?: 0.0
                    val current = if (isEditMode) {
                        normalizeAccountNumericInput(currentBalance).toDoubleOrNull() ?: opening
                    } else {
                        opening
                    }
                    if (name.isNotBlank()) {
                        if (existingAccount != null) {
                            onSave(
                                existingAccount.copy(
                                    name = name.trim(),
                                    type = selectedType,
                                    provider = provider.trim().ifBlank { null },
                                    accountNumber = accountNumber.trim().ifBlank { null },
                                    openingBalance = opening,
                                    currentBalance = current
                                )
                            )
                        } else {
                            onSave(
                                Account(
                                    name = name.trim(),
                                    type = selectedType,
                                    provider = provider.trim().ifBlank { null },
                                    accountNumber = accountNumber.trim().ifBlank { null },
                                    openingBalance = opening,
                                    currentBalance = opening
                                )
                            )
                        }
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text(
                    if (isEditMode) {
                        if (isBangla) "আপডেট করুন" else "Update"
                    } else {
                        if (isBangla) "যোগ করুন" else "Add"
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (isBangla) "বাতিল" else "Cancel")
            }
        }
    )
}
