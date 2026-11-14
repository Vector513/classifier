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
    private var supplies = 0L
    private var assembly = 0L
    private var subassembly = 0L
    private var variant = 0L
    private var cable = 0L
    private var clip = 0L
    private var meter = 0L
    private var pcs = 0L

    private fun node(code: String, parent: Long? = root): Long = jdbc.queryForObject("""
        INSERT INTO classifier_node(code,name,parent_id,sort_order,created_at,updated_at)
        VALUES (?,?,?,0,now(),now()) RETURNING id""",Long::class.java,code,code,parent)!!
    private fun product(code: String, kind: ProductKind, parent: Long=root, unit: Long=pcs): Long {
        val id=node(code,parent)
        jdbc.update("UPDATE classifier_node SET node_kind='PRODUCT',unit_of_measure_id=? WHERE id=?",unit,id)
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
        meter=jdbc.queryForObject("INSERT INTO unit_of_measure(code,name) VALUES ('M','метры') RETURNING id",Long::class.java)!!
        root=node("PRODUCTS",null)
        supplies=node("SUPPLIES")
        val cables=node("CABLES",supplies)
        assembly=product("ASSEMBLY",ProductKind.ASSEMBLY)
        subassembly=product("SUBASSEMBLY",ProductKind.ASSEMBLY)
        variant=product("VARIANT",ProductKind.ASSEMBLY)
        cable=product("NETWORK-CABLE",ProductKind.MATERIAL,cables,meter)
        clip=product("CABLE-CLIP",ProductKind.PURCHASED)
    }
    private fun base(): Long {
        val sub=draft(subassembly)
        line(sub,1,cable,"0.5"); line(sub,2,clip,"3"); service.release(sub)
        val top=draft(assembly)
        line(top,1,subassembly,"2",sub); line(top,2,cable,"0.25"); line(top,3,clip,"1")
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
        checkAmount(totals.getValue(cable).totalQuantity,"3.75")
        checkAmount(totals.getValue(clip).totalQuantity,"21")
        checkAmount(totals.getValue(subassembly).totalQuantity,"6")
        val filtered=service.totals(id,BigDecimal("3"),supplies)
        assertEquals(listOf(cable),filtered.map { it.componentId })
        assertEquals("M",filtered.single().unitCode)
        mvc.perform(get("/api/v1/bom/specifications/$id/totals").param("quantity","3").param("classId",supplies.toString()))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].totalQuantity").value(3.75))
    }
    @Test fun `modification replaces excludes and restores inherited positions without copying base`() {
        val base=base(); val changed=draft(variant,base)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM bom_line WHERE specification_id=?",Int::class.java,changed))
        line(changed,2,cable,"1"); service.exclude(changed,3)
        checkAmount(service.totals(changed,BigDecimal.ONE,supplies).single().totalQuantity,"2")
        assertEquals(2,service.lines(changed).size)
        service.reset(changed,2); service.reset(changed,3)
        checkAmount(service.totals(changed,BigDecimal.ONE,supplies).single().totalQuantity,"1.25")
        service.release(changed)
        val second=draft(variant,changed); line(second,2,cable,"2")
        checkAmount(service.totals(second,BigDecimal.ONE,supplies).single().totalQuantity,"3")
        checkAmount(service.totals(base,BigDecimal.ONE,supplies).single().totalQuantity,"1.25")
    }

    @Test fun `cost sums terminal components and reports missing catalog prices`() {
        val id = base()
        val rubles = jdbc.queryForObject(
            "INSERT INTO unit_of_measure(code, name) VALUES ('RUB', 'рубль') RETURNING id", Long::class.java
        )
        val price = jdbc.queryForObject("""
            INSERT INTO numeric_parameter(code, name, unit_of_measure_id, created_at, updated_at)
            VALUES ('PRICE', 'Цена', ?, now(), now()) RETURNING id
        """, Long::class.java, rubles)
        jdbc.update("""
            INSERT INTO node_numeric_parameter(classifier_node_id, numeric_parameter_id, created_at)
            VALUES (?, ?, now())
        """, root, price)
        fun priceComponent(component: Long, value: String) {
            jdbc.update("""
                INSERT INTO node_numeric_value(classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
            """, component, price, BigDecimal(value))
        }
        priceComponent(cable, "100")
        priceComponent(subassembly, "9000")
        mvc.perform(get("/api/v1/bom/specifications/$id/cost"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pricedSubtotal").value("125.00"))
            .andExpect(jsonPath("$.missingPrices[0].code").value("CABLE-CLIP"))
            .andExpect(jsonPath("$.missingPrices.length()").value(1))
            .andExpect(jsonPath("$.positions[0].position").value(1))
            .andExpect(jsonPath("$.positions[0].unitPrice").value("9000"))
            .andExpect(jsonPath("$.positions[0].pricedSubtotal").value("100.00"))
            .andExpect(jsonPath("$.positions[0].missingPrice").value(true))
            .andExpect(jsonPath("$.positions[2].unitPrice").isEmpty)

        priceComponent(clip, "2.5")
        mvc.perform(get("/api/v1/bom/specifications/$id/cost"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pricedSubtotal").value("142.50"))
            .andExpect(jsonPath("$.missingPrices").isEmpty)
            .andExpect(jsonPath("$.positions[0].pricedSubtotal").value("115.00"))
            .andExpect(jsonPath("$.positions[0].missingPrice").value(false))
            .andExpect(jsonPath("$.positions[1].unitPrice").value("100"))
            .andExpect(jsonPath("$.positions[1].pricedSubtotal").value("25.00"))
            .andExpect(jsonPath("$.positions[2].unitPrice").value("2.5"))

        val changed = draft(variant, id)
        service.exclude(changed, 3)
        assertEquals("140.00", service.cost(changed).pricedSubtotal)
        assertEquals(listOf(1, 2), service.cost(changed).positions.map { it.position })
        assertEquals("142.50", service.cost(id).pricedSubtotal)
    }

    @Test fun `description can be edited only on a draft`() {
        val id = draft(assembly)
        val request = put("/api/v1/bom/specifications/$id/description")
            .contentType(MediaType.APPLICATION_JSON).content("""{"description":"Уточнённый состав"}""")
        mvc.perform(request).andExpect(status().isNoContent)
        assertEquals("Уточнённый состав", service.get(id).description)

        line(id, 1, cable, "1")
        service.release(id)
        mvc.perform(request).andExpect(status().isConflict)
    }

    @Test fun `batch calculation requires whole pieces and accepts decimal meters`() {
        val pieces = base()
        mvc.perform(get("/api/v1/bom/specifications/$pieces/explosion").param("quantity", "1.5"))
            .andExpect(status().isUnprocessableEntity)
        mvc.perform(get("/api/v1/bom/specifications/$pieces/totals")
            .param("quantity", "1.5").param("classId", root.toString()))
            .andExpect(status().isUnprocessableEntity)
        mvc.perform(get("/api/v1/bom/specifications/$pieces/explosion").param("quantity", "2.0"))
            .andExpect(status().isOk)

        val meters = draft(cable)
        line(meters, 1, clip, "2")
        mvc.perform(get("/api/v1/bom/specifications/$meters/totals")
            .param("quantity", "1.5").param("classId", root.toString()))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].totalQuantity").value(3.0))
    }

    @Test fun `released history and pinned child versions are immutable`() {
        val base=base()
        requestLine(base,1,"""{"componentId":$cable,"quantity":4}""").andExpect(status().isConflict)
        mvc.perform(delete("/api/v1/bom/specifications/$base")).andExpect(status().isConflict)
        val child=service.lines(base).first().componentSpecificationId!!
        val revised=draft(subassembly,child); line(revised,1,cable,"10"); service.release(revised)
        checkAmount(service.totals(base,BigDecimal.ONE,supplies).single().totalQuantity,"1.25")
        val next=draft(assembly,base); assertEquals(2,service.get(next).revision)
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            jdbc.update("UPDATE bom_line SET quantity=9 WHERE specification_id=?",base)
        }
        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            jdbc.update("UPDATE bom_specification SET description='tampered' WHERE id=?",base)
        }
    }
    @Test fun `physical product cycle through older released revision is rejected and rolled back`() {
        val a=draft(assembly); line(a,1,cable,"1"); service.release(a)
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
            requestLine(id,1,"""{"componentId":$cable,"quantity":$qty}""").andExpect(status().isUnprocessableEntity)
        }
        requestLine(id,0,"""{"componentId":$cable,"quantity":1}""").andExpect(status().isUnprocessableEntity)
        requestLine(id,1,"""{"componentId":999999,"quantity":1}""").andExpect(status().isNotFound)
        mvc.perform(post("/api/v1/bom/specifications/$id/release")).andExpect(status().isUnprocessableEntity)
        mvc.perform(get("/api/v1/bom/specifications/$id/explosion").param("quantity","0")).andExpect(status().isUnprocessableEntity)
        mvc.perform(get("/api/v1/bom/specifications/$id/totals").param("classId","999999")).andExpect(status().isNotFound)
        val child=draft(subassembly)
        requestLine(id,1,"""{"componentId":$subassembly,"componentSpecificationId":$child,"quantity":1}""")
            .andExpect(status().isUnprocessableEntity)
        line(child,1,cable,"1"); service.release(child)
        requestLine(id,1,"""{"componentId":$cable,"componentSpecificationId":$child,"quantity":1}""")
            .andExpect(status().isUnprocessableEntity)
    }
    @Test fun `new draft registration deletion and missing resources follow REST contract`() {
        mvc.perform(post("/api/v1/bom/products").contentType(MediaType.APPLICATION_JSON)
            .content("""{"nodeId":$root,"kind":"ASSEMBLY","unitId":$pcs}""")).andExpect(status().isUnprocessableEntity)
        mvc.perform(post("/api/v1/bom/products").contentType(MediaType.APPLICATION_JSON)
            .content("""{"nodeId":$cable,"kind":"MATERIAL","unitId":$meter}""")).andExpect(status().isConflict)
        val response=mvc.perform(post("/api/v1/bom/specifications").contentType(MediaType.APPLICATION_JSON)
            .content("""{"productId":$assembly,"description":"Первая версия"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id=json.readTree(response)["id"].asLong()
        requestLine(id,1,"""{"componentId":$cable,"quantity":0.000001}""").andExpect(status().isOk)
        mvc.perform(delete("/api/v1/bom/specifications/$id")).andExpect(status().isNoContent)
        mvc.perform(get("/api/v1/bom/specifications/$id")).andExpect(status().isNotFound)
        mvc.perform(delete("/api/v1/nodes/$cable")).andExpect(status().isConflict)
    }
    @Test fun `schema initialization is repeatable without resetting history`() {
        val base=base()
        initializer.run(org.springframework.boot.DefaultApplicationArguments())
        assertEquals("RELEASED",service.get(base).status)
        checkAmount(service.totals(base,BigDecimal.ONE,supplies).single().totalQuantity,"1.25")
    }

    @Test fun `electronics demo loads five released pairs and preserves changes on restart`() {
        val existing = base()
        val phone = product("PHONES-SAMSUNG-S24", ProductKind.PART)
        jdbc.update("UPDATE classifier_node SET name='Мой Galaxy' WHERE id=?", phone)
        jdbc.update("DELETE FROM schema_migration WHERE name='bom-electronics-demo-v1'")
        initializer.seedDemo()

        val demos = service.specifications().filter { it.productCode.startsWith("BOM-EL-") }
        val released = demos.filter { it.status == "RELEASED" }.associateBy { it.productCode.removePrefix("BOM-EL-") }
        assertEquals(11, released.size)
        val variants = mapOf(
            "PC-PRO" to ("PC-STD" to listOf(3,4)),
            "HOME-PRO" to ("HOME-START" to listOf(2,3,4,6)),
            "GAME-PREMIUM" to ("GAME-STD" to listOf(2,3,5)),
            "SEC-PRO" to ("SEC-STD" to listOf(1,3,5)),
            "PHONE-PREMIUM" to ("PHONE-STD" to listOf(5,6))
        )
        for ((code, pair) in variants) {
            val (baseCode, overrides) = pair
            val modification = released.getValue(code)
            val original = released.getValue(baseCode)
            assertEquals(original.id, modification.baseId)
            assertEquals(1, modification.revision)
            assertEquals(overrides, service.localPositions(modification.id))
            assertEquals(service.lines(original.id).filterNot { it.position in overrides },
                service.lines(modification.id).filterNot { it.position in overrides })
        }

        val gamingSet = released.getValue("GAME-STD")
        val gameClass = service.products().first { it.code == "BOM-EL-GAME-KEYBOARD" }.id
        checkAmount(service.totals(gamingSet.id, BigDecimal.TEN, gameClass).single().totalQuantity, "10")
        val pc = released.getValue("PC-PRO")
        val fan = service.explode(pc.id, BigDecimal("3")).single { it.path == listOf(7,1) }
        assertEquals("BOM-EL-PC-FAN", fan.line.code)
        checkAmount(fan.totalQuantity, "6")
        val cableTotal = service.explode(released.getValue("SEC-PRO").id, BigDecimal("3")).single { it.path == listOf(5) }
        assertEquals("M", cableTotal.line.unitCode)
        checkAmount(cableTotal.totalQuantity, "120")
        val reusedPhone = service.lines(released.getValue("PHONE-PREMIUM").id).first()
        assertEquals(phone, reusedPhone.componentId)
        assertEquals("Мой Galaxy", reusedPhone.name)
        assertEquals(ProductKind.PART, service.products().single { it.id == phone }.kind)

        val demoDraft = demos.single { it.status == "DRAFT" }
        assertEquals(2, demoDraft.revision)
        assertEquals(gamingSet.id, demoDraft.baseId)
        assertEquals(listOf(1,2,3,5), service.lines(demoDraft.id).map { it.position })
        service.delete(demoDraft.id)
        jdbc.update("UPDATE classifier_node SET name='Мой игровой набор' WHERE id=?", gamingSet.productId)
        initializer.seedDemo()
        assertEquals(demos.map { it.id }.toSet() - demoDraft.id,
            service.specifications().filter { it.productCode.startsWith("BOM-EL-") }.map { it.id }.toSet())
        assertEquals("Мой игровой набор", service.get(gamingSet.id).productName)
        checkAmount(service.totals(existing,BigDecimal.ONE,supplies).single().totalQuantity,"1.25")
    }

    @Test fun `catalog parameter demo preserves exclusions and edited values`() {
        jdbc.update("DELETE FROM schema_migration WHERE name='catalog-parameters-demo-v3'")
        fun loadFixture() = jdbc.execute(org.springframework.core.io.ClassPathResource("catalog-parameters-demo.sql")
            .getContentAsString(Charsets.UTF_8))
        loadFixture()
        val kitId=jdbc.queryForObject("SELECT id FROM classifier_node WHERE code='DEMO-KIT-SIX'",Long::class.java)!!
        val lengthId=jdbc.queryForObject("SELECT id FROM numeric_parameter WHERE code='DEMO-LENGTH'",Long::class.java)!!
        val surfaceId=jdbc.queryForObject("SELECT id FROM enumeration WHERE code='DEMO-SURFACE'",Long::class.java)!!
        assertEquals(false,jdbc.queryForObject("SELECT catalog_numeric_applicable(?,?)",Boolean::class.java,kitId,lengthId))
        assertEquals(false,jdbc.queryForObject("SELECT catalog_enum_applicable(?,?)",Boolean::class.java,kitId,surfaceId))
        val itemId=jdbc.queryForObject("SELECT id FROM classifier_node WHERE code='DEMO-BLANK-MATTE'",Long::class.java)!!
        jdbc.update("UPDATE node_numeric_value SET value=0.7 WHERE classifier_node_id=? AND numeric_parameter_id=?",itemId,lengthId)
        loadFixture()
        val value=jdbc.queryForObject("SELECT value FROM node_numeric_value WHERE classifier_node_id=? AND numeric_parameter_id=?",
            BigDecimal::class.java,itemId,lengthId)!!
        checkAmount(value,"0.7")
    }

    @Test fun `catalog products are registered when used in a specification`() {
        val phone = node("PHONE")
        val charger = node("CHARGER")
        jdbc.update("UPDATE classifier_node SET node_kind='MODEL',unit_of_measure_id=? WHERE id=?", pcs, phone)
        jdbc.update("UPDATE classifier_node SET node_kind='PRODUCT',unit_of_measure_id=? WHERE id=?", pcs, charger)
        val id = draft(phone)
        requestLine(id,1,"""{"componentId":$root,"quantity":1}""").andExpect(status().isUnprocessableEntity)
        requestLine(id,1,"""{"componentId":$charger,"componentSpecificationId":$id,"quantity":1}""")
            .andExpect(status().isUnprocessableEntity)
        assertFalse(service.products().any { it.id==charger })
        requestLine(id,1,"""{"componentId":$charger,"quantity":2}""").andExpect(status().isOk)
        assertEquals(ProductKind.ASSEMBLY,service.products().single { it.id==phone }.kind)
        assertEquals(ProductKind.PURCHASED,service.products().single { it.id==charger }.kind)
        checkAmount(service.totals(id,BigDecimal("3"),root).single().totalQuantity,"6")
    }
}
