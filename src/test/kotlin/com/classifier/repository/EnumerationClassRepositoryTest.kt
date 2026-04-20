package com.classifier.repository

import com.classifier.entity.EnumerationClass
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
class EnumerationClassRepositoryTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var classRepo: EnumerationClassRepository

    @BeforeEach
    fun setUp() {
        classRepo.deleteAll()
        classRepo.save(EnumerationClass(code = "COLOR", name = "Цвет", description = "Цветовая гамма"))
        classRepo.save(EnumerationClass(code = "OS", name = "Операционная система"))
    }

    @Test
    fun `existsByCode returns true for existing code`() {
        assertTrue(classRepo.existsByCode("COLOR"))
        assertFalse(classRepo.existsByCode("NONEXISTENT"))
    }

    @Test
    fun `findByCode returns correct entity`() {
        val cls = classRepo.findByCode("COLOR")
        assertNotNull(cls)
        assertEquals("Цвет", cls!!.name)
        assertEquals("Цветовая гамма", cls.description)
    }

    @Test
    fun `findByCode returns null for missing code`() {
        assertNull(classRepo.findByCode("MISSING"))
    }

    @Test
    fun `findAllByOrderByName returns sorted by name`() {
        val all = classRepo.findAllByOrderByName()
        assertEquals(2, all.size)
        assertEquals("Операционная система", all[0].name)
        assertEquals("Цвет", all[1].name)
    }

    @Test
    fun `save persists description`() {
        val cls = classRepo.findByCode("COLOR")!!
        assertEquals("Цветовая гамма", cls.description)
    }

    @Test
    fun `code uniqueness constraint is enforced`() {
        assertThrows(Exception::class.java) {
            classRepo.saveAndFlush(EnumerationClass(code = "COLOR", name = "Дубликат"))
        }
    }
}
