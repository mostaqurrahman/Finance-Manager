package com.example.ui.more

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.Person
import com.example.data.model.SavingsGoal
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.ui.FinanceUiState
import com.example.ui.transaction.ManageCategoriesDialog
import com.example.ui.transaction.ManagePeopleDialog
import com.example.util.DailyFinanceReminderManager
import com.example.util.ExportFormat
import com.example.util.ExportOutcome
import com.example.util.FinanceExportUtil
import com.example.util.FinancialEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreFeaturesScreen(
    state: FinanceUiState,
    onToggleLanguage: () -> Unit,
    onAddBudget: (Budget) -> Unit,
    onAddSavingsGoal: (SavingsGoal) -> Unit,
    onUpdateSavingsGoal: (SavingsGoal) -> Unit,
    onSaveCategory: (Category) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onSavePerson: (Person) -> Unit = {},
    onDeletePerson: (String) -> Unit = {},
    onSignOut: () -> Unit
) {
    val isBn = state.isBanglaLanguage
    val context = LocalContext.current
    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var showManagePeopleDialog by remember { mutableStateOf(false) }
    var showUnifiedExportDialog by remember { mutableStateOf(false) }
    var lastExportOutcomes by remember { mutableStateOf<List<ExportOutcome>>(emptyList()) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    var reminderConfig by remember { mutableStateOf(DailyFinanceReminderManager.getConfig(context)) }
    var reminderStatusMsg by remember { mutableStateOf<String?>(null) }
    var pendingActionAfterPermission by remember { mutableStateOf<String?>(null) }

    val sendPreviewNow: () -> Unit = {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayTxs = state.allTransactions.filter { it.date == todayStr }
        val todayExpense = todayTxs
            .filter { it.type == TransactionType.EXPENSE.name }
            .sumOf { it.amount }
        DailyFinanceReminderManager.updateFinanceSnapshot(
            context = context,
            todayTxCount = todayTxs.size,
            todayExpense = todayExpense,
            availableBalance = state.summary.availableBalance,
            isBangla = isBn
        )
        val sent = DailyFinanceReminderManager.showStoredDailyFinanceNotification(context)
        reminderStatusMsg = if (sent) {
            if (isBn) "দৈনিক ফাইন্যান্স আপডেট নোটিফিকেশন পাঠানো হয়েছে!" else "Daily finance update notification sent!"
        } else {
            if (isBn) "নোটিফিকেশন পাঠাতে অনুমতির প্রয়োজন।" else "Notification permission is required."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingActionAfterPermission) {
                "enable" -> {
                    reminderConfig = DailyFinanceReminderManager.scheduleDailyReminder(
                        context,
                        reminderConfig.hour,
                        reminderConfig.minute
                    )
                    reminderStatusMsg = if (isBn) {
                        "প্রতিদিন ${DailyFinanceReminderManager.formatReminderTime(reminderConfig.hour, reminderConfig.minute, true)} এ রিমাইন্ডার সেট হয়েছে।"
                    } else {
                        "Daily reminder scheduled for ${DailyFinanceReminderManager.formatReminderTime(reminderConfig.hour, reminderConfig.minute, false)}."
                    }
                }
                "test" -> {
                    sendPreviewNow()
                }
            }
        } else {
            reminderConfig = DailyFinanceReminderManager.cancelDailyReminder(context)
            reminderStatusMsg = if (isBn) {
                "নোটিফিকেশন অনুমতি দেওয়া হয়নি।"
            } else {
                "Notification permission was denied."
            }
        }
        pendingActionAfterPermission = null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Language & Account Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = state.profile?.displayName ?: "User",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.profile?.email ?: "Google Account Connected",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Language Toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBn) "বাংলা" else "EN",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = isBn,
                                onCheckedChange = { onToggleLanguage() }
                            )
                        }
                    }
                }
            }
        }

        // Section: Daily Finance Update Notifications
        item {
            val timeOptions = remember {
                listOf(
                    20 to 0, // 8:00 PM
                    21 to 0, // 9:00 PM
                    22 to 0  // 10:00 PM
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = if (isBn) "দৈনিক ফাইন্যান্স আপডেট" else "Daily Finance Update",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isBn) "দৈনিক ফাইন্যান্স আপডেট" else "Daily Finance Update",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBn) {
                                        "প্রতিদিন আয়-ব্যয় ও ব্যালেন্স আপডেট রিমাইন্ডার"
                                    } else {
                                        "Regular daily income, expense & balance reminder"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = reminderConfig.enabled,
                            modifier = Modifier.testTag("daily_notification_switch"),
                            onCheckedChange = { wantEnabled ->
                                if (!wantEnabled) {
                                    reminderConfig = DailyFinanceReminderManager.cancelDailyReminder(context)
                                    reminderStatusMsg = if (isBn) "দৈনিক রিমাইন্ডার বন্ধ করা হয়েছে।" else "Daily reminder disabled."
                                } else if (DailyFinanceReminderManager.hasNotificationPermission(context)) {
                                    reminderConfig = DailyFinanceReminderManager.scheduleDailyReminder(
                                        context,
                                        reminderConfig.hour,
                                        reminderConfig.minute
                                    )
                                    reminderStatusMsg = if (isBn) {
                                        "প্রতিদিন ${DailyFinanceReminderManager.formatReminderTime(reminderConfig.hour, reminderConfig.minute, true)} এ রিমাইন্ডার চালু।"
                                    } else {
                                        "Daily reminder enabled for ${DailyFinanceReminderManager.formatReminderTime(reminderConfig.hour, reminderConfig.minute, false)}."
                                    }
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pendingActionAfterPermission = "enable"
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isBn) "সময় নির্বাচন:" else "Reminder Time:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        timeOptions.forEach { (h, m) ->
                            val isSelected = reminderConfig.hour == h && reminderConfig.minute == m
                            val label = DailyFinanceReminderManager.formatReminderTime(h, m, isBn)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (reminderConfig.enabled && DailyFinanceReminderManager.hasNotificationPermission(context)) {
                                        reminderConfig = DailyFinanceReminderManager.scheduleDailyReminder(context, h, m)
                                    } else {
                                        reminderConfig = reminderConfig.copy(hour = h, minute = m)
                                        context.getSharedPreferences("pfm_daily_reminder_prefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putInt("daily_reminder_hour", h)
                                            .putInt("daily_reminder_minute", m)
                                            .apply()
                                    }
                                    reminderStatusMsg = if (isBn) "সময় $label সেট করা হয়েছে।" else "Reminder time set to $label."
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (DailyFinanceReminderManager.hasNotificationPermission(context)) {
                                sendPreviewNow()
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pendingActionAfterPermission = "test"
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("test_daily_notification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBn) "এখনই ডেইলি আপডেট নোটিফিকেশন দেখুন" else "Send Daily Finance Update Now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    reminderStatusMsg?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section: Budgets
        item {
            SectionTitleRow(
                title = if (isBn) "মাসিক বাজেট ও নিয়ন্ত্রণ (Monthly Budgets)" else "Monthly Budgets",
                actionText = "+ বাজেট",
                onAction = { showAddBudgetDialog = true }
            )
        }

        if (state.budgets.isEmpty()) {
            item {
                EmptyStateCard(message = if (isBn) "কোনো ক্যাটাগরি বাজেট সেট করা হয়নি। '+ বাজেট' দিয়ে সীমা নির্ধারণ করুন।" else "No active category budgets set.")
            }
        } else {
            items(state.budgets.size) { idx ->
                val b = state.budgets[idx]
                val spent = state.filteredTransactions
                    .filter { it.type == TransactionType.EXPENSE.name && it.categoryId == b.categoryId }
                    .sumOf { it.amount }
                val progress = if (b.limitAmount > 0) (spent / b.limitAmount).toFloat().coerceIn(0f, 1f) else 0f
                val isAlert = b.limitAmount > 0 && (spent / b.limitAmount) >= (b.alertThreshold / 100.0)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingBudget = b },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = b.categoryName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${FinancialEngine.formatBdt(spent)} / ${FinancialEngine.formatBdt(b.limitAmount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { editingBudget = b },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("edit_budget_${b.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = if (isBn) "বাজেট এডিট করুন" else "Edit Budget",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        if (isAlert) {
                            Text(
                                text = "⚠️ সতর্কবার্তা: বাজেটের ${b.alertThreshold.toInt()}% সীমা ছাড়িয়ে গেছে!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section: Savings Goals
        item {
            SectionTitleRow(
                title = if (isBn) "সঞ্চয় লক্ষ্য (Savings Goals)" else "Savings Goals",
                actionText = "+ লক্ষ্য",
                onAction = {
                    editingGoal = null
                    showAddGoalDialog = true
                }
            )
        }

        if (state.savingsGoals.isEmpty()) {
            item {
                EmptyStateCard(message = if (isBn) "কোনো সঞ্চয় লক্ষ্য নেই। যেমন: ল্যাপটপ, হজ্ব বা ইমার্জেন্সি ফান্ড।" else "No savings goals added.")
            }
        } else {
            items(state.savingsGoals.size) { idx ->
                val goal = state.savingsGoals[idx]
                val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingGoal = goal },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = goal.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${FinancialEngine.formatBdt(goal.currentAmount)} / ${FinancialEngine.formatBdt(goal.targetAmount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF059669)
                            )
                            IconButton(
                                onClick = { editingGoal = goal },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("edit_goal_${goal.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = if (isBn) "লক্ষ্য এডিট করুন" else "Edit Savings Goal",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}% সঞ্চিত",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Section: Category Expense Breakdown
        item {
            Text(
                text = if (isBn) "খাতভিত্তিক ব্যয়ের বিবরণ (Category Breakdown)" else "Category Expense Breakdown",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            val expenseByCategory = state.filteredTransactions
                .filter { it.type == TransactionType.EXPENSE.name }
                .groupBy { it.categoryName ?: "অন্যান্য (Other)" }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }

            if (expenseByCategory.isEmpty()) {
                EmptyStateCard(message = if (isBn) "এই সময়কালে কোনো ব্যয়ের রেকর্ড নেই।" else "No expenses recorded in this period.")
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        expenseByCategory.take(6).forEach { (catName, amt) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = catName, fontSize = 13.sp)
                                Text(
                                    text = FinancialEngine.formatBdt(amt),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Person-wise Expense Breakdown
        item {
            Text(
                text = if (isBn) "ব্যক্তিভিত্তিক ব্যয়ের বণ্টন (Person Breakdown)" else "Person Expense Breakdown",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            val expenseByPerson = state.filteredTransactions
                .filter { it.type == TransactionType.EXPENSE.name && !it.personName.isNullOrBlank() }
                .groupBy { it.personName!! }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }

            if (expenseByPerson.isEmpty()) {
                EmptyStateCard(message = if (isBn) "ব্যক্তিভিত্তিক ব্যয়ের বণ্টন নেই।" else "No person attribution recorded.")
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        expenseByPerson.forEach { (personName, amt) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = personName, fontSize = 13.sp)
                                Text(
                                    text = FinancialEngine.formatBdt(amt),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Custom Categories & People/Relationships Setup
        item {
            Text(
                text = if (isBn) "ক্যাটাগরি ও ব্যক্তি কাস্টমাইজেশন" else "Categories & People Setup",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showManageCategoriesDialog = true }
                        .testTag("more_manage_categories_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBn) "ক্যাটাগরি সেটআপ" else "Categories",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBn) "${state.categories.size} টি ক্যাটাগরি (আয়/খরচ)" else "${state.categories.size} categories",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showManagePeopleDialog = true }
                        .testTag("more_manage_people_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBn) "ব্যক্তি ও সম্পর্ক" else "People & Relations",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBn) "${state.people.size} জন সংরক্ষিত" else "${state.people.size} people saved",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Unified Complete Report Export & Raw CSV Backup
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("more_download_data_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = if (isBn) "সম্পূর্ণ রিপোর্ট এক্সপোর্ট" else "Export Complete Report",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBn) "আর্থিক রিপোর্ট এক্সপোর্ট (Export Report)" else "Complete Financial Report Export",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isBn) {
                                    "নির্বাচিত মাস বা বছরের সকল অ্যাকাউন্ট স্টেটমেন্ট, ব্যক্তি, ক্যাটাগরি, ঋণ ও সঞ্চয়ের সম্পূর্ণ রিপোর্ট PDF / Excel ফাইলে ডাউনলোড করুন"
                                } else {
                                    "Export one complete Monthly or Yearly report (all account statements, person, category, loan & savings) as PDF, Excel, or Both"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ONE Central Unified Export Report CTA
                    Button(
                        onClick = { showUnifiedExportDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_unified_export_dialog_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBn) "রিপোর্ট এক্সপোর্ট (Export Report)" else "Export Report",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Optional Secondary Raw CSV & Cloud Data Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val res = FinanceExportUtil.exportTransactions(
                                    context = context,
                                    format = ExportFormat.CSV,
                                    transactions = state.allTransactions,
                                    accounts = state.accounts,
                                    summary = state.summary
                                )
                                res.onSuccess { outcome ->
                                    lastExportOutcomes = listOf(outcome)
                                    exportStatusMessage = if (outcome.savedToDownloads) {
                                        if (isBn) "Downloads এ সেভ হয়েছে: ${outcome.fileName}" else "Saved to Downloads: ${outcome.fileName}"
                                    } else {
                                        if (isBn) "CSV ফাইল প্রস্তুত: ${outcome.fileName}" else "CSV ready: ${outcome.fileName}"
                                    }
                                    if (!outcome.savedToDownloads) {
                                        context.startActivity(
                                            FinanceExportUtil.createShareIntent(
                                                outcome,
                                                if (isBn) "CSV এক্সপোর্ট শেয়ার করুন" else "Share CSV Export"
                                            )
                                        )
                                    }
                                }.onFailure { err ->
                                    exportStatusMessage = err.localizedMessage ?: "Export failed"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("more_export_csv_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBn) "র-লেনদেন CSV" else "Raw CSV (.csv)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { showBackupDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBn) "ডেটা সারসংক্ষেপ" else "Data Summary",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    exportStatusMessage?.let { msg ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (lastExportOutcomes.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            context.startActivity(
                                                FinanceExportUtil.createShareOutcomesIntent(
                                                    lastExportOutcomes,
                                                    if (isBn) "রিপোর্ট ফাইল শেয়ার / খুলুন" else "Share or Open Report"
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("more_share_export_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = if (isBn) "শেয়ার করুন" else "Share",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Sign Out
        item {
            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isBn) "লগ আউট (Sign Out)" else "Sign Out")
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Add / Edit Budget Dialog
    if (showAddBudgetDialog || editingBudget != null) {
        AddBudgetDialog(
            categories = state.categories.filter { it.type.equals("EXPENSE", ignoreCase = true) },
            existingBudget = editingBudget,
            isBangla = isBn,
            onSave = { b ->
                onAddBudget(b)
                editingBudget = null
                showAddBudgetDialog = false
            },
            onDismiss = {
                editingBudget = null
                showAddBudgetDialog = false
            }
        )
    }

    // Add / Edit Savings Goal Dialog
    if (showAddGoalDialog || editingGoal != null) {
        AddGoalDialog(
            existingGoal = editingGoal,
            isBangla = isBn,
            onSave = { g ->
                if (editingGoal != null) {
                    onUpdateSavingsGoal(g)
                } else {
                    onAddSavingsGoal(g)
                }
                editingGoal = null
                showAddGoalDialog = false
            },
            onDismiss = {
                editingGoal = null
                showAddGoalDialog = false
            }
        )
    }

    // Manage Categories Dialog
    if (showManageCategoriesDialog) {
        ManageCategoriesDialog(
            userId = state.profile?.userId ?: "",
            categories = state.categories,
            isBangla = isBn,
            onSaveCategory = onSaveCategory,
            onDeleteCategory = onDeleteCategory,
            onDismiss = { showManageCategoriesDialog = false }
        )
    }

    // Manage People & Relationships Dialog
    if (showManagePeopleDialog) {
        ManagePeopleDialog(
            userId = state.profile?.userId ?: "",
            people = state.people,
            isBangla = isBn,
            onSavePerson = onSavePerson,
            onDeletePerson = onDeletePerson,
            onDismiss = { showManagePeopleDialog = false }
        )
    }

    // Unified Export Report Modal
    if (showUnifiedExportDialog) {
        UnifiedExportDialog(
            state = state,
            onDismiss = { showUnifiedExportDialog = false },
            onExportSuccess = { outcomes, periodLabel ->
                showUnifiedExportDialog = false
                lastExportOutcomes = outcomes
                val names = outcomes.joinToString(" & ") { it.fileName }
                val allSaved = outcomes.all { it.savedToDownloads }
                exportStatusMessage = if (allSaved) {
                    if (isBn) "$periodLabel এর সম্পূর্ণ রিপোর্ট Downloads এ সেভ হয়েছে: $names"
                    else "Complete report for $periodLabel saved to Downloads: $names"
                } else {
                    if (isBn) "$periodLabel এর রিপোর্ট প্রস্তুত: $names"
                    else "Complete report ready for $periodLabel: $names"
                }
                if (!allSaved && outcomes.isNotEmpty()) {
                    context.startActivity(
                        FinanceExportUtil.createShareOutcomesIntent(
                            outcomes,
                            if (isBn) "সম্পূর্ণ রিপোর্ট শেয়ার করুন" else "Share Complete Report"
                        )
                    )
                }
            }
        )
    }

    // Backup Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("ডেটা ব্যাকআপ বিবরণ") },
            text = {
                Text(
                    text = "আপনার অ্যাকাউন্টে ${state.accounts.size} টি অ্যাকাউন্ট, ${state.allTransactions.size} টি লেনদেন এবং ${state.loans.size} টি ঋণের রেকর্ড রয়েছে। সকল তথ্য ক্লাউড ফায়ারস্টোরে নিরাপদভাবে সংরক্ষিত।"
                )
            },
            confirmButton = {
                Button(onClick = { showBackupDialog = false }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }
}

@Composable
private fun SectionTitleRow(title: String, actionText: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        OutlinedButton(onClick = onAction, shape = RoundedCornerShape(8.dp)) {
            Text(text = actionText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

private fun normalizeMoreNumericInput(input: String): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val idx = bnDigits.indexOf(ch)
        if (idx >= 0) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
    }
    return sb.toString()
}

private fun formatMoreDouble(v: Double): String {
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBudgetDialog(
    categories: List<Category>,
    existingBudget: Budget? = null,
    isBangla: Boolean = true,
    onSave: (Budget) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditMode = existingBudget != null
    var selectedCat by remember(existingBudget?.id) {
        mutableStateOf(
            if (existingBudget != null) {
                categories.find { it.id == existingBudget.categoryId } ?: categories.firstOrNull()
            } else {
                categories.firstOrNull()
            }
        )
    }
    var limitText by remember(existingBudget?.id) {
        mutableStateOf(if (existingBudget != null) formatMoreDouble(existingBudget.limitAmount) else "")
    }
    var alertThresholdText by remember(existingBudget?.id) {
        mutableStateOf(if (existingBudget != null) formatMoreDouble(existingBudget.alertThreshold) else "80")
    }
    val currentMonth = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditMode) {
                    if (isBangla) "বাজেট এডিট করুন" else "Edit Budget"
                } else {
                    if (isBangla) "নতুন বাজেট নির্ধারণ" else "Set Budget"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (isBangla) "ক্যাটাগরি নির্বাচন করুন:" else "Select Category:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCat?.id == cat.id,
                            onClick = { selectedCat = cat },
                            label = { Text(cat.nameBn.ifBlank { cat.name }, fontSize = 12.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text(if (isBangla) "মাসিক সর্বোচ্চ বাজেট (৳)" else "Monthly Limit (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("budget_limit_input")
                )

                OutlinedTextField(
                    value = alertThresholdText,
                    onValueChange = { alertThresholdText = it },
                    label = { Text(if (isBangla) "সতর্কবার্তা থ্রেশহোল্ড (%) যেমন ৮০%" else "Alert Threshold (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = normalizeMoreNumericInput(limitText).toDoubleOrNull() ?: 0.0
                    val threshold = normalizeMoreNumericInput(alertThresholdText).toDoubleOrNull() ?: 80.0
                    if (selectedCat != null && limit > 0) {
                        if (existingBudget != null) {
                            onSave(
                                existingBudget.copy(
                                    categoryId = selectedCat!!.id,
                                    categoryName = selectedCat!!.nameBn.ifBlank { selectedCat!!.name },
                                    limitAmount = limit,
                                    alertThreshold = threshold
                                )
                            )
                        } else {
                            onSave(
                                Budget(
                                    categoryId = selectedCat!!.id,
                                    categoryName = selectedCat!!.nameBn.ifBlank { selectedCat!!.name },
                                    month = currentMonth,
                                    limitAmount = limit,
                                    alertThreshold = threshold
                                )
                            )
                        }
                    }
                },
                enabled = selectedCat != null && (normalizeMoreNumericInput(limitText).toDoubleOrNull() ?: 0.0) > 0,
                modifier = Modifier.testTag("save_budget_button")
            ) {
                Text(
                    if (isEditMode) {
                        if (isBangla) "আপডেট করুন" else "Update"
                    } else {
                        if (isBangla) "সেট করুন" else "Save"
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(if (isBangla) "বাতিল" else "Cancel") }
        }
    )
}

@Composable
fun AddGoalDialog(
    existingGoal: SavingsGoal? = null,
    isBangla: Boolean = true,
    onSave: (SavingsGoal) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditMode = existingGoal != null
    var title by remember(existingGoal?.id) { mutableStateOf(existingGoal?.title ?: "") }
    var targetText by remember(existingGoal?.id) {
        mutableStateOf(if (existingGoal != null) formatMoreDouble(existingGoal.targetAmount) else "")
    }
    var currentText by remember(existingGoal?.id) {
        mutableStateOf(if (existingGoal != null) formatMoreDouble(existingGoal.currentAmount) else "0")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditMode) {
                    if (isBangla) "সঞ্চয় লক্ষ্য এডিট করুন" else "Edit Savings Goal"
                } else {
                    if (isBangla) "নতুন সঞ্চয় লক্ষ্য যোগ করুন" else "Add Savings Goal"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isBangla) "লক্ষ্যের নাম (যেমন: ল্যাপটপ কেনা)" else "Goal Title") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_title_input")
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text(if (isBangla) "লক্ষ্যমাত্রা (৳)" else "Target Amount (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("goal_target_input")
                )
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it },
                    label = { Text(if (isBangla) "ইতিমধ্যে জমা হয়েছে (৳)" else "Current Saved (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("goal_current_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = normalizeMoreNumericInput(targetText).toDoubleOrNull() ?: 0.0
                    val current = normalizeMoreNumericInput(currentText).toDoubleOrNull() ?: 0.0
                    val status = if (current >= target && target > 0) "COMPLETED" else "ACTIVE"
                    if (title.isNotBlank() && target > 0) {
                        if (existingGoal != null) {
                            onSave(
                                existingGoal.copy(
                                    title = title.trim(),
                                    targetAmount = target,
                                    currentAmount = current,
                                    status = status
                                )
                            )
                        } else {
                            onSave(
                                SavingsGoal(
                                    title = title.trim(),
                                    targetAmount = target,
                                    currentAmount = current,
                                    status = status
                                )
                            )
                        }
                    }
                },
                enabled = title.isNotBlank() && (normalizeMoreNumericInput(targetText).toDoubleOrNull() ?: 0.0) > 0,
                modifier = Modifier.testTag("save_goal_button")
            ) {
                Text(
                    if (isEditMode) {
                        if (isBangla) "আপডেট করুন" else "Update"
                    } else {
                        if (isBangla) "সংরক্ষণ করুন" else "Save"
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(if (isBangla) "বাতিল" else "Cancel") }
        }
    )
}
