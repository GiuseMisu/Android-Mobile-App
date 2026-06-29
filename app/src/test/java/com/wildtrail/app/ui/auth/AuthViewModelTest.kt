package com.wildtrail.app.ui.auth

import app.cash.turbine.test
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.testing.FakeAuthRepository
import com.wildtrail.app.testing.testUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepo: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeAuthRepository()
        viewModel = AuthViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is the default`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("", state.email)
            assertEquals("", state.password)
            assertFalse(state.isSignUp)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `submit with empty fields sets validation error`() = runTest {
        viewModel.submit()
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals(0, fakeRepo.signInCalls)
        assertEquals(0, fakeRepo.signUpCalls)
    }

    @Test
    fun `submit triggers signIn in login mode`() = runTest {
        viewModel.onEmailChanged("a@b.com")
        viewModel.onPasswordChanged("verysecret")
        viewModel.submit()
        assertEquals(1, fakeRepo.signInCalls)
        assertEquals(0, fakeRepo.signUpCalls)
    }

    @Test
    fun `submit triggers signUp in signUp mode`() = runTest {
        viewModel.toggleMode()
        viewModel.onEmailChanged("a@b.com")
        viewModel.onPasswordChanged("verysecret")
        viewModel.onUsernameChanged("aldo")
        viewModel.onSexChanged(com.wildtrail.app.domain.model.Sex.MALE)
        viewModel.onDateOfBirthChanged(0L)
        viewModel.onCountryChanged("IT")
        viewModel.submit()
        assertEquals(0, fakeRepo.signInCalls)
        assertEquals(1, fakeRepo.signUpCalls)
    }

    @Test
    fun `repository failure populates errorMessage`() = runTest {
        fakeRepo.nextResult = Result.failure(IllegalStateException("Invalid credentials"))
        viewModel.onEmailChanged("a@b.com")
        viewModel.onPasswordChanged("verysecret")
        viewModel.submit()
        assertEquals("Invalid credentials", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `repository success leaves errorMessage null`() = runTest {
        fakeRepo.nextResult = Result.success(testUser())
        viewModel.onEmailChanged("a@b.com")
        viewModel.onPasswordChanged("verysecret")
        viewModel.submit()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
