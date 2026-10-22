package com.agolock.media.messaging

import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class RabbitImagePublisher(
    private val rabbitTemplate: RabbitTemplate,
) {
    fun publishImageUploaded(payload: String) {
        publish("image.uploaded", payload)
    }

    private fun publish(
        eventType: String,
        payload: String,
    ) {
        val correlation = CorrelationData(UUID.randomUUID().toString())
        rabbitTemplate.convertAndSend("agolock.events", eventType, payload, correlation)
        val confirm = correlation.future.get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(confirm.isAck) { "RabbitMQ publish nack: $eventType" }
        check(correlation.returned == null) { "RabbitMQ unroutable message: $eventType" }
    }

    private companion object {
        const val CONFIRM_TIMEOUT_SECONDS = 5L
    }
}
