package com.wildtrail.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

open class FirebaseAuthService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    open val currentUser: FirebaseUser? get() = auth.currentUser

    open fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    open suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Firebase returned a null user after sign-in")
    }

    open suspend fun signUp(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: error("Firebase returned a null user after sign-up")
    }

    open fun signOut() = auth.signOut()

    open val currentEmail: String? get() = auth.currentUser?.email

    open suspend fun updatePassword(newPassword: String) {
        val user = auth.currentUser ?: error("Not signed in")
        user.updatePassword(newPassword).await()
    }

    open suspend fun sendEmailChangeVerification(newEmail: String) {
        val user = auth.currentUser ?: error("Not signed in")
        user.verifyBeforeUpdateEmail(newEmail).await()
    }

    open suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: error("Firebase returned a null user after Google sign-in")
    }
}
