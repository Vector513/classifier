package com.classifier.repository

import com.classifier.entity.ClassifierNode
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
class ClassifierNodeRepositoryTest {

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
    lateinit var nodeRepo: ClassifierNodeRepository

    @Autowired
    lateinit var unitRepo: UnitOfMeasureRepository

    // Электроника → Смартфоны → Apple → iPhone 16
    //                         → Samsung
    //            → Ноутбуки
    lateinit var electronics: ClassifierNode
    lateinit var phones: ClassifierNode
    lateinit var apple: ClassifierNode
    lateinit var iphone16: ClassifierNode
    lateinit var samsung: ClassifierNode
    lateinit var laptops: ClassifierNode

    @BeforeEach
    fun setUp() {
        nodeRepo.deleteAll()
        unitRepo.deleteAll()

        val pcs = unitRepo.save(UnitOfMeasure(code = "PCS", name = "штуки"))

        electronics = nodeRepo.save(ClassifierNode(code = "ELECTRONICS", name = "Электроника", sortOrder = 0))
        phones = nodeRepo.save(ClassifierNode(code = "PHONES", name = "Смартфоны", parent = electronics, sortOrder = 0, unitOfMeasure = pcs))
        apple = nodeRepo.save(ClassifierNode(code = "PHONES-APPLE", name = "Apple", parent = phones, sortOrder = 0))
        iphone16 = nodeRepo.save(ClassifierNode(code = "PHONES-APPLE-IP16", name = "iPhone 16", parent = apple, sortOrder = 0, unitOfMeasure = pcs))
        samsung = nodeRepo.save(ClassifierNode(code = "PHONES-SAMSUNG", name = "Samsung", parent = phones, sortOrder = 1))
        laptops = nodeRepo.save(ClassifierNode(code = "LAPTOPS", name = "Ноутбуки", parent = electronics, sortOrder = 1))
    }

    @Test
    fun `findByParentIsNull returns root nodes`() {
        val roots = nodeRepo.findByParentIsNullOrderBySortOrder()
        assertEquals(1, roots.size)
        assertEquals("ELECTRONICS", roots[0].code)
    }

    @Test
    fun `findByParentId returns children sorted by sortOrder`() {
        val children = nodeRepo.findByParentIdOrderBySortOrder(phones.id)
        assertEquals(2, children.size)
        assertEquals("PHONES-APPLE", children[0].code)
        assertEquals("PHONES-SAMSUNG", children[1].code)
    }

    @Test
    fun `existsByCode returns true for existing code`() {
        assertTrue(nodeRepo.existsByCode("ELECTRONICS"))
        assertFalse(nodeRepo.existsByCode("NONEXISTENT"))
    }

    @Test
    fun `countByParentId returns number of children`() {
        assertEquals(2, nodeRepo.countByParentId(electronics.id))
        assertEquals(2, nodeRepo.countByParentId(phones.id))
        assertEquals(0, nodeRepo.countByParentId(iphone16.id))
    }

    @Test
    fun `findDescendants returns all descendants recursively`() {
        val descendants = nodeRepo.findDescendants(electronics.id)
        assertEquals(5, descendants.size)
        val codes = descendants.map { it.code }.toSet()
        assertTrue(codes.containsAll(setOf("PHONES", "LAPTOPS", "PHONES-APPLE", "PHONES-SAMSUNG", "PHONES-APPLE-IP16")))
    }

    @Test
    fun `findDescendants of leaf returns empty list`() {
        val descendants = nodeRepo.findDescendants(iphone16.id)
        assertTrue(descendants.isEmpty())
    }

    @Test
    fun `findAncestors returns path to root`() {
        val ancestors = nodeRepo.findAncestors(iphone16.id)
        assertEquals(3, ancestors.size)
        val codes = ancestors.map { it.code }.toSet()
        assertTrue(codes.containsAll(setOf("PHONES-APPLE", "PHONES", "ELECTRONICS")))
    }

    @Test
    fun `findAncestors of root returns empty list`() {
        val ancestors = nodeRepo.findAncestors(electronics.id)
        assertTrue(ancestors.isEmpty())
    }

    @Test
    fun `findTerminals returns leaf nodes only`() {
        val terminals = nodeRepo.findTerminals(electronics.id)
        val codes = terminals.map { it.code }.toSet()
        assertEquals(setOf("PHONES-APPLE-IP16", "PHONES-SAMSUNG", "LAPTOPS"), codes)
    }

    @Test
    fun `searchByQuery finds by code`() {
        val results = nodeRepo.searchByQuery("APPLE")
        assertEquals(2, results.size)
    }

    @Test
    fun `searchByQuery finds by name case-insensitive`() {
        val results = nodeRepo.searchByQuery("ноутбуки")
        assertEquals(1, results.size)
        assertEquals("LAPTOPS", results[0].code)
    }

    @Test
    fun `searchByQuery returns empty for no match`() {
        val results = nodeRepo.searchByQuery("NONEXISTENT")
        assertTrue(results.isEmpty())
    }
}
