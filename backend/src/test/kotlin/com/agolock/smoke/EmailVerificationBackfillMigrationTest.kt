package com.agolock.smoke

import com.agolock.support.SharedTestcontainers
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class EmailVerificationBackfillMigrationTest {
    @Test
    fun `V21은 V16 이전 미인증 사용자만 백필하고 기존 인증과 삭제 사용자를 보존한다`() {
        val postgres = SharedTestcontainers.postgres()
        val url = SharedTestcontainers.jdbcUrl(SharedTestcontainers.createDatabase())
        val jdbc = JdbcTemplate(DriverManagerDataSource(url, postgres.username, postgres.password))

        fun migrateTo(version: String) {
            Flyway
                .configure()
                .dataSource(url, postgres.username, postgres.password)
                .target(version)
                .load()
                .migrate()
        }

        fun insertLegacyUser(
            email: String,
            deleted: Boolean = false,
        ) {
            jdbc.update(
                """
                INSERT INTO app_user (id, email, password_hash, display_name, created_at, updated_at, deleted_at)
                VALUES (?, ?, 'test-hash', 'migration fixture',
                        TIMESTAMPTZ '2000-01-01 00:00:00+00', TIMESTAMPTZ '2000-01-01 00:00:00+00',
                        CASE WHEN ? THEN TIMESTAMPTZ '2000-01-02 00:00:00+00' ELSE NULL END)
                """.trimIndent(),
                UUID.randomUUID(),
                email,
                deleted,
            )
        }

        migrateTo("15")
        insertLegacyUser("legacy@example.com")
        insertLegacyUser("deleted@example.com", deleted = true)
        insertLegacyUser("verified@example.com")
        migrateTo("20")

        val verifiedAt = Timestamp.from(Instant.parse("2001-01-01T00:00:00Z"))
        jdbc.update("UPDATE app_user SET email_verified_at = ? WHERE email = ?", verifiedAt, "verified@example.com")
        // 경계 시각은 DB 안에서 계산해 JVM·DB 시간대와 실행 속도의 영향을 피한다.
        for ((email, interval) in listOf("boundary@example.com" to "0 seconds", "new@example.com" to "1 second")) {
            jdbc.update(
                """
                INSERT INTO app_user (id, email, password_hash, display_name, created_at, updated_at)
                SELECT ?, ?, 'test-hash', 'migration fixture', installed_on + CAST(? AS interval), installed_on
                FROM flyway_schema_history WHERE version = '16' AND success = true
                """.trimIndent(),
                UUID.randomUUID(),
                email,
                interval,
            )
        }

        migrateTo("21")

        assertThat(
            jdbc.queryForObject(
                "SELECT email_verified_at = created_at FROM app_user WHERE email = 'legacy@example.com'",
                Boolean::class.java,
            ),
        ).isTrue()
        assertThat(
            jdbc.queryForObject(
                "SELECT email_verified_at FROM app_user WHERE email = 'verified@example.com'",
                Timestamp::class.java,
            ),
        ).isEqualTo(verifiedAt)
        for (email in listOf("deleted@example.com", "boundary@example.com", "new@example.com")) {
            assertThat(
                jdbc.queryForObject(
                    "SELECT email_verified_at IS NULL FROM app_user WHERE email = ?",
                    Boolean::class.java,
                    email,
                ),
            ).describedAs("%s는 백필 대상이 아니다", email).isTrue()
        }
    }
}
