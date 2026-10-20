package com.agolock.common.mybatis

import org.apache.ibatis.annotations.Select

interface SystemMapper {
    @Select("SELECT 1")
    fun selectOne(): Int
}
