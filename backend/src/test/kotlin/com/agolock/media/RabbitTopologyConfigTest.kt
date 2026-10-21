package com.agolock.media

import com.agolock.media.messaging.RabbitTopologyConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RabbitTopologyConfigTest {
    @Test
    fun `image_process 큐는 DLQ로 실패 메시지를 보낸다`() {
        val config = RabbitTopologyConfig()
        val queue = config.imageProcessQueue()
        assertThat(queue.name).isEqualTo("image.process")
        assertThat(queue.isDurable).isTrue()
        assertThat(queue.arguments["x-dead-letter-exchange"]).isEqualTo("agolock.dlx")
        assertThat(queue.arguments["x-dead-letter-routing-key"]).isEqualTo("image.process.dlq")
        assertThat(config.imageProcessDlq().name).isEqualTo("image.process.dlq")
    }
}
