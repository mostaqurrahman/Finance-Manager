package com.example.ui.transaction

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.ui.dashboard.TransactionRowItem
import com.example.util.ExportFormat
import com.example.util.FinanceExportUtil
import com.example.util.FinancialEngine
import com.example.util.FinancialSummary

@Composable
fun TransactionListScreen(
    transactions: List<Transaction>,
    isBangla: Boolean,
    accounts: List<Account> = emptyList(),
    summary: FinancialSummary? = null,
    onOpenAddTx: () -> Unit,
    onEditTx: (Transaction) -> Unit,
    onDeleteTx: (Transaction) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val filtered = transactions.filter { tx ->
        val matchesType = when (selectedTypeFilter) {
            "ALL" -> true
            else -> tx.type == selectedTypeFilter
        }
        val matchesSearch = if (searchQuery.isBlank()) true else {
            (tx.description?.contains(searchQuery, ignoreCase = true) == true) ||
            (tx.categoryName?.contains(searchQuery, ignoreCase = true) == true) ||
            (tx.accountName?.contains(searchQuery, ignoreCase = true) == true) ||
            (tx.personName?.contains(searchQuery, ignoreCase = true) == true)
        }
        matchesType && matchesSearch
    }

    val effectiveSummary = remember(summary, accounts, filtered) {
        summary ?: FinancialEngine.calculateSummary(accounts, filtered, emptyList())
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddTx,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_tx_list")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(if (isBangla) "লেনদেন খুঁজুন (বিবরণ, খাত, ব্যক্তি)..." else "Search transactions...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_tx_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to if (isBangla) "সকল" else "All",
                    TransactionType.EXPENSE.name to if (isBangla) "ব্যয়" else "Expense",
                    TransactionType.INCOME.name to if (isBangla) "আয়" else "Income",
                    TransactionType.TRANSFER.name to if (isBangla) "ট্রান্সফার" else "Transfer",
                    TransactionType.REFUND.name to if (isBangla) "রিফান্ড" else "Refund"
                ).forEach { (typeKey, label) ->
                    FilterChip(
                        selected = selectedTypeFilter == typeKey,
                        onClick = { selectedTypeFilter = typeKey },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBangla) "${filtered.size} টি লেনদেন" else "${filtered.size} transactions",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {
                            FinanceExportUtil.exportTransactions(
                                context = context,
                                format = ExportFormat.CSV,
                                transactions = filtered,
                                accounts = accounts,
                                summary = effectiveSummary
                            ).onSuccess { outcome ->
                                exportNotice = if (outcome.savedToDownloads) {
                                    if (isBangla) "Downloads এ সেভ হয়েছে: ${outcome.fileName}" else "Saved: ${outcome.fileName}"
                                } else {
                                    context.startActivity(
                                        FinanceExportUtil.createShareIntent(
                                            outcome,
                                            if (isBangla) "CSV এক্সপোর্ট শেয়ার করুন" else "Share CSV"
                                        )
                                    )
                                    outcome.fileName
                                }
                            }
                        },
                        label = { Text("CSV", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.testTag("tx_list_export_csv_button")
                    )

                    AssistChip(
                        onClick = {
                            FinanceExportUtil.exportTransactions(
                                context = context,
                                format = ExportFormat.XLSX,
                                transactions = filtered,
                                accounts = accounts,
                                summary = effectiveSummary
                            ).onSuccess { outcome ->
                                exportNotice = if (outcome.savedToDownloads) {
                                    if (isBangla) "Downloads এ সেভ হয়েছে: ${outcome.fileName}" else "Saved: ${outcome.fileName}"
                                } else {
                                    context.startActivity(
                                        FinanceExportUtil.createShareIntent(
                                            outcome,
                                            if (isBangla) "Excel এক্সপোর্ট শেয়ার করুন" else "Share Excel"
                                        )
                                    )
                                    outcome.fileName
                                }
                            }
                        },
                        label = { Text(".xlsx", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.testTag("tx_list_export_xlsx_button")
                    )
                }
            }

            exportNotice?.let { notice ->
                Text(
                    text = notice,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isBangla) "কোনো লেনদেন পাওয়া যায়নি।" else "No transactions match your search.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { tx ->
                        TransactionRowItem(
                            tx = tx,
                            onEdit = { onEditTx(tx) },
                            onDelete = { onDeleteTx(tx) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
