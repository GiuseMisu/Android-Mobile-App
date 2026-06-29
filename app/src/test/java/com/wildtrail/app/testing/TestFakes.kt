package com.wildtrail.app.testing

import com.wildtrail.app.data.local.dao.UserDao
import com.wildtrail.app.data.local.entity.UserEntity
import com.wildtrail.app.data.remote.FirebaseAuthService
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.domain.model.DEFAULT_EMERGENCY_NUMBER
import com.wildtrail.app.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

fun testUser(
    uid: String = "uid-1",
    name: String = "tester",
): User = User(
    firebaseUid = uid,
    username = name,
    sex = null,
    dateOfBirth = null,
    country = "IT",
    level = 1,
    xpPoints = 0,
    totalDistanceKm = 0f,
    totalHikesCount = 0,
    profilePictureUrl = null,
    bio = null,
    emergencyContactNumber = DEFAULT_EMERGENCY_NUMBER,
    createdAt = 0L,
    lastActive = 0L,
    isPublic = true,
)

class FakeUserDao : UserDao {
    private val state = MutableStateFlow<Map<String, UserEntity>>(emptyMap())

    override suspend fun upsert(user: UserEntity) {
        state.value = state.value + (user.firebaseUid to user)
    }

    override suspend fun upsertAll(users: List<UserEntity>) {
        state.value = state.value + users.associateBy { it.firebaseUid }
    }

    override suspend fun update(user: UserEntity) = upsert(user)

    override suspend fun getById(uid: String): UserEntity? = state.value[uid]

    override fun observeById(uid: String): Flow<UserEntity?> =
        state.map { it[uid] }

    override fun observeLeaderboard(limit: Int): Flow<List<UserEntity>> =
        state.map { it.values.toList() }

    override suspend fun searchByUsername(q: String): List<UserEntity> =
        state.value.values.filter { it.username.contains(q, ignoreCase = true) }

    override suspend fun deleteById(uid: String) {
        state.value = state.value - uid
    }

    override suspend fun clear() {
        state.value = emptyMap()
    }
}

fun stubAuthService(): FirebaseAuthService = mock {
    on { authStateFlow() } doReturn flowOf(null)
}

fun stubFirestoreService(): FirestoreService = FirestoreService(db = null)

fun stubStorageService(): com.wildtrail.app.data.remote.StorageService =
    object : com.wildtrail.app.data.remote.StorageService() {
        override suspend fun uploadProfilePicture(uid: String, localUri: android.net.Uri): String =
            "https://example.test/$uid.jpg"
    }

fun stubLocalImageStore(): com.wildtrail.app.util.LocalImageStore =
    object : com.wildtrail.app.util.LocalImageStore(mock()) {
        override suspend fun saveProfilePicture(uid: String, srcUri: android.net.Uri) =
            java.io.File("/tmp/$uid.jpg")
    }

class FakeAuthRepository : AuthRepository(
    authService = stubAuthService(),
    firestore = stubFirestoreService(),
    storage = stubStorageService(),
    imageStore = stubLocalImageStore(),
    userDao = FakeUserDao(),
    externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState

    var nextResult: Result<User> = Result.success(testUser())
    var signInCalls = 0
    var signUpCalls = 0
    var signOutCalls = 0

    override suspend fun signIn(email: String, password: String): Result<User> {
        signInCalls++
        nextResult.onSuccess { _authState.value = AuthState.SignedIn(it) }
        return nextResult
    }

    override suspend fun signUp(
        email: String,
        password: String,
        username: String,
        sex: com.wildtrail.app.domain.model.Sex,
        dateOfBirth: Long,
        country: String,
        bio: String?,
        profilePictureUri: android.net.Uri?,
        emergencyContactNumber: String?,
    ): Result<User> {
        signUpCalls++
        nextResult.onSuccess { _authState.value = AuthState.SignedIn(it) }
        return nextResult
    }

    override fun signOut() {
        signOutCalls++
        _authState.value = AuthState.SignedOut
    }
}
