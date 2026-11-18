package com.example.data.repository

import android.util.Log
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Person
import com.example.util.BengaliVoiceParser
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedVoiceTransaction(
    val type: String, // EXPENSE, INCOME, TRANSFER, LOAN_GIVEN, LOAN_RECEIVED
    val amount: Double,
    val category: String,
    val person: String,
    val accountName: String,
    val description: String,
    val date: String,
    val transcript: String = ""
)

sealed interface AiVoiceState {
    data object Idle : AiVoiceState
    data object Processing : AiVoiceState
    data class Success(val parsed: ParsedVoiceTransaction) : AiVoiceState
    data class Error(val message: String) : AiVoiceState
}

class AiVoiceRepository {

    private val transactionSchema = Schema.obj(
        properties = mapOf(
            "transcript" to Schema.string(description = "Exact Bengali or English spoken text recognized from audio or cleaned from text input"),
            "type" to Schema.enumeration(
                values = listOf("EXPENSE", "INCOME", "TRANSFER", "LOAN_GIVEN", "LOAN_RECEIVED"),
                description = "The type of transaction"
            ),
            "amount" to Schema.double(description = "The monetary amount in BDT / Taka as numeric value"),
            "category" to Schema.string(description = "The category e.g. Food & Grocery, House Rent, Transportation, Utilities, Medical, Shopping, Education, Family Expense, Salary, Business, Other"),
            "person" to Schema.string(description = "Associated person e.g. Family, Wife, Mother, Father, Personal, friend name, or empty string"),
            "accountName" to Schema.string(description = "The payment account mentioned like bKash, Nagad, Rocket, DBBL, City Bank, Bank, or Cash if not mentioned"),
            "description" to Schema.string(description = "Brief Bengali description of what the transaction was for"),
            "date" to Schema.string(description = "Date in YYYY-MM-DD format. Resolve আজ/today, গতকাল/yesterday relative to current date")
        ),
        optionalProperties = listOf("transcript", "person", "accountName", "description", "date")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = transactionSchema
            }
        )
    }

    private fun buildContextSnippet(
        accounts: List<Account>,
        categories: List<Category>,
        people: List<Person>
    ): String {
        val accNames = accounts.joinToString(", ") { it.name }.ifBlank { "Cash, bKash, Nagad, Bank" }
        val catNames = categories.joinToString(", ") { "${it.name}${if (it.nameBn.isNotBlank()) " (${it.nameBn})" else ""}" }
            .ifBlank { "Food & Grocery, House Rent, Transportation, Utilities, Medical, Salary, Family Expense, Other" }
        val pplNames = people.joinToString(", ") { it.name }.ifBlank { "Personal, Family, Wife" }
        return """
            User Accounts: [$accNames]
            User Categories: [$catNames]
            User People: [$pplNames]
        """.trimIndent()
    }

    suspend fun parseVoiceInput(
        bengaliText: String,
        accounts: List<Account> = emptyList(),
        categories: List<Category> = emptyList(),
        people: List<Person> = emptyList()
    ): Result<ParsedVoiceTransaction> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val contextInfo = buildContextSnippet(accounts, categories, people)
        val prompt = """
            You are an expert Bengali financial assistant for personal finance management in Bangladesh.
            Analyze the following Bengali / Banglish / English voice transcript or text and extract the financial transaction details.
            Convert Bengali numerals (০-৯) and Bengali spoken number words (e.g. পাঁচ হাজার, বারো শত, দেড় হাজার) into exact numeric amount.
            Current date: $todayStr (Asia/Dhaka). Default currency: BDT (৳).
            $contextInfo
            Set transcript to the user input text verbatim.
            User input: "$bengaliText"
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("AI model returned empty response")
            Result.success(parseJsonToDraft(jsonString, fallbackTranscript = bengaliText, todayStr = todayStr))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e("AiVoiceRepository", "AI text voice parsing timed out; using deterministic Bengali parser fallback", e)
            Result.success(BengaliVoiceParser.parse(bengaliText, accounts, categories, people, todayStr))
        } catch (e: Exception) {
            Log.e("AiVoiceRepository", "AI text voice parsing failed; using deterministic Bengali parser fallback", e)
            Result.success(BengaliVoiceParser.parse(bengaliText, accounts, categories, people, todayStr))
        }
    }

    suspend fun parseAudioInput(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        accounts: List<Account> = emptyList(),
        categories: List<Category> = emptyList(),
        people: List<Person> = emptyList()
    ): Result<ParsedVoiceTransaction> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val contextInfo = buildContextSnippet(accounts, categories, people)
        val prompt = """
            You are an expert Bengali speech recognizer and financial assistant for Bangladesh.
            Listen carefully to this spoken Bengali (or mixed Banglish/English) audio recording about a personal finance transaction.
            1. Transcribe the exact spoken words in Bengali script into "transcript".
            2. Extract amount (converting Bengali spoken numbers into numeric BDT value), type, accountName, category, person, date (relative to $todayStr), and brief Bengali description.
            $contextInfo
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(prompt)
                    }
                )
            }
            val jsonString = response.text ?: throw IllegalStateException("AI audio model returned empty response")
            val parsed = parseJsonToDraft(jsonString, fallbackTranscript = "", todayStr = todayStr)
            val transcript = parsed.transcript.ifBlank { parsed.description }
            Result.success(
                parsed.copy(
                    transcript = transcript,
                    description = parsed.description.ifBlank { transcript }
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e("AiVoiceRepository", "AI audio voice parsing timed out", e)
            Result.failure(Exception("ভয়েস অডিও বিশ্লেষণে সময় বেশি লেগেছে। আবার চেষ্টা করুন অথবা নিচে লিখে/এডিট করে যোগ করুন।"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AiVoiceRepository", "AI audio voice parsing failed", e)
            Result.failure(Exception("ভয়েস অডিও বিশ্লেষণ ব্যর্থ হয়েছে (${e.localizedMessage ?: "Network/AI error"})। অনুগ্রহ করে আবার বলুন অথবা কিবোর্ড স্পিচ/টাইপ ব্যবহার করুন।"))
        }
    }

    private fun parseJsonToDraft(
        jsonString: String,
        fallbackTranscript: String,
        todayStr: String
    ): ParsedVoiceTransaction {
        val json = JSONObject(jsonString)
        val transcript = json.optString("transcript", "").ifBlank { fallbackTranscript }
        val amount = json.optDouble("amount", 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it }
        return ParsedVoiceTransaction(
            type = json.optString("type", "EXPENSE").ifBlank { "EXPENSE" },
            amount = amount,
            category = json.optString("category", "Other").ifBlank { "Other" },
            person = json.optString("person", ""),
            accountName = json.optString("accountName", "Cash").ifBlank { "Cash" },
            description = json.optString("description", transcript).ifBlank { transcript },
            date = json.optString("date", todayStr).ifBlank { todayStr },
            transcript = transcript
        )
    }
}
