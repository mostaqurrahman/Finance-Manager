package com.example.ui.transaction

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Person
import com.example.data.repository.ParsedVoiceTransaction
import com.example.util.AudioVoiceRecorder
import com.example.util.BengaliVoiceParser
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceTransactionSheet(
    isProcessing: Boolean,
    voiceError: String?,
    draftTx: ParsedVoiceTransaction?,
    accounts: List<Account>,
    categories: List<Category>,
    people: List<Person>,
    onParseText: (String) -> Unit,
    onParseAudio: (ByteArray, String) -> Unit,
    onDirectEdit: (String) -> Unit,
    onClearDraft: () -> Unit,
    onConfirm: (
        editedDraft: ParsedVoiceTransaction,
        accountId: String,
        toAccountId: String?,
        categoryId: String?,
        personId: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var localError by remember { mutableStateOf<String?>(null) }

    val audioRecorder = remember(context) { AudioVoiceRecorder(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.cancelAndRelease()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds += 1
            }
        } else {
            recordingSeconds = 0
        }
    }

    fun beginAudioRecording() {
        localError = null
        val started = audioRecorder.startRecording()
        if (started.isSuccess) {
            isRecording = true
        } else {
            isRecording = false
            localError = started.exceptionOrNull()?.localizedMessage
                ?: "মাইক্রোফোন চালু করা যায়নি। নিচে লিখে বা সিস্টেম স্পিচ ব্যবহার করুন।"
        }
    }

    fun finishAudioRecordingAndAnalyze() {
        isRecording = false
        val readResult = audioRecorder.stopAndReadBytes()
        readResult.onSuccess { bytes ->
            localError = null
            onParseAudio(bytes, "audio/mp4")
        }.onFailure { err ->
            localError = err.localizedMessage ?: "অডিও রেকর্ডিং পাওয়া যায়নি।"
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginAudioRecording()
        } else {
            localError = "ভয়েস রেকর্ড করতে মাইক্রোফোন অনুমতি (RECORD_AUDIO) প্রয়োজন। অথবা নিচে লিখে/কিবোর্ড মাইক দিয়ে বলুন।"
        }
    }

    // Secondary System Speech Recognizer launcher (bn-BD) that preserves & appends existing text
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spokenText.isNullOrBlank()) {
                localError = null
                inputText = if (inputText.isBlank()) {
                    spokenText
                } else {
                    "${inputText.trim()} $spokenText"
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            audioRecorder.cancelAndRelease()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (draftTx == null) "বাংলা ভয়েস ও টেক্সট ইনপুট" else "ড্রাফট যাচাই ও পরিবর্তন",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = {
                        audioRecorder.cancelAndRelease()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("close_voice_sheet_button")
                ) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (draftTx == null) {
                // =========================================================
                // STEP 1: RECORD / SPEAK / TYPE & MODIFY TEXT BEFORE PARSING
                // =========================================================
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) {
                                Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFFEF4444)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF006C4C), Color(0xFF10B981)))
                            }
                        )
                        .clickable(enabled = !isProcessing) {
                            if (isRecording) {
                                finishAudioRecordingAndAnalyze()
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    beginAudioRecording()
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        .testTag("voice_record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isProcessing -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        }
                        isRecording -> {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop recording and analyze",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record Bengali voice",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        isProcessing -> "ভয়েস ও হিসাব বিশ্লেষণ চলছে..."
                        isRecording -> String.format(
                            "রেকর্ডিং চলছে (%02d:%02d) — বলা শেষ হলে ট্যাপ করুন",
                            recordingSeconds / 60,
                            recordingSeconds % 60
                        )
                        else -> "মাইকে ট্যাপ করে সরাসরি বাংলায় বলুন"
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary: System Google Speech Keyboard (appends spoken text cleanly)
                OutlinedButton(
                    onClick = {
                        localError = null
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                "বাংলায় বলুন (যেমন: বাজার করার জন্য বিকাশ থেকে ১২০০ টাকা খরচ)"
                            )
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            localError = "এই ডিভাইসে Google Speech ইঞ্জিন নেই। উপরের সবুজ মাইকে ট্যাপ করে অডিও রেকর্ড করুন অথবা নিচে টাইপ করুন।"
                        } catch (e: Exception) {
                            localError = "স্পিচ ইনপুট চালু করা যায়নি: ${e.localizedMessage ?: "Unknown error"}"
                        }
                    },
                    enabled = !isProcessing && !isRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("system_speech_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardVoice,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("সিস্টেম ভয়েস টাইপিং (টেক্সটে যোগ হবে)")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Editable voice/text box
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        localError = null
                    },
                    label = { Text("ভয়েস টেক্সট দেখুন বা পরিবর্তন করুন") },
                    placeholder = { Text("যেমন: আজকে বিকাশ থেকে বাজার খরচ ১২০০ টাকা") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_text_input"),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { inputText = "" },
                                modifier = Modifier.testTag("clear_voice_text_button")
                            ) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear text")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            localError = null
                            onDirectEdit(inputText)
                        },
                        enabled = !isProcessing && !isRecording,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("direct_edit_draft_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সরাসরি এডিট")
                    }

                    Button(
                        onClick = {
                            localError = null
                            if (inputText.isNotBlank()) {
                                onParseText(inputText.trim())
                            }
                        },
                        enabled = inputText.isNotBlank() && !isProcessing && !isRecording,
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("parse_voice_text_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("বিশ্লেষণ করুন")
                    }
                }

                val combinedError = localError ?: voiceError
                AnimatedVisibility(visible = !combinedError.isNullOrBlank()) {
                    combinedError?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "উদাহরণ বাক্যসমূহ (ট্যাপ করে এডিট/টেস্ট করুন):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                val examples = listOf(
                    "বাজার করার জন্য বিকাশ থেকে ১২০০ টাকা খরচ করেছি, Family Expense এ রাখো।",
                    "অফিস থেকে বেতন পেয়েছি ৬০,০০০ টাকা ব্যাংক একাউন্টে।",
                    "বন্ধু শাকিলকে ক্যাশ থেকে ৩০০০ টাকা ধার দিয়েছি।"
                )

                examples.forEach { ex ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !isProcessing && !isRecording) {
                                inputText = ex
                                localError = null
                                onParseText(ex)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "\"$ex\"",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // =========================================================
                // STEP 2: FULLY EDITABLE REVIEW & MODIFY SCREEN
                // =========================================================
                var editedTranscript by remember(draftTx) {
                    mutableStateOf(draftTx.transcript.ifBlank { draftTx.description })
                }
                var editedType by remember(draftTx) { mutableStateOf(draftTx.type) }
                var editedAmountText by remember(draftTx) {
                    mutableStateOf(
                        if (draftTx.amount > 0.0) {
                            if (draftTx.amount % 1.0 == 0.0) draftTx.amount.toLong().toString()
                            else draftTx.amount.toString()
                        } else ""
                    )
                }
                var editedDate by remember(draftTx) { mutableStateOf(draftTx.date) }
                var editedDescription by remember(draftTx) { mutableStateOf(draftTx.description) }

                val initialAccount = remember(draftTx, accounts) {
                    accounts.find {
                        it.name.contains(draftTx.accountName, ignoreCase = true) ||
                            it.provider?.contains(draftTx.accountName, ignoreCase = true) == true
                    } ?: accounts.firstOrNull()
                }
                var selectedAccountId by remember(draftTx, accounts) {
                    mutableStateOf(initialAccount?.id.orEmpty())
                }

                var selectedToAccountId by remember(draftTx, accounts) {
                    mutableStateOf(
                        accounts.firstOrNull { it.id != initialAccount?.id }?.id.orEmpty()
                    )
                }

                val initialCategory = remember(draftTx, categories) {
                    categories.find {
                        it.name.contains(draftTx.category, ignoreCase = true) ||
                            it.nameBn.contains(draftTx.category, ignoreCase = true)
                    }
                }
                var selectedCategoryId by remember(draftTx, categories) {
                    mutableStateOf(initialCategory?.id)
                }

                val initialPerson = remember(draftTx, people) {
                    if (draftTx.person.isBlank()) null
                    else people.find {
                        it.name.contains(draftTx.person, ignoreCase = true) ||
                            it.relationship?.contains(draftTx.person, ignoreCase = true) == true
                    }
                }
                var selectedPersonId by remember(draftTx, people) {
                    mutableStateOf(initialPerson?.id)
                }

                var formValidationError by remember { mutableStateOf<String?>(null) }

                // Editable spoken text + Re-parse button
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = editedTranscript,
                            onValueChange = { editedTranscript = it },
                            label = { Text("ভয়েস টেক্সট (এডিট করে আবার Parse করা যাবে)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("draft_transcript_input"),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    if (editedTranscript.isNotBlank()) {
                                        inputText = editedTranscript
                                    }
                                    onClearDraft()
                                },
                                modifier = Modifier.testTag("back_to_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("আবার বলুন")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (editedTranscript.isNotBlank()) {
                                        onParseText(editedTranscript.trim())
                                    }
                                },
                                enabled = editedTranscript.isNotBlank() && !isProcessing,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("reparse_transcript_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("পুনরায় Parse")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transaction Type Chips
                Text(
                    text = "লেনদেনের ধরন",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                val typeOptions = listOf(
                    "EXPENSE" to "খরচ",
                    "INCOME" to "আয়",
                    "TRANSFER" to "ট্রান্সফার",
                    "LOAN_GIVEN" to "ধার দেওয়া",
                    "LOAN_RECEIVED" to "ধার নেওয়া"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    typeOptions.forEach { (code, label) ->
                        FilterChip(
                            selected = editedType == code,
                            onClick = { editedType = code },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.testTag("draft_type_chip_$code")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Editable Amount + Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editedAmountText,
                        onValueChange = { newVal ->
                            editedAmountText = BengaliVoiceParser.normalizeBengaliDigits(newVal)
                            formValidationError = null
                        },
                        label = { Text("পরিমাণ (৳)") },
                        prefix = { Text("৳ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("draft_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editedDate,
                        onValueChange = { editedDate = it },
                        label = { Text("তারিখ") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("draft_date_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selectable Accounts
                if (accounts.isNotEmpty()) {
                    Text(
                        text = if (editedType == "TRANSFER") "উৎস অ্যাকাউন্ট (কোনটা থেকে)" else "অ্যাকাউন্ট",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        accounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.name, fontSize = 12.sp) },
                                modifier = Modifier.testTag("draft_account_chip_${acc.id}")
                            )
                        }
                    }
                }

                if (editedType == "TRANSFER" && accounts.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "গন্তব্য অ্যাকাউন্ট (কোনটায় যাবে)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        accounts.filter { it.id != selectedAccountId }.forEach { acc ->
                            FilterChip(
                                selected = selectedToAccountId == acc.id,
                                onClick = { selectedToAccountId = acc.id },
                                label = { Text(acc.name, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Selectable Categories
                if (categories.isNotEmpty() && editedType != "TRANSFER") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ক্যাটাগরি",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = {
                                    selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id
                                },
                                label = {
                                    Text(
                                        text = if (cat.nameBn.isNotBlank()) cat.nameBn else cat.name,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }
                }

                // Selectable People
                if (people.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "সম্পর্কিত ব্যক্তি (ঐচ্ছিক)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedPersonId == null,
                            onClick = { selectedPersonId = null },
                            label = { Text("কেউ না", fontSize = 12.sp) }
                        )
                        people.forEach { person ->
                            val labelText = if (!person.relationship.isNullOrBlank()) {
                                "${person.name} (${person.relationship})"
                            } else {
                                person.name
                            }
                            FilterChip(
                                selected = selectedPersonId == person.id,
                                onClick = { selectedPersonId = person.id },
                                label = { Text(labelText, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Editable Description
                OutlinedTextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it },
                    label = { Text("বিবরণ / নোট") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("draft_description_input"),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                AnimatedVisibility(visible = !formValidationError.isNullOrBlank()) {
                    formValidationError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            onClearDraft()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("বাতিল")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val parsedAmount = BengaliVoiceParser.normalizeBengaliDigits(editedAmountText)
                                .replace(",", "")
                                .trim()
                                .toDoubleOrNull() ?: 0.0
                            if (parsedAmount <= 0.0) {
                                formValidationError = "সঠিক টাকার পরিমাণ দিন (০ এর বেশি)।"
                                return@Button
                            }
                            val resolvedAccId = selectedAccountId.ifBlank {
                                accounts.firstOrNull()?.id.orEmpty()
                            }
                            if (editedType == "TRANSFER" && accounts.size > 1 && selectedToAccountId.isBlank()) {
                                formValidationError = "ট্রান্সফারের জন্য গন্তব্য অ্যাকাউন্ট বেছে নিন।"
                                return@Button
                            }
                            val resolvedCategory = categories.find { it.id == selectedCategoryId }
                            val resolvedPerson = people.find { it.id == selectedPersonId }
                            val resolvedAccount = accounts.find { it.id == resolvedAccId }

                            val finalDraft = draftTx.copy(
                                transcript = editedTranscript.trim(),
                                type = editedType,
                                amount = parsedAmount,
                                accountName = resolvedAccount?.name ?: draftTx.accountName,
                                category = resolvedCategory?.name ?: draftTx.category,
                                person = resolvedPerson?.name ?: if (selectedPersonId == null) "" else draftTx.person,
                                date = editedDate.trim().ifBlank { draftTx.date },
                                description = editedDescription.trim().ifBlank { editedTranscript.trim() }
                            )
                            onConfirm(
                                finalDraft,
                                resolvedAccId,
                                if (editedType == "TRANSFER") selectedToAccountId else null,
                                selectedCategoryId,
                                selectedPersonId
                            )
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .heightIn(min = 50.dp)
                            .testTag("confirm_voice_tx_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("নিশ্চিত করে সেভ করুন")
                    }
                }
            }
        }
    }
}
