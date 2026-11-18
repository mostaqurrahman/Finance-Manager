package com.example.ui.setup

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.UserProfile
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.launch

@Composable
fun SetupWizardScreen(
    userId: String,
    repository: FinanceRepository,
    initialProfile: UserProfile?,
    onSetupComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(1) }
    var displayName by remember { mutableStateOf(initialProfile?.displayName ?: "User") }
    var cashBalance by remember { mutableStateOf("0") }
    var bankName by remember { mutableStateOf("DBBL") }
    var bankBalance by remember { mutableStateOf("0") }
    var mobileWalletName by remember { mutableStateOf("bKash") }
    var mobileWalletBalance by remember { mutableStateOf("0") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Progress Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepBadge(step = 1, currentStep = currentStep, label = "প্রোফাইল")
                Spacer(modifier = Modifier.width(16.dp))
                StepBadge(step = 2, currentStep = currentStep, label = "অ্যাকাউন্ট")
                Spacer(modifier = Modifier.width(16.dp))
                StepBadge(step = 3, currentStep = currentStep, label = "সম্পন্ন")
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (currentStep) {
                1 -> {
                    Text(
                        text = "স্বাগতম হিসাব খাতায়!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "আপনার ব্যক্তিগত আর্থিক ব্যবস্থাপনার শুরুতে কিছু তথ্য সেট করুন",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("আপনার নাম") },
                        modifier = Modifier.fillMaxWidth().testTag("setup_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ডিফল্ট সেটিংস:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• মুদ্রা: বাংলাদেশী টাকা (BDT ৳)")
                            Text("• ভাষা: বাংলা (বাংলা / English)")
                            Text("• টাইমজোন: Asia/Dhaka")
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { currentStep = 2 },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("setup_next_step1"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("পরবর্তী ধাপ (অ্যাকাউন্ট সেটআপ)")
                    }
                }

                2 -> {
                    Text(
                        text = "প্রাথমিক অ্যাকাউন্ট যোগ করুন",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "আপনার কাছে এখন থাকা বর্তমান ব্যালেন্স লিখুন (ঐচ্ছিক)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )

                    // Cash Account Card
                    AccountSetupCard(
                        icon = Icons.Default.Payments,
                        title = "নগদ টাকা (Cash)",
                        subtitle = "হাতে থাকা ক্যাশ ব্যালেন্স",
                        balanceValue = cashBalance,
                        onBalanceChange = { cashBalance = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bank Account Card
                    AccountSetupCard(
                        icon = Icons.Default.AccountBalance,
                        title = "ব্যাংক অ্যাকাউন্ট",
                        subtitle = bankName,
                        balanceValue = bankBalance,
                        onBalanceChange = { bankBalance = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mobile Banking Card
                    AccountSetupCard(
                        icon = Icons.Default.PhoneAndroid,
                        title = "মোবাইল ব্যাংকিং (bKash/Nagad)",
                        subtitle = mobileWalletName,
                        balanceValue = mobileWalletBalance,
                        onBalanceChange = { mobileWalletBalance = it }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { currentStep = 1 },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("পূর্ববর্তী")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { currentStep = 3 },
                            modifier = Modifier.weight(1f).height(50.dp).testTag("setup_next_step2"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("পরবর্তী ধাপ")
                        }
                    }
                }

                3 -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Ready",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "সবকিছু প্রস্তুত!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "ডিফল্ট ক্যাটাগরি এবং পরিচিত মানুষদের তালিকা স্বয়ংক্রিয়ভাবে তৈরি হবে।",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                // 1. Save profile
                                repository.saveUserProfile(
                                    UserProfile(
                                        userId = userId,
                                        displayName = displayName,
                                        currency = "BDT",
                                        language = "bn",
                                        setupCompleted = true
                                    )
                                )

                                // 2. Create accounts if balances provided
                                val cashAmt = cashBalance.toDoubleOrNull() ?: 0.0
                                repository.addAccount(
                                    Account(
                                        userId = userId,
                                        name = "নগদ টাকা (Cash)",
                                        type = AccountType.CASH.name,
                                        provider = "Cash",
                                        openingBalance = cashAmt,
                                        currentBalance = cashAmt
                                    )
                                )

                                val bankAmt = bankBalance.toDoubleOrNull() ?: 0.0
                                repository.addAccount(
                                    Account(
                                        userId = userId,
                                        name = "$bankName Bank",
                                        type = AccountType.BANK.name,
                                        provider = bankName,
                                        openingBalance = bankAmt,
                                        currentBalance = bankAmt
                                    )
                                )

                                val mobileAmt = mobileWalletBalance.toDoubleOrNull() ?: 0.0
                                repository.addAccount(
                                    Account(
                                        userId = userId,
                                        name = mobileWalletName,
                                        type = AccountType.MOBILE_BANKING.name,
                                        provider = mobileWalletName,
                                        openingBalance = mobileAmt,
                                        currentBalance = mobileAmt
                                    )
                                )

                                // 3. Seed default categories & people
                                repository.seedDefaultsIfEmpty(userId)

                                isSaving = false
                                onSetupComplete()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("setup_finish_button"),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("হিসাব শুরু করুন (Start Now)")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                repository.saveUserProfile(
                                    UserProfile(
                                        userId = userId,
                                        displayName = displayName,
                                        setupCompleted = true
                                    )
                                )
                                repository.seedDefaultsIfEmpty(userId)
                                isSaving = false
                                onSetupComplete()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("স্কিপ করুন এবং পরে যোগ করুন")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(step: Int, currentStep: Int, label: String) {
    val isActive = step == currentStep
    val isDone = step < currentStep
    val bgColor = when {
        isDone -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isDone -> MaterialTheme.colorScheme.onPrimary
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AccountSetupCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    balanceValue: String,
    onBalanceChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = balanceValue,
                onValueChange = onBalanceChange,
                label = { Text("৳ ব্যালেন্স") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
