package com.classifier.repository

import com.classifier.entity.UnitOfMeasure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UnitOfMeasureRepositoryTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var unitRepo: UnitOfMeasureRepository

    @BeforeEach
    fun setUp() {
        unitRepo.deleteAll()
    }

    @Test
    fun `save and findById works`() {
        val saved = unitRepo.save(UnitOfMeasure(code = "PCS", name = "штуки"))
        val found = unitRepo.findById(saved.id)
        assertTrue(found.isPresent)
        assertEquals("PCS", found.get().code)
        assertEquals("штуки", found.get().name)
    }

    @Test
    fun `existsByCode returns true for existing code`() {
        unitRepo.save(UnitOfMeasure(code = "KG", name = "килограммы"))
        assertTrue(unitRepo.existsByCode("KG"))
        assertFalse(unitRepo.existsByCode("NONEXISTENT"))
    }

    @Test
    fun `findAll returns all units`() {
        unitRepo.save(UnitOfMeasure(code = "PCS", name = "штуки"))
        unitRepo.save(UnitOfMeasure(code = "KG", name = "килограммы"))
        assertEquals(2, unitRepo.findAll().size)
    }

    @Test
    fun `delete removes unit`() {
        val saved = unitRepo.save(UnitOfMeasure(code = "M", name = "метры"))
        unitRepo.deleteById(saved.id)
        assertFalse(unitRepo.findById(saved.id).isPresent)
    }
}
