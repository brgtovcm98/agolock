package com.agolock.common.config

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * 계약 미리보기 모드에서는 데이터베이스와 MyBatis를 기동하지 않습니다.
 * M1에서 도메인 매퍼가 추가되면 이 설정이 실제 영속성 진입점이 됩니다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "agolock",
    name = ["contract-mode"],
    havingValue = "false",
    matchIfMissing = true,
)
@MapperScan("com.agolock")
class PersistenceConfig
