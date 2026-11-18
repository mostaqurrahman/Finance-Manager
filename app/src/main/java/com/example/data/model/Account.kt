package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AccountType {
    CASH,
    BANK,
    MOBILE_BANKING,
    SAVINGS,
    DPS_FDR,
    OTHER
}

@IgnoreExtraProperties
data class Account(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: String = AccountType.CASH.name,
    val provider: String? = null,
    val accountNumber: String? = null,
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val currency: String = "BDT",
    val isArchived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
