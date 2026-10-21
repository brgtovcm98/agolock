package com.agolock.identity.mail

interface MailPort {
    fun sendPasswordReset(
        email: String,
        displayName: String,
        token: String,
    )

    fun sendEmailVerification(
        email: String,
        displayName: String,
        token: String,
    )

    fun sendShareInvitation(
        email: String,
        resourceType: String,
        token: String,
        expiresAt: java.time.OffsetDateTime,
    )
}
