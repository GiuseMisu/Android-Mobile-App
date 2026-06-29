package com.wildtrail.app.data.repository

import android.util.Log
import com.wildtrail.app.data.local.dao.EmergencyContactDao
import com.wildtrail.app.data.local.entity.toDomain
import com.wildtrail.app.data.local.entity.toEntity
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.dto.toDomain
import com.wildtrail.app.data.remote.dto.toDto
import com.wildtrail.app.domain.model.EmergencyContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class EmergencyContactRepository(
    private val dao: EmergencyContactDao,
    private val firestore: FirestoreService,
    private val externalScope: CoroutineScope,
) {

    fun observe(uid: String): Flow<List<EmergencyContact>> {
        firestore.observeEmergencyContacts(uid)
            .catch { Log.w(TAG, "Firestore emergency-contact listener errored", it) }
            .onEach { dtos -> dtos.forEach { dao.upsert(it.toDomain().toEntity()) } }
            .launchIn(externalScope)
        return dao.observeForUser(uid).map { list -> list.map { it.toDomain() } }
    }

    suspend fun upsert(contact: EmergencyContact) {
        dao.upsert(contact.toEntity())
        runCatching { firestore.upsertEmergencyContact(contact.toDto()) }
            .onFailure { Log.w(TAG, "Firestore contact upsert skipped", it) }
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
        runCatching { firestore.deleteEmergencyContact(id) }
            .onFailure { Log.w(TAG, "Firestore contact delete skipped", it) }
    }

    suspend fun getFallNotifyList(uid: String): List<EmergencyContact> =
        dao.getFallNotifyList(uid).map { it.toDomain() }

    private companion object { const val TAG = "EmergencyContactRepo" }
}
