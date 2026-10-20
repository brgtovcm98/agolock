package com.agolock.identity.mail

data class PasswordResetRequestedEvent(
    val email: String,
    val displayName: String,
    val token: String,
)
