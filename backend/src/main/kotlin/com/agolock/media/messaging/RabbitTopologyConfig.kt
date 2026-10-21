package com.agolock.media.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitTopologyConfig {
    @Bean
    fun agolockEventsExchange(): TopicExchange = TopicExchange("agolock.events", true, false)

    @Bean
    fun agolockDeadLetterExchange(): TopicExchange = TopicExchange("agolock.dlx", true, false)

    @Bean
    fun imageProcessQueue(): Queue =
        QueueBuilder
            .durable("image.process")
            .deadLetterExchange("agolock.dlx")
            .deadLetterRoutingKey("image.process.dlq")
            .build()

    @Bean
    fun imageProcessDlq(): Queue = Queue("image.process.dlq", true)

    @Bean
    fun imageProcessDlqBinding(
        imageProcessDlq: Queue,
        agolockDeadLetterExchange: TopicExchange,
    ): Binding =
        BindingBuilder
            .bind(imageProcessDlq)
            .to(agolockDeadLetterExchange)
            .with("image.process.dlq")

    @Bean
    fun imageProcessBinding(
        imageProcessQueue: Queue,
        agolockEventsExchange: TopicExchange,
    ): Binding = BindingBuilder.bind(imageProcessQueue).to(agolockEventsExchange).with("image.uploaded")
}
