package com.classifier.repository

import com.classifier.entity.*
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
class NodeAttributeValueRepositoryTest {

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

    @Autowired lateinit var navRepo: NodeAttributeValueRepository
    @Autowired lateinit var nodeRepo: ClassifierNodeRepository
    @Autowired lateinit var enumRepo: EnumerationRepository
    @Autowired lateinit var valueRepo: EnumerationValueRepository
    @Autowired lateinit var classRepo: EnumerationClassRepository

    lateinit var phone: ClassifierNode
    lateinit var laptop: ClassifierNode
    lateinit var colorEnum: Enumeration
    lateinit var osEnum: Enumeration
    lateinit var blackValue: EnumerationValue
    lateinit var whiteValue: EnumerationValue
    lateinit var iosValue: EnumerationValue

    @BeforeEach
    fun setUp() {
        navRepo.deleteAll()
        valueRepo.deleteAll()
        enumRepo.deleteAll()
        classRepo.deleteAll()
        nodeRepo.deleteAll()

        phone  = nodeRepo.save(ClassifierNode(code = "PHONES",  name = "Смартфоны", sortOrder = 0))
        laptop = nodeRepo.save(ClassifierNode(code = "LAPTOPS", name = "Ноутбуки",  sortOrder = 1))

        val colorClass = classRepo.save(EnumerationClass(code = "COLOR", name = "Цвет"))
        val osClass    = classRepo.save(EnumerationClass(code = "OS",    name = "ОС"))

        colorEnum = enumRepo.save(Enumeration(code = "PHONE-COLORS", name = "Цвета телефонов", enumerationClass = colorClass))
        osEnum    = enumRepo.save(Enumeration(code = "PHONE-OS",     name = "ОС телефонов",    enumerationClass = osClass))

        blackValue = valueRepo.save(EnumerationValue(code = "BLACK", name = "Чёрный", enumeration = colorEnum, sortOrder = 0))
        whiteValue = valueRepo.save(EnumerationValue(code = "WHITE", name = "Белый",  enumeration = colorEnum, sortOrder = 1))
        iosValue   = valueRepo.save(EnumerationValue(code = "IOS",   name = "iOS",    enumeration = osEnum,    sortOrder = 0))
    }

    @Test
    fun `findByClassifierNodeIdOrderByEnumerationId returns all attributes for node`() {
        navRepo.save(NodeAttributeValue(classifierNode = phone, enumeration = colorEnum, selectedValue = blackValue))
        navRepo.save(NodeAttributeValue(classifierNode = phone, enumeration = osEnum,    selectedValue = iosValue))

        val result = navRepo.findByClassifierNodeIdOrderByEnumerationId(phone.id)

        assertEquals(2, result.size)
    }

    @Test
    fun `findByClassifierNodeIdOrderByEnumerationId returns empty for node without attributes`() {
        val result = navRepo.findByClassifierNodeIdOrderByEnumerationId(laptop.id)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findByClassifierNodeIdOrderByEnumerationId does not return other nodes attributes`() {
        navRepo.save(NodeAttributeValue(classifierNode = phone,  enumeration = colorEnum, selectedValue = blackValue))
        navRepo.save(NodeAttributeValue(classifierNode = laptop, enumeration = colorEnum, selectedValue = whiteValue))

        val result = navRepo.findByClassifierNodeIdOrderByEnumerationId(phone.id)

        assertEquals(1, result.size)
        assertEquals(blackValue.id, result[0].selectedValue.id)
    }

    @Test
    fun `findByClassifierNodeIdAndEnumerationId returns correct record`() {
        navRepo.save(NodeAttributeValue(classifierNode = phone, enumeration = colorEnum, selectedValue = blackValue))

        val result = navRepo.findByClassifierNodeIdAndEnumerationId(phone.id, colorEnum.id)

        assertNotNull(result)
        assertEquals(blackValue.id, result!!.selectedValue.id)
    }

    @Test
    fun `findByClassifierNodeIdAndEnumerationId returns null when not set`() {
        val result = navRepo.findByClassifierNodeIdAndEnumerationId(phone.id, colorEnum.id)

        assertNull(result)
    }

    @Test
    fun `existsByClassifierNodeIdAndEnumerationId returns true when record exists`() {
        navRepo.save(NodeAttributeValue(classifierNode = phone, enumeration = colorEnum, selectedValue = blackValue))

        assertTrue(navRepo.existsByClassifierNodeIdAndEnumerationId(phone.id, colorEnum.id))
    }

    @Test
    fun `existsByClassifierNodeIdAndEnumerationId returns false when record absent`() {
        assertFalse(navRepo.existsByClassifierNodeIdAndEnumerationId(phone.id, colorEnum.id))
    }

    @Test
    fun `deleteByClassifierNodeIdAndEnumerationId removes only the target record`() {
        navRepo.save(NodeAttributeValue(classifierNode = phone, enumeration = colorEnum, selectedValue = blackValue))
        navRepo.save(NodeAttributeValue(classifierNode = phone, enumeration = osEnum,    selectedValue = iosValue))

        navRepo.deleteByClassifierNodeIdAndEnumerationId(phone.id, colorEnum.id)

        assertFalse(navRepo.existsByClassifierNodeIdAndEnumerationId(phone.id, colorEnum.id))
        assertTrue(navRepo.existsByClassifierNodeIdAndEnumerationId(phone.id, osEnum.id))
    }

    @Test
    fun `unique constraint prevents duplicate node-enumeration pair`() {
        navRepo.save(NodeAttributeValue(classifierNode = phone, enumeration = colorEnum, selectedValue = blackValue))

        assertThrows(Exception::class.java) {
            navRepo.saveAndFlush(NodeAttributeValue(classifierNode = phone, enumeration = colorEnum, selectedValue = whiteValue))
        }
    }

    @Test
    fun `countBySelectedValueId returns correct count`() {
        navRepo.save(NodeAttributeValue(classifierNode = phone,  enumeration = colorEnum, selectedValue = blackValue))
        navRepo.save(NodeAttributeValue(classifierNode = laptop, enumeration = colorEnum, selectedValue = blackValue))

        assertEquals(2L, navRepo.countBySelectedValueId(blackValue.id))
        assertEquals(0L, navRepo.countBySelectedValueId(whiteValue.id))
    }
}
