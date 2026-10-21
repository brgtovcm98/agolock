package com.agolock.support

import com.agolock.identity.mail.MailPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

data class SentMail(
    val email: String,
    val displayName: String,
    val token: String,
)

data class SentVerificationMail(
    val email: String,
    val displayName: String,
    val token: String,
)

data class SentInvitationMail(
    val email: String,
    val resourceType: String,
    val token: String,
)

class FakeMailPort : MailPort {
    val messages = CopyOnWriteArrayList<SentMail>()
    val invitationMessages = CopyOnWriteArrayList<SentInvitationMail>()
    val verificationMessages = CopyOnWriteArrayList<SentVerificationMail>()

    override fun sendPasswordReset(
        email: String,
        displayName: String,
        token: String,
    ) {
        messages += SentMail(email, displayName, token)
    }

    override fun sendEmailVerification(
        email: String,
        displayName: String,
        token: String,
    ) {
        verificationMessages += SentVerificationMail(email, displayName, token)
    }

    override fun sendShareInvitation(
        email: String,
        resourceType: String,
        token: String,
        expiresAt: java.time.OffsetDateTime,
    ) {
        invitationMessages += SentInvitationMail(email, resourceType, token)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FakeMailConfiguration {
    @Bean
    @Primary
    fun fakeMailPort(): FakeMailPort = FakeMailPort()
}
