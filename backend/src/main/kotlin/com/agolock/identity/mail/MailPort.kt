package com.agolock.identity.mail

interface MailPort {
    fun sendPasswordReset(
        email: String,
        displayName: String,
        token: String,
    )
}
