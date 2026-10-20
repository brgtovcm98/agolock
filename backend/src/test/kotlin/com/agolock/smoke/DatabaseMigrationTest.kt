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
        fun `MyBatis가 실제 PostgreSQL에 연결한다`() {
            assertThat(systemMapper.selectOne()).isEqualTo(1)
        }
    }
