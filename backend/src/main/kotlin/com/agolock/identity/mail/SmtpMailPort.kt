package com.agolock.identity.mail

import com.agolock.common.config.AppProperties
import com.agolock.common.config.AuthProperties
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

@Component
class SmtpMailPort(
    private val mailSenderProvider: ObjectProvider<JavaMailSender>,
    private val appProperties: AppProperties,
    private val authProperties: AuthProperties,
) : MailPort {
    override fun sendPasswordReset(
        email: String,
        displayName: String,
        token: String,
    ) {
        val mailSender =
            mailSenderProvider.getIfAvailable()
                ?: error("메일 발송이 설정되지 않았습니다. MAIL_HOST를 확인하세요.")

        val link = "${appProperties.publicBaseUrl.trimEnd('/')}/reset-password?token=$token"
        val minutes =
            authProperties.passwordReset.ttl
                .toMinutes()
                .coerceAtLeast(1)
        val safeName = escapeHtml(displayName)
        val message: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(appProperties.mail.from)
        helper.setTo(email)
        helper.setSubject("[AGO Lock] 비밀번호 재설정 안내")
        helper.setText(
            """
            안녕하세요, ${displayName}님.

            아래 링크에서 비밀번호를 재설정할 수 있습니다.
            $link

            이 링크는 ${minutes}분 동안 한 번만 사용할 수 있습니다.
            본인이 요청하지 않았다면 이 메일을 무시해 주세요.
            """.trimIndent(),
            """
            <div style="font-family:'Noto Sans KR',sans-serif;background:#f1eee3;color:#111;padding:24px">
              <div style="max-width:560px;margin:0 auto;border:2.5px solid #111;background:#fff;box-shadow:5px 5px 0 #111;padding:24px">
                <h1 style="font-size:24px;margin:0 0 16px">AGO LOCK</h1>
                <p style="font-size:16px;line-height:1.6">안녕하세요, <strong>$safeName</strong>님.</p>
                <p style="font-size:14px;line-height:1.7">아래 버튼에서 비밀번호를 재설정할 수 있습니다. 이 링크는 ${minutes}분 동안 한 번만 사용할 수 있습니다.</p>
                <p style="margin:24px 0">
                  <a href="$link" style="display:inline-block;border:2.5px solid #111;background:#ff5a1f;color:#111;padding:12px 20px;font-weight:900;text-decoration:none;box-shadow:4px 4px 0 #111">비밀번호 재설정</a>
                </p>
                <p style="font-size:12px;color:#555;line-height:1.6">본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
              </div>
            </div>
            """.trimIndent(),
        )
        mailSender.send(message)
    }

    override fun sendShareInvitation(
        email: String,
        resourceType: String,
        token: String,
        expiresAt: java.time.OffsetDateTime,
    ) {
        val mailSender =
            mailSenderProvider.getIfAvailable()
                ?: error("메일 발송이 설정되지 않았습니다. MAIL_HOST를 확인하세요.")
        val link = "${appProperties.publicBaseUrl.trimEnd('/')}/invitations#token=$token"
        val message: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(appProperties.mail.from)
        helper.setTo(email)
        helper.setSubject("[AGO Lock] 공유 초대가 도착했어요")
        helper.setText(
            """
            AGO Lock 공유 초대가 도착했습니다.

            초대 링크: $link
            만료 시각: $expiresAt
            로그인한 계정의 이메일이 초대 이메일과 같아야 수락할 수 있습니다.
            """.trimIndent(),
            """
            <div style="font-family:'Noto Sans KR',sans-serif;background:#f1eee3;color:#111;padding:24px">
              <div style="max-width:560px;margin:0 auto;border:2.5px solid #111;background:#fff;box-shadow:5px 5px 0 #111;padding:24px">
                <h1 style="font-size:24px;margin:0 0 16px">AGO LOCK</h1>
                <p style="font-size:16px;line-height:1.6">공유 초대가 도착했습니다. ${escapeHtml(resourceType)}에 초대되었습니다.</p>
                <p style="margin:24px 0">
                  <a href="$link" style="display:inline-block;border:2.5px solid #111;background:#ff5a1f;color:#111;padding:12px 20px;font-weight:900;text-decoration:none;box-shadow:4px 4px 0 #111">초대 확인</a>
                </p>
                <p style="font-size:12px;color:#555;line-height:1.6">만료: ${escapeHtml(expiresAt.toString())}</p>
              </div>
            </div>
            """.trimIndent(),
        )
        mailSender.send(message)
    }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
