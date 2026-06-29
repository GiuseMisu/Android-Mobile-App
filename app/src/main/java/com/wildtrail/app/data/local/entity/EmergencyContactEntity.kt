package com.wildtrail.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wildtrail.app.domain.model.EmergencyContact

@Entity(
    tableName = "emergency_contacts",
    indices = [Index("userUid")],
)
data class EmergencyContactEntity(
    @PrimaryKey val contactId: String,
    val userUid: String,
    val name: String,
    val phoneNumber: String,
    val relationship: String?,
    val isPrimary: Boolean,
    val notifyOnFall: Boolean,
)

fun EmergencyContactEntity.toDomain(): EmergencyContact = EmergencyContact(
    contactId = contactId,
    userUid = userUid,
    name = name,
    phoneNumber = phoneNumber,
    relationship = relationship,
    isPrimary = isPrimary,
    notifyOnFall = notifyOnFall,
)

fun EmergencyContact.toEntity(): EmergencyContactEntity = EmergencyContactEntity(
    contactId = contactId,
    userUid = userUid,
    name = name,
    phoneNumber = phoneNumber,
    relationship = relationship,
    isPrimary = isPrimary,
    notifyOnFall = notifyOnFall,
)
