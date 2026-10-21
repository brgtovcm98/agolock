package com.agolock.common.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@ConfigurationProperties(prefix = "auth")
@Validated
class AuthProperties {
    @field:Valid
    var signup: Signup = Signup()

    @field:Valid
    var session: Session = Session()

    @field:Valid
    var passwordReset: PasswordReset = PasswordReset()

    @field:Valid
    var login: Login = Login()

    @field:Valid
    var invitation: Invitation = Invitation()

    class Signup {
        var inviteRequired: Boolean = true
    }

    class Session {
        @field:NotNull
        var timeout: Duration = Duration.ofMinutes(DEFAULT_SESSION_MINUTES)

        @field:Min(1)
        var max: Int = DEFAULT_MAX_SESSIONS
    }

    class PasswordReset {
        @field:NotNull
        var ttl: Duration = Duration.ofMinutes(DEFAULT_RESET_MINUTES)
    }

    class Invitation {
        var emailDiscoveryEnabled: Boolean = false
    }

    class Login {
        @field:Min(1)
        var maxFailures: Int = DEFAULT_MAX_FAILURES

        @field:NotNull
        var window: Duration = Duration.ofMinutes(DEFAULT_WINDOW_MINUTES)

        @field:NotNull
        var lockDuration: Duration = Duration.ofMinutes(DEFAULT_LOCK_MINUTES)
    }

    private companion object {
        const val DEFAULT_SESSION_MINUTES = 30L
        const val DEFAULT_MAX_SESSIONS = 5
        const val DEFAULT_RESET_MINUTES = 30L
        const val DEFAULT_MAX_FAILURES = 10
        const val DEFAULT_WINDOW_MINUTES = 15L
        const val DEFAULT_LOCK_MINUTES = 15L
    }
}
