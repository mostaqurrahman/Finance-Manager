package com.example.ui.transaction

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Person
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    userId: String,
    accounts: List<Account>,
    categories: List<Category>,
    people: List<Person>,
    initialType: String = TransactionType.EXPENSE.name,
    existingTransaction: Transaction? = null,
    isBangla: Boolean = true,
    onSaveCategory: (Category, (String) -> Unit) -> Unit = { _, _ -> },
    onDeleteCategory: (String) -> Unit = {},
    onSavePerson: (Person, (String) -> Unit) -> Unit = { _, _ -> },
    onDeletePerson: (String) -> Unit = {},
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val nowTimeStr = remember { SimpleDateFormat("hh:mm a", Locale.US).format(Date()) }

    var selectedType by remember(existingTransaction, initialType) {
        mutableStateOf(existingTransaction?.type ?: initialType)
    }
    var amountText by remember(existingTransaction) {
        mutableStateOf(
            existingTransaction?.let { tx ->
                if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString()
            } ?: ""
        )
    }
    var selectedAccountId by remember(existingTransaction, accounts) {
        mutableStateOf(
            existingTransaction?.accountId?.takeIf { it.isNotBlank() }
                ?: accounts.firstOrNull()?.id
                ?: ""
        )
    }
    var selectedToAccountId by remember(existingTransaction, accounts) {
        mutableStateOf(
            existingTransaction?.toAccountId?.takeIf { it.isNotBlank() }
                ?: accounts.getOrNull(1)?.id
                ?: ""
        )
    }

    val targetCatType = if (selectedType == TransactionType.INCOME.name) "INCOME" else "EXPENSE"
    val filteredCats = remember(categories, targetCatType) {
        categories.filter { it.type.equals(targetCatType, ignoreCase = true) }
    }

    var selectedCategoryId by remember(existingTransaction) {
        mutableStateOf(
            existingTransaction?.categoryId?.takeIf { it.isNotBlank() } ?: ""
        )
    }

    LaunchedEffect(selectedType, filteredCats) {
        if (selectedType != TransactionType.TRANSFER.name) {
            val currentExists = filteredCats.any { it.id == selectedCategoryId }
            if (!currentExists) {
                selectedCategoryId = filteredCats.firstOrNull()?.id ?: ""
            }
        }
    }

    var selectedPersonId by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.personId ?: "")
    }
    var descriptionText by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.description ?: "")
    }
    var dateText by remember(existingTransaction) {
        mutableStateOf(existingTransaction?.date ?: todayStr)
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var showManagePeopleDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTransaction != null) {
                        "লেনদেন এডিট করুন (Edit)"
                    } else {
                        "নতুন লেনদেন যোগ করুন"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Transaction Type Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val typeOptions = buildList {
                    add(TransactionType.EXPENSE.name to "খরচ (Expense)")
                    add(TransactionType.INCOME.name to "আয় (Income)")
                    add(TransactionType.TRANSFER.name to "ট্রান্সফার (Transfer)")
                    add(TransactionType.REFUND.name to "রিফান্ড (Refund)")
                    if (existingTransaction != null && none { it.first == existingTransaction.type }) {
                        add(existingTransaction.type to existingTransaction.type)
                    }
                }
                typeOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = com.example.util.BengaliVoiceParser.normalizeBengaliDigits(it)
                },
                label = { Text("টাকার পরিমাণ (BDT ৳)") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_amount_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Account selection
            Text(
                text = if (selectedType == TransactionType.TRANSFER.name) "উৎস অ্যাকাউন্ট (From)" else "অ্যাকাউন্ট:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.filter { !it.isArchived || it.id == selectedAccountId }.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text("${acc.name} (৳${acc.currentBalance.toInt()})", fontSize = 12.sp) }
                    )
                }
            }

            // Destination account if Transfer
            if (selectedType == TransactionType.TRANSFER.name) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "গন্তব্য অ্যাকাউন্ট (To):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.filter { (!it.isArchived || it.id == selectedToAccountId) && it.id != selectedAccountId }.forEach { acc ->
                        FilterChip(
                            selected = selectedToAccountId == acc.id,
                            onClick = { selectedToAccountId = acc.id },
                            label = { Text(acc.name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Category Selection (for non-transfer)
            if (selectedType != TransactionType.TRANSFER.name) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBangla) "ক্যাটাগরি:" else "Category:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showAddCategoryDialog = true },
                            modifier = Modifier.testTag("add_custom_category_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(if (isBangla) "নতুন" else "Add", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { showManageCategoriesDialog = true },
                            modifier = Modifier.testTag("manage_categories_button")
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (isBangla) "সাজান" else "Manage", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    filteredCats.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.nameBn.ifBlank { cat.name }, fontSize = 12.sp) }
                        )
                    }
                    AssistChip(
                        onClick = { showAddCategoryDialog = true },
                        label = { Text(if (isBangla) "+ নতুন ক্যাটাগরি" else "+ New Category", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("add_custom_category_chip")
                    )
                }
            }

            // Person / Relationship Selection
            if (selectedType != TransactionType.TRANSFER.name) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBangla) "সম্পর্কিত ব্যক্তি / সম্পর্ক (ঐচ্ছিক):" else "Person / Relation (Optional):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showAddPersonDialog = true },
                            modifier = Modifier.testTag("add_custom_person_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(if (isBangla) "নতুন" else "Add", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { showManagePeopleDialog = true },
                            modifier = Modifier.testTag("manage_people_button")
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (isBangla) "সাজান" else "Manage", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedPersonId.isBlank(),
                        onClick = { selectedPersonId = "" },
                        label = { Text(if (isBangla) "কেউ না" else "None", fontSize = 12.sp) }
                    )
                    people.forEach { person ->
                        val chipLabel = if (!person.relationship.isNullOrBlank()) {
                            "${person.name} (${person.relationship})"
                        } else {
                            person.name
                        }
                        FilterChip(
                            selected = selectedPersonId == person.id,
                            onClick = { selectedPersonId = person.id },
                            label = { Text(chipLabel, fontSize = 12.sp) }
                        )
                    }
                    AssistChip(
                        onClick = { showAddPersonDialog = true },
                        label = { Text(if (isBangla) "+ নতুন ব্যক্তি / সম্পর্ক" else "+ New Person", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("add_custom_person_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Date
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("তারিখ (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth().testTag("tx_date_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Description
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("বিবরণ / নোট (ঐচ্ছিক)") },
                placeholder = { Text("যেমন: সুপারশপ বাজার, ওষুধ ইত্যাদি") },
                modifier = Modifier.fillMaxWidth().testTag("tx_desc_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            val parsedAmount = amountText.replace(",", "").trim().toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (parsedAmount <= 0) return@Button

                    val account = accounts.find { it.id == selectedAccountId }
                    val toAccount = accounts.find { it.id == selectedToAccountId }
                    val category = categories.find { it.id == selectedCategoryId }
                    val person = people.find { it.id == selectedPersonId }

                    val tx = Transaction(
                        id = existingTransaction?.id ?: "",
                        userId = existingTransaction?.userId?.ifBlank { userId } ?: userId,
                        type = selectedType,
                        amount = parsedAmount,
                        accountId = selectedAccountId,
                        accountName = account?.name ?: existingTransaction?.accountName,
                        toAccountId = if (selectedType == TransactionType.TRANSFER.name) selectedToAccountId else null,
                        toAccountName = if (selectedType == TransactionType.TRANSFER.name) toAccount?.name else null,
                        categoryId = if (selectedType != TransactionType.TRANSFER.name) selectedCategoryId.ifBlank { null } else null,
                        categoryName = if (selectedType != TransactionType.TRANSFER.name) {
                            category?.nameBn?.ifBlank { category.name } ?: existingTransaction?.categoryName
                        } else null,
                        personId = if (selectedType != TransactionType.TRANSFER.name) selectedPersonId.ifBlank { null } else null,
                        personName = if (selectedType != TransactionType.TRANSFER.name) person?.name else null,
                        date = dateText.ifBlank { todayStr },
                        time = existingTransaction?.time ?: nowTimeStr,
                        description = descriptionText.ifBlank { null },
                        linkedExpenseId = existingTransaction?.linkedExpenseId,
                        isSplit = existingTransaction?.isSplit ?: false,
                        createdAt = existingTransaction?.createdAt
                    )
                    onSave(tx)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_transaction_button"),
                enabled = parsedAmount > 0 && selectedAccountId.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (existingTransaction != null) {
                        "আপডেট করুন (Update Transaction)"
                    } else {
                        "সংরক্ষণ করুন (Save Transaction)"
                    }
                )
            }
        }
    }

    if (showAddCategoryDialog) {
        AddEditCategoryDialog(
            userId = userId,
            defaultType = targetCatType,
            isBangla = isBangla,
            onSave = { newCategory ->
                onSaveCategory(newCategory) { newId ->
                    if (newCategory.type.equals(targetCatType, ignoreCase = true)) {
                        selectedCategoryId = newId
                    }
                }
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    if (showManageCategoriesDialog) {
        ManageCategoriesDialog(
            userId = userId,
            categories = categories,
            initialType = targetCatType,
            isBangla = isBangla,
            onSaveCategory = { cat ->
                onSaveCategory(cat) { newId ->
                    if (cat.id.isBlank() && cat.type.equals(targetCatType, ignoreCase = true)) {
                        selectedCategoryId = newId
                    }
                }
            },
            onDeleteCategory = { delId ->
                if (selectedCategoryId == delId) selectedCategoryId = ""
                onDeleteCategory(delId)
            },
            onDismiss = { showManageCategoriesDialog = false }
        )
    }

    if (showAddPersonDialog) {
        AddEditPersonDialog(
            userId = userId,
            isBangla = isBangla,
            onSave = { newPerson ->
                onSavePerson(newPerson) { newId ->
                    selectedPersonId = newId
                }
                showAddPersonDialog = false
            },
            onDismiss = { showAddPersonDialog = false }
        )
    }

    if (showManagePeopleDialog) {
        ManagePeopleDialog(
            userId = userId,
            people = people,
            isBangla = isBangla,
            onSavePerson = { p ->
                onSavePerson(p) { newId ->
                    if (p.id.isBlank()) {
                        selectedPersonId = newId
                    }
                }
            },
            onDeletePerson = { delId ->
                if (selectedPersonId == delId) selectedPersonId = ""
                onDeletePerson(delId)
            },
            onDismiss = { showManagePeopleDialog = false }
        )
    }
}

