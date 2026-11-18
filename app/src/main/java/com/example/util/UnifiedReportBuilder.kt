package com.example.util

import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.Loan
import com.example.data.model.LoanType
import com.example.data.model.Person
import com.example.data.model.SavingsGoal
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriodType(val labelEn: String, val labelBn: String) {
    MONTHLY("Monthly Report", "মাসিক রিপোর্ট"),
    YEARLY("Yearly Report", "বার্ষিক রিপোর্ট")
}

enum class ReportExportFormat(val labelEn: String, val labelBn: String) {
    PDF("PDF (.pdf)", "PDF ডকুমেন্ট (.pdf)"),
    XLSX("Excel Workbook (.xlsx)", "Excel ওয়ার্কবুক (.xlsx)"),
    BOTH("Both (PDF + XLSX)", "উভয়ই (PDF + XLSX)")
}

data class AvailableReportPeriod(
    val key: String, // YYYY-MM or YYYY
    val labelEn: String,
    val labelBn: String,
    val startDate: String,
    val endDate: String,
    val txCount: Int
)

data class ReportTypeSummaryRow(
    val type: String,
    val count: Int,
    val totalAmount: Double
)

data class ReportAccountSummaryRow(
    val accountId: String,
    val accountName: String,
    val accountType: String,
    val provider: String,
    val periodOpeningBalance: Double,
    val moneyIn: Double,
    val moneyOut: Double,
    val netChange: Double,
    val periodClosingBalance: Double,
    val liveCurrentBalance: Double,
    val txCount: Int,
    val status: String
)

data class ReportAccountStatementEntry(
    val date: String,
    val type: String,
    val counterpartyOrDetail: String,
    val description: String,
    val moneyIn: Double,
    val moneyOut: Double,
    val runningBalance: Double
)

data class ReportAccountStatement(
    val accountId: String,
    val accountName: String,
    val accountType: String,
    val provider: String,
    val openingBalance: Double,
    val closingBalance: Double,
    val totalMoneyIn: Double,
    val totalMoneyOut: Double,
    val entries: List<ReportAccountStatementEntry>
)

data class ReportTrendBucket(
    val bucketKey: String,
    val label: String,
    val txCount: numberAlias = 0,
    val income: Double,
    val grossExpense: Double,
    val refunds: Double,
    val netExpense: Double,
    val netCashFlow: Double
)

typealias numberAlias = Int

data class ReportPersonSummaryRow(
    val personKey: String,
    val personName: String,
    val relationship: String,
    val txCount: Int,
    val totalExpense: Double,
    val totalIncome: Double,
    val totalOtherFlow: Double,
    val expenseSharePercent: Double
)

data class ReportPersonDetailRow(
    val personName: String,
    val relationship: String,
    val date: String,
    val type: String,
    val accountName: String,
    val categoryName: String,
    val amount: Double,
    val description: String
)

data class ReportCategorySummaryRow(
    val categoryName: String,
    val type: String, // INCOME or EXPENSE
    val txCount: Int,
    val amount: Double,
    val sharePercent: Double
)

data class ReportCategoryDetailRow(
    val categoryName: String,
    val categoryType: String,
    val date: String,
    val accountName: String,
    val personName: String,
    val amount: Double,
    val description: String
)

data class ReportLoanRow(
    val type: String,
    val partyName: String,
    val principal: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val date: String,
    val dueDate: String,
    val status: String,
    val notes: String
)

data class ReportSavingsAccountRow(
    val name: String,
    val type: String,
    val provider: String,
    val currentBalance: Double,
    val status: String
)

data class ReportAssetLiabilityRow(
    val classification: String, // Asset or Liability
    val name: String,
    val category: String,
    val originalOrTarget: Double,
    val currentOrRemaining: Double,
    val status: String
)

data class ReportTransactionRow(
    val date: String,
    val time: String,
    val type: String,
    val amount: Double,
    val accountName: String,
    val toAccountName: String,
    val categoryName: String,
    val personName: String,
    val description: String
)

data class UnifiedReportData(
    val periodType: ReportPeriodType,
    val periodKey: String,
    val periodLabelEn: String,
    val periodLabelBn: String,
    val startDate: String,
    val endDate: String,
    val ownerName: String,
    val ownerEmail: String,
    val generatedAt: String,
    val periodIncome: Double,
    val periodGrossExpense: Double,
    val periodRefunds: Double,
    val periodNetExpense: Double,
    val periodNetCashFlow: Double,
    val savingsRatePercent: Double,
    val availableLiquidity: Double,
    val cashBalance: Double,
    val bankBalance: Double,
    val mobileWalletBalance: Double,
    val totalSavings: Double,
    val loanGivenOutstanding: Double,
    val loanTakenOutstanding: Double,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double,
    val transactionCount: Int,
    val transferCount: Int,
    val transferVolume: Double,
    val typeSummaries: List<ReportTypeSummaryRow>,
    val accountSummaries: List<ReportAccountSummaryRow>,
    val accountStatements: List<ReportAccountStatement>,
    val trendBuckets: List<ReportTrendBucket>,
    val personSummaries: List<ReportPersonSummaryRow>,
    val personDetails: List<ReportPersonDetailRow>,
    val categorySummaries: List<ReportCategorySummaryRow>,
    val categoryDetails: List<ReportCategoryDetailRow>,
    val loanGivenPrincipal: Double,
    val loanGivenPaid: Double,
    val loanTakenPrincipal: Double,
    val loanTakenPaid: Double,
    val loanRows: List<ReportLoanRow>,
    val savingsAccounts: List<ReportSavingsAccountRow>,
    val savingsGoals: List<SavingsGoal>,
    val budgets: List<Budget>,
    val assetItems: List<ReportAssetLiabilityRow>,
    val liabilityItems: List<ReportAssetLiabilityRow>,
    val transactions: List<ReportTransactionRow>
)

object UnifiedReportBuilder {

    private val MONTH_NAMES_EN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private val MONTH_NAMES_BN = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    private fun toBanglaDigits(input: String): String {
        val bn = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') sb.append(bn[ch - '0']) else sb.append(ch)
        }
        return sb.toString()
    }

    fun buildFileName(
        periodType: ReportPeriodType,
        periodKey: String,
        extension: String
    ): String {
        val safeKey = periodKey.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val prefix = if (periodType == ReportPeriodType.MONTHLY) {
            "HisabKhata_Monthly"
        } else {
            "HisabKhata_Yearly"
        }
        return "${prefix}_${safeKey}.${extension.lowercase(Locale.US)}"
    }

    fun getPeriodBounds(periodType: ReportPeriodType, periodKey: String): AvailableReportPeriod {
        return if (periodType == ReportPeriodType.MONTHLY) {
            val parts = periodKey.split("-")
            val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
            val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 12)
            val cal = Calendar.getInstance(Locale.US).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val mm = month.toString().padStart(2, '0')
            val dd = lastDay.toString().padStart(2, '0')
            AvailableReportPeriod(
                key = "$year-$mm",
                labelEn = "${MONTH_NAMES_EN[month - 1]} $year",
                labelBn = "${MONTH_NAMES_BN[month - 1]} ${toBanglaDigits(year.toString())}",
                startDate = "$year-$mm-01",
                endDate = "$year-$mm-$dd",
                txCount = 0
            )
        } else {
            val year = periodKey.toIntOrNull() ?: 2026
            AvailableReportPeriod(
                key = year.toString(),
                labelEn = year.toString(),
                labelBn = toBanglaDigits(year.toString()),
                startDate = "$year-01-01",
                endDate = "$year-12-31",
                txCount = 0
            )
        }
    }

    /**
     * Dynamically extracts non-empty months or years from recorded transactions,
     * sorted newest to oldest. No hardcoded months or empty months.
     */
    fun getAvailablePeriods(
        transactions: List<Transaction>,
        periodType: ReportPeriodType
    ): List<AvailableReportPeriod> {
        val counts = linkedMapOf<String, Int>()
        for (tx in transactions) {
            val date = tx.date.trim()
            if (!DATE_REGEX.matches(date)) continue
            val key = if (periodType == ReportPeriodType.MONTHLY) date.substring(0, 7) else date.substring(0, 4)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries
            .sortedByDescending { it.key }
            .map { (key, count) ->
                getPeriodBounds(periodType, key).copy(txCount = count)
            }
    }

    private fun getTransactionAccountImpact(tx: Transaction, accountId: String): Pair<Double, Double> {
        var moneyIn = 0.0
        var moneyOut = 0.0

        if (tx.accountId == accountId) {
            when (tx.type) {
                TransactionType.INCOME.name,
                TransactionType.LOAN_RECEIVED.name,
                TransactionType.LOAN_REPAYMENT_RECEIVED.name,
                TransactionType.SAVINGS_WITHDRAWAL.name,
                TransactionType.REFUND.name -> moneyIn += tx.amount

                TransactionType.EXPENSE.name,
                TransactionType.LOAN_GIVEN.name,
                TransactionType.LOAN_REPAYMENT_PAID.name,
                TransactionType.SAVINGS_DEPOSIT.name,
                TransactionType.TRANSFER.name -> moneyOut += tx.amount
            }
        }

        if (tx.toAccountId == accountId && tx.type == TransactionType.TRANSFER.name) {
            moneyIn += tx.amount
        }

        return moneyIn to moneyOut
    }

    fun buildReport(
        periodType: ReportPeriodType,
        periodKey: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        loans: List<Loan>,
        categories: List<Category>,
        people: List<Person>,
        savingsGoals: List<SavingsGoal> = emptyList(),
        budgets: List<Budget> = emptyList(),
        ownerName: String = "Account Holder",
        ownerEmail: String = ""
    ): UnifiedReportData {
        val bounds = getPeriodBounds(periodType, periodKey)
        val startDate = bounds.startDate
        val endDate = bounds.endDate

        val accountMap = accounts.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        val personById = people.associateBy { it.id }

        val sortedAll = transactions
            .filter { DATE_REGEX.matches(it.date.trim()) }
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.time ?: "" }.thenBy { it.id })

        val periodTxAsc = sortedAll.filter { it.date in startDate..endDate }
        val afterPeriodTx = sortedAll.filter { it.date > endDate }

        // 1. Overall Income, Expense, Refunds, Cash Flow & Transfers
        var periodIncome = 0.0
        var periodGrossExpense = 0.0
        var periodRefunds = 0.0
        var transferCount = 0
        var transferVolume = 0.0

        val typeBuckets = linkedMapOf<String, Pair<Int, Double>>()
        for (tx in periodTxAsc) {
            val prev = typeBuckets[tx.type] ?: (0 to 0.0)
            typeBuckets[tx.type] = (prev.first + 1) to (prev.second + tx.amount)

            when (tx.type) {
                TransactionType.INCOME.name -> periodIncome += tx.amount
                TransactionType.EXPENSE.name -> periodGrossExpense += tx.amount
                TransactionType.REFUND.name -> periodRefunds += tx.amount
                TransactionType.TRANSFER.name -> {
                    transferCount += 1
                    transferVolume += tx.amount
                }
            }
        }

        val periodNetExpense = (periodGrossExpense - periodRefunds).coerceAtLeast(0.0)
        val periodNetCashFlow = periodIncome - periodNetExpense
        val savingsRatePercent = if (periodIncome > 0) {
            ((periodNetCashFlow / periodIncome) * 1000.0).toInt() / 10.0
        } else {
            0.0
        }

        val typeSummaries = typeBuckets.entries
            .map { (type, pair) -> ReportTypeSummaryRow(type, pair.first, pair.second) }
            .sortedByDescending { it.totalAmount }

        // 2. Balance Sheet Position (Current Live Liquidity, Savings, Loans, Net Worth)
        val liveSummary = FinancialEngine.calculateSummary(accounts, transactions, loans)

        // 3. Account Summaries & Per-Account Detailed Statements (Backwards-anchored from currentBalance)
        val accountSummaries = mutableListOf<ReportAccountSummaryRow>()
        val accountStatements = mutableListOf<ReportAccountStatement>()

        for (acc in accounts) {
            var postNetChange = 0.0
            for (tx in afterPeriodTx) {
                val (inAmt, outAmt) = getTransactionAccountImpact(tx, acc.id)
                postNetChange += (inAmt - outAmt)
            }

            // Period Closing Balance = current live balance minus changes after endDate
            val periodClosing = acc.currentBalance - postNetChange

            val accountPeriodTxs = periodTxAsc.filter {
                it.accountId == acc.id || (it.type == TransactionType.TRANSFER.name && it.toAccountId == acc.id)
            }

            var totalIn = 0.0
            var totalOut = 0.0
            for (tx in accountPeriodTxs) {
                val (inAmt, outAmt) = getTransactionAccountImpact(tx, acc.id)
                totalIn += inAmt
                totalOut += outAmt
            }
            val netPeriodChange = totalIn - totalOut
            val periodOpening = periodClosing - netPeriodChange

            accountSummaries += ReportAccountSummaryRow(
                accountId = acc.id,
                accountName = acc.name,
                accountType = acc.type,
                provider = acc.provider ?: acc.type,
                periodOpeningBalance = periodOpening,
                moneyIn = totalIn,
                moneyOut = totalOut,
                netChange = netPeriodChange,
                periodClosingBalance = periodClosing,
                liveCurrentBalance = acc.currentBalance,
                txCount = accountPeriodTxs.size,
                status = if (acc.isArchived) "ARCHIVED" else "ACTIVE"
            )

            var running = periodOpening
            val entries = accountPeriodTxs.map { tx ->
                val (inAmt, outAmt) = getTransactionAccountImpact(tx, acc.id)
                running += (inAmt - outAmt)
                val detail = when (tx.type) {
                    TransactionType.TRANSFER.name -> {
                        if (tx.accountId == acc.id) {
                            val toName = tx.toAccountName ?: accountMap[tx.toAccountId]?.name ?: tx.toAccountId ?: "Account"
                            "Transfer to $toName"
                        } else {
                            val fromName = tx.accountName ?: accountMap[tx.accountId]?.name ?: tx.accountId
                            "Transfer from $fromName"
                        }
                    }
                    else -> {
                        listOfNotNull(
                            tx.categoryName?.takeIf { it.isNotBlank() },
                            tx.personName?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ").ifBlank { tx.type }
                    }
                }
                ReportAccountStatementEntry(
                    date = tx.date,
                    type = tx.type,
                    counterpartyOrDetail = detail,
                    description = tx.description?.ifBlank { "-" } ?: "-",
                    moneyIn = inAmt,
                    moneyOut = outAmt,
                    runningBalance = running
                )
            }

            accountStatements += ReportAccountStatement(
                accountId = acc.id,
                accountName = acc.name,
                accountType = acc.type,
                provider = acc.provider ?: acc.type,
                openingBalance = periodOpening,
                closingBalance = periodClosing,
                totalMoneyIn = totalIn,
                totalMoneyOut = totalOut,
                entries = entries
            )
        }

        // 4. Period Trend Buckets (Daily for Monthly Report, Monthly for Yearly Report)
        data class MutableTrend(
            var txCount: Int = 0,
            var income: Double = 0.0,
            var grossExpense: Double = 0.0,
            var refunds: Double = 0.0
        )
        val trendMap = linkedMapOf<String, MutableTrend>()
        for (tx in periodTxAsc) {
            val key = if (periodType == ReportPeriodType.MONTHLY) tx.date else tx.date.substring(0, 7)
            val bucket = trendMap.getOrPut(key) { MutableTrend() }
            bucket.txCount += 1
            when (tx.type) {
                TransactionType.INCOME.name -> bucket.income += tx.amount
                TransactionType.EXPENSE.name -> bucket.grossExpense += tx.amount
                TransactionType.REFUND.name -> bucket.refunds += tx.amount
            }
        }
        val trendBuckets = trendMap.entries.sortedBy { it.key }.map { (k, v) ->
            val label = if (periodType == ReportPeriodType.YEARLY && k.length == 7) {
                val mIdx = (k.substring(5, 7).toIntOrNull() ?: 1).coerceIn(1, 12) - 1
                "${MONTH_NAMES_EN[mIdx]} ${k.substring(0, 4)}"
            } else {
                k
            }
            val netExp = (v.grossExpense - v.refunds).coerceAtLeast(0.0)
            ReportTrendBucket(
                bucketKey = k,
                label = label,
                txCount = v.txCount,
                income = v.income,
                grossExpense = v.grossExpense,
                refunds = v.refunds,
                netExpense = netExp,
                netCashFlow = v.income - netExp
            )
        }

        // 5. Person-wise Summary & Detailed Analysis
        data class MutablePersonAgg(
            val name: String,
            val relationship: String,
            var txCount: Int = 0,
            var expense: Double = 0.0,
            var income: Double = 0.0,
            var other: Double = 0.0
        )
        val personAggMap = linkedMapOf<String, MutablePersonAgg>()
        val personDetails = mutableListOf<ReportPersonDetailRow>()

        for (tx in periodTxAsc) {
            val pObj = tx.personId?.let { personById[it] }
            val resolvedName = tx.personName?.takeIf { it.isNotBlank() }
                ?: pObj?.name?.takeIf { it.isNotBlank() }
            val key = resolvedName ?: "__SELF__"
            val displayName = resolvedName ?: "Self / Unassigned"
            val relation = pObj?.relationship?.takeIf { it.isNotBlank() }
                ?: if (resolvedName == null) "Self" else "General"

            val agg = personAggMap.getOrPut(key) { MutablePersonAgg(displayName, relation) }
            agg.txCount += 1
            when (tx.type) {
                TransactionType.EXPENSE.name -> agg.expense += tx.amount
                TransactionType.INCOME.name -> agg.income += tx.amount
                else -> agg.other += tx.amount
            }

            if (resolvedName != null) {
                personDetails += ReportPersonDetailRow(
                    personName = displayName,
                    relationship = relation,
                    date = tx.date,
                    type = tx.type,
                    accountName = tx.accountName ?: accountMap[tx.accountId]?.name ?: tx.accountId,
                    categoryName = tx.categoryName?.ifBlank { "-" } ?: "-",
                    amount = tx.amount,
                    description = tx.description?.ifBlank { "-" } ?: "-"
                )
            }
        }

        val totalPersonExp = personAggMap.values.sumOf { it.expense }
        val personSummaries = personAggMap.entries.map { (k, v) ->
            val share = if (totalPersonExp > 0) ((v.expense / totalPersonExp) * 1000.0).toInt() / 10.0 else 0.0
            ReportPersonSummaryRow(
                personKey = k,
                personName = v.name,
                relationship = v.relationship,
                txCount = v.txCount,
                totalExpense = v.expense,
                totalIncome = v.income,
                totalOtherFlow = v.other,
                expenseSharePercent = share
            )
        }.sortedByDescending { it.totalExpense + it.totalOtherFlow }

        // 6. Category-wise Summary & Detailed Analysis
        data class MutableCatAgg(
            val catName: String,
            val type: String,
            var txCount: Int = 0,
            var amount: Double = 0.0
        )
        val catAggMap = linkedMapOf<String, MutableCatAgg>()
        val categoryDetails = mutableListOf<ReportCategoryDetailRow>()

        for (tx in periodTxAsc) {
            if (tx.type != TransactionType.INCOME.name && tx.type != TransactionType.EXPENSE.name) continue
            val catObj = tx.categoryId?.let { categoryById[it] }
            val catName = tx.categoryName?.takeIf { it.isNotBlank() }
                ?: catObj?.nameBn?.takeIf { it.isNotBlank() }
                ?: catObj?.name?.takeIf { it.isNotBlank() }
                ?: "Uncategorized"
            val key = "${tx.type}:$catName"
            val agg = catAggMap.getOrPut(key) { MutableCatAgg(catName, tx.type) }
            agg.txCount += 1
            agg.amount += tx.amount

            categoryDetails += ReportCategoryDetailRow(
                categoryName = catName,
                categoryType = tx.type,
                date = tx.date,
                accountName = tx.accountName ?: accountMap[tx.accountId]?.name ?: tx.accountId,
                personName = tx.personName?.ifBlank { "-" } ?: "-",
                amount = tx.amount,
                description = tx.description?.ifBlank { "-" } ?: "-"
            )
        }

        val totalCatExp = catAggMap.values.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amount }
        val totalCatInc = catAggMap.values.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amount }
        val categorySummaries = catAggMap.values.map { v ->
            val denom = if (v.type == TransactionType.EXPENSE.name) totalCatExp else totalCatInc
            val share = if (denom > 0) ((v.amount / denom) * 1000.0).toInt() / 10.0 else 0.0
            ReportCategorySummaryRow(
                categoryName = v.catName,
                type = v.type,
                txCount = v.txCount,
                amount = v.amount,
                sharePercent = share
            )
        }.sortedWith(compareBy<ReportCategorySummaryRow> { it.type }.thenByDescending { it.amount })

        // 7. Loans Summary (Relevant to period + active receivables/payables)
        val relevantLoans = loans.filter { loan ->
            loan.date in startDate..endDate || loan.remainingAmount > 0.0
        }
        val givenLoans = relevantLoans.filter { it.type == LoanType.GIVEN.name }
        val takenLoans = relevantLoans.filter { it.type == LoanType.TAKEN.name }
        val loanRows = relevantLoans.map {
            ReportLoanRow(
                type = it.type,
                partyName = it.partyName,
                principal = it.principal,
                paidAmount = it.paidAmount,
                remainingAmount = it.remainingAmount,
                date = it.date,
                dueDate = it.dueDate ?: "-",
                status = it.status,
                notes = it.notes ?: "-"
            )
        }

        // 8. Savings, Assets & Liabilities
        val savingsAccounts = accounts
            .filter {
                it.type == AccountType.SAVINGS.name || it.type == AccountType.DPS_FDR.name
            }
            .map {
                ReportSavingsAccountRow(
                    name = it.name,
                    type = it.type,
                    provider = it.provider ?: "-",
                    currentBalance = it.currentBalance,
                    status = if (it.isArchived) "ARCHIVED" else "ACTIVE"
                )
            }

        val assetItems = buildList {
            for (acc in accounts.filter { !it.isArchived && it.currentBalance != 0.0 }) {
                add(
                    ReportAssetLiabilityRow(
                        classification = "Asset",
                        name = acc.name,
                        category = acc.type,
                        originalOrTarget = acc.openingBalance,
                        currentOrRemaining = acc.currentBalance,
                        status = "ACTIVE"
                    )
                )
            }
            for (goal in savingsGoals) {
                add(
                    ReportAssetLiabilityRow(
                        classification = "Asset",
                        name = "Goal: ${goal.title}",
                        category = "SAVINGS_GOAL",
                        originalOrTarget = goal.targetAmount,
                        currentOrRemaining = goal.currentAmount,
                        status = goal.status
                    )
                )
            }
            for (loan in loans.filter { it.type == LoanType.GIVEN.name && it.remainingAmount > 0.0 }) {
                add(
                    ReportAssetLiabilityRow(
                        classification = "Asset",
                        name = "Loan Receivable: ${loan.partyName}",
                        category = "LOAN_GIVEN",
                        originalOrTarget = loan.principal,
                        currentOrRemaining = loan.remainingAmount,
                        status = loan.status
                    )
                )
            }
        }

        val liabilityItems = loans
            .filter { it.type == LoanType.TAKEN.name && it.remainingAmount > 0.0 }
            .map { loan ->
                ReportAssetLiabilityRow(
                    classification = "Liability",
                    name = "Loan Payable: ${loan.partyName}",
                    category = "LOAN_TAKEN",
                    originalOrTarget = loan.principal,
                    currentOrRemaining = loan.remainingAmount,
                    status = loan.status
                )
            }

        val totalAssets = liveSummary.availableBalance + liveSummary.totalSavings + liveSummary.loanGivenReceivable
        val totalLiabilities = liveSummary.loanTakenPayable

        // 9. Transactions Ledger (Newest first in period)
        val periodTxRows = periodTxAsc.reversed().map { tx ->
            ReportTransactionRow(
                date = tx.date,
                time = tx.time ?: "",
                type = tx.type,
                amount = tx.amount,
                accountName = tx.accountName ?: accountMap[tx.accountId]?.name ?: tx.accountId,
                toAccountName = tx.toAccountName ?: tx.toAccountId?.let { accountMap[it]?.name ?: it } ?: "",
                categoryName = tx.categoryName ?: "",
                personName = tx.personName ?: "",
                description = tx.description ?: ""
            )
        }

        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        return UnifiedReportData(
            periodType = periodType,
            periodKey = bounds.key,
            periodLabelEn = bounds.labelEn,
            periodLabelBn = bounds.labelBn,
            startDate = startDate,
            endDate = endDate,
            ownerName = ownerName,
            ownerEmail = ownerEmail,
            generatedAt = generatedAt,
            periodIncome = periodIncome,
            periodGrossExpense = periodGrossExpense,
            periodRefunds = periodRefunds,
            periodNetExpense = periodNetExpense,
            periodNetCashFlow = periodNetCashFlow,
            savingsRatePercent = savingsRatePercent,
            availableLiquidity = liveSummary.availableBalance,
            cashBalance = liveSummary.cashBalance,
            bankBalance = liveSummary.bankBalance,
            mobileWalletBalance = liveSummary.mobileWalletBalance,
            totalSavings = liveSummary.totalSavings,
            loanGivenOutstanding = liveSummary.loanGivenReceivable,
            loanTakenOutstanding = liveSummary.loanTakenPayable,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = liveSummary.netWorth,
            transactionCount = periodTxAsc.size,
            transferCount = transferCount,
            transferVolume = transferVolume,
            typeSummaries = typeSummaries,
            accountSummaries = accountSummaries,
            accountStatements = accountStatements,
            trendBuckets = trendBuckets,
            personSummaries = personSummaries,
            personDetails = personDetails,
            categorySummaries = categorySummaries,
            categoryDetails = categoryDetails,
            loanGivenPrincipal = givenLoans.sumOf { it.principal },
            loanGivenPaid = givenLoans.sumOf { it.paidAmount },
            loanTakenPrincipal = takenLoans.sumOf { it.principal },
            loanTakenPaid = takenLoans.sumOf { it.paidAmount },
            loanRows = loanRows,
            savingsAccounts = savingsAccounts,
            savingsGoals = savingsGoals,
            budgets = budgets,
            assetItems = assetItems,
            liabilityItems = liabilityItems,
            transactions = periodTxRows
        )
    }
}
