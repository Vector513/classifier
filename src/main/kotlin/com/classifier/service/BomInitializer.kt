package com.classifier.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.stereotype.Component
import javax.sql.DataSource

/** JPA и прежние schema.sql/data.sql уже загружены к моменту запуска runner. */
@Component
class BomInitializer(
    private val dataSource: DataSource,
    @Value("\${classifier.demo.enabled:false}") private val demoEnabled: Boolean
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        ResourceDatabasePopulator().apply {
            setSeparator("^^^")
            addScript(ClassPathResource("bom.sql"))
        }.execute(dataSource)
        if (demoEnabled) seedDemo()
    }

    /** Один SQL-блок с транзакционной блокировкой и постоянной отметкой выполнения. */
    fun seedDemo() {
        ResourceDatabasePopulator().apply {
            setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR)
            addScript(ClassPathResource("bom-demo.sql"))
        }.execute(dataSource)
    }
}
