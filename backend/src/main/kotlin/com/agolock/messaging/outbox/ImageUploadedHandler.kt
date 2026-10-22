package com.agolock.messaging.outbox

import com.agolock.media.messaging.RabbitImagePublisher
import org.springframework.stereotype.Component

@Component
class ImageUploadedHandler(
    private val publisher: RabbitImagePublisher,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "image.uploaded"

    override fun handle(event: ClaimedOutboxEvent) {
        publisher.publishImageUploaded(event.payload)
    }
}
