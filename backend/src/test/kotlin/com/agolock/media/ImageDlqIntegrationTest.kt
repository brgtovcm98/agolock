package com.agolock.media

import com.agolock.media.messaging.RabbitTopologyConfig
import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.nio.charset.StandardCharsets

@SpringBootTest(properties = ["spring.rabbitmq.listener.simple.auto-startup=false"])
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ImageDlqIntegrationTest
    @Autowired
    constructor(
        private val connectionFactory: ConnectionFactory,
        private val rabbitTemplate: RabbitTemplate,
        private val amqpAdmin: AmqpAdmin,
    ) {
        @Test
        fun `image process 실패 메시지는 DLQ로 이동한다`() {
            val config = RabbitTopologyConfig()
            val exchange = config.agolockEventsExchange()
            val queue = config.imageProcessQueue()
            val dlq = config.imageProcessDlq()
            val dlx = config.agolockDeadLetterExchange()
            amqpAdmin.declareExchange(exchange)
            amqpAdmin.declareExchange(dlx)
            amqpAdmin.declareQueue(queue)
            amqpAdmin.declareQueue(dlq)
            amqpAdmin.declareBinding(config.imageProcessBinding(queue, exchange))
            amqpAdmin.declareBinding(config.imageProcessDlqBinding(dlq, dlx))
            amqpAdmin.purgeQueue("image.process", true)
            amqpAdmin.purgeQueue("image.process.dlq", true)

            rabbitTemplate.convertAndSend("agolock.events", "image.uploaded", "payload")
            connectionFactory.createConnection().use { connection ->
                connection.createChannel(false).use { channel ->
                    val received = channel.basicGet("image.process", false)
                    assertThat(received).isNotNull
                    channel.basicNack(received.envelope.deliveryTag, false, false)
                }
            }
            Thread.sleep(500)
            connectionFactory.createConnection().use { connection ->
                connection.createChannel(false).use { channel ->
                    val dead = channel.basicGet("image.process.dlq", true)
                    assertThat(dead).isNotNull
                    assertThat(String(dead.body, StandardCharsets.UTF_8)).isEqualTo("payload")
                }
            }
        }
    }
