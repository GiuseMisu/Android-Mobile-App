package com.wildtrail.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.domain.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val sex: Sex? = null,
    val dateOfBirth: Long? = null,
    val country: String = "",
    val bio: String = "",
    val emergencyContactNumber: String = "",
    val profilePictureUri: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onUsernameChanged(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onSexChanged(value: Sex) = _uiState.update { it.copy(sex = value, errorMessage = null) }
    fun onDateOfBirthChanged(value: Long) = _uiState.update { it.copy(dateOfBirth = value, errorMessage = null) }
    fun onCountryChanged(value: String) = _uiState.update { it.copy(country = value, errorMessage = null) }
    fun onBioChanged(value: String) = _uiState.update { it.copy(bio = value) }
    fun onEmergencyContactChanged(value: String) =
        _uiState.update { it.copy(emergencyContactNumber = value, errorMessage = null) }
    fun onProfilePictureChanged(value: String) = _uiState.update { it.copy(profilePictureUri = value) }
    fun toggleMode() = _uiState.update { it.copy(isSignUp = !it.isSignUp, errorMessage = null) }

    fun signInWithGoogle(idTokenProvider: suspend () -> Result<String>) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val tokenResult = idTokenProvider()
            tokenResult
                .onSuccess { token ->
                    val r = authRepository.signInWithGoogleIdToken(token)
                    r.onSuccess { _uiState.update { it.copy(isLoading = false) } }
                        .onFailure { err ->
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = err.message ?: "Google sign-in failed")
                            }
                        }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Google sign-in cancelled")
                    }
                }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Enter an email and at least 6 chars of password") }
            return
        }
        if (state.isSignUp) {
            if (state.username.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Username is required") }
                return
            }
            if (state.sex == null) {
                _uiState.update { it.copy(errorMessage = "Please select a sex option") }
                return
            }
            if (state.dateOfBirth == null) {
                _uiState.update { it.copy(errorMessage = "Date of birth is required") }
                return
            }
            if (state.country.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Country is required") }
                return
            }
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = if (state.isSignUp) {
                val pictureUri = state.profilePictureUri.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { android.net.Uri.parse(it) }
                authRepository.signUp(
                    email = state.email.trim(),
                    password = state.password,
                    username = state.username.trim(),
                    sex = state.sex!!,
                    dateOfBirth = state.dateOfBirth!!,
                    country = state.country.trim(),
                    bio = state.bio.trim().takeIf { it.isNotEmpty() },
                    profilePictureUri = pictureUri,
                    emergencyContactNumber = state.emergencyContactNumber.trim()
                        .takeIf { it.isNotEmpty() },
                )
            } else {
                authRepository.signIn(state.email.trim(), state.password)
            }
            result
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Authentication failed")
                    }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                AuthViewModel(app.container.authRepository)
            }
        }
    }
}
