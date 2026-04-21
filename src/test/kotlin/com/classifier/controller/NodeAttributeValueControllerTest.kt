package com.classifier.controller

import com.classifier.entity.*
import com.classifier.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NodeAttributeValueControllerTest {

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

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var navRepo: NodeAttributeValueRepository
    @Autowired lateinit var nodeRepo: ClassifierNodeRepository
    @Autowired lateinit var enumRepo: EnumerationRepository
    @Autowired lateinit var valueRepo: EnumerationValueRepository
    @Autowired lateinit var classRepo: EnumerationClassRepository

    lateinit var node: ClassifierNode
    lateinit var enumeration: Enumeration
    lateinit var blackValue: EnumerationValue
    lateinit var whiteValue: EnumerationValue

    @BeforeEach
    fun setup() {
        navRepo.deleteAll()
        valueRepo.deleteAll()
        enumRepo.deleteAll()
        classRepo.deleteAll()
        nodeRepo.deleteAll()

        node = nodeRepo.save(ClassifierNode(code = "IPHONE16", name = "iPhone 16", sortOrder = 0))
        val cls = classRepo.save(EnumerationClass(code = "COLOR", name = "Цвет"))
        enumeration = enumRepo.save(Enumeration(code = "PHONE-COLORS", name = "Цвета телефонов", enumerationClass = cls))
        blackValue = valueRepo.save(EnumerationValue(code = "BLACK", name = "Чёрный", enumeration = enumeration, sortOrder = 0))
        whiteValue = valueRepo.save(EnumerationValue(code = "WHITE", name = "Белый", enumeration = enumeration, sortOrder = 1))
    }

    @Test
    fun `GET all attributes returns empty list for new node`() {
        mockMvc.perform(get("/api/v1/nodes/${node.id}/attributes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `PUT selects value and returns 200`() {
        mockMvc.perform(
            put("/api/v1/nodes/${node.id}/attributes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enumerationId": ${enumeration.id}, "valueId": ${blackValue.id}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedValueCode").value("BLACK"))
            .andExpect(jsonPath("$.selectedValueName").value("Чёрный"))
            .andExpect(jsonPath("$.classifierNodeName").value("iPhone 16"))
            .andExpect(jsonPath("$.enumerationName").value("Цвета телефонов"))
    }

    @Test
    fun `PUT replaces existing selection`() {
        navRepo.save(NodeAttributeValue(classifierNode = node, enumeration = enumeration, selectedValue = blackValue))

        mockMvc.perform(
            put("/api/v1/nodes/${node.id}/attributes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enumerationId": ${enumeration.id}, "valueId": ${whiteValue.id}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedValueCode").value("WHITE"))
    }

    @Test
    fun `GET single attribute returns selected value`() {
        navRepo.save(NodeAttributeValue(classifierNode = node, enumeration = enumeration, selectedValue = blackValue))

        mockMvc.perform(get("/api/v1/nodes/${node.id}/attributes/${enumeration.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedValueCode").value("BLACK"))
    }

    @Test
    fun `GET single attribute returns 404 when not selected`() {
        mockMvc.perform(get("/api/v1/nodes/${node.id}/attributes/${enumeration.id}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT returns 422 when value does not belong to enumeration`() {
        val otherCls = classRepo.save(EnumerationClass(code = "SIZE", name = "Размер"))
        val otherEnum = enumRepo.save(Enumeration(code = "SIZES", name = "Размеры", enumerationClass = otherCls))
        val xlValue = valueRepo.save(EnumerationValue(code = "XL", name = "XL", enumeration = otherEnum, sortOrder = 0))

        mockMvc.perform(
            put("/api/v1/nodes/${node.id}/attributes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enumerationId": ${enumeration.id}, "valueId": ${xlValue.id}}""")
        )
            .andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `DELETE clears attribute and returns 204`() {
        navRepo.save(NodeAttributeValue(classifierNode = node, enumeration = enumeration, selectedValue = blackValue))

        mockMvc.perform(delete("/api/v1/nodes/${node.id}/attributes/${enumeration.id}"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/nodes/${node.id}/attributes/${enumeration.id}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE returns 404 when attribute not set`() {
        mockMvc.perform(delete("/api/v1/nodes/${node.id}/attributes/${enumeration.id}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET all returns list after multiple selections`() {
        val cls2 = classRepo.save(EnumerationClass(code = "OS", name = "ОС"))
        val osEnum = enumRepo.save(Enumeration(code = "PHONE-OS", name = "ОС телефонов", enumerationClass = cls2))
        val iosValue = valueRepo.save(EnumerationValue(code = "IOS", name = "iOS", enumeration = osEnum, sortOrder = 0))

        navRepo.save(NodeAttributeValue(classifierNode = node, enumeration = enumeration, selectedValue = blackValue))
        navRepo.save(NodeAttributeValue(classifierNode = node, enumeration = osEnum, selectedValue = iosValue))

        mockMvc.perform(get("/api/v1/nodes/${node.id}/attributes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }
}
