package com.example

import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Person
import com.example.util.BengaliVoiceParser
import org.junit.Assert.assertEquals
import org.junit.Test

class BengaliVoiceParserTest {

    private val accounts = listOf(
        Account(id = "acc_cash", name = "Personal Cash", provider = "Cash"),
        Account(id = "acc_bkash", name = "bKash", provider = "bKash"),
        Account(id = "acc_bank", name = "City Bank", provider = "City Bank")
    )

    private val categories = listOf(
        Category(id = "cat_food", name = "Food & Grocery", nameBn = "খাবার ও বাজার"),
        Category(id = "cat_salary", name = "Salary", nameBn = "বেতন"),
        Category(id = "cat_family", name = "Family Expense", nameBn = "পরিবার খরচ")
    )

    private val people = listOf(
        Person(id = "p_shakil", name = "শাকিল", relationship = "Friend")
    )

    @Test
    fun testDigitNormalizationAndWordAmounts() {
        assertEquals("1250 টাকা", BengaliVoiceParser.normalizeBengaliDigits("১২৫০ টাকা"))
        assertEquals(5000.0, BengaliVoiceParser.extractBengaliAmount("পাঁচ হাজার টাকা"), 0.001)
        assertEquals(1200.0, BengaliVoiceParser.extractBengaliAmount("বারো শত টাকা"), 0.001)
        assertEquals(1500.0, BengaliVoiceParser.extractBengaliAmount("দেড় হাজার টাকা"), 0.001)
    }

    @Test
    fun testExpenseWithBkashAndYesterdayDate() {
        val parsed = BengaliVoiceParser.parse(
            text = "গতকাল বাজার করার জন্য বিকাশ থেকে ১২০০ টাকা খরচ করেছি",
            accounts = accounts,
            categories = categories,
            people = people,
            todayIso = "2026-11-14"
        )
        assertEquals("EXPENSE", parsed.type)
        assertEquals(1200.0, parsed.amount, 0.001)
        assertEquals("bKash", parsed.accountName)
        assertEquals("Food & Grocery", parsed.category)
        assertEquals("2026-11-13", parsed.date)
    }

    @Test
    fun testSalaryIncomeWithBank() {
        val parsed = BengaliVoiceParser.parse(
            text = "অফিস থেকে বেতন পেয়েছি ৬০,০০০ টাকা সিটি ব্যাংক একাউন্টে",
            accounts = accounts,
            categories = categories,
            people = people,
            todayIso = "2026-11-14"
        )
        assertEquals("INCOME", parsed.type)
        assertEquals(60000.0, parsed.amount, 0.001)
        assertEquals("City Bank", parsed.accountName)
        assertEquals("Salary", parsed.category)
    }

    @Test
    fun testLoanGivenToFriend() {
        val parsed = BengaliVoiceParser.parse(
            text = "বন্ধু শাকিলকে ক্যাশ থেকে ৩০০০ টাকা ধার দিয়েছি",
            accounts = accounts,
            categories = categories,
            people = people,
            todayIso = "2026-11-14"
        )
        assertEquals("LOAN_GIVEN", parsed.type)
        assertEquals(3000.0, parsed.amount, 0.001)
        assertEquals("Personal Cash", parsed.accountName)
        assertEquals("শাকিল", parsed.person)
    }
}
