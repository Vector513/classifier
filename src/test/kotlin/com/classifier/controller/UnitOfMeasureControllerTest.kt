package com.classifier.controller

import com.classifier.entity.UnitOfMeasure
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.UnitOfMeasureRepository
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
class UnitOfMeasureControllerTest {

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
    lateinit var unitRepo: UnitOfMeasureRepository

    @Autowired
    lateinit var nodeRepo: ClassifierNodeRepository

    private lateinit var unit: UnitOfMeasure

    @BeforeEach
    fun setup() {
        nodeRepo.deleteAll()
        unitRepo.deleteAll()
        unit = unitRepo.save(UnitOfMeasure(code = "PCS", name = "штуки"))
    }

    @Test
    fun `GET all returns units`() {
        mockMvc.perform(get("/api/v1/units"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("PCS"))
    }

    @Test
    fun `GET by id returns unit`() {
        mockMvc.perform(get("/api/v1/units/${unit.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("PCS"))
            .andExpect(jsonPath("$.name").value("штуки"))
    }

    @Test
    fun `GET by id returns 404 for non-existent`() {
        mockMvc.perform(get("/api/v1/units/9999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST creates unit`() {
        mockMvc.perform(
            post("/api/v1/units")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "KG", "name": "килограммы"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value("KG"))
            .andExpect(jsonPath("$.name").value("килограммы"))
    }

    @Test
    fun `POST returns 409 for duplicate code`() {
        mockMvc.perform(
            post("/api/v1/units")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "PCS", "name": "duplicate"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `POST returns 422 for blank code`() {
        mockMvc.perform(
            post("/api/v1/units")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "", "name": "test"}""")
        )
            .andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `PUT updates unit`() {
        mockMvc.perform(
            put("/api/v1/units/${unit.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "PCS", "name": "единицы"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("единицы"))
    }

    @Test
    fun `DELETE removes unit`() {
        mockMvc.perform(delete("/api/v1/units/${unit.id}"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/units/${unit.id}"))
            .andExpect(status().isNotFound)
    }
}
