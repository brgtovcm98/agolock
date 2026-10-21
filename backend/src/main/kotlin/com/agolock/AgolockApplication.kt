package com.agolock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class AgolockApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<AgolockApplication>(*args)
}
