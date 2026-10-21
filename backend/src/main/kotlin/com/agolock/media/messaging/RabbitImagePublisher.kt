package com.agolock.media.messaging

import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

@Component
class RabbitImagePublisher(
    private val rabbitTemplate: RabbitTemplate,
) {
    fun publishImageUploaded(payload: String) {
        rabbitTemplate.convertAndSend("agolock.events", "image.uploaded", payload)
    }

    fun publishDomainEvent(
        eventType: String,
        payload: String,
    ) {
        rabbitTemplate.convertAndSend("agolock.events", eventType, payload)
    }
}
