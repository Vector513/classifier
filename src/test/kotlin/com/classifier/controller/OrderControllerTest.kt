package com.classifier.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as httpGet
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerTest {
    companion object {
        @Container val postgres=PostgreSQLContainer("postgres:15")
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url",postgres::getJdbcUrl)
            r.add("spring.datasource.username",postgres::getUsername)
            r.add("spring.datasource.password",postgres::getPassword)
        }
    }
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var dataSource: javax.sql.DataSource
    var template=0L;var unit=0L;var product=0L;var model=0L;var group=0L;var enumeration=0L;var customer=0L
    private val api="/api/v1/orders"
    private fun scalar(sql: String,vararg args: Any?)=jdbc.queryForObject(sql,Long::class.java,*args)!!
    private fun count(table: String)=scalar("SELECT count(*) FROM $table")
    private fun c(v: Any)=mapOf("kind" to "CONST","value" to v)
    private fun p(code: String)=mapOf("kind" to "PARAMETER","parameterCode" to code)
    private fun a(code: String)=mapOf("kind" to "FORMAL","formalCode" to code)
    private fun op(op: String,vararg args: Any)=mapOf("kind" to "OPERATOR","operator" to op,"args" to args.toList())
    private fun call(id: Long,vararg args: Any)=mapOf("kind" to "CALL","functionId" to id,"args" to args.toList())
    private fun post(path: String,body: Any): JsonNode {
        val r=mvc.perform(post(api+path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn().response
        assertTrue(r.status in 200..299,"${r.status}: ${r.getContentAsString(Charsets.UTF_8)}")
        return mapper.readTree(r.getContentAsString(Charsets.UTF_8))
    }
    private fun bad(path: String,body: Any): String {
        val r=mvc.perform(post(api+path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn().response
        assertTrue(r.status in 400..499,"${r.status}: ${r.getContentAsString(Charsets.UTF_8)}");return r.getContentAsString(Charsets.UTF_8)
    }
    private fun get(path: String): JsonNode {
        val r=mvc.perform(httpGet(api+path)).andReturn().response;assertEquals(200,r.status,r.getContentAsString(Charsets.UTF_8));return mapper.readTree(r.getContentAsString(Charsets.UTF_8))
    }
    private fun parameter(code: String,type: String="NUMBER",required: Boolean=true)=post("/templates/$template/parameters",mapOf("code" to code,"name" to code,"dataType" to type,"required" to required,"minValue" to if(type=="NUMBER")1 else null,"maxValue" to if(type=="NUMBER")2000 else null,"enumerationId" to if(type=="ENUM")enumeration else null))["id"].asLong()
    private fun variant(code: String="V1",values: Map<String,Any> = mapOf("h" to 500,"w" to 300))=post("/templates/$template/variants",mapOf("code" to code,"name" to code,"values" to values))["id"].asLong()
    private fun order()=post("",mapOf("customerId" to customer,"orderNumber" to "O-${count("customer_order")}","orderDate" to "2026-11-14","requestedDate" to "2026-11-28"))["id"].asLong()
    private fun rule(code: String,expression: Any)=post("/templates/$template/rules",mapOf("code" to code,"name" to code,"errorMessage" to "Правило $code нарушено","expression" to expression))["id"].asLong()
    private fun function(code: String,expr: Any,params: List<String> = listOf("a","b"),result: String="BOOLEAN")=post("/functions",mapOf("code" to code,"name" to code,"resultType" to result,"parameters" to params.map { mapOf("code" to it,"dataType" to "NUMBER") },"expression" to expr))["id"].asLong()
    private fun resourcePrice(node: Long,price: String,code: String="PRICE") {
        val rub=jdbc.queryForList("SELECT id FROM unit_of_measure WHERE code='RUB'",Long::class.java).firstOrNull()
            ?: scalar("INSERT INTO unit_of_measure(code,name) VALUES('RUB','рубли') RETURNING id")
        val parameter=jdbc.queryForList("SELECT id FROM numeric_parameter WHERE code=?",Long::class.java,code).firstOrNull()
            ?: scalar("INSERT INTO numeric_parameter(code,name,unit_of_measure_id,created_at,updated_at) VALUES(?,?,?,now(),now()) RETURNING id",code,code,rub)
        jdbc.update("INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,created_at) VALUES(?,?,now()) ON CONFLICT DO NOTHING",node,parameter)
        jdbc.update("INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at) VALUES(?,?,?::numeric,now(),now()) ON CONFLICT(classifier_node_id,numeric_parameter_id) DO UPDATE SET value=excluded.value",node,parameter,price)
    }
    private fun component(node: Long,quantity: String="1",quantityParameter: Long?=null,conditions: Map<String,Any> = emptyMap(),group: String="",required: Boolean=false)=
        post("/templates/$template/components",mapOf("componentId" to node,"quantity" to quantity,"quantityParameterId" to quantityParameter,"whenValues" to conditions,"selectionGroup" to group,"requiredGroup" to required))["id"].asLong()
    @BeforeEach fun setup() {
        jdbc.execute("TRUNCATE classifier_node,enumeration_class,unit_of_measure,numeric_parameter,customer,constraint_function RESTART IDENTITY CASCADE")
        unit=scalar("INSERT INTO unit_of_measure(code,name) VALUES('PCS','штуки') RETURNING id")
        fun node(code: String,kind: String)=scalar("INSERT INTO classifier_node(code,name,node_kind,unit_of_measure_id,sort_order,created_at,updated_at) VALUES(?,?,?, ?,0,now(),now()) RETURNING id",code,code,kind,if(kind=="GROUP")null else unit)
        group=node("G","GROUP");model=node("M","MODEL");product=node("P","PRODUCT")
        val cls=scalar("INSERT INTO enumeration_class(code,name,created_at,updated_at) VALUES('CON','Соединения',now(),now()) RETURNING id")
        enumeration=scalar("INSERT INTO enumeration(code,name,enumeration_class_id,created_at,updated_at) VALUES('CON','Соединение',?,now(),now()) RETURNING id",cls)
        jdbc.update("INSERT INTO enumeration_value(code,name,enumeration_id,sort_order,created_at,updated_at) VALUES('FLANGE','Фланец',?,0,now(),now()),('RAIL','Шина',?,1,now(),now())",enumeration,enumeration)
        customer=post("/customers",mapOf("name" to "Заказчик","phone" to "+70000000000"))["id"].asLong()
        template=post("/templates",mapOf("productId" to model,"code" to "T","name" to "Шаблон"))["id"].asLong()
        parameter("h");parameter("w");parameter("connection","ENUM",false);parameter("insulated","BOOLEAN",false)
    }
    @Test fun `order totals automatically follow resource prices and quantities without manual overrides`() {
        resourcePrice(product,"10.10")
        val order=order()
        post("/$order/lines",mapOf("productId" to product,"quantity" to 3,"unitPrice" to "9999"))
        assertEquals("30.30",get("/$order")["order"]["total_amount"].asText())
        resourcePrice(product,"20.20")
        assertEquals("60.60",get("/$order")["order"]["total_amount"].asText())
        resourcePrice(product,"12.34","PURCHASE_PRICE")
        assertEquals("37.02",get("/$order")["order"]["total_amount"].asText())
        assertEquals("37.02",get("")["orders"].first { it["id"].asLong()==order }["total_amount"].asText())
        val document=mvc.perform(httpGet("$api/$order/document")).andReturn().response.getContentAsString(Charsets.UTF_8)
        assertTrue(document.contains("37,02"));assertTrue(document.contains("составу изделий"))
    }
    @Test fun `variant cost depends on selected components and numeric parameter quantities`() {
        resourcePrice(product,"12.34")
        val h=scalar("SELECT id FROM configuration_parameter WHERE template_id=? AND code='h'",template)
        component(product,"0.125",h)
        component(product,"2",conditions=mapOf("connection" to "FLANGE"),group="Соединение",required=true)
        component(product,"1",conditions=mapOf("connection" to "RAIL"),group="Соединение",required=true)
        val v=variant(values=mapOf("h" to 16,"w" to 300,"connection" to "FLANGE"))
        val order=order();post("/$order/lines",mapOf("variantId" to v,"quantity" to 2))
        val details=get("/$order")
        assertEquals("49.36",details["lines"][0]["unit_price"].asText())
        assertEquals("98.72",details["order"]["total_amount"].asText())
        assertEquals("4.000000",details["lines"][0]["cost_components"][0]["quantity"].asText())
        val other=variant("OTHER",mapOf("h" to 32,"w" to 300,"connection" to "RAIL"))
        post("/$order/lines",mapOf("variantId" to other,"quantity" to 1))
        assertEquals("61.70",get("/$order")["lines"][1]["unit_price"].asText())
    }
    @Test fun `nested assemblies expand to leaves without counting assembly retail price twice`() {
        resourcePrice(product,"2.50")
        val kit=scalar("INSERT INTO classifier_node(code,name,node_kind,unit_of_measure_id,sort_order,created_at,updated_at) VALUES('KIT','Сборка','PRODUCT',?,0,now(),now()) RETURNING id",unit)
        jdbc.update("INSERT INTO bom_product(node_id,kind) VALUES(?,'PURCHASED'),(?,'ASSEMBLY')",product,kit)
        val spec=scalar("INSERT INTO bom_specification(product_id,revision,description) VALUES(?,1,'Состав') RETURNING id",kit)
        jdbc.update("INSERT INTO bom_line(specification_id,position,component_id,quantity) VALUES(?,1,?,4)",spec,product)
        jdbc.update("UPDATE bom_specification SET status='RELEASED' WHERE id=?",spec)
        resourcePrice(kit,"999")
        component(kit,"2");component(product,"3")
        val order=order();post("/$order/lines",mapOf("variantId" to variant(),"quantity" to 1))
        val detail=get("/$order")["lines"][0]
        assertEquals("27.50",detail["unit_price"].asText());assertEquals(1,detail["cost_components"].size())
        assertEquals(11,detail["cost_components"][0]["quantity"].asDouble().toInt())
        post("/$order/lines",mapOf("productId" to kit,"quantity" to 2))
        assertEquals("47.50",get("/$order")["order"]["total_amount"].asText())
    }
    @Test fun `missing component prices and uncovered mandatory groups make total incomplete`() {
        val rule=component(product,conditions=mapOf("connection" to "FLANGE"),group="Соединение",required=true)
        val order=order();post("/$order/lines",mapOf("variantId" to variant(),"quantity" to 1))
        var detail=get("/$order")
        assertTrue(detail["order"]["total_amount"].isNull)
        assertTrue(detail["lines"][0]["cost_issues"].toString().contains("обязательной группы"))
        val noPrice=variant("NO_PRICE",mapOf("h" to 500,"w" to 300,"connection" to "FLANGE"))
        post("/$order/lines",mapOf("variantId" to noPrice,"quantity" to 1))
        assertTrue(get("/$order")["lines"][1]["cost_issues"].toString().contains("Не задана цена компонента"))
        resourcePrice(product,"0")
        assertEquals("0.00",get("/$order")["lines"][1]["unit_price"].asText())
        assertTrue(get("/$order")["order"]["total_amount"].isNull)
        mvc.perform(delete("$api/templates/$template/components/$rule")).andReturn().response.let { assertEquals(204,it.status) }
        assertTrue(get("/$order")["lines"][0]["cost_issues"].toString().contains("не задан состав"))
    }
    @Test fun `component quantities and conditions must refer to the template parameters`() {
        bad("/templates/$template/components",mapOf("componentId" to product,"quantity" to -1,"whenValues" to emptyMap<String,Any>()))
        bad("/templates/$template/components",mapOf("componentId" to product,"quantity" to 1,"quantityParameterId" to 999999,"whenValues" to emptyMap<String,Any>()))
        bad("/templates/$template/components",mapOf("componentId" to product,"quantity" to 1,"whenValues" to mapOf("unknown" to 1)))
        bad("/templates/$template/components",mapOf("componentId" to product,"quantity" to 1,"whenValues" to mapOf("connection" to "OTHER")))
        assertEquals(0,count("configuration_component"))
        component(product,conditions=mapOf("connection" to "FLANGE"))
        val connection=scalar("SELECT id FROM configuration_parameter WHERE template_id=? AND code='connection'",template)
        assertThrows(Exception::class.java){jdbc.update("DELETE FROM configuration_parameter WHERE id=?",connection)}
        assertThrows(Exception::class.java){jdbc.update("UPDATE configuration_parameter SET code='renamed' WHERE id=?",connection)}
    }
    @Test fun `all diagram tables and independent variant JSONB storage are present`() {
        val expected=listOf("customer","customer_order","order_line","product_template","product_variant","configuration_parameter","variant_parameter_value","configuration_rule","expression","constraint_function","formal_parameter")
        expected.forEach { assertEquals(1,scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",it)) }
        val id=variant(values=mapOf("h" to 500,"w" to 300,"connection" to "FLANGE","insulated" to false))
        val values=get("/variants/$id")["values"];assertEquals(false,values["insulated"].asBoolean());assertEquals("FLANGE",values["connection"].asText())
        assertEquals(3,count("classifier_node"));assertEquals(0,count("node_numeric_value"));assertEquals(4,count("variant_parameter_value"))
        assertEquals(1,scalar("SELECT count(*) FROM variant_parameter_value WHERE variant_id=? AND value IS NULL AND enumeration_value_id IS NOT NULL",id))
    }
    @Test fun `trial evaluates new and edited functions without saving changes`() {
        val existingId = function("TRY_EXISTING", op("LE", a("a"), a("b")))
        val expressionCount = count("expression")
        val trial = mapOf(
            "code" to "TRY_NEW",
            "name" to "Пробная функция",
            "resultType" to "BOOLEAN",
            "parameters" to listOf("a", "b").map { mapOf("code" to it, "dataType" to "NUMBER") },
            "expression" to op("GE", a("a"), a("b"))
        )
        val arguments = listOf(650, 450)
        assertTrue(post("/functions/trial", mapOf("function" to trial, "arguments" to arguments)).asBoolean())
        assertTrue(post("/functions/trial?id=$existingId", mapOf("function" to trial, "arguments" to arguments)).asBoolean())
        assertFalse(post("/functions/$existingId/evaluate", mapOf("arguments" to arguments)).asBoolean())
        bad("/functions/trial", mapOf("function" to trial, "arguments" to listOf(650)))
        assertEquals(1, count("constraint_function"))
        assertEquals(2, count("formal_parameter"))
        assertEquals(expressionCount, count("expression"))
        assertEquals(1, scalar("SELECT count(*) FROM constraint_function WHERE code='TRY_EXISTING'"))
    }
    @Test fun `runtime functions evaluate nested arithmetic and reject ratio violations atomically`() {
        val f=function("RATIO",op("AND",op("GE",op("DIV",a("a"),a("b")),c(.5)),op("LE",op("DIV",a("a"),a("b")),c(2))))
        rule("RATIO",call(f,p("h"),p("w")))
        assertTrue(post("/functions/$f/evaluate",mapOf("arguments" to listOf(500,300))).asBoolean())
        assertFalse(post("/functions/$f/evaluate",mapOf("arguments" to listOf(1500,100))).asBoolean())
        variant();val before=count("product_variant")
        bad("/templates/$template/variants",mapOf("code" to "BAD","name" to "Bad","values" to mapOf("h" to 1500,"w" to 100)))
        assertEquals(before,count("product_variant"));assertEquals(2,count("variant_parameter_value"))
        val errors=post("/templates/$template/validate",mapOf("values" to mapOf("h" to 1500,"w" to 100)))
        assertFalse(errors["valid"].asBoolean());assertTrue(errors["errors"][0].asText().contains("RATIO"))
    }
    @Test fun `ordered arguments and enum dependent implication work`() {
        val subtraction=function("SUBTRACT",op("SUB",a("a"),a("b")),result="NUMBER")
        assertEquals(-7,post("/functions/$subtraction/evaluate",mapOf("arguments" to listOf(3,10))).asInt())
        rule("ENUM",op("IMPLIES",op("EQ",p("connection"),c("FLANGE")),op("GT",p("h"),c(400))))
        variant(values=mapOf("h" to 500,"w" to 300,"connection" to "FLANGE"))
        variant("V2",mapOf("h" to 100,"w" to 300,"connection" to "RAIL"))
        bad("/templates/$template/variants",mapOf("code" to "BAD","name" to "Bad","values" to mapOf("h" to 100,"w" to 300,"connection" to "FLANGE")))
    }
    @Test fun `types mandatory unknown enum and bounds are checked before storing`() {
        val samples=listOf(mapOf("h" to 1),mapOf("h" to "500","w" to 300),mapOf("h" to 0,"w" to 300),mapOf("h" to 500,"w" to 300,"unknown" to 1),mapOf("h" to 500,"w" to 300,"connection" to "OTHER"),mapOf("h" to 500,"w" to 300,"insulated" to "false"))
        samples.forEach { values->bad("/templates/$template/variants",mapOf("code" to "BAD","name" to "Bad","values" to values)) }
        assertEquals(0,count("product_variant"));assertEquals(0,count("variant_parameter_value"))
    }
    @Test fun `division by zero is reported and lazy boolean skips unused branch`() {
        // zero is allowed here so the arithmetic evaluator, rather than min bound, is tested.
        jdbc.update("UPDATE configuration_parameter SET min_value=0 WHERE code='w'")
        rule("DIVIDE",op("GT",op("DIV",p("h"),p("w")),c(0)))
        val checked=post("/templates/$template/validate",mapOf("values" to mapOf("h" to 500,"w" to 0)))
        assertFalse(checked["valid"].asBoolean());assertTrue(checked["errors"][0].asText().contains("ноль"))
        val f=function("SHORT",op("OR",c(true),op("GT",op("DIV",c(1),c(0)),c(0))),emptyList())
        assertTrue(post("/functions/$f/evaluate",mapOf("arguments" to emptyList<Int>())).asBoolean())
    }
    @Test fun `order accepts ready and configured products and supports replace remove and document`() {
        val order=order();val v=variant()
        post("/$order/lines",mapOf("productId" to product,"quantity" to 2,"note" to "<script>alert(1)</script>"))
        val line=post("/$order/lines",mapOf("variantId" to v,"quantity" to 10))["id"].asLong()
        post("/$order/lines?id=$line",mapOf("variantId" to v,"quantity" to 12))
        val response=get("/$order");assertEquals(2,response["lines"].size());assertEquals(12,response["lines"][1]["quantity"].asInt());assertEquals(2,response["lines"][1]["parameters"].size())
        val doc=mvc.perform(httpGet("$api/$order/document")).andReturn().response;assertEquals(200,doc.status);assertTrue(doc.getContentAsString(Charsets.UTF_8).contains("&lt;script&gt;"));assertFalse(doc.getContentAsString(Charsets.UTF_8).contains("<script>"));assertTrue(doc.getContentAsString(Charsets.UTF_8).contains("500"))
        assertEquals(204,mvc.perform(delete("$api/$order/lines/$line")).andReturn().response.status);assertEquals(1,get("/$order")["lines"].size())
    }
    @Test fun `configured line is one transaction and invalid quantity leaves no orphan variant`() {
        val order=order();val values=mapOf("h" to 500,"w" to 300)
        bad("/$order/configured-lines",mapOf("templateId" to template,"code" to "ORPHAN","name" to "No","values" to values,"quantity" to 1.5))
        assertEquals(0,count("product_variant"));assertEquals(0,count("order_line"))
        val result=post("/$order/configured-lines",mapOf("templateId" to template,"code" to "ATOMIC","name" to "Atomic","values" to values,"quantity" to 3))
        assertTrue(result["variantId"].asLong()>0);assertEquals(1,count("product_variant"));assertEquals(1,count("order_line"))
    }
    @Test fun `alternative references groups templates quantity and ownership are enforced`() {
        val order=order();val other=order();val v=variant()
        for(body in listOf(mapOf("productId" to group,"quantity" to 1),mapOf("productId" to model,"quantity" to 1),mapOf("quantity" to 1),mapOf("productId" to product,"variantId" to v,"quantity" to 1),mapOf("productId" to product,"quantity" to 0)))bad("/$order/lines",body)
        val line=post("/$order/lines",mapOf("productId" to product,"quantity" to 1))["id"].asLong()
        bad("/$other/lines?id=$line",mapOf("productId" to product,"quantity" to 7));assertEquals(1,get("/$order")["lines"][0]["quantity"].asInt())
    }
    @Test fun `saved variants prevent invalidating rule parameter and ordered value changes`() {
        val v=variant();val before=count("expression")
        bad("/templates/$template/rules",mapOf("code" to "INVALIDATE","name" to "No","errorMessage" to "No","expression" to op("GT",p("h"),c(1000))))
        assertEquals(before,count("expression"));assertEquals(0,count("configuration_rule"))
        assertThrows(Exception::class.java){jdbc.update("UPDATE configuration_parameter SET min_value=1000 WHERE code='h'")}
        val order=order();post("/$order/lines",mapOf("variantId" to v,"quantity" to 1))
        assertThrows(Exception::class.java){jdbc.update("UPDATE variant_parameter_value SET value='600' WHERE variant_id=? AND parameter_id=(SELECT id FROM configuration_parameter WHERE code='h')",v)}
        assertEquals(500,get("/variants/$v")["values"]["h"].asInt())
    }
    @Test fun `functions validate scope arity return type and recursive calls`() {
        bad("/functions",mapOf("code" to "WRONG","name" to "Wrong","resultType" to "BOOLEAN","parameters" to emptyList<Any>(),"expression" to c(1)))
        val f=function("F",op("GT",a("a"),a("b")))
        bad("/functions/$f/evaluate",mapOf("arguments" to listOf(1)))
        for (args in listOf(mapOf("a" to 1),true,1)) bad("/functions/$f/evaluate",mapOf("arguments" to args))
        bad("/templates/$template/rules",mapOf("code" to "ARGS","name" to "Args","errorMessage" to "No","expression" to call(f,c(1))))
        bad("/functions?id=$f",mapOf("code" to "F","name" to "F","resultType" to "BOOLEAN","parameters" to emptyList<Any>(),"expression" to call(f)))
        bad("/functions",mapOf("code" to "SCOPE","name" to "Scope","resultType" to "NUMBER","parameters" to emptyList<Any>(),"expression" to p("h")))
        assertEquals(1,count("constraint_function"));assertEquals(3,count("expression"))
    }
    @Test fun `SQL mutations cannot introduce wrong value root ownership or cycles`() {
        val v=variant()
        assertThrows(Exception::class.java){jdbc.update("UPDATE variant_parameter_value SET value='\"bad\"' WHERE variant_id=?",v)}
        val f=function("F",op("GT",a("a"),a("b")))
        val root=scalar("SELECT root_expression_id FROM constraint_function WHERE id=?",f)
        assertThrows(Exception::class.java){jdbc.update("UPDATE expression SET parent_expression_id=id,arg_position=1 WHERE id=?",root)}
        assertThrows(Exception::class.java){jdbc.update("INSERT INTO configuration_rule(template_id,root_expression_id,code,name,error_message) VALUES(?,?,'DOUBLE','Double','No')",template,root)}
        assertEquals(0,count("configuration_rule"))
    }
    @Test fun `unused variant can change while ordered variant cannot`() {
        val v=variant()
        fun update(h: Int)=mvc.perform(put("$api/variants/$v").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(mapOf("code" to "V1","name" to "Changed","values" to mapOf("h" to h,"w" to 300))))).andReturn().response
        assertEquals(200,update(600).status);assertEquals(600,get("/variants/$v")["values"]["h"].asInt())
        val order=order();post("/$order/lines",mapOf("variantId" to v,"quantity" to 1))
        assertTrue(update(700).status in 400..499);assertEquals(600,get("/variants/$v")["values"]["h"].asInt())
    }
    @Test fun `demonstration is repeatable and leaves existing catalog records intact`() {
        val seed=com.classifier.service.OrderInitializer(dataSource,true)
        seed.run(org.springframework.boot.DefaultApplicationArguments())
        val totals=listOf(count("product_variant"),count("customer_order"),count("configuration_rule"),count("expression"),count("classifier_node"))
        assertEquals(1,totals[0]);assertEquals(3,totals[2]);assertEquals(6,totals[4])
        seed.run(org.springframework.boot.DefaultApplicationArguments())
        assertEquals(totals,listOf(count("product_variant"),count("customer_order"),count("configuration_rule"),count("expression"),count("classifier_node")))
        assertEquals(3,scalar("SELECT count(*) FROM classifier_node WHERE code IN ('G','M','P')"))
        assertEquals(2,scalar("SELECT count(*) FROM get_order_contents((SELECT id FROM customer_order WHERE order_number='ЛР7-0001'))"))
    }

    @Test fun `shop examples cover six configurations and preserve edits on repeated load`() {
        val groups=listOf("BOM-EL-KITS","LAPTOPS","PHONES","BOM-EL-GAME-DEVICES","BOM-EL-HOME-DEVICES","BOM-EL-SEC-DEVICES")
        groups.forEach { code -> jdbc.update("INSERT INTO classifier_node(code,name,node_kind,sort_order,created_at,updated_at) VALUES(?,?,'GROUP',0,now(),now())",code,code) }
        val ready=listOf("BOM-EL-PHONE-POWERBANK","BOM-EL-GAME-MAT","BOM-EL-GAME-WIRELESS-MOUSE","BOM-EL-SEC-CABLE","BOM-EL-SEC-HDD2","BOM-EL-HOME-BULB","ASSORT-USB-C-CABLE")
        ready.forEach { code -> jdbc.update("INSERT INTO classifier_node(code,name,node_kind,unit_of_measure_id,sort_order,created_at,updated_at) VALUES(?,?,'PRODUCT',?,0,now(),now())",code,code,unit) }
        val colors=scalar("INSERT INTO enumeration(code,name,enumeration_class_id,created_at,updated_at) SELECT 'PHONE-COLORS','Цвета',id,now(),now() FROM enumeration_class WHERE code='CON' RETURNING id")
        listOf("BLACK","BLUE","WHITE").forEach { color -> jdbc.update("INSERT INTO enumeration_value(enumeration_id,code,name,sort_order,created_at,updated_at) VALUES(?,?,?,0,now(),now())",colors,color,color) }
        val originalNodes=count("classifier_node")
        fun load()=org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(org.springframework.core.io.ClassPathResource("orders-shop-demo.sql")).apply {
            setSqlScriptEncoding("UTF-8");setSeparator(org.springframework.jdbc.datasource.init.ScriptUtils.EOF_STATEMENT_SEPARATOR)
        }.execute(dataSource)
        load()
        assertEquals(6,scalar("SELECT count(*) FROM product_template WHERE code LIKE 'SHOP-%'"))
        assertEquals(18,scalar("SELECT count(*) FROM product_variant WHERE code LIKE 'SHOP-%'"))
        assertEquals(36,scalar("SELECT count(*) FROM configuration_parameter WHERE template_id IN (SELECT id FROM product_template WHERE code LIKE 'SHOP-%')"))
        assertEquals(20,count("configuration_rule"));assertEquals(3,count("constraint_function"))
        assertEquals(8,count("customer_order"));assertEquals(23,count("order_line"))
        assertEquals(originalNodes+6,count("classifier_node"))
        assertEquals(0,scalar("SELECT count(*) FROM product_variant WHERE NOT check_constraints(template_id,orders_variant_values(id))"))
        val cases=listOf(
            Triple("SHOP-PC-GAMING","psu",450), Triple("SHOP-WORKPLACE-OFFICE","dock",false),
            Triple("SHOP-PHONE-EVERYDAY","charger",false), Triple("SHOP-GAMING-WIRELESS","connection","WIRED"),
            Triple("SHOP-HOME-FLAT","hub",false), Triple("SHOP-SECURITY-BUSINESS","channels",4),
            Triple("SHOP-SECURITY-BUSINESS","poe_budget",30), Triple("SHOP-SECURITY-BUSINESS","storage",1024)
        )
        cases.forEach { (code,key,value) ->
            val variant=jdbc.queryForMap("SELECT id,template_id FROM product_variant WHERE code=?",code)
            val values=get("/variants/${variant["id"]}")["values"].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            values.set<JsonNode>(key,mapper.valueToTree(value))
            assertFalse(post("/templates/${variant["template_id"]}/validate",mapOf("values" to values))["valid"].asBoolean(),code+":"+key)
        }
        jdbc.update("UPDATE customer SET name='Изменённый покупатель' WHERE email='anna.krylova@example.test'")
        val before=listOf(count("classifier_node"),count("product_variant"),count("customer_order"),count("order_line"),count("expression"))
        load()
        assertEquals(before,listOf(count("classifier_node"),count("product_variant"),count("customer_order"),count("order_line"),count("expression")))
        assertEquals(1,scalar("SELECT count(*) FROM customer WHERE name='Изменённый покупатель'"))
        assertEquals(3,scalar("SELECT count(*) FROM classifier_node WHERE code IN ('G','M','P')"))
    }

}
