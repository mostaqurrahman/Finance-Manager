package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "AuthScreen"

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val activity = context as? Activity ?: run {
                onAuthError("Context is not an Activity")
                return@launch
            }
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential format received.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled: ${e.message}")
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checkedAutoSignIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!checkedAutoSignIn) {
            checkedAutoSignIn = true
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = { /* show interactive UI */ },
                scope = scope
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A2540),
                            Color(0xFF0F172A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Logo / Symbol
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF059669), Color(0xFF10B981))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Finance Icon",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "হিসাব খাতা",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Personal Finance Manager",
                    fontSize = 15.sp,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Feature Highlights Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FeatureRow(
                            icon = Icons.Default.Security,
                            title = "ব্যক্তিগত ও সুরক্ষিত স্পেস",
                            desc = "প্রতিটি ব্যবহারকারীর সম্পূর্ণ স্বাধীন ও এনক্রিপ্টেড হিসাব"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FeatureRow(
                            icon = Icons.Default.CloudSync,
                            title = "অটো ক্লাউড ও অফলাইন সিঙ্ক",
                            desc = "ইন্টারনেট ছাড়াও হিসাব রাখুন, অনলাইন হলে স্বয়ংক্রিয় সিঙ্ক"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FeatureRow(
                            icon = Icons.Default.Mic,
                            title = "বাংলা ভয়েস ইনপুট (Gemini AI)",
                            desc = "মুখে বাংলায় বলুন, AI স্বয়ংক্রিয়ভাবে লেনদেন তৈরি করবে"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // Google Sign In Button
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { err ->
                                isLoading = false
                                errorMessage = err
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button"),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1F2937)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF0F172A),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Google 'G' stylized representation
                            Text(
                                text = "G",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color(0xFF4285F4),
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = "Continue with Google",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Google অ্যাকাউন্ট দিয়ে প্রবেশ করুন। কোনো পাসওয়ার্ড বা অনুমোদনের প্রয়োজন নেই।",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF059669).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF34D399),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
    }
}
