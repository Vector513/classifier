package com.classifier.controller

import com.classifier.dto.*
import com.classifier.entity.NodeKind
import com.classifier.service.*
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogControllerTest {
    companion object {
        @Container val postgres=PostgreSQLContainer("postgres:15")
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url",postgres::getJdbcUrl)
            r.add("spring.datasource.username",postgres::getUsername)
            r.add("spring.datasource.password",postgres::getPassword)
        }
    }
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var nodes: ClassifierNodeService
    @Autowired lateinit var enums: EnumerationService
    @Autowired lateinit var catalog: CatalogService
    @Autowired lateinit var attributes: NodeAttributeValueService
    @Autowired lateinit var numbers: NumericParameterService
    @Autowired lateinit var search: ItemSearchService
    @Autowired lateinit var mvc: MockMvc
    private var unit=0L
    private var group=0L
    private var model=0L
    private var item=0L
    private var enumId=0L
    private var black=0L
    private var white=0L
    private var red=0L
    private fun node(code: String,kind: NodeKind,parent: Long?=null)=nodes.create(CreateNodeRequest(code,code,parent,if(kind==NodeKind.GROUP)null else unit,kind)).id
    @BeforeEach fun setup() {
        jdbc.execute("TRUNCATE classifier_node,enumeration_class,unit_of_measure,numeric_parameter RESTART IDENTITY CASCADE")
        unit=jdbc.queryForObject("INSERT INTO unit_of_measure(code,name) VALUES('PCS','штуки') RETURNING id",Long::class.java)!!
        group=node("PHONES",NodeKind.GROUP)
        model=node("MODEL",NodeKind.MODEL,group)
        item=node("BLACK-SKU",NodeKind.PRODUCT,model)
        val cls=enums.createClass(CreateEnumerationClassRequest("COLOR","Цвет"))
        val enumeration=enums.createEnumeration(CreateEnumerationRequest("COLORS","Цвет",cls.id))
        enumId=enumeration.id
        black=enums.addValue(enumId,CreateEnumerationValueRequest("BLACK","Чёрный")).id
        white=enums.addValue(enumId,CreateEnumerationValueRequest("WHITE","Белый")).id
        red=enums.addValue(enumId,CreateEnumerationValueRequest("RED","Красный")).id
        catalog.setBinding(group,enumId,EnumerationBindingRequest())
        catalog.setBinding(model,enumId,EnumerationBindingRequest(listOf(black,white)))
    }
    @Test fun `empty groups stay groups and product children are rejected`() {
        val empty=node("EMPTY",NodeKind.GROUP,group)
        mvc.perform(get("/api/v1/nodes/$empty")).andExpect(jsonPath("$.nodeKind").value("GROUP")).andExpect(jsonPath("$.isTerminal").value(true))
        assertFalse(search.searchByMultipleFilters(MultiFilterRequest(rootNodeId=group)).any { it.id==empty })
        mvc.perform(post("/api/v1/nodes").contentType(MediaType.APPLICATION_JSON).content("""{"code":"BAD","name":"Bad","parentId":$item,"nodeKind":"GROUP"}"""))
            .andExpect(status().isUnprocessableEntity)
        mvc.perform(post("/api/v1/nodes").contentType(MediaType.APPLICATION_JSON).content("""{"code":"BAD-MODEL","name":"Bad","parentId":$model,"nodeKind":"MODEL","unitOfMeasureId":$unit}"""))
            .andExpect(status().isUnprocessableEntity)
    }
    @Test fun `model options differ from selected SKU and invalid color is rejected`() {
        assertEquals(setOf(black,white),catalog.effectiveEnumerations(model).single().values.map { it.id }.toSet())
        assertTrue(attributes.getNodeAttributes(model).isEmpty())
        attributes.selectValue(item,SelectEnumerationValueRequest(enumId,black))
        assertEquals(black,attributes.getNodeAttributes(item).single().selectedValue.id)
        mvc.perform(put("/api/v1/nodes/$item/attributes").contentType(MediaType.APPLICATION_JSON).content("""{"enumerationId":$enumId,"valueId":$red}"""))
            .andExpect(status().isUnprocessableEntity)
        // Сужение модели не может сделать существующее исполнение недопустимым.
        mvc.perform(put("/api/v1/nodes/$model/enumeration-bindings/$enumId").contentType(MediaType.APPLICATION_JSON).content("""{"allowedValueIds":[$white]}"""))
            .andExpect(status().isConflict)
        assertEquals(setOf(black,white),catalog.effectiveEnumerations(model).single().values.map { it.id }.toSet())
    }
    @Test fun `one list can be bound to several groups without duplicating values`() {
        val other=node("OTHER",NodeKind.GROUP)
        catalog.setBinding(other,enumId,EnumerationBindingRequest(listOf(red)))
        assertEquals(listOf(red),catalog.effectiveEnumerations(other).single().values.map { it.id })
        assertEquals(3,enums.getValues(enumId).size)
        mvc.perform(get("/api/v1/enumerations/$enumId")).andExpect(jsonPath("$.assignedNodeIds.length()").value(3))
    }
    @Test fun `moving a SKU cannot invalidate its chosen attributes`() {
        attributes.selectValue(item,SelectEnumerationValueRequest(enumId,black))
        val other=node("RED-MODEL",NodeKind.MODEL,group)
        catalog.setBinding(other,enumId,EnumerationBindingRequest(listOf(red)))
        mvc.perform(patch("/api/v1/nodes/$item/move").contentType(MediaType.APPLICATION_JSON).content("""{"newParentId":$other}"""))
            .andExpect(status().isConflict)
        assertEquals(model,nodes.getById(item).parent?.id)
    }
    @Test fun `numeric facts require an assigned parameter and valid ranges`() {
        val price=numbers.create(CreateNumericParameterRequest("PRICE","Цена",minValue=BigDecimal.ZERO,maxValue=BigDecimal("100"))).id
        mvc.perform(put("/api/v1/nodes/$item/numeric/values").contentType(MediaType.APPLICATION_JSON).content("""{"parameterId":$price,"value":50}"""))
            .andExpect(status().isUnprocessableEntity)
        numbers.assignToNode(group,price)
        numbers.setValue(item,SetNumericValueRequest(price,BigDecimal("50")))
        mvc.perform(patch("/api/v1/numeric-parameters/$price").contentType(MediaType.APPLICATION_JSON).content("""{"maxValue":40}"""))
            .andExpect(status().isConflict)
        assertEquals(0,numbers.getById(price).maxValue!!.compareTo(BigDecimal("100")))
    }
    @Test fun `search can distinguish available model variants from a chosen SKU value`() {
        attributes.selectValue(item,SelectEnumerationValueRequest(enumId,black))
        val exact=search.searchByMultipleFilters(MultiFilterRequest(rootNodeId=group,enumCriteria=listOf(EnumFilterCriterion(enumId,black))))
        assertEquals(listOf(item),exact.map { it.id })
        val available=search.searchByMultipleFilters(MultiFilterRequest(rootNodeId=group,enumCriteria=listOf(EnumFilterCriterion(enumId,black,true))))
        assertEquals(setOf(model,item),available.map { it.id }.toSet())
    }
    @Test fun `database rejects direct SQL writes that bypass API checks`() {
        assertThrows(Exception::class.java) {
            jdbc.update("INSERT INTO node_attribute_value(classifier_node_id,enumeration_id,enumeration_value_id,created_at,updated_at) VALUES(?,?,?,now(),now())",item,enumId,red)
        }
        assertThrows(Exception::class.java) { jdbc.update("UPDATE classifier_node SET parent_id=? WHERE id=?",model,group) }
        assertEquals(null,nodes.getById(group).parent)
    }
    @Test fun `deleting unused SKU cascades owned facts but retains model and dictionaries`() {
        attributes.selectValue(item,SelectEnumerationValueRequest(enumId,black))
        val param=numbers.create(CreateNumericParameterRequest("MASS","Масса")).id
        numbers.assignToNode(item,param)
        numbers.setValue(item,SetNumericValueRequest(param,BigDecimal.ONE))
        mvc.perform(delete("/api/v1/nodes/$item")).andExpect(status().isNoContent)
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM node_attribute_value WHERE classifier_node_id=?",Int::class.java,item))
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM node_numeric_value WHERE classifier_node_id=?",Int::class.java,item))
        assertEquals(model,nodes.getById(model).id)
        assertEquals(3,enums.getValues(enumId).size)
    }

    @Test fun `subclass can exclude inherited parameters without changing siblings and restore them`() {
        val sibling=node("OTHER-MODEL",NodeKind.MODEL,group)
        val parameter=numbers.create(CreateNumericParameterRequest("LENGTH","Длина")).id
        numbers.assignToNode(group,parameter)
        mvc.perform(put("/api/v1/nodes/$model/numeric/parameters/$parameter/exclusion"))
            .andExpect(status().isNoContent)
        assertTrue(numbers.getEffectiveParameters(item).isEmpty())
        assertEquals(parameter,numbers.getEffectiveParameters(sibling).single().parameterId)
        assertTrue(numbers.getOwnParameters(model).single().excluded)
        mvc.perform(post("/api/v1/nodes/$model/numeric/parameters/$parameter")).andExpect(status().isCreated)
        assertEquals(parameter,numbers.getEffectiveParameters(item).single().parameterId)
        mvc.perform(put("/api/v1/nodes/$model/enumeration-bindings/$enumId").contentType(MediaType.APPLICATION_JSON).content("""{"excluded":true}"""))
            .andExpect(status().isNoContent)
        assertTrue(catalog.effectiveEnumerations(item).isEmpty())
        assertFalse(catalog.effectiveEnumerations(sibling).isEmpty())
        assertEquals(false,jdbc.queryForObject("SELECT catalog_enum_applicable(?,?)",Boolean::class.java,item,enumId))
        catalog.removeBinding(model,enumId)
        assertEquals(3,catalog.effectiveEnumerations(item).single().values.size)
        numbers.excludeFromNode(model,parameter)
        assertEquals(false,jdbc.queryForObject("SELECT catalog_numeric_applicable(?,?)",Boolean::class.java,item,parameter))
        numbers.assignToNode(item,parameter)
        assertEquals(parameter,numbers.getEffectiveParameters(item).single().parameterId)
    }
    @Test fun `exclusion never silently deletes saved product facts`() {
        attributes.selectValue(item,SelectEnumerationValueRequest(enumId,black))
        mvc.perform(put("/api/v1/nodes/$model/enumeration-bindings/$enumId").contentType(MediaType.APPLICATION_JSON).content("""{"excluded":true}"""))
            .andExpect(status().isConflict)
        assertEquals(black,attributes.getNodeAttributes(item).single().selectedValue.id)
        val parameter=numbers.create(CreateNumericParameterRequest("LENGTH","Длина")).id
        numbers.assignToNode(group,parameter)
        numbers.setValue(item,SetNumericValueRequest(parameter,BigDecimal.TEN))
        mvc.perform(put("/api/v1/nodes/$model/numeric/parameters/$parameter/exclusion"))
            .andExpect(status().isConflict)
        assertEquals(0,numbers.getValues(item).single().value.compareTo(BigDecimal.TEN))
        assertEquals(parameter,numbers.getEffectiveParameters(item).single().parameterId)
    }
    @Test fun `numeric bounds can be cleared and aggregates include selected product but exclude groups`() {
        val parameter=numbers.create(CreateNumericParameterRequest("LENGTH","Длина",minValue=BigDecimal.ZERO,maxValue=BigDecimal.TEN)).id
        numbers.assignToNode(group,parameter)
        numbers.setValue(group,SetNumericValueRequest(parameter,BigDecimal.TEN))
        numbers.setValue(model,SetNumericValueRequest(parameter,BigDecimal("2")))
        numbers.setValue(item,SetNumericValueRequest(parameter,BigDecimal("4")))
        mvc.perform(patch("/api/v1/numeric-parameters/$parameter").contentType(MediaType.APPLICATION_JSON).content("""{"clearMinValue":true,"clearMaxValue":true}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.minValue").isEmpty).andExpect(jsonPath("$.maxValue").isEmpty)
        mvc.perform(get("/api/v1/nodes/$group/numeric/aggregates/$parameter"))
            .andExpect(jsonPath("$.count").value(2)).andExpect(jsonPath("$.sumValue").value(6))
            .andExpect(jsonPath("$.avgValue").value(3))
        assertEquals(listOf(item),numbers.filterNodes(item,parameter,null,null).map { it.classifierNodeId })
        attributes.selectValue(item,SelectEnumerationValueRequest(enumId,black))
        assertEquals(1L,attributes.getEnumerationAggregates(item,enumId).totalCount)
        assertEquals(listOf(item),attributes.filterByEnumerationValue(item,enumId,black).map { it.classifierNodeId })
    }
    @Test fun `scope search offers parameters declared only in subclasses`() {
        val root=node("ROOT",NodeKind.GROUP)
        nodes.move(group,root)
        val parameter=numbers.create(CreateNumericParameterRequest("SUBCLASS-ONLY","Параметр подкласса")).id
        numbers.assignToNode(model,parameter)
        assertTrue(numbers.getEffectiveParameters(root).isEmpty())
        mvc.perform(get("/api/v1/nodes/$root/parameter-options"))
            .andExpect(status().isOk).andExpect(jsonPath("$.numericParameters[0].id").value(parameter))
            .andExpect(jsonPath("$.enumerations[0].id").value(enumId))
        numbers.excludeFromNode(model,parameter)
        mvc.perform(get("/api/v1/nodes/$root/parameter-options"))
            .andExpect(jsonPath("$.numericParameters.length()").value(0))
    }

}
