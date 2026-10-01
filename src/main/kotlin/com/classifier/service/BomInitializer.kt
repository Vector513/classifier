package com.classifier.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.transaction.annotation.Transactional
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.stereotype.Component
import javax.sql.DataSource

/** JPA и прежние schema.sql/data.sql уже загружены к моменту запуска runner. */
@Component
@Order(10)
class BomInitializer(
    private val dataSource: DataSource,
    @Value("\${classifier.demo.enabled:false}") private val demoEnabled: Boolean
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        ResourceDatabasePopulator().apply {
            setSeparator("^^^")
            addScript(ClassPathResource("bom.sql"))
        }.execute(dataSource)
        if (demoEnabled) seedDemo()
    }

    /** Один SQL-блок с транзакционной блокировкой и постоянной отметкой выполнения. */
    @Transactional
    fun seedDemo() {
        ResourceDatabasePopulator().apply {
            setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR)
            addScript(ClassPathResource("bom-demo.sql"))
            addScript(ClassPathResource("bom-demo-updates.sql"))
            addScript(ClassPathResource("catalog-demo.sql"))
            addScript(ClassPathResource("catalog-parameters-demo.sql"))
        }.execute(dataSource)
    }
}
