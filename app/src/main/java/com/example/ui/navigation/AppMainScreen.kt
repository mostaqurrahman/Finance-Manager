package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.repository.FinanceRepository
import com.example.ui.FinanceViewModel
import com.example.ui.FinanceViewModelFactory
import com.example.ui.accounts.AccountsScreen
import com.example.ui.auth.signOut
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.loans.LoansScreen
import com.example.ui.more.MoreFeaturesScreen
import com.example.ui.setup.SetupWizardScreen
import com.example.ui.transaction.AddTransactionDialog
import com.example.ui.transaction.TransactionListScreen
import com.example.ui.transaction.VoiceTransactionSheet
import com.example.util.DailyFinanceReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScreenTab(val titleBn: String, val titleEn: String, val icon: ImageVector) {
    DASHBOARD("হোম", "Home", Icons.Default.Dashboard),
    TRANSACTIONS("লেনদেন", "Tx", Icons.Default.ReceiptLong),
    ACCOUNTS("অ্যাকাউন্ট", "Accounts", Icons.Default.AccountBalanceWallet),
    LOANS("ঋণ", "Loans", Icons.Default.Handshake),
    MORE("অন্যান্য", "More", Icons.Default.MoreHoriz)
}

@Composable
fun AppMainScreen(
    currentUserId: String
) {
    val context = LocalContext.current
    val repository = remember { FinanceRepository.getInstance(context) }
    val viewModel: FinanceViewModel = viewModel(
        key = currentUserId,
        factory = FinanceViewModelFactory(currentUserId, repository)
    )

    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    var showVoiceSheet by remember { mutableStateOf(false) }
    var showAddTxDialog by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var initialAddTxType by remember { mutableStateOf(TransactionType.EXPENSE.name) }

    LaunchedEffect(
        state.allTransactions,
        state.summary.availableBalance,
        state.isBanglaLanguage
    ) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayTxs = state.allTransactions.filter { it.date == todayStr }
        val todayExpense = todayTxs
            .filter { it.type == TransactionType.EXPENSE.name }
            .sumOf { it.amount }
        DailyFinanceReminderManager.updateFinanceSnapshot(
            context = context,
            todayTxCount = todayTxs.size,
            todayExpense = todayExpense,
            availableBalance = state.summary.availableBalance,
            isBangla = state.isBanglaLanguage
        )
    }

    // If on subscreen, BackHandler returns to Dashboard
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    // Check if new user needs setup wizard
    val needsSetup = state.profile != null && !state.profile!!.setupCompleted && state.accounts.isEmpty()

    if (needsSetup) {
        SetupWizardScreen(
            userId = currentUserId,
            repository = repository,
            initialProfile = state.profile,
            onSetupComplete = {
                // Handled reactively via state.profile
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                ScreenTab.entries.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.titleEn,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (state.isBanglaLanguage) tab.titleBn else tab.titleEn,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                // Bengali Voice Input floating button on Dashboard
                FloatingActionButton(
                    onClick = { showVoiceSheet = true },
                    containerColor = Color(0xFF006C4C),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_voice_input")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Bengali Voice Input"
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Crossfade(targetState = selectedTab, label = "ScreenTransition") { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardScreen(
                        state = state,
                        onFilterSelect = { viewModel.setDateFilter(it) },
                        onOpenAddTx = {
                            editingTx = null
                            initialAddTxType = TransactionType.EXPENSE.name
                            showAddTxDialog = true
                        },
                        onOpenVoiceTx = { showVoiceSheet = true },
                        onOpenTransfer = {
                            editingTx = null
                            initialAddTxType = TransactionType.TRANSFER.name
                            showAddTxDialog = true
                        },
                        onOpenAddLoan = {
                            selectedTab = 3 // Switch to Loans tab
                        },
                        onEditTx = { tx ->
                            editingTx = tx
                            initialAddTxType = tx.type
                            showAddTxDialog = true
                        },
                        onDeleteTx = { tx -> viewModel.deleteTransaction(tx) },
                        onNavigateAccounts = { selectedTab = 2 },
                        onNavigateLoans = { selectedTab = 3 },
                        onGenerateAiInsights = { viewModel.generateDashboardAiInsights() }
                    )

                    1 -> TransactionListScreen(
                        transactions = state.allTransactions,
                        isBangla = state.isBanglaLanguage,
                        accounts = state.accounts,
                        summary = state.summary,
                        onOpenAddTx = {
                            editingTx = null
                            initialAddTxType = TransactionType.EXPENSE.name
                            showAddTxDialog = true
                        },
                        onEditTx = { tx ->
                            editingTx = tx
                            initialAddTxType = tx.type
                            showAddTxDialog = true
                        },
                        onDeleteTx = { tx -> viewModel.deleteTransaction(tx) }
                    )

                    2 -> AccountsScreen(
                        accounts = state.accounts,
                        isBangla = state.isBanglaLanguage,
                        onAddAccount = { viewModel.addAccount(it) },
                        onUpdateAccount = { viewModel.updateAccount(it) },
                        onOpenTransfer = {
                            editingTx = null
                            initialAddTxType = TransactionType.TRANSFER.name
                            showAddTxDialog = true
                        }
                    )

                    3 -> LoansScreen(
                        loans = state.loans,
                        accounts = state.accounts,
                        isBangla = state.isBanglaLanguage,
                        onAddLoan = { loan, fundingId -> viewModel.addLoan(loan, fundingId) },
                        onUpdateLoan = { viewModel.updateLoan(it) },
                        onDeleteLoan = { viewModel.deleteLoan(it) },
                        onRecordRepayment = { loan, amt, accId, date, notes ->
                            viewModel.recordLoanRepayment(loan, amt, accId, date, notes)
                        }
                    )

                    4 -> MoreFeaturesScreen(
                        state = state,
                        onToggleLanguage = { viewModel.toggleLanguage() },
                        onAddBudget = { viewModel.saveBudget(it) },
                        onAddSavingsGoal = { viewModel.addSavingsGoal(it) },
                        onUpdateSavingsGoal = { viewModel.updateSavingsGoal(it) },
                        onSaveCategory = { viewModel.saveCategory(it) },
                        onDeleteCategory = { viewModel.deleteCategory(it) },
                        onSavePerson = { viewModel.savePerson(it) },
                        onDeletePerson = { viewModel.deletePerson(it) },
                        onSignOut = {
                            val credMgr = CredentialManager.create(context)
                            signOut(credMgr, CoroutineScope(Dispatchers.Main))
                        }
                    )
                }
            }
        }
    }

    // Modal Voice Sheet
    if (showVoiceSheet) {
        VoiceTransactionSheet(
            isProcessing = state.isVoiceProcessing,
            voiceError = state.voiceError,
            draftTx = state.draftVoiceTx,
            accounts = state.accounts,
            categories = state.categories,
            people = state.people,
            onParseText = { viewModel.parseBengaliVoiceInput(it) },
            onParseAudio = { bytes, mimeType -> viewModel.parseBengaliAudioInput(bytes, mimeType) },
            onDirectEdit = { viewModel.openDirectVoiceDraft(it) },
            onClearDraft = { viewModel.clearVoiceDraft() },
            onConfirm = { editedDraft, accId, toAccId, catId, personId ->
                viewModel.confirmVoiceTransaction(editedDraft, accId, toAccId, catId, personId)
                showVoiceSheet = false
            },
            onDismiss = {
                viewModel.clearVoiceDraft()
                showVoiceSheet = false
            }
        )
    }

    // Modal Add / Edit Transaction Dialog
    if (showAddTxDialog || editingTx != null) {
        AddTransactionDialog(
            userId = currentUserId,
            accounts = state.accounts,
            categories = state.categories,
            people = state.people,
            initialType = initialAddTxType,
            existingTransaction = editingTx,
            isBangla = state.isBanglaLanguage,
            onSaveCategory = { cat, onSaved -> viewModel.saveCategory(cat, onSaved) },
            onDeleteCategory = { viewModel.deleteCategory(it) },
            onSavePerson = { person, onSaved -> viewModel.savePerson(person, onSaved) },
            onDeletePerson = { viewModel.deletePerson(it) },
            onSave = { tx ->
                val prev = editingTx
                if (prev != null) {
                    viewModel.updateTransaction(prev, tx)
                } else {
                    viewModel.addTransaction(tx)
                }
                editingTx = null
                showAddTxDialog = false
            },
            onDismiss = {
                editingTx = null
                showAddTxDialog = false
            }
        )
    }
}
