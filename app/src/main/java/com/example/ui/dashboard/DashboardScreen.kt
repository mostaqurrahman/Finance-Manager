package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.repository.AiInsightsUiState
import com.example.ui.DateFilter
import com.example.ui.FinanceUiState
import com.example.util.FinancialEngine

@Composable
fun DashboardScreen(
    state: FinanceUiState,
    onFilterSelect: (DateFilter) -> Unit,
    onOpenAddTx: () -> Unit,
    onOpenVoiceTx: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenAddLoan: () -> Unit,
    onEditTx: (Transaction) -> Unit,
    onDeleteTx: (Transaction) -> Unit,
    onNavigateAccounts: () -> Unit,
    onNavigateLoans: () -> Unit,
    onGenerateAiInsights: () -> Unit = {}
) {
    val summary = state.summary
    val isBn = state.isBanglaLanguage

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Sync status and Greeting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isBn) "আসসালামু আলাইকুম," else "Hello,",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.profile?.displayName?.ifBlank { "ব্যবহারকারী" } ?: "ব্যবহারকারী",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Cloud Sync Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBn) "ক্লাউড সিঙ্কড" else "Synced",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Period Filters Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateFilter.entries.forEach { filter ->
                    val isSelected = state.activeDateFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterSelect(filter) },
                        label = {
                            Text(
                                text = if (isBn) filter.labelBn else filter.labelEn,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Hero Net Worth & Liquid Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF064E3B), // Deep Emerald
                                    Color(0xFF0F766E), // Teal
                                    Color(0xFF1E3A8A)  // Deep Blue
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "মোট তরল ব্যালেন্স" else "Total Liquid Balance",
                                fontSize = 13.sp,
                                color = Color(0xFFD1FAE5)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isBn) "BDT / ৳" else "BDT / ৳",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = FinancialEngine.formatBdt(summary.availableBalance),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Liquid accounts row inside hero card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeroAccountPill(
                                icon = Icons.Default.Payments,
                                label = if (isBn) "নগদ" else "Cash",
                                amount = summary.cashBalance
                            )
                            HeroAccountPill(
                                icon = Icons.Default.AccountBalance,
                                label = if (isBn) "ব্যাংক" else "Bank",
                                amount = summary.bankBalance
                            )
                            HeroAccountPill(
                                icon = Icons.Default.PhoneAndroid,
                                label = if (isBn) "মোবাইল" else "Mobile",
                                amount = summary.mobileWalletBalance
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Net worth bottom subline
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBn) "সর্বমোট নিট সম্পদ (Net Worth):" else "Estimated Net Worth:",
                                fontSize = 12.sp,
                                color = Color(0xFFA7F3D0)
                            )
                            Text(
                                text = FinancialEngine.formatBdt(summary.netWorth),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A) // Soft gold
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionButton(
                    icon = Icons.Default.Add,
                    label = if (isBn) "+ লেনদেন" else "+ Tx",
                    bgColor = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    testTag = "quick_add_tx",
                    onClick = onOpenAddTx
                )
                QuickActionButton(
                    icon = Icons.Default.Mic,
                    label = if (isBn) "🎙️ ভয়েস" else "🎙️ Voice",
                    bgColor = Color(0xFF10B981).copy(alpha = 0.2f),
                    tint = Color(0xFF059669),
                    testTag = "quick_voice_tx",
                    onClick = onOpenVoiceTx
                )
                QuickActionButton(
                    icon = Icons.Default.SwapHoriz,
                    label = if (isBn) "⇄ ট্রান্সফার" else "⇄ Transfer",
                    bgColor = Color(0xFF3B82F6).copy(alpha = 0.18f),
                    tint = Color(0xFF2563EB),
                    testTag = "quick_transfer_tx",
                    onClick = onOpenTransfer
                )
                QuickActionButton(
                    icon = Icons.Default.Savings,
                    label = if (isBn) "+ ঋণ" else "+ Loan",
                    bgColor = Color(0xFFF59E0B).copy(alpha = 0.18f),
                    tint = Color(0xFFD97706),
                    testTag = "quick_loan_tx",
                    onClick = onOpenAddLoan
                )
            }
        }

        // Income & Expense Breakdown Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Income Card
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "মোট আয়" else "Income",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = FinancialEngine.formatBdt(summary.totalIncome),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                }

                // Expense Card
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "মোট ব্যয়" else "Expense",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = FinancialEngine.formatBdt(summary.totalExpense),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }
        }

        // Loans Overview Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateLoans() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isBn) "ঋণ ও দেনা-পাওনা" else "Loans Overview",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${if (isBn) "পাবো:" else "Receivable:"} ${FinancialEngine.formatBdt(summary.loanGivenReceivable)}",
                            fontSize = 12.sp,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (isBn) "দেবো:" else "Payable:"} ${FinancialEngine.formatBdt(summary.loanTakenPayable)}",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isBn) "বিস্তারিত দেখুন ›" else "View Details ›",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // AI-Powered Spending Insights & Potential Savings Section
        item {
            AiInsightsDashboardCard(
                aiInsightsState = state.aiInsightsState,
                isBn = isBn,
                hasTransactions = state.filteredTransactions.isNotEmpty(),
                onGenerate = onGenerateAiInsights
            )
        }

        // Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "সাম্প্রতিক লেনদেনসমূহ" else "Recent Transactions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${state.filteredTransactions.size} টি রেকর্ড",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.filteredTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isBn) "এই সময়কালে কোনো লেনদেন পাওয়া যায়নি।" else "No transactions in this period.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBn) "উপরের '+ লেনদেন' অথবা 'ভয়েস ইনপুট' দিয়ে হিসাব যোগ করুন" else "Tap '+ Tx' or 'Voice' above to log your finances.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            items(state.filteredTransactions.take(15), key = { it.id }) { tx ->
                TransactionRowItem(
                    tx = tx,
                    onEdit = { onEditTx(tx) },
                    onDelete = { onDeleteTx(tx) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp)) // Bottom nav padding
        }
    }
}

@Composable
private fun AiInsightsDashboardCard(
    aiInsightsState: AiInsightsUiState,
    isBn: Boolean,
    hasTransactions: Boolean,
    onGenerate: () -> Unit
) {
    val isLoading = aiInsightsState is AiInsightsUiState.Loading
    val hasSuccess = aiInsightsState is AiInsightsUiState.Success

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_ai_insights_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = if (isBn) "AI বিশ্লেষণ" else "Gemini AI Insights",
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isBn) "AI ব্যয় ও সঞ্চয় পরামর্শ" else "AI Spending & Savings Insights",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isBn) "খরচের ধরন ও সাশ্রয়ের সম্ভাব্য খাত" else "Spending patterns & potential savings",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onGenerate,
                    enabled = !isLoading && hasTransactions,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_ai_insights_button")
                ) {
                    Icon(
                        imageVector = if (hasSuccess) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isLoading -> if (isBn) "চলছে..." else "Analyzing..."
                            hasSuccess -> if (isBn) "রিফ্রেশ" else "Refresh"
                            else -> if (isBn) "বিশ্লেষণ করুন" else "Analyze"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (aiInsightsState) {
                is AiInsightsUiState.Idle -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (!hasTransactions) {
                                if (isBn) "বিশ্লেষণের জন্য এই সময়কালে অন্তত একটি লেনদেন যোগ করুন।"
                                else "Add at least one transaction in this period to analyze spending patterns."
                            } else {
                                if (isBn) "আপনার খরচের ধরন ও কোন কোন খাতে টাকা বাঁচানো যাবে তা জানতে 'বিশ্লেষণ করুন' চাপুন।"
                                else "Tap 'Analyze' to summarize spending patterns and highlight categories with savings potential."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                is AiInsightsUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF4F46E5)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isBn) "Gemini AI আপনার খরচের হিসাব বিশ্লেষণ করছে..." else "Gemini AI is analyzing your spending patterns...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is AiInsightsUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_ai_insights_error")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = if (isBn) "ত্রুটি" else "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = aiInsightsState.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is AiInsightsUiState.Success -> {
                    val insights = aiInsightsState.insights
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("dashboard_ai_insights_results")
                    ) {
                        // Summary Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4F46E5).copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (insights.headline.isNotBlank()) {
                                    Text(
                                        text = insights.headline,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3730A3)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                if (insights.spendingSummary.isNotBlank()) {
                                    Text(
                                        text = insights.spendingSummary,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Spending Patterns
                        if (insights.patterns.isNotEmpty()) {
                            Text(
                                text = if (isBn) "খরচের ধরনসমূহ" else "Spending Patterns",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            insights.patterns.forEach { pattern ->
                                val (trendIcon, trendTint) = when (pattern.trend) {
                                    "UP" -> Icons.Default.TrendingUp to Color(0xFFDC2626)
                                    "DOWN" -> Icons.Default.TrendingDown to Color(0xFF059669)
                                    else -> Icons.AutoMirrored.Filled.TrendingFlat to Color(0xFF2563EB)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = trendIcon,
                                        contentDescription = pattern.trend,
                                        tint = trendTint,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pattern.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = pattern.detail,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Potential Savings Categories
                        if (insights.savingsOpportunities.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBn) "সম্ভাব্য সঞ্চয়ের খাতসমূহ" else "Potential Savings Categories",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (insights.savingsPotentialTotal > 0.0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "${if (isBn) "মোট সাশ্রয়:" else "Save:"} ~${FinancialEngine.formatBdt(insights.savingsPotentialTotal)}/mo",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF047857),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            insights.savingsOpportunities.forEach { opp ->
                                val priorityColor = when (opp.priority) {
                                    "HIGH" -> Color(0xFFDC2626)
                                    "MEDIUM" -> Color(0xFFD97706)
                                    else -> Color(0xFF059669)
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = opp.categoryName,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = priorityColor.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = opp.priority,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = priorityColor,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "~${FinancialEngine.formatBdt(opp.estimatedMonthlySavings)}/${if (isBn) "মাস" else "mo"}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF059669)
                                            )
                                        }
                                        if (opp.currentSpend > 0.0) {
                                            Text(
                                                text = "${if (isBn) "বর্তমান ব্যয়:" else "Current spend:"} ${FinancialEngine.formatBdt(opp.currentSpend)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (opp.actionableTip.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = opp.actionableTip,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroAccountPill(icon: ImageVector, label: String, amount: Double) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = label, fontSize = 10.sp, color = Color(0xFFD1FAE5))
                Text(
                    text = FinancialEngine.formatBdt(amount),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(6.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TransactionRowItem(
    tx: Transaction,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val isIncome = tx.type == TransactionType.INCOME.name || tx.type == TransactionType.REFUND.name || tx.type == TransactionType.LOAN_REPAYMENT_RECEIVED.name
    val isTransfer = tx.type == TransactionType.TRANSFER.name
    val amountColor = when {
        isTransfer -> Color(0xFF2563EB)
        isIncome -> Color(0xFF059669)
        else -> Color(0xFFDC2626)
    }

    val icon = when {
        isTransfer -> Icons.Default.SwapHoriz
        isIncome -> Icons.Default.ArrowDownward
        else -> Icons.Default.ArrowUpward
    }

    val prefix = when {
        isTransfer -> ""
        isIncome -> "+"
        else -> "-"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description ?: tx.categoryName ?: tx.type,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.accountName ?: "Cash",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    if (!tx.personName.isNullOrBlank()) {
                        Text(
                            text = " • ${tx.personName}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = " • ${tx.date}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${FinancialEngine.formatBdt(tx.amount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp).testTag("edit_tx_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit transaction",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).testTag("delete_tx_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete transaction",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
