package com.example.util

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException

enum class OperationType {
    GET,
    LIST,
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Structured Firestore error handler logging diagnostic metadata.
 */
fun handleFirestoreError(e: Exception, operation: OperationType, path: String) {
    val code = if (e is FirebaseFirestoreException) e.code.name else "UNKNOWN"
    val message = e.message ?: "No error message"
    Log.e(
        "FirestoreError",
        "Firestore $operation error at [$path]: code=$code, message=$message",
        e
    )
}
