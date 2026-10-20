package com.agolock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class AgolockApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<AgolockApplication>(*args)
}
