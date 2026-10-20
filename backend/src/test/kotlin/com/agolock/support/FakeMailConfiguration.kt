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

class FakeMailPort : MailPort {
    val messages = CopyOnWriteArrayList<SentMail>()

    override fun sendPasswordReset(
        email: String,
        displayName: String,
        token: String,
    ) {
        messages += SentMail(email, displayName, token)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FakeMailConfiguration {
    @Bean
    @Primary
    fun fakeMailPort(): FakeMailPort = FakeMailPort()
}
