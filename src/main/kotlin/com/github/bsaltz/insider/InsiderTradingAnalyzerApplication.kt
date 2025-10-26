package com.github.bsaltz.insider

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.shell.command.annotation.CommandScan
import java.time.Clock

@SpringBootApplication
@CommandScan
@EnableJdbcRepositories
class InsiderTradingAnalyzerApplication {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<InsiderTradingAnalyzerApplication>(*args)
}
