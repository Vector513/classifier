package com.classifier.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

@Component
@Order(30)
class OrderInitializer(private val dataSource: DataSource,
    @Value("\${classifier.demo.enabled:false}") private val demoEnabled: Boolean) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        load("orders-schema.sql")
        load("orders-logic.sql")
        if (demoEnabled) {
            load("orders-demo.sql")
            load("orders-shop-demo.sql")
        }
    }
    private fun load(file: String) = ResourceDatabasePopulator(ClassPathResource(file)).apply {
        setSqlScriptEncoding("UTF-8")
        setSeparator(if (file.endsWith("-demo.sql")) ScriptUtils.EOF_STATEMENT_SEPARATOR else "^^^")
    }.execute(dataSource)
}
