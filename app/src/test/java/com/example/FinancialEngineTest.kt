package com.example

import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Loan
import com.example.data.model.LoanType
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.util.FinancialEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialEngineTest {

    @Test
    fun testBdtFormatting() {
        assertEquals("৳1,00,000", FinancialEngine.formatBdt(100000.0))
        assertEquals("৳50,000", FinancialEngine.formatBdt(50000.0))
        assertEquals("৳12,50,000", FinancialEngine.formatBdt(1250000.0))
        assertEquals("৳500", FinancialEngine.formatBdt(500.0))
    }

    @Test
    fun testTransferScenario_doesNotAffectIncomeOrExpense() {
        // Scenario 1: Bank to bKash transfer ৳5,000
        val dbbl = Account(id = "acc1", name = "DBBL", type = AccountType.BANK.name, currentBalance = 25000.0)
        val bkash = Account(id = "acc2", name = "bKash", type = AccountType.MOBILE_BANKING.name, currentBalance = 10000.0)
        val transferTx = Transaction(
            type = TransactionType.TRANSFER.name,
            amount = 5000.0,
            accountId = "acc1",
            toAccountId = "acc2"
        )

        val summary = FinancialEngine.calculateSummary(
            accounts = listOf(dbbl, bkash),
            transactions = listOf(transferTx),
            loans = emptyList()
        )

        assertEquals(0.0, summary.totalIncome, 0.001)
        assertEquals(0.0, summary.totalExpense, 0.001)
        assertEquals(35000.0, summary.availableBalance, 0.001)
    }

    @Test
    fun testRefundScenario_reducesNetExpense() {
        // Scenario 5: Expense ৳2,000, Refund ৳500 -> Net Expense ৳1,500
        val expTx = Transaction(type = TransactionType.EXPENSE.name, amount = 2000.0, accountId = "cash")
        val refundTx = Transaction(type = TransactionType.REFUND.name, amount = 500.0, accountId = "cash")

        val summary = FinancialEngine.calculateSummary(
            accounts = listOf(Account(id = "cash", type = AccountType.CASH.name, currentBalance = 8000.0)),
            transactions = listOf(expTx, refundTx),
            loans = emptyList()
        )

        assertEquals(1500.0, summary.totalExpense, 0.001)
        assertEquals(0.0, summary.totalIncome, 0.001)
    }

    @Test
    fun testLoanReceivableAndPayable_inNetWorth() {
        // Scenario: Liquid Cash ৳10,000, Loan Given ৳5,000, Loan Taken ৳2,000
        // Net Worth = 10,000 + 5,000 - 2,000 = ৳13,000
        val cash = Account(id = "cash", type = AccountType.CASH.name, currentBalance = 10000.0)
        val loanGiven = Loan(id = "l1", type = LoanType.GIVEN.name, principal = 5000.0, remainingAmount = 5000.0)
        val loanTaken = Loan(id = "l2", type = LoanType.TAKEN.name, principal = 2000.0, remainingAmount = 2000.0)

        val summary = FinancialEngine.calculateSummary(
            accounts = listOf(cash),
            transactions = emptyList(),
            loans = listOf(loanGiven, loanTaken)
        )

        assertEquals(10000.0, summary.availableBalance, 0.001)
        assertEquals(5000.0, summary.loanGivenReceivable, 0.001)
        assertEquals(2000.0, summary.loanTakenPayable, 0.001)
        assertEquals(13000.0, summary.netWorth, 0.001)
    }

    @Test
    fun testNormalizeEditedLoan_transitionsAndClamping() {
        val base = Loan(id = "l1", type = LoanType.GIVEN.name, principal = 5000.0, paidAmount = 0.0)

        val partial = FinancialEngine.normalizeEditedLoan(base.copy(paidAmount = 1500.0))
        assertEquals(3500.0, partial.remainingAmount, 0.001)
        assertEquals("PARTIAL", partial.status)

        val completed = FinancialEngine.normalizeEditedLoan(base.copy(paidAmount = 6000.0))
        assertEquals(5000.0, completed.paidAmount, 0.001)
        assertEquals(0.0, completed.remainingAmount, 0.001)
        assertEquals("COMPLETED", completed.status)

        val pending = FinancialEngine.normalizeEditedLoan(base.copy(principal = 7000.0, paidAmount = -200.0))
        assertEquals(0.0, pending.paidAmount, 0.001)
        assertEquals(7000.0, pending.remainingAmount, 0.001)
        assertEquals("PENDING", pending.status)
    }

    @Test
    fun testComputeAdjustedBalanceOnOpeningChange() {
        val adjustedUp = FinancialEngine.computeAdjustedBalanceOnOpeningChange(
            oldCurrentBalance = 12500.0,
            oldOpeningBalance = 10000.0,
            newOpeningBalance = 15000.0
        )
        assertEquals(17500.0, adjustedUp, 0.001)

        val adjustedDown = FinancialEngine.computeAdjustedBalanceOnOpeningChange(
            oldCurrentBalance = 12500.0,
            oldOpeningBalance = 10000.0,
            newOpeningBalance = 8000.0
        )
        assertEquals(10500.0, adjustedDown, 0.001)
    }
}
