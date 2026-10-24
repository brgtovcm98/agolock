package com.agolock.support

import org.springframework.boot.autoconfigure.amqp.RabbitConnectionDetails
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.UUID

/**
 * 테스트 JVM 하나가 Postgres·RabbitMQ 컨테이너를 각각 하나만 사용하도록 공유합니다.
 *
 * 테스트 클래스의 속성 조합에 따라 Spring application context가 여러 개 만들어지므로, 컨테이너를 빈으로 선언하면
 * context마다 컨테이너 한 쌍이 새로 기동합니다. 여기서 JVM당 한 번만 기동하고, [TestcontainersConfiguration]은
 * 컨테이너가 아니라 연결 정보만 빈으로 노출합니다. 컨테이너는 Testcontainers의 `Startable`이지만 Spring 빈이
 * 아니므로 context 수명 주기와 무관하게 유지되고, 프로세스 종료 후 Testcontainers의 Ryuk이 정리합니다.
 */
object SharedTestcontainers {
    private const val DATABASE_NAME_PREFIX = "test_"

    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.7-pg16"))
    private val rabbit: RabbitMQContainer =
        RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13.7-management"))

    init {
        postgres.start()
        rabbit.start()
    }

    fun postgres(): PostgreSQLContainer<*> = postgres

    fun rabbit(): RabbitMQContainer = rabbit

    /**
     * context 하나가 쓸 빈 데이터베이스를 공유 Postgres에 만들고 이름을 돌려줍니다.
     *
     * 스키마를 공유하면 context마다 별도로 도는 `@Scheduled` outbox relay가 다른 context의 이벤트를
     * 먼저 claim·발행해 버립니다. 메일 발송은 context별 fake [com.agolock.identity.mail.MailPort]로 가므로,
     * 다른 context가 claim한 초대 이벤트의 메일은 그 context에만 남아 테스트가 받지 못합니다.
     * database를 나누면 context 간 데이터 간섭이 없어집니다.
     */
    fun createDatabase(): String {
        val name = DATABASE_NAME_PREFIX + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement -> statement.execute("CREATE DATABASE \"$name\"") }
        }
        return name
    }

    fun jdbcUrl(database: String): String =
        "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}/$database"
}

/**
 * 공유 컨테이너의 연결 정보를 Spring Boot 자동 설정에 전달합니다.
 *
 * 컨테이너 자체를 `@ServiceConnection` 빈으로 선언하면 Spring이 그 빈을 `Startable`로 보고 context 종료 시
 * 컨테이너를 닫으므로, 연결 정보 빈만 노출해 컨테이너 수명을 JVM 전체로 유지합니다.
 * `JdbcConnectionDetails`와 `RabbitConnectionDetails`는 `@ConditionalOnMissingBean` 대상이라
 * 이 빈이 `application.yml`의 URL·계정 설정을 대체하고, pool 설정은 `spring.datasource.hikari` 값이 유지됩니다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    fun jdbcConnectionDetails(): JdbcConnectionDetails {
        val database = SharedTestcontainers.createDatabase()
        val postgres = SharedTestcontainers.postgres()
        return object : JdbcConnectionDetails {
            override fun getUsername(): String = postgres.username

            override fun getPassword(): String = postgres.password

            override fun getJdbcUrl(): String = SharedTestcontainers.jdbcUrl(database)

            override fun getDriverClassName(): String = postgres.driverClassName
        }
    }

    @Bean
    fun rabbitConnectionDetails(): RabbitConnectionDetails {
        val rabbit = SharedTestcontainers.rabbit()
        return object : RabbitConnectionDetails {
            override fun getUsername(): String = rabbit.adminUsername

            override fun getPassword(): String = rabbit.adminPassword

            override fun getAddresses(): List<RabbitConnectionDetails.Address> =
                listOf(RabbitConnectionDetails.Address(rabbit.host, rabbit.amqpPort))
        }
    }
}
