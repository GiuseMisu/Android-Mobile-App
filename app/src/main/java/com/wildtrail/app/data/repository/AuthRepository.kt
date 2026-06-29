package com.wildtrail.app.data.repository

import android.net.Uri
import android.util.Log
import com.wildtrail.app.data.local.dao.UserDao
import com.wildtrail.app.data.local.entity.toDomain
import com.wildtrail.app.data.local.entity.toEntity
import com.wildtrail.app.data.remote.FirebaseAuthService
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.StorageService
import com.wildtrail.app.data.remote.dto.toDomain
import com.wildtrail.app.data.remote.dto.toDto
import com.wildtrail.app.domain.model.DEFAULT_EMERGENCY_NUMBER
import com.wildtrail.app.domain.model.Sex
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.util.LocalImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: User) : AuthState
}

open class AuthRepository(
    private val authService: FirebaseAuthService,
    private val firestore: FirestoreService,
    private val storage: StorageService,
    private val imageStore: LocalImageStore,
    private val userDao: UserDao,
    private val externalScope: CoroutineScope,
) {

    private val _isInitialised = MutableStateFlow(false)
    open val isInitialised: StateFlow<Boolean> = _isInitialised.asStateFlow()

    open val authState: StateFlow<AuthState> = authService.authStateFlow()
        .map { firebaseUser ->
            if (firebaseUser == null) {
                _isInitialised.value = true
                AuthState.SignedOut
            } else {
                val cached = userDao.getById(firebaseUser.uid)?.toDomain()
                externalScope.launch {
                    runCatching {
                        val remote = firestore.getUser(firebaseUser.uid)?.toDomain()
                        if (remote != null) {
                            val local = userDao.getById(firebaseUser.uid)?.toDomain()
                            userDao.upsert(remote.keepingLocalPicture(local).toEntity())
                        }
                    }.onFailure { Log.w(TAG, "Background user refresh failed", it) }
                }
                _isInitialised.value = true
                AuthState.SignedIn(cached ?: bootstrapUser(firebaseUser.uid, firebaseUser.email))
            }
        }
        .stateIn(externalScope, SharingStarted.Eagerly, AuthState.Loading)

    open suspend fun signIn(email: String, password: String): Result<User> = runCatching {
        val fbUser = authService.signIn(email, password)
        val remote = runCatching { firestore.getUser(fbUser.uid)?.toDomain() }.getOrNull()
        val local = userDao.getById(fbUser.uid)?.toDomain()
        val user = remote?.keepingLocalPicture(local)
            ?: local
            ?: bootstrapUser(fbUser.uid, fbUser.email)
        userDao.upsert(user.toEntity())
        user
    }

    open suspend fun signUp(
        email: String,
        password: String,
        username: String,
        sex: Sex,
        dateOfBirth: Long,
        country: String,
        bio: String? = null,
        profilePictureUri: Uri? = null,
        emergencyContactNumber: String? = null,
    ): Result<User> = runCatching {
        val fbUser = authService.signUp(email, password)
        val localFile = profilePictureUri?.let { uri ->
            runCatching { imageStore.saveProfilePicture(fbUser.uid, uri) }
                .onFailure { Log.w(TAG, "Local profile picture copy failed", it) }
                .getOrNull()
        }
        val localPath = localFile?.let { Uri.fromFile(it).toString() }
        val now = System.currentTimeMillis()
        val user = User(
            firebaseUid = fbUser.uid,
            username = username,
            sex = sex,
            dateOfBirth = dateOfBirth,
            country = country,
            level = 1,
            xpPoints = 0,
            totalDistanceKm = 0f,
            totalHikesCount = 0,
            profilePictureUrl = localPath,
            bio = bio,
            emergencyContactNumber = emergencyContactNumber?.takeIf { it.isNotBlank() }
                ?: DEFAULT_EMERGENCY_NUMBER,
            createdAt = now,
            lastActive = now,
            isPublic = true,
        )
        userDao.upsert(user.toEntity())
        // never send the local file:// path to Firestore; the https url follows after upload
        runCatching { firestore.upsertUser(user.copy(profilePictureUrl = null).toDto()) }
            .onFailure { Log.w(TAG, "Firestore profile sync skipped on signUp", it) }

        val uploadSource = localFile?.let(Uri::fromFile) ?: profilePictureUri
        if (uploadSource != null) {
            // app scope, not viewModelScope: sign-up flips the auth state and navigates
            // away, which would cancel the upload before it finishes
            externalScope.launch {
                runCatching {
                    val url = storage.uploadProfilePicture(fbUser.uid, uploadSource)
                    val latest = userDao.getById(fbUser.uid)?.toDomain() ?: user
                    val patched = latest.copy(profilePictureUrl = url)
                    userDao.upsert(patched.toEntity())
                    runCatching { firestore.upsertUser(patched.toDto()) }
                        .onFailure { Log.w(TAG, "Firestore picture URL sync skipped on signUp", it) }
                }.onFailure { Log.w(TAG, "Profile picture upload skipped", it) }
            }
        }
        user
    }

    open fun signOut() = authService.signOut()

    open fun currentEmail(): String? = authService.currentEmail

    open suspend fun changePassword(newPassword: String): Result<Unit> = runCatching {
        authService.updatePassword(newPassword)
    }

    open suspend fun changeEmail(newEmail: String): Result<Unit> = runCatching {
        authService.sendEmailChangeVerification(newEmail)
    }

    open suspend fun signInWithGoogleIdToken(idToken: String): Result<User> = runCatching {
        val fbUser = authService.signInWithGoogleIdToken(idToken)
        val remote = runCatching { firestore.getUser(fbUser.uid)?.toDomain() }.getOrNull()
        val user = if (remote != null) {
            remote
        } else {
            val now = System.currentTimeMillis()
            val fresh = User(
                firebaseUid = fbUser.uid,
                username = fbUser.displayName?.takeIf { it.isNotBlank() }
                    ?: fbUser.email?.substringBefore("@")
                    ?: "hiker",
                sex = null,
                dateOfBirth = null,
                country = null,
                level = 1,
                xpPoints = 0,
                totalDistanceKm = 0f,
                totalHikesCount = 0,
                profilePictureUrl = fbUser.photoUrl?.toString(),
                bio = null,
                emergencyContactNumber = DEFAULT_EMERGENCY_NUMBER,
                createdAt = now,
                lastActive = now,
                isPublic = true,
            )
            runCatching { firestore.upsertUser(fresh.toDto()) }
                .onFailure { Log.w(TAG, "Firestore profile sync skipped on Google sign-in", it) }
            fresh
        }
        userDao.upsert(user.toEntity())
        user
    }

    open fun observeUser(uid: String) = userDao.observeById(uid).map { it?.toDomain() }

    private suspend fun bootstrapUser(uid: String, email: String?): User {
        val now = System.currentTimeMillis()
        return User(
            firebaseUid = uid,
            username = email?.substringBefore("@") ?: "hiker",
            sex = null,
            dateOfBirth = null,
            country = null,
            level = 1,
            xpPoints = 0,
            totalDistanceKm = 0f,
            totalHikesCount = 0,
            profilePictureUrl = null,
            bio = null,
            emergencyContactNumber = DEFAULT_EMERGENCY_NUMBER,
            createdAt = now,
            lastActive = now,
            isPublic = true,
        ).also { userDao.upsert(it.toEntity()) }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
