package com.classifier.repository

import com.classifier.entity.ClassifierNode
import com.classifier.entity.Enumeration
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
class EnumerationRepositoryTest {

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

    @Autowired lateinit var enumRepo: EnumerationRepository
    @Autowired lateinit var classRepo: EnumerationClassRepository
    @Autowired lateinit var nodeRepo: ClassifierNodeRepository

    lateinit var colorClass: EnumerationClass
    lateinit var osClass: EnumerationClass
    lateinit var phonesNode: ClassifierNode

    @BeforeEach
    fun setUp() {
        enumRepo.deleteAll()
        classRepo.deleteAll()
        nodeRepo.deleteAll()

        colorClass = classRepo.save(EnumerationClass(code = "COLOR", name = "Цвет"))
        osClass = classRepo.save(EnumerationClass(code = "OS", name = "ОС"))
        phonesNode = nodeRepo.save(ClassifierNode(code = "PHONES", name = "Смартфоны", sortOrder = 0))

        enumRepo.save(Enumeration(code = "PHONE-COLORS", name = "Цвета телефонов", enumerationClass = colorClass, classifierNode = phonesNode))
        enumRepo.save(Enumeration(code = "LAPTOP-COLORS", name = "Цвета ноутбуков", enumerationClass = colorClass))
        enumRepo.save(Enumeration(code = "PHONE-OS", name = "ОС телефонов", enumerationClass = osClass, classifierNode = phonesNode))
    }

    @Test
    fun `existsByCode returns true for existing code`() {
        assertTrue(enumRepo.existsByCode("PHONE-COLORS"))
        assertFalse(enumRepo.existsByCode("NONEXISTENT"))
    }

    @Test
    fun `findByEnumerationClassId returns only enums of that class`() {
        val colorEnums = enumRepo.findByEnumerationClassIdOrderByName(colorClass.id)
        assertEquals(2, colorEnums.size)
        val codes = colorEnums.map { it.code }.toSet()
        assertTrue(codes.containsAll(setOf("PHONE-COLORS", "LAPTOP-COLORS")))
    }

    @Test
    fun `findByEnumerationClassId sorted by name`() {
        val enums = enumRepo.findByEnumerationClassIdOrderByName(colorClass.id)
        assertEquals("Цвета ноутбуков", enums[0].name)
        assertEquals("Цвета телефонов", enums[1].name)
    }

    @Test
    fun `findByClassifierNodeId returns enums linked to that node`() {
        val phoneEnums = enumRepo.findByClassifierNodeIdOrderByName(phonesNode.id)
        assertEquals(2, phoneEnums.size)
        val codes = phoneEnums.map { it.code }.toSet()
        assertTrue(codes.containsAll(setOf("PHONE-COLORS", "PHONE-OS")))
    }

    @Test
    fun `findByClassifierNodeId returns empty for node without enums`() {
        val other = nodeRepo.save(ClassifierNode(code = "LAPTOPS", name = "Ноутбуки", sortOrder = 1))
        val result = enumRepo.findByClassifierNodeIdOrderByName(other.id)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `code uniqueness constraint is enforced`() {
        assertThrows(Exception::class.java) {
            enumRepo.saveAndFlush(Enumeration(code = "PHONE-COLORS", name = "Dup", enumerationClass = colorClass))
        }
    }
}
