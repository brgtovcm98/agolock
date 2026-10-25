package com.agolock.search.persistence

import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.OffsetDateTime
import java.util.UUID

data class RecentSearchRow(
    val id: UUID,
    val query: String,
    val normalizedQuery: String,
    val lastSearchedAt: OffsetDateTime,
)

@Mapper
interface RecentSearchMapper {
    @Insert(
        """
        INSERT INTO recent_search (id, user_id, query, normalized_query, last_searched_at)
        VALUES (#{id}, #{userId}, #{query}, #{normalizedQuery}, #{now})
        ON CONFLICT (user_id, normalized_query)
        DO UPDATE SET query = #{query}, last_searched_at = #{now}
        """,
    )
    fun upsert(
        @Param("id") id: UUID,
        @Param("userId") userId: UUID,
        @Param("query") query: String,
        @Param("normalizedQuery") normalizedQuery: String,
        @Param("now") now: OffsetDateTime,
    )

    @Select(
        """
        SELECT id, query, normalized_query, last_searched_at
        FROM recent_search
        WHERE user_id = #{userId}
          AND last_searched_at >= #{cutoff}
        ORDER BY last_searched_at DESC, id DESC
        LIMIT 10
        """,
    )
    fun list(
        @Param("userId") userId: UUID,
        @Param("cutoff") cutoff: OffsetDateTime,
    ): List<RecentSearchRow>

    @Delete("DELETE FROM recent_search WHERE user_id = #{userId}")
    fun clear(
        @Param("userId") userId: UUID,
    )

    @Delete("DELETE FROM recent_search WHERE last_searched_at < #{before}")
    fun deleteBefore(
        @Param("before") before: OffsetDateTime,
    ): Int
}
