package com.agolock.identity.mail

import com.agolock.common.logging.LogMasker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PasswordResetMailListener(
    private val mailPort: MailPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true,
    )
    @Suppress("TooGenericExceptionCaught")
    fun onPasswordResetRequested(event: PasswordResetRequestedEvent) {
        try {
            mailPort.sendPasswordReset(event.email, event.displayName, event.token)
        } catch (exception: Exception) {
            log.warn(
                "비밀번호 재설정 메일 발송 실패 email={} reason={}",
                LogMasker.mask(event.email),
                exception.javaClass.simpleName,
            )
        }
    }
}
