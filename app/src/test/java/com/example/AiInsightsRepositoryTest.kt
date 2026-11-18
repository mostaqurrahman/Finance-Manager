package com.example

import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.repository.AiInsightsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiInsightsRepositoryTest {

    @Test
    fun `buildSpendingDigest excludes transfers and deducts refunds from net expense`() {
        val categories = listOf(
            Category(id = "cat-food", name = "Food & Dining", nameBn = "খাবার"),
            Category(id = "cat-transport", name = "Transport", nameBn = "যাতায়াত")
        )
        val transactions = listOf(
            Transaction(
                id = "t1",
                type = TransactionType.INCOME.name,
                amount = 50000.0,
                accountId = "acc-bank",
                date = "2026-11-01"
            ),
            Transaction(
                id = "t2",
                type = TransactionType.EXPENSE.name,
                amount = 6000.0,
                accountId = "acc-cash",
                categoryId = "cat-food",
                date = "2026-11-03"
            ),
            Transaction(
                id = "t3",
                type = TransactionType.EXPENSE.name,
                amount = 2000.0,
                accountId = "acc-cash",
                categoryId = "cat-transport",
                date = "2026-11-05"
            ),
            Transaction(
                id = "t4",
                type = TransactionType.REFUND.name,
                amount = 500.0,
                accountId = "acc-cash",
                date = "2026-11-06"
            ),
            Transaction(
                id = "t5",
                type = TransactionType.TRANSFER.name,
                amount = 15000.0,
                accountId = "acc-bank",
                toAccountId = "acc-cash",
                date = "2026-11-07"
            )
        )

        val digest = AiInsightsRepository.buildSpendingDigest(
            transactions = transactions,
            categories = categories,
            periodLabel = "This Month",
            isBangla = false
        )

        assertEquals(50000.0, digest.totalIncome, 0.001)
        assertEquals(7500.0, digest.totalExpense, 0.001)
        assertEquals(42500.0, digest.netCashFlow, 0.001)
        assertEquals(85.0, digest.savingsRatePercent, 0.001)
        assertEquals(5, digest.txCount)
        assertEquals(2, digest.categoryBreakdown.size)
        assertEquals("Food & Dining", digest.categoryBreakdown[0].categoryName)
        assertEquals(6000.0, digest.categoryBreakdown[0].amount, 0.001)
        assertEquals(75.0, digest.categoryBreakdown[0].sharePercent, 0.001)
        assertEquals("Transport", digest.categoryBreakdown[1].categoryName)
        assertEquals(2000.0, digest.categoryBreakdown[1].amount, 0.001)
        assertEquals(25.0, digest.categoryBreakdown[1].sharePercent, 0.001)
    }

    @Test
    fun `parseInsightsJson parses patterns savings opportunities and falls back for total savings`() {
        val rawJson = """
            {
              "headline": "Dining drives 60% of monthly expenses",
              "spendingSummary": "Net cash flow remains healthy with room to optimize restaurant orders.",
              "patterns": [
                {
                  "title": "High Food Spend",
                  "detail": "৳6,000 spent on Food & Dining across 8 orders.",
                  "trend": "up"
                },
                {
                  "title": "Consistent Commute",
                  "detail": "Transport remained steady at ৳2,000.",
                  "trend": "stable"
                }
              ],
              "savingsOpportunities": [
                {
                  "categoryName": "Food & Dining",
                  "currentSpend": 6000,
                  "estimatedMonthlySavings": 1200,
                  "actionableTip": "Cook dinner at home 3 nights a week.",
                  "priority": "high"
                },
                {
                  "categoryName": "Transport",
                  "currentSpend": 2000,
                  "estimatedMonthlySavings": 300,
                  "actionableTip": "Use metro rail passes for regular commutes.",
                  "priority": "low"
                }
              ],
              "savingsPotentialTotal": 0
            }
        """.trimIndent()

        val parsed = AiInsightsRepository.parseInsightsJson(rawJson)

        assertEquals("Dining drives 60% of monthly expenses", parsed.headline)
        assertEquals(2, parsed.patterns.size)
        assertEquals("UP", parsed.patterns[0].trend)
        assertEquals("STABLE", parsed.patterns[1].trend)
        assertEquals(2, parsed.savingsOpportunities.size)
        assertEquals("Food & Dining", parsed.savingsOpportunities[0].categoryName)
        assertEquals("HIGH", parsed.savingsOpportunities[0].priority)
        assertEquals(1200.0, parsed.savingsOpportunities[0].estimatedMonthlySavings, 0.001)
        assertTrue(parsed.savingsPotentialTotal == 1500.0)
    }
}
