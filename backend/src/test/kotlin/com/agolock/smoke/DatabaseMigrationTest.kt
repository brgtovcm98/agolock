package com.agolock.smoke

import com.agolock.common.mybatis.SystemMapper
import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class DatabaseMigrationTest
    @Autowired
    constructor(
        private val jdbcTemplate: JdbcTemplate,
        private val systemMapper: SystemMapper,
    ) {
        @Test
        fun `Flyway가 확장 마이그레이션을 적용한다`() {
            val extensions =
                jdbcTemplate.queryForList(
                    "SELECT extname FROM pg_extension",
                    String::class.java,
                )

            assertThat(extensions).contains("pg_trgm", "vector", "citext")
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                    Int::class.java,
                ),
            ).isGreaterThanOrEqualTo(1)
        }

        @Test
        fun `M1 테이블과 Spring Session 스키마가 적용된다`() {
            val tables =
                jdbcTemplate.queryForList(
                    """
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                    """.trimIndent(),
                    String::class.java,
                )

            assertThat(tables).contains(
                "app_user",
                "space",
                "invite_code",
                "invite_code_use",
                "password_reset_token",
                "category",
                "item",
                "inventory",
                "inventory_movement",
                "share_invitation",
                "share",
                "audit_log",
                "outbox_event",
                "image",
                "email_verification_token",
                "notification_secret",
                "spring_session",
                "spring_session_attributes",
            )

            val statuses =
                jdbcTemplate.queryForList(
                    """
                    SELECT e.enumlabel
                    FROM pg_enum e
                    JOIN pg_type t ON t.oid = e.enumtypid
                    WHERE t.typname = 'user_status'
                    """.trimIndent(),
                    String::class.java,
                )
            assertThat(statuses).containsExactly("ACTIVE", "LOCKED", "DELETED")
        }

        @Test
        fun `V21이 기존 활성 사용자의 email_verified_at을 백필한다`() {
            val applied =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '21' AND success = true",
                    Int::class.java,
                )
            assertThat(applied).isEqualTo(1)

            // V21은 테스트 컨텍스트 기동 시 이미 적용된다. V16 적용 시점 이전에 생성된
            // 활성 사용자만 백필 대상이므로, 그 구간에 미인증 사용자가 남아 있지 않은지 검증한다.
            val unverifiedBeforeV16 =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM app_user
                    WHERE email_verified_at IS NULL
                      AND deleted_at IS NULL
                      AND created_at < (
                          SELECT installed_on
                          FROM flyway_schema_history
                          WHERE version = '16' AND success = true
                      )
                    """.trimIndent(),
                    Long::class.java,
                )
            assertThat(unverifiedBeforeV16).isZero()
        }

        @Test
        fun `MyBatis가 실제 PostgreSQL에 연결한다`() {
            assertThat(systemMapper.selectOne()).isEqualTo(1)
        }
    }
