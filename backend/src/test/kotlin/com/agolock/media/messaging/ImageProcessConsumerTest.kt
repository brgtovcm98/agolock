package com.agolock.media.messaging

import com.agolock.media.service.ImageProcessService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message

class ImageProcessConsumerTest {
    @Test
    fun `형식이 잘못된 메시지는 requeue하지 않고 DLQ로 보낸다`() {
        val consumer =
            ImageProcessConsumer(
                mock(ImageProcessService::class.java),
                ObjectMapper(),
            )

        assertThrows<AmqpRejectAndDontRequeueException> {
            consumer.onMessage(Message("not-json".toByteArray()))
        }
    }
}
