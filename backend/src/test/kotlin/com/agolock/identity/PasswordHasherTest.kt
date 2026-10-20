package com.agolock.identity

import com.agolock.identity.service.PasswordHasher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher()

    @Test
    fun `Argon2id 해시는 원문과 다르고 검증된다`() {
        val encoded = hasher.hash("password-1234")

        assertThat(encoded).startsWith("{argon2}")
        assertThat(encoded).doesNotContain("password-1234")
        assertThat(hasher.matches("password-1234", encoded)).isTrue()
        assertThat(hasher.matches("wrong-password", encoded)).isFalse()
    }

    @Test
    fun `같은 비밀번호도 매번 다른 salt를 사용한다`() {
        assertThat(hasher.hash("password-1234")).isNotEqualTo(hasher.hash("password-1234"))
    }
}
