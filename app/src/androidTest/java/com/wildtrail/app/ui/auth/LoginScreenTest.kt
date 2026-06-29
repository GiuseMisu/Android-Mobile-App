package com.wildtrail.app.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wildtrail.app.ui.theme.WildTrailTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun showsLoginByDefault() {
        composeTestRule.setContent {
            WildTrailTheme {
                LoginContent(
                    state = AuthUiState(),
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
        composeTestRule.onNodeWithText("Log in").assertIsDisplayed()
    }

    @Test
    fun toggleModeShowsSignUpLabel() {
        composeTestRule.setContent {
            WildTrailTheme {
                LoginContent(
                    state = AuthUiState(isSignUp = true),
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
        composeTestRule.onNodeWithText("Create account").assertIsDisplayed()
    }

    @Test
    fun clickingPrimaryButtonInvokesSubmit() {
        var submitted = false
        composeTestRule.setContent {
            WildTrailTheme {
                LoginContent(
                    state = AuthUiState(email = "a@b.com", password = "secret123"),
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
                    onSubmit = { submitted = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Log in").performClick()
        assertTrue("onSubmit should have been invoked", submitted)
    }
}
