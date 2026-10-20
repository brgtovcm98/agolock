package com.agolock.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RequestIdFilterTest {
    private val filter = RequestIdFilter(Clock.fixed(Instant.ofEpochMilli(1_700_000_000_123L), ZoneOffset.UTC))

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `요청 ID가 없으면 UUIDv7을 만들고 응답 헤더에 넣는다`() {
        val request = MockHttpServletRequest("GET", "/api/v1/info")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        val requestId = response.getHeader(RequestIdFilter.HEADER)
        assertThat(requestId).isNotNull()
        assertThat(requestId).matches("[0-9a-f-]{36}")
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull()
    }

    @Test
    fun `유효한 요청 ID는 그대로 전달한다`() {
        val request = MockHttpServletRequest("GET", "/api/v1/info")
        request.addHeader(RequestIdFilter.HEADER, "client-request-1234")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-request-1234")
    }

    @Test
    fun `위험한 문자가 있는 요청 ID는 새로 만든다`() {
        val request = MockHttpServletRequest("GET", "/api/v1/info")
        request.addHeader(RequestIdFilter.HEADER, "bad\nvalue")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotEqualTo("bad\nvalue")
    }
}
