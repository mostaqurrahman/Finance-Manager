package com.example.util

import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Loan
import com.example.data.model.LoanType
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FinancialSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val availableBalance: Double,
    val cashBalance: Double,
    val bankBalance: Double,
    val mobileWalletBalance: Double,
    val totalSavings: Double,
    val loanGivenReceivable: Double,
    val loanTakenPayable: Double,
    val netWorth: Double
)

object FinancialEngine {

    /**
     * Calculates comprehensive financial position according to single-truth accounting rules.
     */
    fun calculateSummary(
        accounts: List<Account>,
        transactions: List<Transaction>,
        loans: List<Loan>
    ): FinancialSummary {
        // Genuine income & expense from transactions
        var income = 0.0
        var expense = 0.0

        for (tx in transactions) {
            when (tx.type) {
                TransactionType.INCOME.name -> income += tx.amount
                TransactionType.EXPENSE.name -> expense += tx.amount
                TransactionType.REFUND.name -> expense -= tx.amount // Linked refund reduces net expense
            }
        }
        if (expense < 0.0) expense = 0.0

        // Liquid account balances
        var cashBal = 0.0
        var bankBal = 0.0
        var mobileBal = 0.0
        var savingsBal = 0.0

        for (acc in accounts) {
            if (acc.isArchived) continue
            when (acc.type) {
                AccountType.CASH.name -> cashBal += acc.currentBalance
                AccountType.BANK.name -> bankBal += acc.currentBalance
                AccountType.MOBILE_BANKING.name -> mobileBal += acc.currentBalance
                AccountType.SAVINGS.name, AccountType.DPS_FDR.name -> savingsBal += acc.currentBalance
                else -> cashBal += acc.currentBalance
            }
        }

        val available = cashBal + bankBal + mobileBal

        // Loan given (receivable / asset) & Loan taken (payable / liability)
        var loanGivenTotal = 0.0
        var loanTakenTotal = 0.0

        for (loan in loans) {
            if (loan.type == LoanType.GIVEN.name) {
                loanGivenTotal += loan.remainingAmount
            } else {
                loanTakenTotal += loan.remainingAmount
            }
        }

        // Net worth: Liquid balances + Savings + Loan Given (receivable) - Loan Taken (payable)
        val netWorth = available + savingsBal + loanGivenTotal - loanTakenTotal

        return FinancialSummary(
            totalIncome = income,
            totalExpense = expense,
            availableBalance = available,
            cashBalance = cashBal,
            bankBalance = bankBal,
            mobileWalletBalance = mobileBal,
            totalSavings = savingsBal,
            loanGivenReceivable = loanGivenTotal,
            loanTakenPayable = loanTakenTotal,
            netWorth = netWorth
        )
    }

    /**
     * Formats amount in Bangladeshi Taka style: ৳ 1,00,000
     */
    fun formatBdt(amount: Double): String {
        val isNegative = amount < 0
        val absAmount = kotlin.math.abs(amount)
        val longVal = absAmount.toLong()
        val decimalVal = ((absAmount - longVal) * 100).toInt()

        val formattedInt = formatSouthAsianNumber(longVal)
        val result = if (decimalVal > 0) {
            "$formattedInt.${decimalVal.toString().padStart(2, '0')}"
        } else {
            formattedInt
        }

        return if (isNegative) "-৳$result" else "৳$result"
    }

    private fun formatSouthAsianNumber(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.substring(s.length - 3)
        var remaining = s.substring(0, s.length - 3)
        val parts = mutableListOf<String>()
        while (remaining.length > 2) {
            parts.add(0, remaining.substring(remaining.length - 2))
            remaining = remaining.substring(0, remaining.length - 2)
        }
        if (remaining.isNotEmpty()) {
            parts.add(0, remaining)
        }
        return parts.joinToString(",") + ",$last3"
    }

    fun formatDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = parser.parse(dateStr)
            if (d != null) {
                SimpleDateFormat("dd MMM yyyy", Locale.US).format(d)
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * Recomputes remainingAmount and status when a user edits a Loan's principal or paidAmount.
     */
    fun normalizeEditedLoan(loan: Loan): Loan {
        val safePrincipal = loan.principal.coerceAtLeast(0.0)
        val safePaid = loan.paidAmount.coerceIn(0.0, safePrincipal)
        val remaining = (safePrincipal - safePaid).coerceAtLeast(0.0)
        val newStatus = when {
            remaining <= 0.0 -> "COMPLETED"
            safePaid > 0.0 -> "PARTIAL"
            else -> "PENDING"
        }
        return loan.copy(
            principal = safePrincipal,
            paidAmount = safePaid,
            remainingAmount = remaining,
            status = newStatus
        )
    }

    /**
     * Adjusts currentBalance proportionally when openingBalance is corrected.
     */
    fun computeAdjustedBalanceOnOpeningChange(
        oldCurrentBalance: Double,
        oldOpeningBalance: Double,
        newOpeningBalance: Double
    ): Double {
        return oldCurrentBalance + (newOpeningBalance - oldOpeningBalance)
    }
}
