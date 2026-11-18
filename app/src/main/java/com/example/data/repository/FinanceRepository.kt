package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.Loan
import com.example.data.model.LoanRepayment
import com.example.data.model.LoanStatus
import com.example.data.model.LoanType
import com.example.data.model.Person
import com.example.data.model.SavingsGoal
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.UserProfile
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(private val db: FirebaseFirestore) {

    companion object {
        @Volatile
        private var instance: FinanceRepository? = null

        fun getInstance(context: Context): FinanceRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(dbId)
                    // Enable persistent disk cache for robust offline-first functionality
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    firestore.firestoreSettings = settings
                    FinanceRepository(firestore).also { instance = it }
                }
            }
        }
    }

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // ------------------------------------------------------------------------
    // User Profile
    // ------------------------------------------------------------------------
    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot -> snapshot.toObject(UserProfile::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to profile.displayName,
            "email" to profile.email,
            "currency" to profile.currency,
            "language" to profile.language,
            "setupCompleted" to profile.setupCompleted,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (profile.createdAt == null) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        }
        try {
            docRef.set(payload.filterValues { it != null }, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // ------------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------------
    fun observeAccounts(userId: String): Flow<List<Account>> = flow {
        val path = "users/$userId/accounts"
        emitAll(
            db.collection("users").document(userId).collection("accounts")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Account::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addAccount(account: Account): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("accounts").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to account.name,
            "type" to account.type,
            "provider" to account.provider,
            "accountNumber" to account.accountNumber,
            "openingBalance" to account.openingBalance,
            "currentBalance" to account.openingBalance,
            "currency" to account.currency,
            "isArchived" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateAccount(account: Account) {
        val uid = requireUserId()
        require(account.id.isNotBlank()) { "Account ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("accounts").document(account.id)
        val payload = mapOf(
            "name" to account.name,
            "type" to account.type,
            "provider" to account.provider,
            "accountNumber" to account.accountNumber,
            "openingBalance" to account.openingBalance,
            "currentBalance" to account.currentBalance,
            "currency" to account.currency,
            "isArchived" to account.isArchived,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAccount(accountId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("accounts").document(accountId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ------------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------------
    fun observeTransactions(userId: String): Flow<List<Transaction>> = flow {
        val path = "users/$userId/transactions"
        emitAll(
            db.collection("users").document(userId).collection("transactions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Transaction::class.java).sortedByDescending { it.date + (it.time ?: "") }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTransaction(tx: Transaction): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("transactions").document()
        val payload = mapOf(
            "userId" to uid,
            "type" to tx.type,
            "amount" to tx.amount,
            "accountId" to tx.accountId,
            "accountName" to tx.accountName,
            "toAccountId" to tx.toAccountId,
            "toAccountName" to tx.toAccountName,
            "categoryId" to tx.categoryId,
            "categoryName" to tx.categoryName,
            "personId" to tx.personId,
            "personName" to tx.personName,
            "date" to tx.date,
            "time" to tx.time,
            "description" to tx.description,
            "linkedExpenseId" to tx.linkedExpenseId,
            "isSplit" to tx.isSplit,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
            // Adjust balances based on transaction type
            adjustAccountBalancesForTransaction(uid, tx, isRevert = false)
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateTransaction(oldTx: Transaction, newTx: Transaction) {
        val uid = requireUserId()
        require(newTx.id.isNotBlank()) { "Transaction ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("transactions").document(newTx.id)
        val payload = mapOf<String, Any?>(
            "type" to newTx.type,
            "amount" to newTx.amount,
            "accountId" to newTx.accountId,
            "accountName" to newTx.accountName,
            "toAccountId" to newTx.toAccountId,
            "toAccountName" to newTx.toAccountName,
            "categoryId" to newTx.categoryId,
            "categoryName" to newTx.categoryName,
            "personId" to newTx.personId,
            "personName" to newTx.personName,
            "date" to newTx.date,
            "time" to newTx.time,
            "description" to newTx.description,
            "linkedExpenseId" to newTx.linkedExpenseId,
            "isSplit" to newTx.isSplit,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            adjustAccountBalancesForTransaction(uid, oldTx, isRevert = true)
            docRef.update(payload).await()
            adjustAccountBalancesForTransaction(uid, newTx, isRevert = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTransaction(tx: Transaction) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("transactions").document(tx.id)
        try {
            docRef.delete().await()
            adjustAccountBalancesForTransaction(uid, tx, isRevert = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private suspend fun adjustAccountBalancesForTransaction(
        uid: String,
        tx: Transaction,
        isRevert: Boolean
    ) {
        val multiplier = if (isRevert) -1.0 else 1.0
        val accountsCol = db.collection("users").document(uid).collection("accounts")

        when (tx.type) {
            TransactionType.EXPENSE.name,
            TransactionType.LOAN_GIVEN.name,
            TransactionType.LOAN_REPAYMENT_PAID.name,
            TransactionType.SAVINGS_DEPOSIT.name -> {
                // Outflow from accountId
                if (tx.accountId.isNotBlank()) {
                    accountsCol.document(tx.accountId)
                        .update("currentBalance", FieldValue.increment(-tx.amount * multiplier))
                        .await()
                }
            }
            TransactionType.INCOME.name,
            TransactionType.LOAN_RECEIVED.name,
            TransactionType.LOAN_REPAYMENT_RECEIVED.name,
            TransactionType.SAVINGS_WITHDRAWAL.name,
            TransactionType.REFUND.name -> {
                // Inflow into accountId
                if (tx.accountId.isNotBlank()) {
                    accountsCol.document(tx.accountId)
                        .update("currentBalance", FieldValue.increment(tx.amount * multiplier))
                        .await()
                }
            }
            TransactionType.TRANSFER.name -> {
                // Outflow from source, inflow to destination
                if (tx.accountId.isNotBlank()) {
                    accountsCol.document(tx.accountId)
                        .update("currentBalance", FieldValue.increment(-tx.amount * multiplier))
                        .await()
                }
                if (!tx.toAccountId.isNullOrBlank()) {
                    accountsCol.document(tx.toAccountId)
                        .update("currentBalance", FieldValue.increment(tx.amount * multiplier))
                        .await()
                }
            }
            TransactionType.ADJUSTMENT.name -> {
                // Difference adjustment handled in dedicated flow
            }
        }
    }

    // ------------------------------------------------------------------------
    // Loans & Repayments
    // ------------------------------------------------------------------------
    fun observeLoans(userId: String): Flow<List<Loan>> = flow {
        val path = "users/$userId/loans"
        emitAll(
            db.collection("users").document(userId).collection("loans")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Loan::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addLoan(loan: Loan, fundingAccountId: String): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("loans").document()
        val payload = mapOf(
            "userId" to uid,
            "type" to loan.type,
            "partyName" to loan.partyName,
            "principal" to loan.principal,
            "paidAmount" to 0.0,
            "remainingAmount" to loan.principal,
            "date" to loan.date,
            "dueDate" to loan.dueDate,
            "status" to LoanStatus.PENDING.name,
            "notes" to loan.notes,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
            // Record associated transaction
            val txType = if (loan.type == LoanType.GIVEN.name) {
                TransactionType.LOAN_GIVEN.name
            } else {
                TransactionType.LOAN_RECEIVED.name
            }
            addTransaction(
                Transaction(
                    userId = uid,
                    type = txType,
                    amount = loan.principal,
                    accountId = fundingAccountId,
                    date = loan.date,
                    description = "${if (loan.type == LoanType.GIVEN.name) "Loan to" else "Loan from"} ${loan.partyName}"
                )
            )
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateLoan(loan: Loan) {
        val uid = requireUserId()
        require(loan.id.isNotBlank()) { "Loan ID must not be blank" }
        val normalized = com.example.util.FinancialEngine.normalizeEditedLoan(loan)
        val docRef = db.collection("users").document(uid).collection("loans").document(loan.id)
        val payload = mapOf<String, Any?>(
            "type" to normalized.type,
            "partyName" to normalized.partyName,
            "principal" to normalized.principal,
            "paidAmount" to normalized.paidAmount,
            "remainingAmount" to normalized.remainingAmount,
            "date" to normalized.date,
            "dueDate" to normalized.dueDate,
            "status" to normalized.status,
            "notes" to normalized.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteLoan(loan: Loan) {
        val uid = requireUserId()
        require(loan.id.isNotBlank()) { "Loan ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("loans").document(loan.id)
        try {
            docRef.delete().await()
            // Clean up any associated repayment records for this loan
            val repayments = db.collection("users").document(uid)
                .collection("loanRepayments")
                .whereEqualTo("loanId", loan.id)
                .get()
                .await()
            for (repDoc in repayments.documents) {
                repDoc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun recordLoanRepayment(
        loan: Loan,
        amount: Double,
        accountId: String,
        date: String,
        notes: String?
    ) {
        val uid = requireUserId()
        val repaymentRef = db.collection("users").document(uid).collection("loanRepayments").document()
        val repaymentPayload = mapOf(
            "userId" to uid,
            "loanId" to loan.id,
            "amount" to amount,
            "accountId" to accountId,
            "date" to date,
            "notes" to notes,
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            repaymentRef.set(repaymentPayload).await()
            val newPaid = loan.paidAmount + amount
            val newRemaining = (loan.principal - newPaid).coerceAtLeast(0.0)
            val newStatus = if (newRemaining <= 0.0) LoanStatus.COMPLETED.name else LoanStatus.PARTIAL.name

            val loanRef = db.collection("users").document(uid).collection("loans").document(loan.id)
            loanRef.update(
                mapOf(
                    "paidAmount" to newPaid,
                    "remainingAmount" to newRemaining,
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Post transaction
            val txType = if (loan.type == LoanType.GIVEN.name) {
                TransactionType.LOAN_REPAYMENT_RECEIVED.name
            } else {
                TransactionType.LOAN_REPAYMENT_PAID.name
            }
            addTransaction(
                Transaction(
                    userId = uid,
                    type = txType,
                    amount = amount,
                    accountId = accountId,
                    date = date,
                    description = "Repayment: ${loan.partyName}"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, repaymentRef.path)
            throw e
        }
    }

    // ------------------------------------------------------------------------
    // Savings Goals
    // ------------------------------------------------------------------------
    fun observeSavingsGoals(userId: String): Flow<List<SavingsGoal>> = flow {
        val path = "users/$userId/savingsGoals"
        emitAll(
            db.collection("users").document(userId).collection("savingsGoals")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(SavingsGoal::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addSavingsGoal(goal: SavingsGoal): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("savingsGoals").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to goal.title,
            "targetAmount" to goal.targetAmount,
            "currentAmount" to goal.currentAmount,
            "targetDate" to goal.targetDate,
            "category" to goal.category,
            "status" to goal.status,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateSavingsGoal(goal: SavingsGoal) {
        val uid = requireUserId()
        require(goal.id.isNotBlank())
        val docRef = db.collection("users").document(uid).collection("savingsGoals").document(goal.id)
        val payload = mapOf(
            "title" to goal.title,
            "targetAmount" to goal.targetAmount,
            "currentAmount" to goal.currentAmount,
            "targetDate" to goal.targetDate,
            "category" to goal.category,
            "status" to if (goal.currentAmount >= goal.targetAmount) "COMPLETED" else "IN_PROGRESS",
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // ------------------------------------------------------------------------
    // Budgets
    // ------------------------------------------------------------------------
    fun observeBudgets(userId: String): Flow<List<Budget>> = flow {
        val path = "users/$userId/budgets"
        emitAll(
            db.collection("users").document(userId).collection("budgets")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Budget::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveBudget(budget: Budget): String {
        val uid = requireUserId()
        if (budget.id.isNotBlank()) {
            val docRef = db.collection("users").document(uid).collection("budgets").document(budget.id)
            val updatePayload = mapOf<String, Any?>(
                "categoryId" to budget.categoryId,
                "categoryName" to budget.categoryName,
                "month" to budget.month,
                "limitAmount" to budget.limitAmount,
                "spentAmount" to budget.spentAmount,
                "alertThreshold" to budget.alertThreshold,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updatePayload).await()
                return docRef.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }

        val docRef = db.collection("users").document(uid).collection("budgets").document()
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "categoryId" to budget.categoryId,
            "categoryName" to budget.categoryName,
            "month" to budget.month,
            "limitAmount" to budget.limitAmount,
            "spentAmount" to budget.spentAmount,
            "alertThreshold" to budget.alertThreshold,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload.filterValues { it != null }).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // ------------------------------------------------------------------------
    // Categories & People
    // ------------------------------------------------------------------------
    fun observeCategories(userId: String): Flow<List<Category>> = flow {
        val path = "users/$userId/categories"
        emitAll(
            db.collection("users").document(userId).collection("categories")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Category::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePeople(userId: String): Flow<List<Person>> = flow {
        val path = "users/$userId/people"
        emitAll(
            db.collection("users").document(userId).collection("people")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Person::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveCategory(category: Category): String {
        val uid = requireUserId()
        val cleanPrimary = category.name.trim().ifBlank { category.nameBn.trim() }.take(100)
        require(cleanPrimary.isNotBlank()) { "Category name must not be blank" }
        val cleanBn = category.nameBn.trim().ifBlank { cleanPrimary }.take(100)
        val cleanType = if (category.type.equals("INCOME", ignoreCase = true)) "INCOME" else "EXPENSE"

        if (category.id.isNotBlank()) {
            val docRef = db.collection("users").document(uid).collection("categories").document(category.id)
            val updatePayload = mutableMapOf<String, Any>(
                "name" to cleanPrimary,
                "nameBn" to cleanBn,
                "type" to cleanType,
                "isCustom" to true
            )
            category.icon.trim().takeIf { it.isNotEmpty() }?.let { updatePayload["icon"] = it.take(50) }
            category.color.trim().takeIf { it.isNotEmpty() }?.let { updatePayload["color"] = it.take(30) }
            try {
                docRef.update(updatePayload).await()
                return docRef.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }

        val docRef = db.collection("users").document(uid).collection("categories").document()
        val createPayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "name" to cleanPrimary,
            "nameBn" to cleanBn,
            "type" to cleanType,
            "isCustom" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )
        category.icon.trim().takeIf { it.isNotEmpty() }?.let { createPayload["icon"] = it.take(50) }
        category.color.trim().takeIf { it.isNotEmpty() }?.let { createPayload["color"] = it.take(30) }

        try {
            docRef.set(createPayload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCategory(categoryId: String) {
        val uid = requireUserId()
        require(categoryId.isNotBlank()) { "Category ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("categories").document(categoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun savePerson(person: Person): String {
        val uid = requireUserId()
        val cleanName = person.name.trim().take(100)
        require(cleanName.isNotBlank()) { "Person name must not be blank" }
        val cleanRelationship = person.relationship?.trim()?.takeIf { it.isNotEmpty() }?.take(50)

        if (person.id.isNotBlank()) {
            val docRef = db.collection("users").document(uid).collection("people").document(person.id)
            val updatePayload = mapOf<String, Any?>(
                "name" to cleanName,
                "relationship" to cleanRelationship
            )
            try {
                docRef.update(updatePayload).await()
                return docRef.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }

        val docRef = db.collection("users").document(uid).collection("people").document()
        val createPayload = mapOf<String, Any?>(
            "userId" to uid,
            "name" to cleanName,
            "relationship" to cleanRelationship,
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        try {
            docRef.set(createPayload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePerson(personId: String) {
        val uid = requireUserId()
        require(personId.isNotBlank()) { "Person ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("people").document(personId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun seedDefaultsIfEmpty(userId: String) {
        if (auth.currentUser?.uid != userId) return
        try {
            val categoriesCol = db.collection("users").document(userId).collection("categories")
            val existingCats = categoriesCol.limit(1).get().await()
            if (existingCats.isEmpty) {
                val defaults = listOf(
                    Triple("Food & Grocery", "বাজার ও খাবার", "EXPENSE"),
                    Triple("House Rent", "বাড়ি ভাড়া", "EXPENSE"),
                    Triple("Utilities", "বিদ্যুৎ ও গ্যাস বিল", "EXPENSE"),
                    Triple("Transportation", "যাতায়াত ও ভাড়া", "EXPENSE"),
                    Triple("Shopping & Clothing", "কেনাকাটা ও পোশাক", "EXPENSE"),
                    Triple("Medical", "চিকিৎসা ও ওষুধ", "EXPENSE"),
                    Triple("Education", "শিক্ষা খরচ", "EXPENSE"),
                    Triple("Family Expense", "পারিবারিক খরচ", "EXPENSE"),
                    Triple("Wife Expense", "স্ত্রীর খরচ", "EXPENSE"),
                    Triple("Personal Expense", "ব্যক্তিগত খরচ", "EXPENSE"),
                    Triple("Entertainment & Travel", "ভ্রমণ ও বিনোদন", "EXPENSE"),
                    Triple("Donation & Charity", "দান ও সদকা", "EXPENSE"),
                    Triple("Salary", "বেতন", "INCOME"),
                    Triple("Business Income", "ব্যবসা থেকে আয়", "INCOME"),
                    Triple("Freelancing", "ফ্রিল্যান্সিং", "INCOME"),
                    Triple("Rental Income", "ভাড়া থেকে আয়", "INCOME")
                )
                val catBatch = db.batch()
                for ((name, nameBn, type) in defaults) {
                    val ref = categoriesCol.document()
                    catBatch.set(
                        ref,
                        mapOf(
                            "userId" to userId,
                            "name" to name,
                            "nameBn" to nameBn,
                            "type" to type,
                            "isCustom" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                catBatch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Safe best-effort default seeding
        }

        try {
            val peopleCol = db.collection("users").document(userId).collection("people")
            val existingPeople = peopleCol.limit(1).get().await()
            if (existingPeople.isEmpty) {
                val defaultPeople = listOf(
                    "পরিবার (Family)" to "পরিবার",
                    "স্ত্রী (Wife)" to "স্ত্রী",
                    "নিজে (Personal)" to "ব্যক্তিগত",
                    "বাবা (Father)" to "বাবা",
                    "মা (Mother)" to "মা",
                    "বন্ধু (Friend)" to "বন্ধু",
                    "ব্যবসা / অফিস (Business)" to "ব্যবসা"
                )
                val peopleBatch = db.batch()
                for ((name, rel) in defaultPeople) {
                    val ref = peopleCol.document()
                    peopleBatch.set(
                        ref,
                        mapOf(
                            "userId" to userId,
                            "name" to name,
                            "relationship" to rel,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                peopleBatch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Safe best-effort default seeding
        }
    }
}
