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
        fun `MyBatis가 실제 PostgreSQL에 연결한다`() {
            assertThat(systemMapper.selectOne()).isEqualTo(1)
        }
    }
