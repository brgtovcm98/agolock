package db.migration

import com.agolock.inventory.domain.CategoryNamePolicy
import com.agolock.inventory.domain.CategoryNormalizationRow
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.UUID

/** 활성 이름 충돌을 먼저 검사합니다. Flyway가 요구하는 V<version>__<description> 이름을 사용합니다. */
@Suppress("ClassNaming", "ktlint:standard:class-naming")
class V25__normalize_category_names : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val connection = context.connection
        connection.createStatement().use { it.execute("LOCK TABLE category IN EXCLUSIVE MODE") }
        val rows =
            connection.createStatement().use { statement ->
                val select = "SELECT id, space_id, name FROM category WHERE deleted_at IS NULL ORDER BY id"
                statement.executeQuery(select).use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                CategoryNormalizationRow(
                                    result.getObject("id", UUID::class.java),
                                    result.getObject("space_id", UUID::class.java),
                                    result.getString("name"),
                                ),
                            )
                        }
                    }
                }
            }
        val plan = CategoryNamePolicy.plan(rows)
        // 전환 중에만 기존 키 충돌을 피하고 최종 유일성은 같은 트랜잭션에서 복구합니다.
        connection.createStatement().use { it.execute("DROP INDEX ux_category_space_normalized_active") }
        connection.prepareStatement("UPDATE category SET normalized_name = ? WHERE id = ?").use { statement ->
            plan.forEach { (id, normalized) ->
                statement.setString(1, normalized)
                statement.setObject(2, id)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        connection.createStatement().use {
            it.execute(
                """
                CREATE UNIQUE INDEX ux_category_space_normalized_active
                ON category (space_id, normalized_name) WHERE deleted_at IS NULL
                """.trimIndent(),
            )
        }
    }
}
