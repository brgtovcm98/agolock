package com.agolock.identity

import com.agolock.identity.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TokenServiceTest {
    private val tokenService = TokenService()

    @Test
    fun `초대코드는 AGO 형식이고 정규화 후 해시가 안정적이다`() {
        val code = tokenService.createInviteCode()

        assertThat(code).matches("^AGO-[0-9A-Z]{4}(-[0-9A-Z]{4}){4}$")
        assertThat(tokenService.normalizeInviteCode(code)).startsWith("AGO").hasSize(23)
        assertThat(tokenService.hashInviteCode(code)).isEqualTo(tokenService.hashInviteCode(code.lowercase()))
        assertThat(tokenService.hashInviteCode(code))
            .isNotEqualTo(tokenService.hashInviteCode("AGO-0000-0000-0000-0000-0000"))
    }

    @Test
    fun `재설정 토큰은 매번 다르고 해시가 일치한다`() {
        val first = tokenService.createToken()
        val second = tokenService.createToken()

        assertThat(first).isNotEqualTo(second)
        assertThat(tokenService.hashToken(first)).isEqualTo(tokenService.hashToken(first))
        assertThat(tokenService.hashToken(first)).isNotEqualTo(tokenService.hashToken(second))
    }
}
