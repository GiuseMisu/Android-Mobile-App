package com.wildtrail.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wildtrail.app.domain.model.DEFAULT_EMERGENCY_NUMBER
import com.wildtrail.app.domain.model.Sex
import androidx.compose.foundation.text.KeyboardOptions
import com.wildtrail.app.ui.profile.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(
        key = "profile/me",
        factory = ProfileViewModel.factory(targetUid = null),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val user = state.user

    var bio by remember(user?.bio) { mutableStateOf(user?.bio.orEmpty()) }
    var country by remember(user?.country) { mutableStateOf(user?.country.orEmpty()) }
    var emergency by remember(user?.emergencyContactNumber) {
        mutableStateOf(user?.emergencyContactNumber.orEmpty())
    }
    var sex by remember(user?.sex) { mutableStateOf(user?.sex) }
    var dob by remember(user?.dateOfBirth) { mutableStateOf(user?.dateOfBirth) }
    var savedFlash by remember { mutableStateOf(false) }

    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1_500L)
            savedFlash = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        if (user == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) { Text("Loading profile…") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            ProfilePictureEditor(
                currentUrl = user.profilePictureUrl,
                onPicked = { uri -> viewModel.changeProfilePicture(uri) },
                isUploading = state.uploadingPicture,
            )

            Text("Account", style = MaterialTheme.typography.titleLarge)
            ReadOnlyRow("Username", user.username)

            Spacer(Modifier.height(8.dp))
            Text("Editable", style = MaterialTheme.typography.titleLarge)

            Text("Sex", style = MaterialTheme.typography.labelLarge)
            SexSelector(
                selected = sex,
                onChange = { sex = it },
                modifier = Modifier.fillMaxWidth(),
            )

            DateOfBirthPicker(millis = dob, onChange = { dob = it })

            OutlinedTextField(
                value = country,
                onValueChange = { country = it },
                label = { Text("Country") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = emergency,
                onValueChange = { emergency = it.filter { ch -> ch.isDigit() || ch == '+' } },
                label = { Text("Emergency contact number") },
                placeholder = { Text("Default: $DEFAULT_EMERGENCY_NUMBER") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.updateProfile(
                        bio = bio.trim().takeIf { it.isNotEmpty() },
                        country = country.trim().takeIf { it.isNotEmpty() },
                        emergencyContactNumber = emergency.trim()
                            .takeIf { it.isNotEmpty() } ?: DEFAULT_EMERGENCY_NUMBER,
                        sex = sex,
                        dateOfBirth = dob,
                    )
                    savedFlash = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save changes") }

            if (savedFlash) {
                Text(
                    "Saved.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SexSelector(
    selected: Sex?,
    onChange: (Sex) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Sex.values().forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onChange(option) },
                label = { Text(option.label()) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthPicker(
    millis: Long?,
    onChange: (Long) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }
    val df = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Column {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                if (millis == null) "Date of birth"
                else "Born ${df.format(Date(millis))} · ${ageInYears(millis)} yrs",
            )
        }
        inlineError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }

    if (showPicker) {
        val now = System.currentTimeMillis()
        val minDob = remember { dobMillisYearsAgo(120) }
        val maxDob = remember { dobMillisYearsAgo(13) }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = millis ?: dobMillisYearsAgo(25),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in minDob..maxDob

                override fun isSelectableYear(year: Int): Boolean {
                    val nowYear = Calendar.getInstance().get(Calendar.YEAR)
                    return year in (nowYear - 120)..(nowYear - 13)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                Button(onClick = {
                    val chosen = state.selectedDateMillis
                    when {
                        chosen == null -> inlineError = "Pick a date"
                        chosen > now -> inlineError = "Date of birth cannot be in the future"
                        chosen > maxDob -> inlineError = "You must be at least 13 years old"
                        chosen < minDob -> inlineError = "Please pick a realistic date"
                        else -> {
                            inlineError = null
                            onChange(chosen)
                            showPicker = false
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
            colors = DatePickerDefaults.colors(),
        ) {
            DatePicker(state = state)
        }
    }
}

private fun Sex.label(): String = when (this) {
    Sex.MALE -> "Male"
    Sex.FEMALE -> "Female"
    Sex.OTHER -> "Other"
    Sex.PREFER_NOT_TO_SAY -> "N/A"
}

private fun dobMillisYearsAgo(years: Int): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.YEAR, -years)
    return cal.timeInMillis
}

@Composable
private fun ProfilePictureEditor(
    currentUrl: String?,
    onPicked: (Uri) -> Unit,
    isUploading: Boolean,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> if (uri != null) onPicked(uri) }
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(112.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (currentUrl != null) {
                AsyncImage(
                    model = currentUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(112.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp),
                )
            }
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                IconButton(
                    onClick = {
                        launcher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = "Change profile picture",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private fun ageInYears(dobMillis: Long): Int {
    val today = java.util.Calendar.getInstance()
    val dob = java.util.Calendar.getInstance().apply { timeInMillis = dobMillis }
    var age = today.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)
    if (today.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) age--
    return age.coerceAtLeast(0)
}
