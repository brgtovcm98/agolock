package com.agolock.common.mybatis.handler

import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import org.apache.ibatis.type.MappedTypes
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

@MappedTypes(UUID::class)
class UuidTypeHandler : BaseTypeHandler<UUID>() {
    override fun setNonNullParameter(
        ps: PreparedStatement,
        i: Int,
        parameter: UUID,
        jdbcType: JdbcType?,
    ) {
        ps.setObject(i, parameter, Types.OTHER)
    }

    override fun getNullableResult(
        rs: ResultSet,
        columnName: String,
    ): UUID? = rs.getObject(columnName, UUID::class.java)

    override fun getNullableResult(
        rs: ResultSet,
        columnIndex: Int,
    ): UUID? = rs.getObject(columnIndex, UUID::class.java)

    override fun getNullableResult(
        cs: CallableStatement,
        columnIndex: Int,
    ): UUID? = cs.getObject(columnIndex, UUID::class.java)
}
