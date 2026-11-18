package com.example.ui.transaction

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.Person

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditCategoryDialog(
    userId: String,
    existingCategory: Category? = null,
    defaultType: String = "EXPENSE",
    isBangla: Boolean = true,
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var catType by remember(existingCategory?.id, defaultType) {
        mutableStateOf(
            existingCategory?.type?.uppercase()?.takeIf { it == "INCOME" || it == "EXPENSE" }
                ?: if (defaultType.equals("INCOME", ignoreCase = true)) "INCOME" else "EXPENSE"
        )
    }
    var nameInput by remember(existingCategory?.id) {
        mutableStateOf(
            existingCategory?.let { it.nameBn.ifBlank { it.name } } ?: ""
        )
    }

    val quickSuggestions = remember(catType) {
        if (catType == "INCOME") {
            listOf("বেতন", "বোনাস", "ব্যবসা আয়", "ফ্রিল্যান্সিং", "ভাড়া আয়", "টিউশনি", "উপহার", "মুনাফা")
        } else {
            listOf("বাজার ও খাবার", "বাড়ি ভাড়া", "বিদ্যুৎ ও গ্যাস", "যাতায়াত", "চিকিৎসা", "শিক্ষা", "শপিং", "পারিবারিক")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingCategory != null) {
                    if (isBangla) "ক্যাটাগরি এডিট করুন" else "Edit Category"
                } else {
                    if (isBangla) "নতুন ক্যাটাগরি যোগ করুন" else "Add Custom Category"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isBangla) "ক্যাটাগরির ধরন:" else "Category Type:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = catType == "EXPENSE",
                        onClick = { catType = "EXPENSE" },
                        label = { Text(if (isBangla) "খরচ (Expense)" else "Expense", fontSize = 12.sp) },
                        modifier = Modifier.testTag("cat_type_expense")
                    )
                    FilterChip(
                        selected = catType == "INCOME",
                        onClick = { catType = "INCOME" },
                        label = { Text(if (isBangla) "আয় (Income)" else "Income", fontSize = 12.sp) },
                        modifier = Modifier.testTag("cat_type_income")
                    )
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(if (isBangla) "ক্যাটাগরির নাম *" else "Category Name *") },
                    placeholder = {
                        Text(
                            if (catType == "INCOME") "যেমন: টিউশনি, বোনাস, দোকান আয়"
                            else "যেমন: নাস্তা, মোবাইল রিচার্জ, গাড়ি মেরামত"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_category_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = if (isBangla) "দ্রুত পছন্দ (ট্যাপ করে বসান):" else "Quick Suggestions:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSuggestions.forEach { suggestion ->
                        FilterChip(
                            selected = nameInput.trim() == suggestion,
                            onClick = { nameInput = suggestion },
                            label = { Text(suggestion, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = nameInput.trim()
                    if (clean.isNotBlank()) {
                        onSave(
                            Category(
                                id = existingCategory?.id ?: "",
                                userId = existingCategory?.userId?.ifBlank { userId } ?: userId,
                                name = clean,
                                nameBn = clean,
                                type = catType,
                                icon = existingCategory?.icon ?: "category",
                                color = existingCategory?.color ?: "#006C4C",
                                isCustom = true,
                                createdAt = existingCategory?.createdAt
                            )
                        )
                    }
                },
                enabled = nameInput.trim().isNotBlank(),
                modifier = Modifier.testTag("save_custom_category_button")
            ) {
                Text(if (isBangla) "সংরক্ষণ করুন" else "Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (isBangla) "বাতিল" else "Cancel")
            }
        }
    )
}

@Composable
fun ManageCategoriesDialog(
    userId: String,
    categories: List<Category>,
    initialType: String = "EXPENSE",
    isBangla: Boolean = true,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeType by remember(initialType) {
        mutableStateOf(if (initialType.equals("INCOME", ignoreCase = true)) "INCOME" else "EXPENSE")
    }
    var showAddEdit by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    val filtered = categories.filter { it.type.equals(activeType, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBangla) "ক্যাটাগরি সেটআপ" else "Manage Categories",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeType == "EXPENSE",
                        onClick = { activeType = "EXPENSE" },
                        label = {
                            Text(
                                if (isBangla) "খরচ (${categories.count { !it.type.equals("INCOME", true) }})" else "Expense",
                                fontSize = 12.sp
                            )
                        }
                    )
                    FilterChip(
                        selected = activeType == "INCOME",
                        onClick = { activeType = "INCOME" },
                        label = {
                            Text(
                                if (isBangla) "আয় (${categories.count { it.type.equals("INCOME", true) }})" else "Income",
                                fontSize = 12.sp
                            )
                        }
                    )
                }

                Button(
                    onClick = {
                        editingCategory = null
                        showAddEdit = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("manage_add_category_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) "নতুন ক্যাটাগরি যোগ করুন" else "Add Category")
                }

                if (filtered.isEmpty()) {
                    Text(
                        text = if (isBangla) "এই ধরনে কোনো ক্যাটাগরি নেই। উপরে ট্যাপ করে যোগ করুন।" else "No categories in this type.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id.ifBlank { it.name + it.type } }) { cat ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.nameBn.ifBlank { cat.name },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (cat.nameBn.isNotBlank() && cat.name.isNotBlank() && cat.nameBn != cat.name) {
                                            Text(
                                                text = cat.name,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            editingCategory = cat
                                            showAddEdit = true
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_category_${cat.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = if (isBangla) "এডিট" else "Edit",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { deletingCategory = cat },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_category_${cat.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = if (isBangla) "মুছুন" else "Delete",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isBangla) "সম্পন্ন" else "Done")
            }
        }
    )

    if (showAddEdit) {
        AddEditCategoryDialog(
            userId = userId,
            existingCategory = editingCategory,
            defaultType = activeType,
            isBangla = isBangla,
            onSave = { cat ->
                onSaveCategory(cat)
                showAddEdit = false
                editingCategory = null
            },
            onDismiss = {
                showAddEdit = false
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text(if (isBangla) "ক্যাটাগরি মুছে ফেলুন" else "Delete Category") },
            text = {
                Text(
                    if (isBangla) "'${target.nameBn.ifBlank { target.name }}' ক্যাটাগরিটি মুছে ফেলতে চান?"
                    else "Delete category '${target.name}'?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target.id.isNotBlank()) onDeleteCategory(target.id)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                ) {
                    Text(if (isBangla) "মুছে ফেলুন" else "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingCategory = null }) {
                    Text(if (isBangla) "বাতিল" else "Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditPersonDialog(
    userId: String,
    existingPerson: Person? = null,
    isBangla: Boolean = true,
    onSave: (Person) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(existingPerson?.id) {
        mutableStateOf(existingPerson?.name ?: "")
    }
    var relationshipInput by remember(existingPerson?.id) {
        mutableStateOf(existingPerson?.relationship ?: "")
    }

    val quickRelations = remember {
        listOf("পরিবার", "বাবা", "মা", "স্ত্রী/স্বামী", "ভাই/বোন", "সন্তান", "আত্মীয়", "বন্ধু", "সহকর্মী", "ক্লায়েন্ট", "ব্যবসা", "ব্যক্তিগত")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingPerson != null) {
                    if (isBangla) "ব্যক্তি / সম্পর্ক এডিট করুন" else "Edit Person"
                } else {
                    if (isBangla) "নতুন ব্যক্তি / সম্পর্ক যোগ করুন" else "Add Person / Relation"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(if (isBangla) "ব্যক্তির নাম বা পরিচয় *" else "Person Name *") },
                    placeholder = { Text("যেমন: করিম সাহেব, ছোট বোন, অফিস") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_person_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = relationshipInput,
                    onValueChange = { relationshipInput = it },
                    label = { Text(if (isBangla) "সম্পর্ক (ঐচ্ছিক)" else "Relationship (Optional)") },
                    placeholder = { Text("যেমন: বন্ধু, পরিবার, সহকর্মী") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_person_relationship_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = if (isBangla) "সম্পর্ক বেছে নিন (ট্যাপ করুন):" else "Quick Relationships:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickRelations.forEach { rel ->
                        FilterChip(
                            selected = relationshipInput.trim() == rel,
                            onClick = {
                                relationshipInput = rel
                                if (nameInput.isBlank()) {
                                    nameInput = rel
                                }
                            },
                            label = { Text(rel, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = nameInput.trim()
                    if (cleanName.isNotBlank()) {
                        onSave(
                            Person(
                                id = existingPerson?.id ?: "",
                                userId = existingPerson?.userId?.ifBlank { userId } ?: userId,
                                name = cleanName,
                                relationship = relationshipInput.trim().ifBlank { null },
                                createdAt = existingPerson?.createdAt
                            )
                        )
                    }
                },
                enabled = nameInput.trim().isNotBlank(),
                modifier = Modifier.testTag("save_custom_person_button")
            ) {
                Text(if (isBangla) "সংরক্ষণ করুন" else "Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (isBangla) "বাতিল" else "Cancel")
            }
        }
    )
}

@Composable
fun ManagePeopleDialog(
    userId: String,
    people: List<Person>,
    isBangla: Boolean = true,
    onSavePerson: (Person) -> Unit,
    onDeletePerson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddEdit by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }
    var deletingPerson by remember { mutableStateOf<Person?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isBangla) "ব্যক্তি ও সম্পর্ক সেটআপ" else "Manage People & Relations",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        editingPerson = null
                        showAddEdit = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("manage_add_person_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) "নতুন ব্যক্তি / সম্পর্ক যোগ করুন" else "Add Person / Relation")
                }

                if (people.isEmpty()) {
                    Text(
                        text = if (isBangla) "কোনো ব্যক্তি যোগ করা নেই। উপরে ট্যাপ করে যোগ করুন।" else "No people added yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(people, key = { it.id.ifBlank { it.name } }) { person ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = person.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (!person.relationship.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    text = person.relationship,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            editingPerson = person
                                            showAddEdit = true
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_person_${person.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = if (isBangla) "এডিট" else "Edit",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { deletingPerson = person },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_person_${person.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = if (isBangla) "মুছুন" else "Delete",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isBangla) "সম্পন্ন" else "Done")
            }
        }
    )

    if (showAddEdit) {
        AddEditPersonDialog(
            userId = userId,
            existingPerson = editingPerson,
            isBangla = isBangla,
            onSave = { p ->
                onSavePerson(p)
                showAddEdit = false
                editingPerson = null
            },
            onDismiss = {
                showAddEdit = false
                editingPerson = null
            }
        )
    }

    deletingPerson?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingPerson = null },
            title = { Text(if (isBangla) "ব্যক্তি মুছে ফেলুন" else "Delete Person") },
            text = {
                Text(
                    if (isBangla) "'${target.name}' কে তালিকা থেকে মুছে ফেলতে চান?"
                    else "Remove '${target.name}' from your list?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target.id.isNotBlank()) onDeletePerson(target.id)
                        deletingPerson = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                ) {
                    Text(if (isBangla) "মুছে ফেলুন" else "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingPerson = null }) {
                    Text(if (isBangla) "বাতিল" else "Cancel")
                }
            }
        )
    }
}
