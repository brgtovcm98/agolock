package com.agolock.search

import com.agolock.hierarchy.domain.Actor
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.search.persistence.SearchQuery
import com.agolock.search.service.SearchRequest
import com.agolock.search.service.SearchService
import com.agolock.support.FakeMailConfiguration
import com.agolock.support.TestcontainersConfiguration
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler
import org.apache.ibatis.session.SqlSessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.system.measureNanoTime

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, FakeMailConfiguration::class)
@Tag("search-perf")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchPerformanceTest
    @Autowired
    constructor(
        private val jdbc: JdbcTemplate,
        private val service: SearchService,
        private val sessionFactory: SqlSessionFactory,
    ) {
        @BeforeAll
        fun seed() {
            ResourceDatabasePopulator(ClassPathResource("search/performance-seed.sql")).execute(jdbc.dataSource!!)
        }

        @Test
        fun `실제 그룹 SQL 실행 계획을 기록한다`() {
            val output = Path.of("build/reports/search-explain.txt")
            Files.createDirectories(output.parent)
            Files.writeString(output, explain(Actor(id("perf-user-1"), id("perf-space-1")), null))
        }

        @Test
        fun `오만 재고 검색 p95와 실행 계획을 기록한다`() {
            val actor = Actor(id("perf-user-1"), id("perf-space-1"))
            val report =
                StringBuilder(
                    "# 검색 성능 측정\n\n10,000 Item·50,000 Inventory, 공유 10%, 이미지 70%. 워밍업 5회, 측정 20회.\n\n",
                )
            val failures = mutableListOf<String>()
            val cases =
                linkedMapOf(
                    "전체 20그룹" to SearchRequest(),
                    "전체 100그룹" to SearchRequest(limit = 100),
                    "이름 부분 검색" to SearchRequest(q = "물건 000"),
                    "공유 필터" to SearchRequest(scope = "SHARED"),
                    "사진 필터" to SearchRequest(hasImage = true),
                )
            cases.forEach { (name, request) ->
                repeat(5) { service.search(actor, request) }
                val measurements =
                    (1..20).map { measureNanoTime { service.search(actor, request) } / 1_000_000.0 }.sorted()
                val p95 = measurements[18]
                report.append("- $name: p95=%.2fms, p99=%.2fms\n".format(p95, measurements.last()))
                if (p95 > 300) failures.add("$name p95: $p95 ms")
                if (measurements.last() > 600) failures.add("$name p99: ${measurements.last()} ms")
            }
            val plan = explain(actor)
            report.append("\n실제 키워드 그룹 검색 계획:\n\n```text\n$plan\n```\n")
            val output = Path.of("build/reports/search-performance.md")
            Files.createDirectories(output.parent)
            Files.writeString(output, report)
            assertThat(plan).contains(
                "Bitmap Index Scan on idx_item_name_trgm",
                "Bitmap Index Scan on idx_inventory_name_override_trgm",
                "Bitmap Index Scan on idx_item_description_trgm",
            )
            assertThat(failures).describedAs("검색 지연 기준 초과 사례: %s", failures).isEmpty()
        }

        private fun id(value: String): UUID = jdbc.queryForObject("SELECT md5(?)::uuid", UUID::class.java, value)!!

        private fun explain(
            actor: Actor,
            keyword: String? = "물건 000",
        ): String {
            val query =
                SearchQuery(
                    ownSpaceId = actor.spaceId,
                    actorUserId = actor.userId,
                    includeOwned = true,
                    includeShared = true,
                    sharedSpaceIds = listOf(id("perf-space-2")),
                    roomIds = listOf(id("perf-room-2")),
                    shelfIds = emptyList(),
                    boxIds = emptyList(),
                    q = keyword,
                    statuses = listOf(InventoryStatus.IN_USE, InventoryStatus.STORED),
                    roomFilterId = null,
                    shelfFilterId = null,
                    boxFilterId = null,
                    categoryId = null,
                    ownerId = null,
                    hasImage = null,
                    sort = if (keyword == null) "UPDATED_DESC" else "RELEVANCE",
                    limit = 21,
                )
            val statementId = "com.agolock.search.persistence.SearchMapper.search"
            val mapped = sessionFactory.configuration.getMappedStatement(statementId)
            val bound = mapped.getBoundSql(query)
            return sessionFactory.openSession().use { session ->
                session.connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS) ${bound.sql}").use { statement ->
                    DefaultParameterHandler(mapped, query, bound).setParameters(statement)
                    statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }.joinToString("\n")
                    }
                }
            }
        }
    }
