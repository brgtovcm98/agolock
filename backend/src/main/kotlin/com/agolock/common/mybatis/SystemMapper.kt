package com.agolock.common.mybatis

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Select

@Mapper
interface SystemMapper {
    @Select("SELECT 1")
    fun selectOne(): Int
}
