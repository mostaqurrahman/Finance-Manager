package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class SavingsGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val targetDate: String? = null,
    val category: String? = null,
    val status: String = "IN_PROGRESS",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Budget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val month: String = "",
    val limitAmount: Double = 0.0,
    val spentAmount: Double = 0.0,
    val alertThreshold: Double = 80.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val nameBn: String = "",
    val type: String = "EXPENSE",
    val icon: String = "category",
    val color: String = "#006C4C",
    val isCustom: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Person(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val relationship: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
