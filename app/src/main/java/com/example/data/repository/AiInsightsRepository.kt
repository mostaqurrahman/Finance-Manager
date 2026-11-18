package com.example.data.repository

import android.util.Log
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.roundToInt

private const val TAG = "AiInsightsRepository"

data class SpendingPatternItem(
    val title: String,
    val detail: String,
    val trend: String // UP, DOWN, STABLE
)

data class SavingsOpportunityItem(
    val categoryName: String,
    val currentSpend: Double,
    val estimatedMonthlySavings: Double,
    val actionableTip: String,
    val priority: String // HIGH, MEDIUM, LOW
)

data class AiSpendingInsights(
    val headline: String,
    val spendingSummary: String,
    val patterns: List<SpendingPatternItem>,
    val savingsOpportunities: List<SavingsOpportunityItem>,
    val savingsPotentialTotal: Double
)

sealed interface AiInsightsUiState {
    data object Idle : AiInsightsUiState
    data object Loading : AiInsightsUiState
    data class Success(val insights: AiSpendingInsights) : AiInsightsUiState
    data class Error(val message: String) : AiInsightsUiState
}

data class CategorySpendDigest(
    val categoryName: String,
    val amount: Double,
    val txCount: Int,
    val sharePercent: Double
)

data class SpendingDigest(
    val periodLabel: String,
    val isBangla: Boolean,
    val totalIncome: Double,
    val totalExpense: Double,
    val netCashFlow: Double,
    val savingsRatePercent: Double,
    val txCount: Int,
    val categoryBreakdown: List<CategorySpendDigest>
)

class AiInsightsRepository {

    private val insightsSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(description = "Short 1-line headline summarizing spending behavior"),
            "spendingSummary" to Schema.string(description = "2-3 sentence summary of spending patterns and cash-flow health"),
            "patterns" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Short pattern title"),
                        "detail" to Schema.string(description = "Observation citing real figures from input"),
                        "trend" to Schema.enumeration(
                            values = listOf("UP", "DOWN", "STABLE"),
                            description = "Direction of spending trend"
                        )
                    )
                ),
                description = "2 to 4 observed spending patterns"
            ),
            "savingsOpportunities" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "categoryName" to Schema.string(description = "Category name from digest where spending can be trimmed"),
                        "currentSpend" to Schema.double(description = "Current period spend in BDT"),
                        "estimatedMonthlySavings" to Schema.double(description = "Realistic monthly savings potential in BDT"),
                        "actionableTip" to Schema.string(description = "Concrete practical saving suggestion"),
                        "priority" to Schema.enumeration(
                            values = listOf("HIGH", "MEDIUM", "LOW"),
                            description = "Savings priority level"
                        )
                    )
                ),
                description = "2 to 4 categories with potential savings"
            ),
            "savingsPotentialTotal" to Schema.double(description = "Sum of estimatedMonthlySavings in BDT")
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = insightsSchema
            }
        )
    }

    suspend fun generateInsights(
        transactions: List<Transaction>,
        categories: List<Category>,
        budgets: List<Budget>,
        periodLabel: String,
        isBangla: Boolean
    ): Result<AiSpendingInsights> {
        val digest = buildSpendingDigest(
            transactions = transactions,
            categories = categories,
            periodLabel = periodLabel,
            isBangla = isBangla
        )
        if (digest.totalIncome <= 0.0 && digest.totalExpense <= 0.0) {
            return Result.failure(
                IllegalStateException(
                    if (isBangla) {
                        "বিশ্লেষণের জন্য এই সময়কালে অন্তত একটি আয় বা ব্যয়ের লেনদেন থাকতে হবে।"
                    } else {
                        "Record at least one income or expense transaction in this period to analyze."
                    }
                )
            )
        }

        val categoryJson = digest.categoryBreakdown.joinToString(", ") {
            """{"category":"${it.categoryName}","amount":${it.amount.roundToInt()},"count":${it.txCount},"sharePercent":${it.sharePercent}}"""
        }
        val budgetSnippet = budgets.joinToString(", ") { b ->
            "${b.categoryName}: limit ৳${b.limitAmount.roundToInt()}"
        }.ifBlank { "None configured" }

        val prompt = """
            You are an expert personal finance advisor in Bangladesh (currency BDT ৳).
            Analyze the user's real financial metrics for "$periodLabel" and return concise, actionable insights.
            Respond in ${if (isBangla) "natural, clear Bengali (বাংলা)" else "clear, concise English"}.
            Only cite numbers and category names present below:
            - Total Income: ৳${digest.totalIncome.roundToInt()}
            - Net Expense (after refunds): ৳${digest.totalExpense.roundToInt()}
            - Net Cash Flow: ৳${digest.netCashFlow.roundToInt()}
            - Savings Rate: ${digest.savingsRatePercent}%
            - Transactions Count: ${digest.txCount}
            - Category Expenses: [$categoryJson]
            - Active Budgets: [$budgetSnippet]

            Identify 2-4 clear spending patterns and 2-4 potential savings categories with realistic monthly BDT savings estimates (5%-25% of current spend).
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("AI model returned empty response.")
            Result.success(parseInsightsJson(jsonString))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI insights generation timed out", e)
            Result.failure(
                Exception(
                    if (isBangla) "AI বিশ্লেষণে সময় বেশি লেগেছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
                    else "AI insights generation timed out after 60 seconds. Please try again."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI insights generation failed", e)
            Result.failure(e)
        }
    }

    companion object {
        fun buildSpendingDigest(
            transactions: List<Transaction>,
            categories: List<Category>,
            periodLabel: String,
            isBangla: Boolean
        ): SpendingDigest {
            val catLookup = categories.associateBy { it.id }
            var totalIncome = 0.0
            var grossExpense = 0.0
            var totalRefunds = 0.0

            val catBuckets = linkedMapOf<String, Pair<Double, Int>>()

            for (tx in transactions) {
                when (tx.type) {
                    TransactionType.INCOME.name -> totalIncome += tx.amount
                    TransactionType.REFUND.name -> totalRefunds += tx.amount
                    TransactionType.EXPENSE.name -> {
                        grossExpense += tx.amount
                        val catObj = tx.categoryId?.let { catLookup[it] }
                        val catName = tx.categoryName?.takeIf { it.isNotBlank() }
                            ?: (if (isBangla) catObj?.nameBn?.takeIf { it.isNotBlank() } else catObj?.name?.takeIf { it.isNotBlank() })
                            ?: catObj?.name
                            ?: if (isBangla) "অন্যান্য" else "Other"
                        val prev = catBuckets[catName] ?: (0.0 to 0)
                        catBuckets[catName] = (prev.first + tx.amount) to (prev.second + 1)
                    }
                }
            }

            val netExpense = (grossExpense - totalRefunds).coerceAtLeast(0.0)
            val netCashFlow = totalIncome - netExpense
            val savingsRate = if (totalIncome > 0.0) {
                ((netCashFlow / totalIncome) * 1000.0).roundToInt() / 10.0
            } else {
                0.0
            }

            val breakdown = catBuckets.entries
                .map { (name, pair) ->
                    val share = if (grossExpense > 0.0) {
                        ((pair.first / grossExpense) * 1000.0).roundToInt() / 10.0
                    } else {
                        0.0
                    }
                    CategorySpendDigest(
                        categoryName = name,
                        amount = pair.first,
                        txCount = pair.second,
                        sharePercent = share
                    )
                }
                .sortedByDescending { it.amount }

            return SpendingDigest(
                periodLabel = periodLabel,
                isBangla = isBangla,
                totalIncome = totalIncome,
                totalExpense = netExpense,
                netCashFlow = netCashFlow,
                savingsRatePercent = savingsRate,
                txCount = transactions.size,
                categoryBreakdown = breakdown
            )
        }

        fun parseInsightsJson(jsonString: String): AiSpendingInsights {
            val json = JSONObject(jsonString)
            val patternsArr = json.optJSONArray("patterns")
            val patterns = buildList {
                if (patternsArr != null) {
                    for (i in 0 until patternsArr.length()) {
                        val obj = patternsArr.optJSONObject(i) ?: continue
                        add(
                            SpendingPatternItem(
                                title = obj.optString("title", ""),
                                detail = obj.optString("detail", ""),
                                trend = obj.optString("trend", "STABLE").uppercase()
                            )
                        )
                    }
                }
            }

            val oppArr = json.optJSONArray("savingsOpportunities")
            val opportunities = buildList {
                if (oppArr != null) {
                    for (i in 0 until oppArr.length()) {
                        val obj = oppArr.optJSONObject(i) ?: continue
                        add(
                            SavingsOpportunityItem(
                                categoryName = obj.optString("categoryName", "General"),
                                currentSpend = obj.optDouble("currentSpend", 0.0).coerceAtLeast(0.0),
                                estimatedMonthlySavings = obj.optDouble("estimatedMonthlySavings", 0.0).coerceAtLeast(0.0),
                                actionableTip = obj.optString("actionableTip", ""),
                                priority = obj.optString("priority", "MEDIUM").uppercase()
                            )
                        )
                    }
                }
            }

            val totalFromField = json.optDouble("savingsPotentialTotal", 0.0)
            val totalSavings = if (totalFromField > 0.0 && !totalFromField.isNaN()) {
                totalFromField
            } else {
                opportunities.sumOf { it.estimatedMonthlySavings }
            }

            return AiSpendingInsights(
                headline = json.optString("headline", ""),
                spendingSummary = json.optString("spendingSummary", ""),
                patterns = patterns,
                savingsOpportunities = opportunities,
                savingsPotentialTotal = totalSavings
            )
        }
    }
}
