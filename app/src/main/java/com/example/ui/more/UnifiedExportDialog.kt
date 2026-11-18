package com.example.ui.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.FinanceUiState
import com.example.util.ExportOutcome
import com.example.util.FinanceExportUtil
import com.example.util.ReportExportFormat
import com.example.util.ReportPeriodType
import com.example.util.UnifiedReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedExportDialog(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onExportSuccess: (outcomes: List<ExportOutcome>, periodLabel: String) -> Unit
) {
    val isBn = state.isBanglaLanguage
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var periodType by remember { mutableStateOf(ReportPeriodType.MONTHLY) }
    val availablePeriods = remember(state.allTransactions, periodType) {
        UnifiedReportBuilder.getAvailablePeriods(state.allTransactions, periodType)
    }

    var selectedPeriodKey by remember { mutableStateOf(availablePeriods.firstOrNull()?.key ?: "") }
    var periodDropdownExpanded by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf(ReportExportFormat.BOTH) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(availablePeriods) {
        if (availablePeriods.isEmpty()) {
            selectedPeriodKey = ""
        } else if (availablePeriods.none { it.key == selectedPeriodKey }) {
            selectedPeriodKey = availablePeriods.first().key
        }
    }

    val selectedPeriod = remember(availablePeriods, selectedPeriodKey) {
        availablePeriods.find { it.key == selectedPeriodKey }
    }

    val expectedFileNames = remember(periodType, selectedPeriod, selectedFormat) {
        if (selectedPeriod == null) {
            emptyList()
        } else {
            when (selectedFormat) {
                ReportExportFormat.PDF -> listOf(
                    UnifiedReportBuilder.buildFileName(periodType, selectedPeriod.key, "pdf")
                )
                ReportExportFormat.XLSX -> listOf(
                    UnifiedReportBuilder.buildFileName(periodType, selectedPeriod.key, "xlsx")
                )
                ReportExportFormat.BOTH -> listOf(
                    UnifiedReportBuilder.buildFileName(periodType, selectedPeriod.key, "pdf"),
                    UnifiedReportBuilder.buildFileName(periodType, selectedPeriod.key, "xlsx")
                )
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp)
                .testTag("unified_export_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBn) "সম্পূর্ণ আর্থিক রিপোর্ট এক্সপোর্ট" else "Export Complete Financial Report",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBn) {
                                "সকল অ্যাকাউন্ট স্টেটমেন্ট, ব্যক্তি, ক্যাটাগরি, ঋণ ও সঞ্চয় সহ"
                            } else {
                                "Includes all account statements, person, category, loan & savings summaries"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { if (!isGenerating) onDismiss() },
                        enabled = !isGenerating,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isBn) "বন্ধ করুন" else "Close"
                        )
                    }
                }

                HorizontalDivider()

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Report Period Type
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isBn) "১. রিপোর্টের ধরন নির্বাচন করুন" else "1. Select Report Period Type",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                ReportPeriodType.MONTHLY to "period_type_monthly",
                                ReportPeriodType.YEARLY to "period_type_yearly"
                            ).forEach { (type, tag) ->
                                val active = periodType == type
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        if (!isGenerating) {
                                            periodType = type
                                            errorMessage = null
                                        }
                                    },
                                    enabled = !isGenerating,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = if (isBn) type.labelBn else type.labelEn,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag(tag)
                                )
                            }
                        }

                        // Dynamic Non-Empty Period Dropdown
                        Text(
                            text = if (periodType == ReportPeriodType.MONTHLY) {
                                if (isBn) "মাস নির্বাচন করুন:" else "Select Available Month:"
                            } else {
                                if (isBn) "বছর নির্বাচন করুন:" else "Select Available Year:"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (availablePeriods.isEmpty()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (isBn) {
                                            "রিপোর্ট তৈরি করার জন্য কোনো লেনদেন পাওয়া যায়নি।"
                                        } else {
                                            "No financial transaction records found to export."
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { if (!isGenerating) periodDropdownExpanded = true },
                                    enabled = !isGenerating,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("period_dropdown_trigger")
                                ) {
                                    val displayText = selectedPeriod?.let { p ->
                                        if (isBn) "${p.labelBn} (${p.txCount}টি লেনদেন)"
                                        else "${p.labelEn} (${p.txCount} transactions)"
                                    } ?: "-"
                                    Text(
                                        text = displayText,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = if (isBn) "তালিকা খুলুন" else "Open period list"
                                    )
                                }

                                DropdownMenu(
                                    expanded = periodDropdownExpanded,
                                    onDismissRequest = { periodDropdownExpanded = false }
                                ) {
                                    availablePeriods.forEach { period ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (isBn) {
                                                        "${period.labelBn} (${period.txCount}টি লেনদেন)"
                                                    } else {
                                                        "${period.labelEn} (${period.txCount} transactions)"
                                                    },
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                selectedPeriodKey = period.key
                                                periodDropdownExpanded = false
                                                errorMessage = null
                                            },
                                            modifier = Modifier.testTag("period_option_${period.key}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Export Format Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isBn) "২. ফাইল ফরম্যাট নির্বাচন করুন" else "2. Select Export Format",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                Triple(ReportExportFormat.PDF, Icons.Default.Description, "export_format_pdf"),
                                Triple(ReportExportFormat.XLSX, Icons.Default.TableChart, "export_format_xlsx"),
                                Triple(ReportExportFormat.BOTH, Icons.Default.Layers, "export_format_both")
                            ).forEach { (fmt, icon, tag) ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = {
                                        if (!isGenerating) {
                                            selectedFormat = fmt
                                            errorMessage = null
                                        }
                                    },
                                    enabled = !isGenerating,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = if (isBn) fmt.labelBn else fmt.labelEn,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag(tag)
                                )
                            }
                        }
                    }

                    // 3. Selection Summary Box
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isBn) "নির্বাচিত রিপোর্টের সারসংক্ষেপ" else "Selection Summary",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            SummaryRow(
                                label = if (isBn) "রিপোর্টের ধরন:" else "Report Type:",
                                value = if (isBn) periodType.labelBn else periodType.labelEn
                            )
                            SummaryRow(
                                label = if (isBn) "নির্বাচিত সময়কাল:" else "Selected Period:",
                                value = selectedPeriod?.let { if (isBn) it.labelBn else it.labelEn } ?: "-"
                            )
                            SummaryRow(
                                label = if (isBn) "ফরম্যাট:" else "Format:",
                                value = if (isBn) selectedFormat.labelBn else selectedFormat.labelEn
                            )
                            SummaryRow(
                                label = if (isBn) "অন্তর্ভুক্ত লেনদেন:" else "Transactions Included:",
                                value = (selectedPeriod?.txCount ?: 0).toString()
                            )

                            if (expectedFileNames.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text(
                                    text = (if (isBn) "ফাইল: " else "File(s): ") + expectedFileNames.joinToString(", "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    errorMessage?.let { err ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = err,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Footer Single Primary Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isGenerating,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(if (isBn) "বাতিল" else "Cancel", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val period = selectedPeriod
                            if (period == null || period.txCount == 0) {
                                errorMessage = if (isBn) {
                                    "অনুগ্রহ করে লেনদেন রয়েছে এমন একটি সময়কাল নির্বাচন করুন।"
                                } else {
                                    "Please select a period with recorded transactions."
                                }
                                return@Button
                            }
                            errorMessage = null
                            isGenerating = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    val report = UnifiedReportBuilder.buildReport(
                                        periodType = periodType,
                                        periodKey = period.key,
                                        accounts = state.accounts,
                                        transactions = state.allTransactions,
                                        loans = state.loans,
                                        categories = state.categories,
                                        people = state.people,
                                        savingsGoals = state.savingsGoals,
                                        budgets = state.budgets,
                                        ownerName = state.profile?.displayName ?: "Account Holder",
                                        ownerEmail = state.profile?.email ?: ""
                                    )
                                    FinanceExportUtil.exportUnifiedReport(
                                        context = context,
                                        format = selectedFormat,
                                        report = report
                                    )
                                }
                                isGenerating = false
                                result.onSuccess { outcomes ->
                                    val periodLabel = if (isBn) period.labelBn else period.labelEn
                                    onExportSuccess(outcomes, periodLabel)
                                }.onFailure { err ->
                                    errorMessage = err.localizedMessage ?: if (isBn) {
                                        "রিপোর্ট এক্সপোর্ট করতে ব্যর্থ হয়েছে।"
                                    } else {
                                        "Failed to export report."
                                    }
                                }
                            }
                        },
                        enabled = !isGenerating && selectedPeriod != null && selectedPeriod.txCount > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_complete_report_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "রিপোর্ট তৈরি হচ্ছে..." else "Generating...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "সম্পূর্ণ রিপোর্ট এক্সপোর্ট" else "Export Complete Report",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
