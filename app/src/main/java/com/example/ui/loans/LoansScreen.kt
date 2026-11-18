package com.example.ui.loans

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.Loan
import com.example.data.model.LoanStatus
import com.example.data.model.LoanType
import com.example.util.FinancialEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoansScreen(
    loans: List<Loan>,
    accounts: List<Account>,
    isBangla: Boolean,
    onAddLoan: (Loan, fundingAccountId: String) -> Unit,
    onUpdateLoan: (Loan) -> Unit,
    onDeleteLoan: (Loan) -> Unit = {},
    onRecordRepayment: (Loan, amount: Double, accountId: String, date: String, notes: String?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Given (পাবো), 1: Taken (দেবো)
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var editingLoanTarget by remember { mutableStateOf<Loan?>(null) }
    var deletingLoanTarget by remember { mutableStateOf<Loan?>(null) }
    var repaymentLoanTarget by remember { mutableStateOf<Loan?>(null) }

    val givenLoans = loans.filter { it.type == LoanType.GIVEN.name }
    val takenLoans = loans.filter { it.type == LoanType.TAKEN.name }

    val totalReceivable = givenLoans.sumOf { it.remainingAmount }
    val totalPayable = takenLoans.sumOf { it.remainingAmount }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddLoanDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_loan")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Loan")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBangla) "আমি পাবো (Receivable)" else "Loan Given",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FinancialEngine.formatBdt(totalReceivable),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBangla) "আমি দেবো (Payable)" else "Loan Taken",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FinancialEngine.formatBdt(totalPayable),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(if (isBangla) "আমি পাবো (${givenLoans.size})" else "Receivable") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(if (isBangla) "আমি দেবো (${takenLoans.size})" else "Payable") }
                )
            }

            val currentList = if (selectedTab == 0) givenLoans else takenLoans

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0) {
                            if (isBangla) "কাউকে ঋণ দেননি। নতুন এন্ট্রি যোগ করতে '+' চাপুন।" else "No active loans given."
                        } else {
                            if (isBangla) "কোনো ঋণ বা দেনা নেই। আলহামদুলিল্লাহ!" else "No active debts."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentList, key = { it.id }) { loan ->
                        LoanCardItem(
                            loan = loan,
                            isBangla = isBangla,
                            onEdit = { editingLoanTarget = loan },
                            onDelete = { deletingLoanTarget = loan },
                            onRepay = { repaymentLoanTarget = loan }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Add Loan Dialog
    if (showAddLoanDialog) {
        AddLoanDialog(
            accounts = accounts,
            isBangla = isBangla,
            initialType = if (selectedTab == 0) LoanType.GIVEN.name else LoanType.TAKEN.name,
            onSave = { loan, fundingId ->
                onAddLoan(loan, fundingId)
                showAddLoanDialog = false
            },
            onDismiss = { showAddLoanDialog = false }
        )
    }

    // Edit Loan Dialog
    editingLoanTarget?.let { loan ->
        EditLoanDialog(
            loan = loan,
            isBangla = isBangla,
            onSave = { updatedLoan ->
                onUpdateLoan(updatedLoan)
                editingLoanTarget = null
            },
            onDelete = {
                editingLoanTarget = null
                deletingLoanTarget = loan
            },
            onDismiss = { editingLoanTarget = null }
        )
    }

    // Delete Confirmation Dialog
    deletingLoanTarget?.let { loan ->
        AlertDialog(
            onDismissRequest = { deletingLoanTarget = null },
            title = {
                Text(
                    text = if (isBangla) "ঋণের রেকর্ড মুছে ফেলবেন?" else "Delete Loan Record?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isBangla) {
                            "${loan.partyName} — মূল ঋণ ${FinancialEngine.formatBdt(loan.principal)} (বকেয়া: ${FinancialEngine.formatBdt(loan.remainingAmount)})"
                        } else {
                            "${loan.partyName} — Principal ${FinancialEngine.formatBdt(loan.principal)} (Remaining: ${FinancialEngine.formatBdt(loan.remainingAmount)})"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isBangla) {
                            "এই ঋণের রেকর্ড এবং এর কিস্তির তালিকা মুছে যাবে। (সম্পর্কিত লেনদেন ইতিহাস আলাদাভাবে লেনদেন ট্যাব থেকে এডিট বা ডিলিট করা যাবে।)"
                        } else {
                            "This loan record and its repayment history will be deleted. Linked account transactions can be managed separately from the Transactions tab."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLoan(loan)
                        deletingLoanTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_loan_button")
                ) {
                    Text(if (isBangla) "হ্যাঁ, মুছে ফেলুন" else "Yes, Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingLoanTarget = null }) {
                    Text(if (isBangla) "না, বাতিল" else "Cancel")
                }
            }
        )
    }

    // Repayment Dialog
    repaymentLoanTarget?.let { loan ->
        RecordRepaymentDialog(
            loan = loan,
            accounts = accounts,
            isBangla = isBangla,
            onSave = { amount, accId, date, note ->
                onRecordRepayment(loan, amount, accId, date, note)
                repaymentLoanTarget = null
            },
            onDismiss = { repaymentLoanTarget = null }
        )
    }
}

@Composable
fun LoanCardItem(
    loan: Loan,
    isBangla: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRepay: () -> Unit
) {
    val progress = if (loan.principal > 0) {
        (loan.paidAmount / loan.principal).toFloat().coerceIn(0f, 1f)
    } else 0f

    val isCompleted = loan.status == LoanStatus.COMPLETED.name || loan.remainingAmount <= 0.0

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.partyName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "তারিখ: ${loan.date}${if (!loan.dueDate.isNullOrBlank()) " • মেয়াদ: ${loan.dueDate}" else ""}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCompleted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isCompleted) "পরিশোধিত" else "বকেয়া",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) Color(0xFF059669) else Color(0xFFD97706),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_loan_${loan.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isBangla) "ঋণ এডিট করুন" else "Edit Loan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_loan_${loan.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = if (isBangla) "ঋণ মুছে ফেলুন" else "Delete Loan",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Amounts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "মূল ঋণ (Principal)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = FinancialEngine.formatBdt(loan.principal),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "পরিশোধিত (Paid)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = FinancialEngine.formatBdt(loan.paidAmount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "অবশিষ্ট (Remaining)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = FinancialEngine.formatBdt(loan.remainingAmount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (loan.type == LoanType.GIVEN.name) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (!isCompleted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRepay,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("কিস্তি বা অর্থ পরিশোধ রেকর্ড করুন")
                }
            }
        }
    }
}

@Composable
fun AddLoanDialog(
    accounts: List<Account>,
    isBangla: Boolean,
    initialType: String,
    onSave: (Loan, fundingAccountId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var loanType by remember { mutableStateOf(initialType) }
    var partyName by remember { mutableStateOf("") }
    var principalText by remember { mutableStateOf("") }
    var dueDateText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBangla) "নতুন ঋণ রেকর্ড যোগ করুন" else "Add New Loan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = loanType == LoanType.GIVEN.name,
                        onClick = { loanType = LoanType.GIVEN.name },
                        label = { Text("আমি ঋণ দিয়েছি (পাবো)") }
                    )
                    FilterChip(
                        selected = loanType == LoanType.TAKEN.name,
                        onClick = { loanType = LoanType.TAKEN.name },
                        label = { Text("আমি ঋণ নিয়েছি (দেবো)") }
                    )
                }

                OutlinedTextField(
                    value = partyName,
                    onValueChange = { partyName = it },
                    label = { Text(if (loanType == LoanType.GIVEN.name) "গ্রহীতার নাম (Borrower)" else "ঋণদাতার নাম (Lender)") },
                    modifier = Modifier.fillMaxWidth().testTag("loan_party_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = { Text("ঋণের পরিমাণ (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("loan_amount_input"),
                    singleLine = true
                )

                Text("লেনদেনের অ্যাকাউন্ট:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    accounts.filter { !it.isArchived }.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("পরিশোধের সম্ভাব্য তারিখ (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("নোট / শর্ত (ঐচ্ছিক)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = principalText.toDoubleOrNull() ?: 0.0
                    if (partyName.isNotBlank() && amt > 0 && selectedAccountId.isNotBlank()) {
                        val loan = Loan(
                            type = loanType,
                            partyName = partyName,
                            principal = amt,
                            remainingAmount = amt,
                            date = todayStr,
                            dueDate = dueDateText.ifBlank { null },
                            notes = notesText.ifBlank { null }
                        )
                        onSave(loan, selectedAccountId)
                    }
                },
                enabled = partyName.isNotBlank() && (principalText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun RecordRepaymentDialog(
    loan: Loan,
    accounts: List<Account>,
    isBangla: Boolean,
    onSave: (amount: Double, accountId: String, date: String, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(loan.remainingAmount.toString()) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var noteText by remember { mutableStateOf("") }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("কিস্তি পরিশোধ রেকর্ড") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${loan.partyName} - অবশিষ্ট বকেয়া: ${FinancialEngine.formatBdt(loan.remainingAmount)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("পরিশোধের পরিমাণ (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("repayment_amount_input"),
                    singleLine = true
                )

                Text("অ্যাকাউন্ট:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    accounts.filter { !it.isArchived }.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("নোট (ঐচ্ছিক)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && selectedAccountId.isNotBlank()) {
                        onSave(amt, selectedAccountId, todayStr, noteText.ifBlank { null })
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

private fun normalizeLoanNumericInput(input: String): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val idx = bnDigits.indexOf(ch)
        if (idx >= 0) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
    }
    return sb.toString()
}

private fun formatLoanDouble(v: Double): String {
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

@Composable
fun EditLoanDialog(
    loan: Loan,
    isBangla: Boolean,
    onSave: (Loan) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var loanType by remember(loan.id) { mutableStateOf(loan.type) }
    var partyName by remember(loan.id) { mutableStateOf(loan.partyName) }
    var principalText by remember(loan.id) { mutableStateOf(formatLoanDouble(loan.principal)) }
    var paidAmountText by remember(loan.id) { mutableStateOf(formatLoanDouble(loan.paidAmount)) }
    var dateText by remember(loan.id) { mutableStateOf(loan.date) }
    var dueDateText by remember(loan.id) { mutableStateOf(loan.dueDate ?: "") }
    var notesText by remember(loan.id) { mutableStateOf(loan.notes ?: "") }

    val parsedPrincipal = normalizeLoanNumericInput(principalText).toDoubleOrNull() ?: 0.0
    val parsedPaid = normalizeLoanNumericInput(paidAmountText).toDoubleOrNull() ?: 0.0
    val previewNormalized = FinancialEngine.normalizeEditedLoan(
        loan.copy(principal = parsedPrincipal, paidAmount = parsedPaid)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBangla) "ঋণের তথ্য এডিট করুন" else "Edit Loan") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = loanType == LoanType.GIVEN.name,
                        onClick = { loanType = LoanType.GIVEN.name },
                        label = { Text(if (isBangla) "আমি দিয়েছি (পাবো)" else "Given", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = loanType == LoanType.TAKEN.name,
                        onClick = { loanType = LoanType.TAKEN.name },
                        label = { Text(if (isBangla) "আমি নিয়েছি (দেবো)" else "Taken", fontSize = 12.sp) }
                    )
                }

                OutlinedTextField(
                    value = partyName,
                    onValueChange = { partyName = it },
                    label = {
                        Text(
                            if (loanType == LoanType.GIVEN.name) {
                                if (isBangla) "গ্রহীতার নাম" else "Borrower Name"
                            } else {
                                if (isBangla) "ঋণদাতার নাম" else "Lender Name"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("edit_loan_party_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = { Text(if (isBangla) "মূল ঋণের পরিমাণ (৳)" else "Principal Amount (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_loan_principal_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it },
                    label = { Text(if (isBangla) "পরিশোধিত পরিমাণ (৳)" else "Paid Amount (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_loan_paid_input"),
                    singleLine = true
                )

                Text(
                    text = if (isBangla) {
                        "অবশিষ্ট বকেয়া হবে: ${FinancialEngine.formatBdt(previewNormalized.remainingAmount)}"
                    } else {
                        "Remaining will be: ${FinancialEngine.formatBdt(previewNormalized.remainingAmount)}"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(if (isBangla) "তারিখ (YYYY-MM-DD)" else "Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text(if (isBangla) "পরিশোধের সম্ভাব্য তারিখ (YYYY-MM-DD)" else "Due Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(if (isBangla) "নোট / শর্ত (ঐচ্ছিক)" else "Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_loan_delete_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBangla) "এই ঋণের রেকর্ড মুছে ফেলুন" else "Delete This Loan")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (partyName.isNotBlank() && parsedPrincipal > 0) {
                        val updated = FinancialEngine.normalizeEditedLoan(
                            loan.copy(
                                type = loanType,
                                partyName = partyName.trim(),
                                principal = parsedPrincipal,
                                paidAmount = parsedPaid,
                                date = dateText.trim().ifBlank { loan.date },
                                dueDate = dueDateText.trim().ifBlank { null },
                                notes = notesText.trim().ifBlank { null }
                            )
                        )
                        onSave(updated)
                    }
                },
                enabled = partyName.isNotBlank() && parsedPrincipal > 0,
                modifier = Modifier.testTag("save_edit_loan_button")
            ) {
                Text(if (isBangla) "আপডেট করুন" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (isBangla) "বাতিল" else "Cancel")
            }
        }
    )
}
