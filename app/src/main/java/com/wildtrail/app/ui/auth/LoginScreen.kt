package com.wildtrail.app.ui.auth

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wildtrail.app.BuildConfig
import com.wildtrail.app.domain.model.DEFAULT_EMERGENCY_NUMBER
import com.wildtrail.app.domain.model.Sex
import com.wildtrail.app.ui.components.AuroraHeader
import com.wildtrail.app.ui.theme.WildTrailTheme
import com.wildtrail.app.util.Countries
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun LoginRoute(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleHelper = remember { GoogleSignInHelper(context) }
    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onUsernameChange = viewModel::onUsernameChanged,
        onSexChange = viewModel::onSexChanged,
        onDateOfBirthChange = viewModel::onDateOfBirthChanged,
        onCountryChange = viewModel::onCountryChanged,
        onBioChange = viewModel::onBioChanged,
        onEmergencyContactChange = viewModel::onEmergencyContactChanged,
        onProfilePictureChange = viewModel::onProfilePictureChanged,
        onToggleMode = viewModel::toggleMode,
        onSubmit = viewModel::submit,
        onGoogleSignIn = {
            viewModel.signInWithGoogle {
                googleHelper.requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoginContent(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSexChange: (Sex) -> Unit,
    onDateOfBirthChange: (Long) -> Unit,
    onCountryChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onEmergencyContactChange: (String) -> Unit,
    onProfilePictureChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            AuroraHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "WildTrail",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Track. Discover. Share.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            if (state.isSignUp) {
                ProfilePicturePicker(
                    uri = state.profilePictureUri,
                    onChange = onProfilePictureChange,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    "Sex *",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 4.dp),
                )
                SexSelector(
                    selected = state.sex,
                    onChange = onSexChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                DateOfBirthPicker(
                    millis = state.dateOfBirth,
                    onChange = onDateOfBirthChange,
                )
                Spacer(Modifier.height(12.dp))

                CountryDropdown(
                    value = state.country,
                    onChange = onCountryChange,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.bio,
                    onValueChange = onBioChange,
                    label = { Text("Short bio (optional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.emergencyContactNumber,
                    onValueChange = onEmergencyContactChange,
                    label = { Text("Emergency contact number (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    supportingText = {
                        Text("If left blank, the default $DEFAULT_EMERGENCY_NUMBER will be used.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Email *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Password *") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(if (state.isSignUp) "Create account" else "Log in")
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  or  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Continue with Google")
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onToggleMode) {
                Text(
                    if (state.isSignUp) "Already have an account? Log in"
                    else "No account yet? Sign up",
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfilePicturePicker(
    uri: String,
    onChange: (String) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { result: Uri? ->
        if (result != null) onChange(result.toString())
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (uri.isNotBlank()) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
        IconButton(
            onClick = {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        ) {
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = "Pick profile picture (optional)",
                tint = if (uri.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onPrimary,
            )
        }
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
                label = {
                    Text(
                        option.shortLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                if (millis == null) "Date of birth *"
                else "Born ${df.format(java.util.Date(millis))}",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryDropdown(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value) { mutableStateOf(value) }
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) Countries.ALL else Countries.ALL.filter { it.contains(q, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onChange(it)
                expanded = true
            },
            label = { Text("Country *") },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        if (filtered.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                scrollState = rememberScrollState(),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 320.dp),
            ) {
                filtered.forEach { country ->
                    DropdownMenuItem(
                        text = { Text(country) },
                        onClick = {
                            query = country
                            onChange(country)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun Sex.shortLabel(): String = when (this) {
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

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    WildTrailTheme {
        LoginContent(
            state = AuthUiState(email = "demo@wildtrail.com", password = "secret"),
            onEmailChange = {},
            onPasswordChange = {},
            onUsernameChange = {},
            onSexChange = {},
            onDateOfBirthChange = {},
            onCountryChange = {},
            onBioChange = {},
            onEmergencyContactChange = {},
            onProfilePictureChange = {},
            onToggleMode = {},
            onSubmit = {},
        )
    }
}