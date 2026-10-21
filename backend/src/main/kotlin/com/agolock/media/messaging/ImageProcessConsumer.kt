package com.agolock.media.messaging

import com.agolock.media.service.ImageProcessService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("worker")
class ImageProcessConsumer(
    private val imageProcessService: ImageProcessService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = ["image.process"])
    fun onMessage(message: Message) {
        val imageId =
            runCatching {
                UUID.fromString(objectMapper.readTree(message.body)["imageId"].asText())
            }.getOrNull()
        if (imageId == null) {
            log.warn("image.process 메시지 형식 오류")
            return
        }
        imageProcessService.process(imageId)
    }
}
