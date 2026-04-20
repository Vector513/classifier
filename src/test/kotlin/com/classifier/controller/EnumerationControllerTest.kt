package com.classifier.controller

import com.classifier.entity.EnumerationClass
import com.classifier.entity.Enumeration
import com.classifier.entity.EnumerationValue
import com.classifier.repository.EnumerationClassRepository
import com.classifier.repository.EnumerationRepository
import com.classifier.repository.EnumerationValueRepository
import com.classifier.repository.ClassifierNodeRepository
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
class EnumerationControllerTest {

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
    @Autowired lateinit var classRepo: EnumerationClassRepository
    @Autowired lateinit var enumRepo: EnumerationRepository
    @Autowired lateinit var valueRepo: EnumerationValueRepository
    @Autowired lateinit var nodeRepo: ClassifierNodeRepository

    private lateinit var cls: EnumerationClass
    private lateinit var enum: Enumeration

    @BeforeEach
    fun setup() {
        valueRepo.deleteAll()
        enumRepo.deleteAll()
        classRepo.deleteAll()
        nodeRepo.deleteAll()

        cls = classRepo.save(EnumerationClass(code = "COLOR", name = "Цвет"))
        enum = enumRepo.save(Enumeration(code = "PHONE-COLORS", name = "Цвета телефонов", enumerationClass = cls))
    }

    // ── EnumerationClass ──────────────────────────────────────────────────────

    @Test
    fun `GET all classes returns list`() {
        mockMvc.perform(get("/api/v1/enumeration-classes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("COLOR"))
    }

    @Test
    fun `GET class by id returns class`() {
        mockMvc.perform(get("/api/v1/enumeration-classes/${cls.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("COLOR"))
            .andExpect(jsonPath("$.name").value("Цвет"))
    }

    @Test
    fun `GET class 404 for non-existent`() {
        mockMvc.perform(get("/api/v1/enumeration-classes/9999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST creates enumeration class`() {
        mockMvc.perform(
            post("/api/v1/enumeration-classes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "SIZE", "name": "Размер", "description": "Размер изделия"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value("SIZE"))
            .andExpect(jsonPath("$.name").value("Размер"))
    }

    @Test
    fun `POST class returns 409 for duplicate code`() {
        mockMvc.perform(
            post("/api/v1/enumeration-classes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "COLOR", "name": "Дубликат"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `PATCH updates class`() {
        mockMvc.perform(
            patch("/api/v1/enumeration-classes/${cls.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Цветовая гамма"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Цветовая гамма"))
    }

    @Test
    fun `DELETE class returns 204`() {
        enumRepo.deleteAll()
        mockMvc.perform(delete("/api/v1/enumeration-classes/${cls.id}"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE class with enumerations returns 409`() {
        mockMvc.perform(delete("/api/v1/enumeration-classes/${cls.id}"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `GET enumerations by class`() {
        mockMvc.perform(get("/api/v1/enumeration-classes/${cls.id}/enumerations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("PHONE-COLORS"))
    }

    // ── Enumeration ───────────────────────────────────────────────────────────

    @Test
    fun `GET enumeration by id with empty values`() {
        mockMvc.perform(get("/api/v1/enumerations/${enum.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("PHONE-COLORS"))
            .andExpect(jsonPath("$.values.length()").value(0))
    }

    @Test
    fun `POST creates enumeration`() {
        mockMvc.perform(
            post("/api/v1/enumerations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "LAPTOP-COLORS", "name": "Цвета ноутбуков", "enumerationClassId": ${cls.id}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value("LAPTOP-COLORS"))
            .andExpect(jsonPath("$.enumerationClassName").value("Цвет"))
    }

    @Test
    fun `POST enumeration 409 for duplicate code`() {
        mockMvc.perform(
            post("/api/v1/enumerations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "PHONE-COLORS", "name": "Dup", "enumerationClassId": ${cls.id}}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `DELETE enumeration with values returns 409`() {
        valueRepo.save(EnumerationValue(code = "BLACK", name = "Чёрный", enumeration = enum, sortOrder = 0))
        mockMvc.perform(delete("/api/v1/enumerations/${enum.id}"))
            .andExpect(status().isConflict)
    }

    // ── EnumerationValue ──────────────────────────────────────────────────────

    @Test
    fun `POST adds value to enumeration`() {
        mockMvc.perform(
            post("/api/v1/enumerations/${enum.id}/values")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "BLACK", "name": "Чёрный"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value("BLACK"))
            .andExpect(jsonPath("$.sortOrder").value(0))
    }

    @Test
    fun `POST second value gets sort order 1`() {
        mockMvc.perform(
            post("/api/v1/enumerations/${enum.id}/values")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "BLACK", "name": "Чёрный"}""")
        )
        mockMvc.perform(
            post("/api/v1/enumerations/${enum.id}/values")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code": "WHITE", "name": "Белый"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sortOrder").value(1))
    }

    @Test
    fun `GET values returns ordered list`() {
        valueRepo.save(EnumerationValue(code = "BLACK", name = "Чёрный", enumeration = enum, sortOrder = 0))
        valueRepo.save(EnumerationValue(code = "WHITE", name = "Белый", enumeration = enum, sortOrder = 1))

        mockMvc.perform(get("/api/v1/enumerations/${enum.id}/values"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].code").value("BLACK"))
            .andExpect(jsonPath("$[1].code").value("WHITE"))
    }

    @Test
    fun `PATCH reorder moves value position`() {
        val v0 = valueRepo.save(EnumerationValue(code = "BLACK", name = "Чёрный", enumeration = enum, sortOrder = 0))
        valueRepo.save(EnumerationValue(code = "WHITE", name = "Белый", enumeration = enum, sortOrder = 1))
        valueRepo.save(EnumerationValue(code = "GOLD", name = "Золотой", enumeration = enum, sortOrder = 2))

        // Переместить BLACK (sortOrder=0) на позицию 2
        mockMvc.perform(
            patch("/api/v1/enumerations/${enum.id}/values/${v0.id}/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newSortOrder": 2}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sortOrder").value(2))
    }

    @Test
    fun `DELETE value shifts remaining sort orders`() {
        val v0 = valueRepo.save(EnumerationValue(code = "BLACK", name = "Чёрный", enumeration = enum, sortOrder = 0))
        valueRepo.save(EnumerationValue(code = "WHITE", name = "Белый", enumeration = enum, sortOrder = 1))

        mockMvc.perform(delete("/api/v1/enumerations/${enum.id}/values/${v0.id}"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/enumerations/${enum.id}/values"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sortOrder").value(0))
    }
}
