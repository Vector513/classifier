package com.classifier.controller

import com.classifier.entity.ClassifierNode
import com.classifier.entity.UnitOfMeasure
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.UnitOfMeasureRepository
import com.fasterxml.jackson.databind.ObjectMapper
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
class ClassifierNodeControllerTest {

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
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var nodeRepo: ClassifierNodeRepository

    @Autowired
    lateinit var unitRepo: UnitOfMeasureRepository

    private lateinit var rootNode: ClassifierNode
    private lateinit var childNode: ClassifierNode
    private lateinit var leafNode: ClassifierNode
    private lateinit var unit: UnitOfMeasure

    @BeforeEach
    fun setup() {
        nodeRepo.deleteAll()
        unitRepo.deleteAll()

        unit = unitRepo.save(UnitOfMeasure(code = "PCS", name = "штуки"))
        rootNode = nodeRepo.save(ClassifierNode(code = "ELECTRONICS", name = "Электроника", sortOrder = 0))
        childNode = nodeRepo.save(ClassifierNode(code = "PHONES", name = "Смартфоны", parent = rootNode, unitOfMeasure = unit, sortOrder = 0))
        leafNode = nodeRepo.save(ClassifierNode(code = "PHONES-APPLE", name = "Apple", parent = childNode, sortOrder = 0))
    }

    // --- GET ---

    @Test
    fun `GET roots returns root nodes`() {
        mockMvc.perform(get("/api/v1/nodes/roots"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("ELECTRONICS"))
            .andExpect(jsonPath("$[0].parentId").isEmpty)
    }

    @Test
    fun `GET by id returns node`() {
        mockMvc.perform(get("/api/v1/nodes/${childNode.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("PHONES"))
            .andExpect(jsonPath("$.parentId").value(rootNode.id))
            .andExpect(jsonPath("$.unitOfMeasure.code").value("PCS"))
    }

    @Test
    fun `GET by id returns 404 for non-existent node`() {
        mockMvc.perform(get("/api/v1/nodes/9999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `GET children returns direct children sorted by sortOrder`() {
        mockMvc.perform(get("/api/v1/nodes/${rootNode.id}/children"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("PHONES"))
    }

    // --- POST ---

    @Test
    fun `POST creates root node`() {
        mockMvc.perform(
            post("/api/v1/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "GAMING", "name": "Игровые устройства"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value("GAMING"))
            .andExpect(jsonPath("$.parentId").isEmpty)
    }

    @Test
    fun `POST creates child node with unit of measure`() {
        mockMvc.perform(
            post("/api/v1/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "TABLETS", "name": "Планшеты", "parentId": ${rootNode.id}, "unitOfMeasureId": ${unit.id}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.parentId").value(rootNode.id))
            .andExpect(jsonPath("$.unitOfMeasure.code").value("PCS"))
    }

    @Test
    fun `POST returns 409 for duplicate code`() {
        mockMvc.perform(
            post("/api/v1/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "PHONES", "name": "Duplicate"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `POST returns 404 for non-existent parent`() {
        mockMvc.perform(
            post("/api/v1/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "ORPHAN", "name": "No parent", "parentId": 9999}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST returns 422 for blank code`() {
        mockMvc.perform(
            post("/api/v1/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "", "name": "test"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.status").value(422))
    }

    // --- PATCH ---

    @Test
    fun `PATCH updates node name`() {
        mockMvc.perform(
            patch("/api/v1/nodes/${childNode.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Мобильные телефоны"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Мобильные телефоны"))
            .andExpect(jsonPath("$.code").value("PHONES"))
    }

    // --- DELETE ---

    @Test
    fun `DELETE removes leaf node`() {
        mockMvc.perform(delete("/api/v1/nodes/${leafNode.id}"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/nodes/${leafNode.id}"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE returns 409 for node with children`() {
        mockMvc.perform(delete("/api/v1/nodes/${rootNode.id}"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
    }

    // --- MOVE ---

    @Test
    fun `PATCH move changes parent`() {
        // Move Apple from PHONES to ELECTRONICS (root)
        mockMvc.perform(
            patch("/api/v1/nodes/${leafNode.id}/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newParentId": ${rootNode.id}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parentId").value(rootNode.id))
    }

    @Test
    fun `PATCH move to null makes root`() {
        mockMvc.perform(
            patch("/api/v1/nodes/${childNode.id}/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newParentId": null}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parentId").isEmpty)
    }

    @Test
    fun `PATCH move to self returns 400`() {
        mockMvc.perform(
            patch("/api/v1/nodes/${childNode.id}/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newParentId": ${childNode.id}}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH move to own descendant returns 400`() {
        mockMvc.perform(
            patch("/api/v1/nodes/${childNode.id}/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newParentId": ${leafNode.id}}""")
        )
            .andExpect(status().isBadRequest)
    }

    // --- TREE TRAVERSAL ---

    @Test
    fun `GET descendants returns all descendants`() {
        mockMvc.perform(get("/api/v1/nodes/${rootNode.id}/descendants"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET ancestors returns path to root`() {
        mockMvc.perform(get("/api/v1/nodes/${leafNode.id}/ancestors"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET terminals returns leaf nodes`() {
        mockMvc.perform(get("/api/v1/nodes/${rootNode.id}/terminals"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("PHONES-APPLE"))
    }

    @Test
    fun `GET tree returns full tree structure`() {
        mockMvc.perform(get("/api/v1/nodes/tree"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").value("ELECTRONICS"))
            .andExpect(jsonPath("$[0].children[0].code").value("PHONES"))
            .andExpect(jsonPath("$[0].children[0].children[0].code").value("PHONES-APPLE"))
    }

    // --- SEARCH ---

    @Test
    fun `GET search finds by code`() {
        mockMvc.perform(get("/api/v1/nodes/search").param("query", "APPLE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("PHONES-APPLE"))
    }

    @Test
    fun `GET search finds by name case-insensitive`() {
        mockMvc.perform(get("/api/v1/nodes/search").param("query", "смартфоны"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `GET search returns empty for no match`() {
        mockMvc.perform(get("/api/v1/nodes/search").param("query", "ZZZZZ"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // --- VALIDATE CYCLES ---

    @Test
    fun `POST validate-cycles returns valid for clean tree`() {
        mockMvc.perform(post("/api/v1/nodes/validate-cycles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.cycles.length()").value(0))
    }

    // --- REORDER ---

    @Test
    fun `PATCH reorder changes sort order`() {
        val child2 = nodeRepo.save(ClassifierNode(code = "LAPTOPS", name = "Ноутбуки", parent = rootNode, sortOrder = 1))

        mockMvc.perform(
            patch("/api/v1/nodes/${child2.id}/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newSortOrder": 0}""")
        )
            .andExpect(status().isNoContent)

        // Verify new order
        mockMvc.perform(get("/api/v1/nodes/${rootNode.id}/children"))
            .andExpect(jsonPath("$[0].code").value("LAPTOPS"))
            .andExpect(jsonPath("$[1].code").value("PHONES"))
    }
}
