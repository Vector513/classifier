package com.classifier.repository

import com.classifier.entity.Enumeration
import com.classifier.entity.EnumerationClass
import com.classifier.entity.EnumerationValue
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
class EnumerationValueRepositoryTest {

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

    @Autowired lateinit var valueRepo: EnumerationValueRepository
    @Autowired lateinit var enumRepo: EnumerationRepository
    @Autowired lateinit var classRepo: EnumerationClassRepository

    lateinit var colors: Enumeration
    lateinit var sizes: Enumeration

    @BeforeEach
    fun setUp() {
        valueRepo.deleteAll()
        enumRepo.deleteAll()
        classRepo.deleteAll()

        val cls = classRepo.save(EnumerationClass(code = "COLOR", name = "Цвет"))
        colors = enumRepo.save(Enumeration(code = "COLORS", name = "Цвета", enumerationClass = cls))
        sizes = enumRepo.save(Enumeration(code = "SIZES", name = "Размеры", enumerationClass = cls))

        valueRepo.save(EnumerationValue(code = "BLACK", name = "Чёрный", enumeration = colors, sortOrder = 0))
        valueRepo.save(EnumerationValue(code = "WHITE", name = "Белый", enumeration = colors, sortOrder = 1))
        valueRepo.save(EnumerationValue(code = "GOLD", name = "Золотой", enumeration = colors, sortOrder = 2))
        valueRepo.save(EnumerationValue(code = "S", name = "Small", enumeration = sizes, sortOrder = 0))
    }

    @Test
    fun `findByEnumerationId returns values ordered by sortOrder`() {
        val values = valueRepo.findByEnumerationIdOrderBySortOrder(colors.id)
        assertEquals(3, values.size)
        assertEquals("BLACK", values[0].code)
        assertEquals("WHITE", values[1].code)
        assertEquals("GOLD", values[2].code)
    }

    @Test
    fun `findByEnumerationId does not return values of other enumerations`() {
        val values = valueRepo.findByEnumerationIdOrderBySortOrder(sizes.id)
        assertEquals(1, values.size)
        assertEquals("S", values[0].code)
    }

    @Test
    fun `countByEnumerationId returns correct count`() {
        assertEquals(3L, valueRepo.countByEnumerationId(colors.id))
        assertEquals(1L, valueRepo.countByEnumerationId(sizes.id))
    }

    @Test
    fun `existsByEnumerationIdAndCode returns true for existing value`() {
        assertTrue(valueRepo.existsByEnumerationIdAndCode(colors.id, "BLACK"))
        assertFalse(valueRepo.existsByEnumerationIdAndCode(colors.id, "NONEXISTENT"))
    }

    @Test
    fun `existsByEnumerationIdAndCode is scoped to enumeration`() {
        // "S" belongs to sizes, not colors
        assertFalse(valueRepo.existsByEnumerationIdAndCode(colors.id, "S"))
        assertTrue(valueRepo.existsByEnumerationIdAndCode(sizes.id, "S"))
    }

    @Test
    fun `sort order is persisted correctly`() {
        val values = valueRepo.findByEnumerationIdOrderBySortOrder(colors.id)
        assertEquals(0, values[0].sortOrder)
        assertEquals(1, values[1].sortOrder)
        assertEquals(2, values[2].sortOrder)
    }
}
