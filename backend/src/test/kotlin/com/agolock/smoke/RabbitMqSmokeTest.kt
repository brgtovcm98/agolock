package com.agolock.smoke

import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class RabbitMqSmokeTest
    @Autowired
    constructor(
        private val connectionFactory: ConnectionFactory,
    ) {
        @Test
        fun `RabbitMQ 연결과 채널을 연다`() {
            connectionFactory.createConnection().use { connection ->
                assertThat(connection.isOpen).isTrue()
                connection.createChannel(false).use { channel ->
                    assertThat(channel.isOpen).isTrue()
                }
            }
        }
    }
