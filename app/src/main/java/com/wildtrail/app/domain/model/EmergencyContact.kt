package com.wildtrail.app.domain.model

data class EmergencyContact(
    val contactId: String,
    val userUid: String,
    val name: String,
    val phoneNumber: String,
    val relationship: String?,
    val isPrimary: Boolean,
    val notifyOnFall: Boolean,
)
