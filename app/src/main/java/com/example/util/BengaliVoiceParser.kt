package com.example.util

import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Person
import com.example.data.repository.ParsedVoiceTransaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Deterministic Bengali / Banglish / English Personal Finance Voice & Text Parser.
 * Used for offline fallback, instant direct editing, and unit-testable parsing without network dependency.
 */
object BengaliVoiceParser {

    private val BENGALI_DIGIT_MAP = mapOf(
        '০' to '0',
        '১' to '1',
        '২' to '2',
        '৩' to '3',
        '৪' to '4',
        '৫' to '5',
        '৬' to '6',
        '৭' to '7',
        '৮' to '8',
        '৯' to '9'
    )

    private val BENGALI_WORD_NUMBERS = mapOf(
        "এক" to 1.0,
        "দুই" to 2.0,
        "তিন" to 3.0,
        "চার" to 4.0,
        "পাঁচ" to 5.0,
        "পান্চ" to 5.0,
        "ছয়" to 6.0,
        "ছয়" to 6.0,
        "সাত" to 7.0,
        "আট" to 8.0,
        "নয়" to 9.0,
        "নয়" to 9.0,
        "দশ" to 10.0,
        "এগারো" to 11.0,
        "বারো" to 12.0,
        "তেরো" to 13.0,
        "চৌদ্দ" to 14.0,
        "পনেরো" to 15.0,
        "ষোল" to 16.0,
        "সতেরো" to 17.0,
        "আঠারো" to 18.0,
        "উনিশ" to 19.0,
        "বিশ" to 20.0,
        "পঁচিশ" to 25.0,
        "ত্রিশ" to 30.0,
        "চল্লিশ" to 40.0,
        "পঞ্চাশ" to 50.0,
        "ষাট" to 60.0,
        "সত্তর" to 70.0,
        "আশি" to 80.0,
        "নব্বই" to 90.0,
        "একশ" to 100.0,
        "একশো" to 100.0,
        "পাঁচশ" to 500.0,
        "পাঁচশো" to 500.0,
        "দেড়" to 1.5,
        "দেড়" to 1.5,
        "আড়াই" to 2.5,
        "আড়াই" to 2.5
    )

    fun normalizeBengaliDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(BENGALI_DIGIT_MAP[ch] ?: ch)
        }
        return sb.toString()
    }

    fun extractBengaliAmount(rawText: String): Double {
        val normalized = normalizeBengaliDigits(rawText).replace(",", "")

        // 1. Numeric + Bengali/Banglish scale multiplier (e.g. "5 হাজার", "1.5 লাখ", "12 শত")
        val scaledNumericRegex = Regex(
            """(\d+(?:\.\d+)?)\s*(হাজার|hajar|k|শত|শো|shoto|লাখ|লক্ষ|lakh)\b""",
            RegexOption.IGNORE_CASE
        )
        scaledNumericRegex.find(normalized)?.let { match ->
            val base = match.groupValues[1].toDoubleOrNull()
            val unit = match.groupValues[2].lowercase(Locale.US)
            if (base != null && base > 0) {
                return when (unit) {
                    "হাজার", "hajar", "k" -> (base * 1000.0).roundToInt().toDouble()
                    "শত", "শো", "shoto" -> (base * 100.0).roundToInt().toDouble()
                    "লাখ", "লক্ষ", "lakh" -> (base * 100_000.0).roundToInt().toDouble()
                    else -> base
                }
            }
        }

        // 2. Numbers immediately adjacent to currency keywords
        val currencyRegex = Regex(
            """(\d+(?:\.\d+)?)\s*(?:টাকা|taka|tk|bdt|৳)|(?:৳|taka|tk)\s*(\d+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        currencyRegex.find(normalized)?.let { match ->
            val rawNum = match.groupValues[1].ifBlank { match.groupValues[2] }
            val value = rawNum.toDoubleOrNull()
            if (value != null && value > 0) {
                return value
            }
        }

        // 3. Spoken Bengali number words with multipliers ("পাঁচ হাজার", "বারো শত", "দেড় হাজার")
        val tokens = normalized.trim().split(Regex("""\s+"""))
        for (i in tokens.indices) {
            val word = tokens[i].trim('.', ',', '!', '?', '।')
            val next = tokens.getOrNull(i + 1)?.trim('.', ',', '!', '?', '।').orEmpty()
            val wordVal = BENGALI_WORD_NUMBERS[word]
            if (wordVal != null) {
                return when (next) {
                    "হাজার", "hajar" -> (wordVal * 1000.0).roundToInt().toDouble()
                    "শত", "শো", "শ" -> (wordVal * 100.0).roundToInt().toDouble()
                    "লাখ", "লক্ষ" -> (wordVal * 100_000.0).roundToInt().toDouble()
                    else -> wordVal
                }
            }
        }

        // 4. Fallback: any standalone number
        val anyNumRegex = Regex("""\b(\d+(?:\.\d+)?)\b""")
        anyNumRegex.find(normalized)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0) return value
        }

        return 0.0
    }

    private fun shiftIsoDate(todayIso: String, deltaDays: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsedDate = runCatching { sdf.parse(todayIso) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply {
            time = parsedDate
            add(Calendar.DAY_OF_MONTH, deltaDays)
        }
        return sdf.format(cal.time)
    }

    fun parse(
        text: String,
        accounts: List<Account> = emptyList(),
        categories: List<Category> = emptyList(),
        people: List<Person> = emptyList(),
        todayIso: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ): ParsedVoiceTransaction {
        val cleanText = text.trim()
        val lower = normalizeBengaliDigits(cleanText).lowercase(Locale.US)

        // 1. Date
        val date = when {
            Regex("""গত\s*পরশু|day before yesterday""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ->
                shiftIsoDate(todayIso, -2)
            Regex("""গতকাল|yesterday|কালকে""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ->
                shiftIsoDate(todayIso, -1)
            else -> todayIso
        }

        // 2. Amount
        val amount = extractBengaliAmount(cleanText)

        // 3. Transaction Type
        val type = when {
            Regex("""ধার\s*দিয়েছি|ধার\s*দিলাম|হাওলাত\s*দিয়েছি|lent|loan\s*given""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ->
                "LOAN_GIVEN"
            Regex("""ধার\s*নিয়েছি|ঋণ\s*নিয়েছি|borrowed|loan\s*taken|loan\s*received""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ->
                "LOAN_RECEIVED"
            Regex("""ট্রান্সফার|transfer|পাঠিয়েছি\s*নিজের""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ->
                "TRANSFER"
            Regex("""বেতন|স্যালারি|salary|আয়|ইনকাম|income|বোনাস|bonus|পেয়েছি|রিসিভ|received|ভাতা|freelance|ব্যবসা\s*থেকে""", RegexOption.IGNORE_CASE).containsMatchIn(lower) &&
                !Regex("""খরচ|কিনেছি|দিয়েছি|ভাড়া|বিল""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ->
                "INCOME"
            else -> "EXPENSE"
        }

        // 4. Account Matching
        val accountKeywords = listOf(
            Regex("""বিকাশ|bkash|b-kash""", RegexOption.IGNORE_CASE) to "bKash",
            Regex("""নগদ|nagad""", RegexOption.IGNORE_CASE) to "Nagad",
            Regex("""রকেট|rocket""", RegexOption.IGNORE_CASE) to "Rocket",
            Regex("""উপায়|upay""", RegexOption.IGNORE_CASE) to "Upay",
            Regex("""ডাচ\s*বাংলা|dbbl""", RegexOption.IGNORE_CASE) to "DBBL",
            Regex("""সিটি\s*ব্যাংক|city\s*bank""", RegexOption.IGNORE_CASE) to "City Bank",
            Regex("""ব্র্যাক|brac""", RegexOption.IGNORE_CASE) to "BRAC Bank",
            Regex("""ইসলামী\s*ব্যাংক|islami\s*bank""", RegexOption.IGNORE_CASE) to "Islami Bank",
            Regex("""ব্যাংক|bank|কার্ড|card""", RegexOption.IGNORE_CASE) to "Bank",
            Regex("""ক্যাশ|হাতে|cash""", RegexOption.IGNORE_CASE) to "Cash"
        )

        var accountName = accounts.firstOrNull()?.name ?: "Cash"
        val directAcc = accounts.find { acc ->
            lower.contains(acc.name.lowercase(Locale.US)) ||
                (!acc.provider.isNullOrBlank() && lower.contains(acc.provider.lowercase(Locale.US)))
        }
        if (directAcc != null) {
            accountName = directAcc.name
        } else {
            for ((regex, canonical) in accountKeywords) {
                if (regex.containsMatchIn(lower)) {
                    val ctxMatch = accounts.find { acc ->
                        acc.name.contains(canonical, ignoreCase = true) ||
                            acc.provider?.contains(canonical, ignoreCase = true) == true
                    }
                    accountName = ctxMatch?.name ?: canonical
                    break
                }
            }
        }

        // 5. Category Matching
        val categoryRules = listOf(
            Regex("""বাজার|খাবার|নাস্তা|লাঞ্চ|ডিনার|মাছ|মাংস|চাল|সবজি|চা|কফি|food|grocery|bazar|restaurant""", RegexOption.IGNORE_CASE) to "Food & Grocery",
            Regex("""বাসা\s*ভাড়া|বাড়ি\s*ভাড়া|রেন্ট|rent|house\s*rent""", RegexOption.IGNORE_CASE) to "House Rent",
            Regex("""রিকশা|সিএনজি|বাস|অটো|উবার|পাঠাও|গাড়ি|ভাড়া|তেল|যাতায়াত|transport|rickshaw|uber|cng""", RegexOption.IGNORE_CASE) to "Transportation",
            Regex("""বিদ্যুৎ|গ্যাস|পানি|ইন্টারনেট|ওয়াইফাই|wifi|বিল|রিচার্জ|মোবাইল\s*বিল|utility|utilities|bill""", RegexOption.IGNORE_CASE) to "Utilities",
            Regex("""ওষুধ|ডাক্তার|হাসপাতাল|মেডিকেল|চিকিৎসা|medicine|medical|doctor""", RegexOption.IGNORE_CASE) to "Medical",
            Regex("""শপিং|কাপড়|জামা|জুতা|কেনাকাটা|shopping|cloth""", RegexOption.IGNORE_CASE) to "Shopping",
            Regex("""স্কুল|কলেজ|ভার্সিটি|বই|টিউশন|পড়ালেখা|education|tuition""", RegexOption.IGNORE_CASE) to "Education",
            Regex("""বেতন|স্যালারি|salary|বোনাস""", RegexOption.IGNORE_CASE) to "Salary",
            Regex("""ব্যবসা|বিক্রি|business""", RegexOption.IGNORE_CASE) to "Business",
            Regex("""পরিবার|ফ্যামিলি|সংসার|family""", RegexOption.IGNORE_CASE) to "Family Expense"
        )

        var categoryName = if (type == "INCOME") "Salary" else "Other"
        val directCat = categories.find { cat ->
            lower.contains(cat.name.lowercase(Locale.US)) ||
                (cat.nameBn.isNotBlank() && cleanText.contains(cat.nameBn))
        }
        if (directCat != null) {
            categoryName = directCat.name
        } else {
            for ((regex, canonicalCat) in categoryRules) {
                if (regex.containsMatchIn(lower)) {
                    val ctxMatch = categories.find { cat ->
                        cat.name.contains(canonicalCat, ignoreCase = true) ||
                            cat.nameBn.contains(canonicalCat, ignoreCase = true)
                    }
                    categoryName = ctxMatch?.name ?: canonicalCat
                    break
                }
            }
        }

        // 6. Person Matching
        var personName = ""
        val directPerson = people.find { p ->
            lower.contains(p.name.lowercase(Locale.US)) ||
                (!p.relationship.isNullOrBlank() && lower.contains(p.relationship.lowercase(Locale.US)))
        }
        if (directPerson != null) {
            personName = directPerson.name
        } else {
            personName = when {
                Regex("""পরিবার|ফ্যামিলি|সংসার|family""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "Family"
                Regex("""স্ত্রী|বউ|wife""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "Wife"
                Regex("""আম্মা|মা\b|mother|mom""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "Mother"
                Regex("""আব্বা|বাবা|father|dad""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "Father"
                Regex("""ব্যক্তিগত|personal""", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> "Personal"
                else -> {
                    val friendMatch = Regex("""(?:বন্ধু|দোস্ত)\s+([^\s,।]+)""").find(cleanText)
                    friendMatch?.groupValues?.getOrNull(1)
                        ?.replace(Regex("""(?:কে|ের|র)$"""), "")
                        .orEmpty()
                }
            }
        }

        return ParsedVoiceTransaction(
            transcript = cleanText,
            type = type,
            amount = amount,
            category = categoryName,
            person = personName,
            accountName = accountName,
            description = cleanText,
            date = date
        )
    }
}
