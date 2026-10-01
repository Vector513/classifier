package com.classifier.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

@Component
@Order(0)
class CatalogInitializer(private val dataSource: DataSource, private val jdbc: JdbcTemplate,
    @Value("\${classifier.bootstrap.enabled:true}") private val bootstrapEnabled: Boolean) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        ResourceDatabasePopulator().apply {
            setSeparator("^^^")
            addScript(ClassPathResource("catalog-schema.sql"))
        }.execute(dataSource)
        if (bootstrapEnabled && jdbc.queryForObject("SELECT count(*) FROM schema_migration WHERE name='catalog-base-data-v2'", Int::class.java)==0) {
            // Все связи в наборе задаются по кодам; существующие записи не перезаписываются.
            ResourceDatabasePopulator(ClassPathResource("data.sql")).execute(dataSource)
            jdbc.update("INSERT INTO schema_migration(name) VALUES('catalog-base-data-v2')")
        }
        ResourceDatabasePopulator().apply {
            setSeparator("^^^")
            addScript(ClassPathResource("catalog-rules.sql"))
        }.execute(dataSource)
        if (jdbc.queryForObject("SELECT to_regclass('ho_class') IS NOT NULL", Boolean::class.java)==true) {
            ResourceDatabasePopulator().apply {
                setSeparator("^^^")
                addScript(ClassPathResource("catalog-ho.sql"))
                addScript(ClassPathResource("procedures.sql"))
            }.execute(dataSource)
        }
        jdbc.execute("SELECT catalog_assert_consistent()")
    }
}
