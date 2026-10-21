package com.agolock.media.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitTopologyConfig {
    @Bean
    fun agolockEventsExchange(): TopicExchange = TopicExchange("agolock.events", true, false)

    @Bean
    fun imageProcessQueue(): Queue = Queue("image.process", true)

    @Bean
    fun imageProcessBinding(
        imageProcessQueue: Queue,
        agolockEventsExchange: TopicExchange,
    ): Binding = BindingBuilder.bind(imageProcessQueue).to(agolockEventsExchange).with("image.uploaded")
}
