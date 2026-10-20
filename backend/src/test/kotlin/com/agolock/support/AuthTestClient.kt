package com.agolock.support

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

class AuthTestClient(
    private val restTemplate: TestRestTemplate,
    private val objectMapper: ObjectMapper,
) {
    private var sessionCookie: String? = null
    private var csrfCookie: String? = null
    private var csrfToken: String? = null

    fun refreshCsrf(): String {
        val response =
            restTemplate.exchange(
                "/api/v1/auth/csrf",
                HttpMethod.GET,
                HttpEntity<Void>(headers()),
                String::class.java,
            )
        captureCookies(response.headers)
        val body = objectMapper.readTree(response.body ?: "{}")
        csrfToken = body["token"]?.asText() ?: error("CSRF token이 없습니다.")
        return csrfToken!!
    }

    fun get(path: String): ResponseEntity<String> =
        restTemplate.exchange(
            path,
            HttpMethod.GET,
            HttpEntity<Void>(headers()),
            String::class.java,
        )

    fun post(
        path: String,
        body: Any,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ResponseEntity<String> {
        if (csrfToken == null) refreshCsrf()
        val httpHeaders = headers()
        extraHeaders.forEach(httpHeaders::set)
        csrfToken?.let { httpHeaders.set("X-XSRF-TOKEN", it) }
        httpHeaders.contentType = MediaType.APPLICATION_JSON
        val response =
            restTemplate.exchange(
                path,
                HttpMethod.POST,
                HttpEntity(objectMapper.writeValueAsString(body), httpHeaders),
                String::class.java,
            )
        captureCookies(response.headers)
        if (response.statusCode == HttpStatus.NO_CONTENT) {
            sessionCookie = null
        }
        return response
    }

    fun patch(
        path: String,
        body: Any,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ResponseEntity<String> {
        if (csrfToken == null) refreshCsrf()
        val httpHeaders = headers()
        extraHeaders.forEach(httpHeaders::set)
        csrfToken?.let { httpHeaders.set("X-XSRF-TOKEN", it) }
        httpHeaders.contentType = MediaType.APPLICATION_JSON
        val response =
            restTemplate.exchange(
                path,
                HttpMethod.PATCH,
                HttpEntity(objectMapper.writeValueAsString(body), httpHeaders),
                String::class.java,
            )
        captureCookies(response.headers)
        return response
    }

    fun patchRaw(
        path: String,
        body: String,
    ): ResponseEntity<String> {
        if (csrfToken == null) refreshCsrf()
        val httpHeaders = headers()
        csrfToken?.let { httpHeaders.set("X-XSRF-TOKEN", it) }
        httpHeaders.contentType = MediaType.APPLICATION_JSON
        val response =
            restTemplate.exchange(
                path,
                HttpMethod.PATCH,
                HttpEntity(body, httpHeaders),
                String::class.java,
            )
        captureCookies(response.headers)
        return response
    }

    fun delete(path: String): ResponseEntity<String> {
        if (csrfToken == null) refreshCsrf()
        val httpHeaders = headers()
        csrfToken?.let { httpHeaders.set("X-XSRF-TOKEN", it) }
        return restTemplate.exchange(
            path,
            HttpMethod.DELETE,
            HttpEntity<Void>(httpHeaders),
            String::class.java,
        )
    }

    fun signup(
        email: String,
        password: String,
        displayName: String,
        inviteCode: String? = null,
    ): ResponseEntity<String> {
        val body = mutableMapOf<String, Any>("email" to email, "password" to password, "displayName" to displayName)
        inviteCode?.let { body["inviteCode"] = it }
        return post("/api/v1/auth/signup", body)
    }

    fun login(
        email: String,
        password: String,
    ): ResponseEntity<String> = post("/api/v1/auth/login", mapOf("email" to email, "password" to password))

    fun sessionCookieValue(): String? = sessionCookie

    private fun headers(): HttpHeaders {
        val headers = HttpHeaders()
        val cookies = listOfNotNull(sessionCookie, csrfCookie)
        if (cookies.isNotEmpty()) {
            headers.set(HttpHeaders.COOKIE, cookies.joinToString("; "))
        }
        return headers
    }

    private fun captureCookies(headers: HttpHeaders) {
        headers[HttpHeaders.SET_COOKIE].orEmpty().forEach { header ->
            val cookie = header.substringBefore(";")
            when {
                cookie.startsWith("SESSION=") -> sessionCookie = cookie
                cookie.startsWith("XSRF-TOKEN=") -> csrfCookie = cookie
            }
        }
    }
}
