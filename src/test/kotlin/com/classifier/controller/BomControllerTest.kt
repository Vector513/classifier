package com.classifier.controller

import com.classifier.dto.*
import com.classifier.service.BomService
import com.classifier.service.BomInitializer
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BomControllerTest {
    companion object {
        @Container val postgres = PostgreSQLContainer("postgres:15")
        @JvmStatic @DynamicPropertySource
        fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url", postgres::getJdbcUrl)
            r.add("spring.datasource.username", postgres::getUsername)
            r.add("spring.datasource.password", postgres::getPassword)
        }
    }
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var service: BomService
    @Autowired lateinit var initializer: BomInitializer
    private var root = 0L
    private var materials = 0L
    private var assembly = 0L
    private var subassembly = 0L
    private var variant = 0L
    private var steel = 0L
    private var bolt = 0L
    private var kg = 0L
    private var pcs = 0L

    private fun node(code: String, parent: Long? = root): Long = jdbc.queryForObject("""
        INSERT INTO classifier_node(code,name,parent_id,sort_order,created_at,updated_at)
        VALUES (?,?,?,0,now(),now()) RETURNING id""",Long::class.java,code,code,parent)!!
    private fun product(code: String, kind: ProductKind, parent: Long=root, unit: Long=pcs): Long {
        val id=node(code,parent)
        service.register(RegisterBomProductRequest(id,kind,unit))
        return id
    }
    private fun draft(id: Long, base: Long?=null) = service.create(CreateBomSpecificationRequest(id,base,"Тестовая версия")).id
    private fun line(spec: Long, pos: Int, component: Long, qty: String, child: Long?=null) =
        service.putLine(spec,pos,BomLineRequest(component,child,BigDecimal(qty)))
    private fun requestLine(spec: Long, pos: Int, body: String) = mvc.perform(put("/api/v1/bom/specifications/$spec/lines/$pos")
        .contentType(MediaType.APPLICATION_JSON).content(body))
    private fun checkAmount(actual: BigDecimal, expected: String) = assertEquals(0,actual.compareTo(BigDecimal(expected)))

    @BeforeEach fun setup() {
        // Отдельная БД Testcontainers; очистка только тестовых таблиц.
        jdbc.execute("TRUNCATE bom_line,bom_specification,bom_product,classifier_node,unit_of_measure RESTART IDENTITY CASCADE")
        pcs=jdbc.queryForObject("INSERT INTO unit_of_measure(code,name) VALUES ('PCS','штуки') RETURNING id",Long::class.java)!!
        kg=jdbc.queryForObject("INSERT INTO unit_of_measure(code,name) VALUES ('KG','килограммы') RETURNING id",Long::class.java)!!
        root=node("PRODUCTS",null)
        materials=node("MATERIALS")
        val metals=node("METALS",materials)
        assembly=product("ASSEMBLY",ProductKind.ASSEMBLY)
        subassembly=product("SUBASSEMBLY",ProductKind.ASSEMBLY)
        variant=product("VARIANT",ProductKind.ASSEMBLY)
        steel=product("STEEL",ProductKind.MATERIAL,metals,kg)
        bolt=product("BOLT",ProductKind.PURCHASED)
    }
    private fun base(): Long {
        val sub=draft(subassembly)
        line(sub,1,steel,"0.5"); line(sub,2,bolt,"3"); service.release(sub)
        val top=draft(assembly)
        line(top,1,subassembly,"2",sub); line(top,2,steel,"0.25"); line(top,3,bolt,"1")
        service.release(top)
        return top
    }

    @Test fun `recursive quantities sum shared resources and include descendant classes`() {
        val id=base()
        val all=service.explode(id,BigDecimal("3"))
        assertEquals(5,all.size)
        assertEquals(listOf(1,1),all[1].path)
        assertEquals(2,all[1].depth)
        val totals=service.totals(id,BigDecimal("3"),root).associateBy { it.componentId }
        checkAmount(totals.getValue(steel).totalQuantity,"3.75")
        checkAmount(totals.getValue(bolt).totalQuantity,"21")
        checkAmount(totals.getValue(subassembly).totalQuantity,"6")
        val filtered=service.totals(id,BigDecimal("3"),materials)
        assertEquals(listOf(steel),filtered.map { it.componentId })
        assertEquals("KG",filtered.single().unitCode)
        mvc.perform(get("/api/v1/bom/specifications/$id/totals").param("quantity","3").param("classId",materials.toString()))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].totalQuantity").value(3.75))
    }
    @Test fun `modification replaces excludes and restores inherited positions without copying base`() {
        val base=base(); val changed=draft(variant,base)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM bom_line WHERE specification_id=?",Int::class.java,changed))
        line(changed,2,steel,"1"); service.exclude(changed,3)
        checkAmount(service.totals(changed,BigDecimal.ONE,materials).single().totalQuantity,"2")
        assertEquals(2,service.lines(changed).size)
        service.reset(changed,2); service.reset(changed,3)
        checkAmount(service.totals(changed,BigDecimal.ONE,materials).single().totalQuantity,"1.25")
        service.release(changed)
        val second=draft(variant,changed); line(second,2,steel,"2")
        checkAmount(service.totals(second,BigDecimal.ONE,materials).single().totalQuantity,"3")
        checkAmount(service.totals(base,BigDecimal.ONE,materials).single().totalQuantity,"1.25")
    }
    @Test fun `released history and pinned child versions are immutable`() {
        val base=base()
        requestLine(base,1,"""{"componentId":$steel,"quantity":4}""").andExpect(status().isConflict)
        mvc.perform(delete("/api/v1/bom/specifications/$base")).andExpect(status().isConflict)
        val child=service.lines(base).first().componentSpecificationId!!
        val revised=draft(subassembly,child); line(revised,1,steel,"10"); service.release(revised)
        checkAmount(service.totals(base,BigDecimal.ONE,materials).single().totalQuantity,"1.25")
        val next=draft(assembly,base); assertEquals(2,service.get(next).revision)
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            jdbc.update("UPDATE bom_line SET quantity=9 WHERE specification_id=?",base)
        }
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            jdbc.update("UPDATE bom_specification SET description='tampered' WHERE id=?",base)
        }
    }
    @Test fun `physical product cycle through older released revision is rejected and rolled back`() {
        val a=draft(assembly); line(a,1,steel,"1"); service.release(a)
        val b=draft(subassembly); line(b,1,assembly,"1",a); service.release(b)
        val revised=draft(assembly,a)
        requestLine(revised,2,"""{"componentId":$subassembly,"componentSpecificationId":$b,"quantity":1}""")
            .andExpect(status().isBadRequest)
        assertEquals(listOf(1),service.lines(revised).map { it.position })
        requestLine(revised,2,"""{"componentId":$assembly,"quantity":1}""").andExpect(status().isBadRequest)
    }
    @Test fun `invalid references quantities positions and empty release return client errors`() {
        val id=draft(assembly)
        for (qty in listOf("0","-1","0.0000001","10000000000000")) {
            requestLine(id,1,"""{"componentId":$steel,"quantity":$qty}""").andExpect(status().isUnprocessableEntity)
        }
        requestLine(id,0,"""{"componentId":$steel,"quantity":1}""").andExpect(status().isUnprocessableEntity)
        requestLine(id,1,"""{"componentId":999999,"quantity":1}""").andExpect(status().isNotFound)
        mvc.perform(post("/api/v1/bom/specifications/$id/release")).andExpect(status().isUnprocessableEntity)
        mvc.perform(get("/api/v1/bom/specifications/$id/explosion").param("quantity","0")).andExpect(status().isUnprocessableEntity)
        mvc.perform(get("/api/v1/bom/specifications/$id/totals").param("classId","999999")).andExpect(status().isNotFound)
        val child=draft(subassembly)
        requestLine(id,1,"""{"componentId":$subassembly,"componentSpecificationId":$child,"quantity":1}""")
            .andExpect(status().isUnprocessableEntity)
        line(child,1,steel,"1"); service.release(child)
        requestLine(id,1,"""{"componentId":$steel,"componentSpecificationId":$child,"quantity":1}""")
            .andExpect(status().isUnprocessableEntity)
    }
    @Test fun `new draft registration deletion and missing resources follow REST contract`() {
        mvc.perform(post("/api/v1/bom/products").contentType(MediaType.APPLICATION_JSON)
            .content("""{"nodeId":$root,"kind":"ASSEMBLY","unitId":$pcs}""")).andExpect(status().isUnprocessableEntity)
        mvc.perform(post("/api/v1/bom/products").contentType(MediaType.APPLICATION_JSON)
            .content("""{"nodeId":$steel,"kind":"MATERIAL","unitId":$kg}""")).andExpect(status().isConflict)
        val response=mvc.perform(post("/api/v1/bom/specifications").contentType(MediaType.APPLICATION_JSON)
            .content("""{"productId":$assembly,"description":"Первая версия"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id=json.readTree(response)["id"].asLong()
        requestLine(id,1,"""{"componentId":$steel,"quantity":0.000001}""").andExpect(status().isOk)
        mvc.perform(delete("/api/v1/bom/specifications/$id")).andExpect(status().isNoContent)
        mvc.perform(get("/api/v1/bom/specifications/$id")).andExpect(status().isNotFound)
        mvc.perform(delete("/api/v1/nodes/$steel")).andExpect(status().isConflict)
    }
    @Test fun `schema initialization is repeatable without resetting history`() {
        val base=base()
        initializer.run(org.springframework.boot.DefaultApplicationArguments())
        assertEquals("RELEASED",service.get(base).status)
        checkAmount(service.totals(base,BigDecimal.ONE,materials).single().totalQuantity,"1.25")
    }
}
