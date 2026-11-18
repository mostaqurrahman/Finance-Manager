package com.example

import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Category
import com.example.data.model.Loan
import com.example.data.model.LoanType
import com.example.data.model.Person
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.util.FinanceExportUtil
import com.example.util.ReportPeriodType
import com.example.util.UnifiedReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class UnifiedReportBuilderTest {

    private fun sampleData(): Triple<List<Account>, List<Transaction>, List<Loan>> {
        // Live balances reflect all transactions up through 2026-11:
        // Cash: 10000 initial + 20000 (Oct income) - 5000 (Nov transfer) = 25000
        // bKash: 2000 initial + 5000 (Nov transfer) - 3000 (Nov expense) + 500 (Nov refund) = 4500
        val accounts = listOf(
            Account(
                id = "cash",
                name = "Personal Cash",
                type = AccountType.CASH.name,
                openingBalance = 10000.0,
                currentBalance = 25000.0
            ),
            Account(
                id = "bkash",
                name = "bKash Wallet",
                type = AccountType.MOBILE_BANKING.name,
                openingBalance = 2000.0,
                currentBalance = 4500.0
            )
        )

        val txs = listOf(
            Transaction(
                id = "tx-oct",
                type = TransactionType.INCOME.name,
                amount = 20000.0,
                accountId = "cash",
                accountName = "Personal Cash",
                categoryId = "cat-salary",
                categoryName = "বেতন",
                date = "2026-10-05",
                description = "October Salary"
            ),
            Transaction(
                id = "tx-nov-transfer",
                type = TransactionType.TRANSFER.name,
                amount = 5000.0,
                accountId = "cash",
                accountName = "Personal Cash",
                toAccountId = "bkash",
                toAccountName = "bKash Wallet",
                date = "2026-11-02",
                description = "Cash in to bKash"
            ),
            Transaction(
                id = "tx-nov-exp",
                type = TransactionType.EXPENSE.name,
                amount = 3000.0,
                accountId = "bkash",
                accountName = "bKash Wallet",
                categoryId = "cat-food",
                categoryName = "খাবার ও বাজার",
                personId = "p-1",
                personName = "রহিম",
                date = "2026-11-05",
                description = "Family groceries"
            ),
            Transaction(
                id = "tx-nov-ref",
                type = TransactionType.REFUND.name,
                amount = 500.0,
                accountId = "bkash",
                accountName = "bKash Wallet",
                date = "2026-11-06",
                description = "Refund from grocer"
            )
        )

        val loans = listOf(
            Loan(
                id = "loan-1",
                type = LoanType.GIVEN.name,
                partyName = "করিম",
                principal = 4000.0,
                paidAmount = 1000.0,
                remainingAmount = 3000.0,
                date = "2026-11-01",
                dueDate = "2026-12-31",
                status = "PARTIAL"
            )
        )

        return Triple(accounts, txs, loans)
    }

    @Test
    fun dynamicPeriodsExcludeEmptyMonthsAndSortNewestFirst() {
        val (_, txs, _) = sampleData()

        val monthly = UnifiedReportBuilder.getAvailablePeriods(txs, ReportPeriodType.MONTHLY)
        assertEquals(2, monthly.size)
        assertEquals("2026-11", monthly[0].key)
        assertEquals("November 2026", monthly[0].labelEn)
        assertEquals(3, monthly[0].txCount)
        assertEquals("2026-10", monthly[1].key)
        assertEquals("October 2026", monthly[1].labelEn)
        assertEquals(1, monthly[1].txCount)

        val yearly = UnifiedReportBuilder.getAvailablePeriods(txs, ReportPeriodType.YEARLY)
        assertEquals(1, yearly.size)
        assertEquals("2026", yearly[0].key)
        assertEquals(4, yearly[0].txCount)
    }

    @Test
    fun unifiedFileNamingMatchesHisabKhataSpec() {
        assertEquals(
            "HisabKhata_Monthly_2026-11.pdf",
            UnifiedReportBuilder.buildFileName(ReportPeriodType.MONTHLY, "2026-11", "pdf")
        )
        assertEquals(
            "HisabKhata_Monthly_2026-11.xlsx",
            UnifiedReportBuilder.buildFileName(ReportPeriodType.MONTHLY, "2026-11", "xlsx")
        )
        assertEquals(
            "HisabKhata_Yearly_2026.pdf",
            UnifiedReportBuilder.buildFileName(ReportPeriodType.YEARLY, "2026", "pdf")
        )
        assertEquals(
            "HisabKhata_Yearly_2026.xlsx",
            UnifiedReportBuilder.buildFileName(ReportPeriodType.YEARLY, "2026", "xlsx")
        )
    }

    @Test
    fun reportComputesPrePeriodOpeningBalancesTransfersRefundsAndAllExcelSheets() {
        val (accounts, txs, loans) = sampleData()
        val categories = listOf(
            Category(id = "cat-salary", name = "Salary", nameBn = "বেতন", type = "INCOME"),
            Category(id = "cat-food", name = "Food", nameBn = "খাবার ও বাজার", type = "EXPENSE")
        )
        val people = listOf(
            Person(id = "p-1", name = "রহিম", relationship = "Brother")
        )

        val report = UnifiedReportBuilder.buildReport(
            periodType = ReportPeriodType.MONTHLY,
            periodKey = "2026-11",
            accounts = accounts,
            transactions = txs,
            loans = loans,
            categories = categories,
            people = people
        )

        // Transfers do NOT inflate Income or Expense; Refund deducts Net Expense
        assertEquals(0.0, report.periodIncome, 0.01)
        assertEquals(3000.0, report.periodGrossExpense, 0.01)
        assertEquals(500.0, report.periodRefunds, 0.01)
        assertEquals(2500.0, report.periodNetExpense, 0.01)
        assertEquals(1, report.transferCount)

        // Account Opening & Closing balances
        val cashSummary = report.accountSummaries.first { it.accountId == "cash" }
        assertEquals(30000.0, cashSummary.periodOpeningBalance, 0.01)
        assertEquals(5000.0, cashSummary.moneyOut, 0.01)
        assertEquals(25000.0, cashSummary.periodClosingBalance, 0.01)

        val bkashSummary = report.accountSummaries.first { it.accountId == "bkash" }
        assertEquals(2000.0, bkashSummary.periodOpeningBalance, 0.01)
        assertEquals(5500.0, bkashSummary.moneyIn, 0.01)
        assertEquals(3000.0, bkashSummary.moneyOut, 0.01)
        assertEquals(4500.0, bkashSummary.periodClosingBalance, 0.01)

        // Verify XLSX workbook contains all 12 core sheets + 2 per-account statement sheets
        val xlsxBytes = FinanceExportUtil.generateUnifiedReportXlsxBytes(report)
        val zipEntries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries[entry.name] = zis.readBytes().toString(StandardCharsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val workbookXml = zipEntries["xl/workbook.xml"]!!
        listOf(
            "Executive Summary",
            "Overall Financial Summary",
            "Account Summary",
            "All Transactions",
            "Monthly Summary",
            "Person-wise Summary",
            "Person-wise Breakdown",
            "Category-wise Summary",
            "Category-wise Breakdown",
            "Loan Summary",
            "Savings &amp; Assets",
            "Liabilities",
            "Stmt - Personal Cash",
            "Stmt - bKash Wallet"
        ).forEach { expectedSheet ->
            assertTrue("Missing sheet: $expectedSheet", workbookXml.contains(expectedSheet))
        }
    }
}
