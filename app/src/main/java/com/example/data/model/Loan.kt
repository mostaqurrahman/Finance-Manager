package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class LoanType {
    GIVEN, // Receivable (Asset)
    TAKEN  // Payable (Liability)
}

enum class LoanStatus {
    PENDING,
    PARTIAL,
    COMPLETED,
    OVERDUE
}

@IgnoreExtraProperties
data class Loan(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = LoanType.GIVEN.name,
    val partyName: String = "",
    val principal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val date: String = "",
    val dueDate: String? = null,
    val status: String = LoanStatus.PENDING.name,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LoanRepayment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val loanId: String = "",
    val amount: Double = 0.0,
    val accountId: String = "",
    val date: String = "",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
