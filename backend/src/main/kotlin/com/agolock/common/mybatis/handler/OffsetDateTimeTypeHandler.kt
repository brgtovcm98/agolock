package com.agolock.common.mybatis.handler

import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import org.apache.ibatis.type.MappedTypes
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.OffsetDateTime

@MappedTypes(OffsetDateTime::class)
class OffsetDateTimeTypeHandler : BaseTypeHandler<OffsetDateTime>() {
    override fun setNonNullParameter(
        ps: PreparedStatement,
        i: Int,
        parameter: OffsetDateTime,
        jdbcType: JdbcType?,
    ) {
        ps.setObject(i, parameter)
    }

    override fun getNullableResult(
        rs: ResultSet,
        columnName: String,
    ): OffsetDateTime? = rs.getObject(columnName, OffsetDateTime::class.java)

    override fun getNullableResult(
        rs: ResultSet,
        columnIndex: Int,
    ): OffsetDateTime? = rs.getObject(columnIndex, OffsetDateTime::class.java)

    override fun getNullableResult(
        cs: CallableStatement,
        columnIndex: Int,
    ): OffsetDateTime? = cs.getObject(columnIndex, OffsetDateTime::class.java)
}
