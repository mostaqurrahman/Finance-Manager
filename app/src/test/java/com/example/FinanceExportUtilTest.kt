package com.example

import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.util.FinanceExportUtil
import com.example.util.FinancialEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class FinanceExportUtilTest {

    @Test
    fun csvExportContainsUtf8BomHeadersAndEscapedBanglaRows() {
        val txs = listOf(
            Transaction(
                id = "tx1",
                type = TransactionType.EXPENSE.name,
                amount = 1250.0,
                accountId = "acc1",
                accountName = "bKash",
                categoryName = "বাজার ও খাবার",
                personName = "রহিম (বন্ধু)",
                date = "2026-11-15",
                time = "09:30 AM",
                description = "সুপারশপ, চাল ও ডাল"
            )
        )

        val bytes = FinanceExportUtil.generateTransactionsCsvBytes(txs)
        // UTF-8 BOM check
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3)
        )

        val content = String(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
        assertTrue(content.contains("Date,Time,Type,Amount (BDT),Account,To Account,Category,Person,Description"))
        assertTrue(content.contains("2026-11-15,09:30 AM,EXPENSE,1250,bKash,,বাজার ও খাবার,রহিম (বন্ধু),\"সুপারশপ, চাল ও ডাল\""))
    }

    @Test
    fun xlsxExportBuildsValidOpenXmlWorkbookWithTransactionsAndSummarySheets() {
        val acc = Account(id = "acc1", name = "Cash Wallet", type = AccountType.CASH.name, currentBalance = 5000.0)
        val txs = listOf(
            Transaction(
                id = "tx1",
                type = TransactionType.INCOME.name,
                amount = 10000.0,
                accountId = "acc1",
                accountName = "Cash Wallet",
                categoryName = "বেতন",
                date = "2026-11-15",
                description = "Monthly Salary & Bonus"
            )
        )
        val summary = FinancialEngine.calculateSummary(listOf(acc), txs, emptyList())

        val xlsxBytes = FinanceExportUtil.generateTransactionsXlsxBytes(txs, listOf(acc), summary)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(StandardCharsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/styles.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))

        val sheet1 = entries["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet1.contains("Monthly Salary &amp; Bonus"))
        assertTrue(sheet1.contains("বেতন"))
        assertTrue(sheet1.contains("<v>10000</v>"))

        val sheet2 = entries["xl/worksheets/sheet2.xml"]!!
        assertTrue(sheet2.contains("Total Income"))
        assertTrue(sheet2.contains("Cash Wallet"))
    }
}
