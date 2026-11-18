package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    LOAN_GIVEN,
    LOAN_RECEIVED,
    LOAN_REPAYMENT_RECEIVED,
    LOAN_REPAYMENT_PAID,
    SAVINGS_DEPOSIT,
    SAVINGS_WITHDRAWAL,
    REFUND,
    ADJUSTMENT
}

@IgnoreExtraProperties
data class Transaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val amount: Double = 0.0,
    val accountId: String = "",
    val accountName: String? = null,
    val toAccountId: String? = null,
    val toAccountName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val personId: String? = null,
    val personName: String? = null,
    val date: String = "",
    val time: String? = null,
    val description: String? = null,
    val linkedExpenseId: String? = null,
    val isSplit: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
