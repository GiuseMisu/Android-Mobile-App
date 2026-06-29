package com.wildtrail.app.data.repository

import com.wildtrail.app.domain.model.User

internal fun User.keepingLocalPicture(local: User?): User {
    // keep a just-picked file:// pic until its upload swaps in the cross-device url
    val localPic = local?.profilePictureUrl
    return if (profilePictureUrl.isNullOrBlank() &&
        localPic != null &&
        localPic.startsWith("file://")
    ) {
        copy(profilePictureUrl = localPic)
    } else {
        this
    }
}
