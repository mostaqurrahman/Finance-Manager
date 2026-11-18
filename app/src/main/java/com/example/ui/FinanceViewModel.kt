package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Account
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.Loan
import com.example.data.model.Person
import com.example.data.model.SavingsGoal
import com.example.data.model.Transaction
import com.example.data.model.UserProfile
import com.example.data.repository.AiInsightsRepository
import com.example.data.repository.AiInsightsUiState
import com.example.data.repository.AiVoiceRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.ParsedVoiceTransaction
import com.example.util.FinancialEngine
import com.example.util.FinancialSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateFilter(val labelBn: String, val labelEn: String) {
    TODAY("আজ", "Today"),
    THIS_WEEK("এই সপ্তাহ", "This Week"),
    THIS_MONTH("এই মাস", "This Month"),
    THIS_YEAR("এই বছর", "This Year"),
    ALL("সকল", "All")
}

data class FinanceUiState(
    val profile: UserProfile? = null,
    val accounts: List<Account> = emptyList(),
    val allTransactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    val summary: FinancialSummary = FinancialSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    val activeDateFilter: DateFilter = DateFilter.THIS_MONTH,
    val isVoiceProcessing: Boolean = false,
    val voiceError: String? = null,
    val draftVoiceTx: ParsedVoiceTransaction? = null,
    val isBanglaLanguage: Boolean = true,
    val aiInsightsState: AiInsightsUiState = AiInsightsUiState.Idle
)

class FinanceViewModel(
    val userId: String,
    private val repository: FinanceRepository,
    private val aiVoiceRepo: AiVoiceRepository = AiVoiceRepository(),
    private val aiInsightsRepo: AiInsightsRepository = AiInsightsRepository()
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfEmpty(userId)
        }
    }

    private val _dateFilter = MutableStateFlow(DateFilter.THIS_MONTH)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _isVoiceProcessing = MutableStateFlow(false)
    val isVoiceProcessing: StateFlow<Boolean> = _isVoiceProcessing.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _draftVoiceTx = MutableStateFlow<ParsedVoiceTransaction?>(null)
    val draftVoiceTx: StateFlow<ParsedVoiceTransaction?> = _draftVoiceTx.asStateFlow()

    private val _isBangla = MutableStateFlow(true)
    val isBangla: StateFlow<Boolean> = _isBangla.asStateFlow()

    private val _aiInsightsState = MutableStateFlow<AiInsightsUiState>(AiInsightsUiState.Idle)
    val aiInsightsState: StateFlow<AiInsightsUiState> = _aiInsightsState.asStateFlow()

    private data class CoreData(
        val profile: UserProfile?,
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val loans: List<Loan>
    )

    private data class ExtraData(
        val budgets: List<Budget>,
        val savings: List<SavingsGoal>,
        val categories: List<Category>,
        val people: List<Person>
    )

    private data class UiControls(
        val filter: DateFilter,
        val isVoiceProcessing: Boolean,
        val voiceError: String?,
        val draftVoiceTx: ParsedVoiceTransaction?,
        val isBangla: Boolean
    )

    private val coreFlow = combine(
        repository.observeUserProfile(userId),
        repository.observeAccounts(userId),
        repository.observeTransactions(userId),
        repository.observeLoans(userId)
    ) { profile, accounts, txs, loans ->
        CoreData(profile, accounts, txs, loans)
    }

    private val extraFlow = combine(
        repository.observeBudgets(userId),
        repository.observeSavingsGoals(userId),
        repository.observeCategories(userId),
        repository.observePeople(userId)
    ) { budgets, savings, categories, people ->
        ExtraData(budgets, savings, categories, people)
    }

    private val controlsFlow = combine(
        _dateFilter,
        _isVoiceProcessing,
        _voiceError,
        _draftVoiceTx,
        _isBangla
    ) { filter, voiceProc, voiceErr, draftTx, isBn ->
        UiControls(filter, voiceProc, voiceErr, draftTx, isBn)
    }

    val uiState: StateFlow<FinanceUiState> = combine(
        coreFlow,
        extraFlow,
        controlsFlow,
        _aiInsightsState
    ) { core, extra, controls, aiInsights ->
        val filteredTxs = filterTransactionsByDate(core.transactions, controls.filter)
        val summary = FinancialEngine.calculateSummary(core.accounts, filteredTxs, core.loans)

        FinanceUiState(
            profile = core.profile,
            accounts = core.accounts,
            allTransactions = core.transactions,
            filteredTransactions = filteredTxs,
            loans = core.loans,
            budgets = extra.budgets,
            savingsGoals = extra.savings,
            categories = extra.categories,
            people = extra.people,
            summary = summary,
            activeDateFilter = controls.filter,
            isVoiceProcessing = controls.isVoiceProcessing,
            voiceError = controls.voiceError,
            draftVoiceTx = controls.draftVoiceTx,
            isBanglaLanguage = controls.isBangla,
            aiInsightsState = aiInsights
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = FinanceUiState()
    )

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun toggleLanguage() {
        _isBangla.value = !_isBangla.value
    }

    fun generateDashboardAiInsights() {
        viewModelScope.launch {
            val current = uiState.value
            val isBn = current.isBanglaLanguage
            val periodLabel = if (isBn) current.activeDateFilter.labelBn else current.activeDateFilter.labelEn
            _aiInsightsState.value = AiInsightsUiState.Loading
            val result = aiInsightsRepo.generateInsights(
                transactions = current.filteredTransactions,
                categories = current.categories,
                budgets = current.budgets,
                periodLabel = periodLabel,
                isBangla = isBn
            )
            result.onSuccess { insights ->
                _aiInsightsState.value = AiInsightsUiState.Success(insights)
            }.onFailure { err ->
                _aiInsightsState.value = AiInsightsUiState.Error(
                    err.localizedMessage ?: if (isBn) {
                        "AI বিশ্লেষণ সম্পন্ন করা যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।"
                    } else {
                        "Failed to generate AI spending insights. Please try again."
                    }
                )
            }
        }
    }

    fun parseBengaliVoiceInput(text: String) {
        viewModelScope.launch {
            _isVoiceProcessing.value = true
            _voiceError.value = null
            val current = uiState.value
            val result = aiVoiceRepo.parseVoiceInput(
                bengaliText = text,
                accounts = current.accounts,
                categories = current.categories,
                people = current.people
            )
            _isVoiceProcessing.value = false
            result.onSuccess { parsed ->
                _draftVoiceTx.value = parsed
            }.onFailure { err ->
                _voiceError.value = err.localizedMessage ?: "AI বিশ্লেষণ ব্যর্থ হয়েছে"
            }
        }
    }

    fun parseBengaliAudioInput(audioBytes: ByteArray, mimeType: String = "audio/mp4") {
        viewModelScope.launch {
            _isVoiceProcessing.value = true
            _voiceError.value = null
            val current = uiState.value
            val result = aiVoiceRepo.parseAudioInput(
                audioBytes = audioBytes,
                mimeType = mimeType,
                accounts = current.accounts,
                categories = current.categories,
                people = current.people
            )
            _isVoiceProcessing.value = false
            result.onSuccess { parsed ->
                _draftVoiceTx.value = parsed
            }.onFailure { err ->
                _voiceError.value = err.localizedMessage ?: "অডিও বিশ্লেষণ ব্যর্থ হয়েছে"
            }
        }
    }

    fun openDirectVoiceDraft(text: String) {
        val current = uiState.value
        _voiceError.value = null
        _draftVoiceTx.value = com.example.util.BengaliVoiceParser.parse(
            text = text,
            accounts = current.accounts,
            categories = current.categories,
            people = current.people
        )
    }

    fun clearVoiceDraft() {
        _draftVoiceTx.value = null
        _voiceError.value = null
    }

    fun confirmVoiceTransaction(
        editedDraft: ParsedVoiceTransaction,
        accountId: String,
        toAccountId: String?,
        categoryId: String?,
        personId: String?
    ) {
        viewModelScope.launch {
            val current = uiState.value
            val account = current.accounts.find { it.id == accountId }
            val toAccount = if (!toAccountId.isNullOrBlank()) {
                current.accounts.find { it.id == toAccountId }
            } else {
                null
            }
            val category = if (!categoryId.isNullOrBlank()) {
                current.categories.find { it.id == categoryId }
            } else {
                null
            }
            val person = if (!personId.isNullOrBlank()) {
                current.people.find { it.id == personId }
            } else {
                null
            }

            repository.addTransaction(
                Transaction(
                    userId = userId,
                    type = editedDraft.type,
                    amount = editedDraft.amount,
                    accountId = accountId,
                    accountName = account?.name ?: editedDraft.accountName,
                    toAccountId = toAccount?.id,
                    toAccountName = toAccount?.name,
                    categoryId = category?.id,
                    categoryName = category?.name ?: editedDraft.category.ifBlank { "Other" },
                    personId = person?.id,
                    personName = person?.name ?: editedDraft.person,
                    date = editedDraft.date,
                    description = editedDraft.description.ifBlank { editedDraft.transcript }
                )
            )
            _draftVoiceTx.value = null
            _voiceError.value = null
        }
    }

    fun addTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.addTransaction(tx)
        }
    }

    fun updateTransaction(oldTx: Transaction, newTx: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(oldTx, newTx)
        }
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
        }
    }

    fun addAccount(account: Account) {
        viewModelScope.launch {
            repository.addAccount(account)
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            repository.updateAccount(account)
        }
    }

    fun addLoan(loan: Loan, accountId: String) {
        viewModelScope.launch {
            repository.addLoan(loan, accountId)
        }
    }

    fun updateLoan(loan: Loan) {
        viewModelScope.launch {
            repository.updateLoan(loan)
        }
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            repository.deleteLoan(loan)
        }
    }

    fun recordLoanRepayment(loan: Loan, amount: Double, accountId: String, date: String, notes: String?) {
        viewModelScope.launch {
            repository.recordLoanRepayment(loan, amount, accountId, date, notes)
        }
    }

    fun saveCategory(category: Category, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repository.saveCategory(category) }
            result.onSuccess { id -> onSaved(id) }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteCategory(categoryId) }
        }
    }

    fun savePerson(person: Person, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repository.savePerson(person) }
            result.onSuccess { id -> onSaved(id) }
        }
    }

    fun deletePerson(personId: String) {
        viewModelScope.launch {
            runCatching { repository.deletePerson(personId) }
        }
    }

    fun addSavingsGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.addSavingsGoal(goal)
        }
    }

    fun updateSavingsGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.updateSavingsGoal(goal)
        }
    }

    fun saveBudget(budget: Budget) {
        viewModelScope.launch {
            repository.saveBudget(budget)
        }
    }

    private fun filterTransactionsByDate(txs: List<Transaction>, filter: DateFilter): List<Transaction> {
        if (filter == DateFilter.ALL) return txs
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        return when (filter) {
            DateFilter.TODAY -> txs.filter { it.date == todayStr }
            DateFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val startWeek = sdf.format(cal.time)
                txs.filter { it.date >= startWeek && it.date <= todayStr }
            }
            DateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startMonth = sdf.format(cal.time)
                txs.filter { it.date >= startMonth && it.date <= todayStr }
            }
            DateFilter.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val startYear = sdf.format(cal.time)
                txs.filter { it.date >= startYear && it.date <= todayStr }
            }
            DateFilter.ALL -> txs
        }
    }
}

class FinanceViewModelFactory(
    private val userId: String,
    private val repository: FinanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(userId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
