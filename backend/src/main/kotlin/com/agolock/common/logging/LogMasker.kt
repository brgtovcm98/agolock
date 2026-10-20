package com.agolock.common.logging

/**
 * 로그에 남기면 안 되는 값을 마스킹합니다.
 *
 * 로그백 인코더 수준의 자동 마스킹은 오탐과 누락이 모두 생기기 쉬워,
 * M0에서는 명시적으로 로그를 남길 때 이 유틸리티를 사용하고 테스트로 규칙을 고정합니다.
 */
object LogMasker {
    private val secretAssignment =
        Regex(
            "(?i)\\b(password|passwd|pwd|session|sessionId|token|inviteCode|invite_code|" +
                "xsrfToken|xsrf-token|cookie)\\s*[=:]\\s*([^\\s&,;]+)",
        )
    private val bearer = Regex("(?i)(bearer\\s+)([A-Za-z0-9._~+/-]+=*)")
    private val authorizationHeader = Regex("(?i)(authorization\\s*[:=]\\s*)(?!\\s*bearer\\b)([^\\r\\n]+)")
    private val presignedUrl =
        Regex("(?i)(https?://\\S*?(?:X-Amz-Signature|Signature|token)=)[^\\s&]+")

    fun mask(value: String): String {
        var masked = value.replace(bearer) { match -> "${match.groupValues[1]}***" }
        masked = masked.replace(authorizationHeader) { match -> "${match.groupValues[1]}***" }
        masked = masked.replace(secretAssignment) { match -> "${match.groupValues[1]}=***" }
        masked = masked.replace(presignedUrl) { match -> "${match.groupValues[1]}***" }
        return masked
    }
}
