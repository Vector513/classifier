package com.classifier.service

import com.classifier.dto.*
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.InvalidSelectionException
import com.classifier.repository.OrderRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.interceptor.TransactionAspectSupport
import java.math.BigDecimal

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class OrderService(private val db: OrderRepository) {
    fun overview(): Map<String, Any> = mapOf(
        "customers" to db.rows("SELECT to_jsonb(c) FROM customer c ORDER BY name,id"),
        "orders" to db.rows("""SELECT to_jsonb(x) FROM (SELECT o.*,c.name AS customer_name,(SELECT count(*) FROM order_line l WHERE l.order_id=o.id) AS line_count FROM customer_order o JOIN customer c ON c.id=o.customer_id ORDER BY o.id DESC) x"""),
        "catalog" to db.rows("""SELECT to_jsonb(x) FROM (SELECT n.id,n.code,n.name,n.node_kind,u.code AS unit,EXISTS(SELECT 1 FROM product_template t WHERE t.product_id=n.id) AS has_template FROM classifier_node n LEFT JOIN unit_of_measure u ON u.id=n.unit_of_measure_id ORDER BY n.name,n.id) x"""),
        "templates" to db.rows("SELECT to_jsonb(t) FROM product_template t ORDER BY name,id"),
        "variants" to db.rows("SELECT to_jsonb(v) FROM product_variant v ORDER BY name,id"),
        "functions" to functions(),
        "units" to db.rows("SELECT to_jsonb(u) FROM unit_of_measure u ORDER BY code"),
        "enumerations" to db.rows("SELECT to_jsonb(e) FROM enumeration e ORDER BY name,id"),
        "enumerationValues" to db.rows("SELECT to_jsonb(e) FROM enumeration_value e ORDER BY enumeration_id,sort_order,id")
    )
    fun functions() = db.rows("""SELECT to_jsonb(f)||jsonb_build_object('parameters',coalesce((SELECT jsonb_agg(to_jsonb(p) ORDER BY argument_number) FROM formal_parameter p WHERE p.function_id=f.id),'[]'), 'expression',orders_expression_json(f.root_expression_id)) FROM constraint_function f ORDER BY f.name,f.id""")
    fun template(id: Long): Map<String, Any> = mapOf(
        "template" to record("product_template",id),
        "parameters" to parameters(id),
        "rules" to db.rows("SELECT to_jsonb(r)||jsonb_build_object('expression',orders_expression_json(r.root_expression_id)) FROM configuration_rule r WHERE template_id=? ORDER BY id",id),
        "variants" to db.rows("SELECT to_jsonb(v)||jsonb_build_object('values',orders_variant_values(v.id)) FROM product_variant v WHERE template_id=? ORDER BY id",id)
    )
    private fun parameters(id: Long) = db.rows("""SELECT to_jsonb(p)||jsonb_build_object('unit',u.code,'options',coalesce((SELECT jsonb_agg(to_jsonb(e) ORDER BY sort_order,id) FROM enumeration_value e WHERE e.enumeration_id=p.enumeration_id),'[]')) FROM configuration_parameter p LEFT JOIN unit_of_measure u ON u.id=p.unit_of_measure_id WHERE p.template_id=? ORDER BY p.id""",id)
    fun variant(id: Long) = record("product_variant",id).let { mapOf("variant" to it,"values" to db.json("SELECT orders_variant_values(?)",id)) }
    fun order(id: Long): Map<String, Any> = mapOf(
        "order" to (db.rows("SELECT to_jsonb(o)||jsonb_build_object('customer',to_jsonb(c)) FROM customer_order o JOIN customer c ON c.id=o.customer_id WHERE o.id=?",id).firstOrNull() ?: missing()),
        "lines" to db.rows("SELECT to_jsonb(l) FROM get_order_contents(?) l",id)
    )
    @Transactional
    fun customer(id: Long?, r: CustomerRequest): Long {
        db.lock();val values=arrayOf<Any?>(required(r.name,255),optional(r.contactPerson,255),optional(r.phone,40),optional(r.email,254))
        if (id==null) return db.id("INSERT INTO customer(name,contact_person,phone,email) VALUES(?,?,?,?) RETURNING id",*values)
        changed(db.execute("UPDATE customer SET name=?,contact_person=?,phone=?,email=? WHERE id=?",*(values+id)));return id
    }
    @Transactional
    fun saveOrder(id: Long?,r: CustomerOrderRequest): Long {
        db.lock();val values=arrayOf<Any?>(r.customerId,required(r.orderNumber,100),r.orderDate,r.requestedDate)
        if (id==null) return db.id("SELECT create_order(?,?,?,?)",*values)
        changed(db.execute("UPDATE customer_order SET customer_id=?,order_number=?,order_date=?,requested_date=? WHERE id=?",*(values+id)));return id
    }
    @Transactional
    fun saveTemplate(id: Long?,r: ProductTemplateRequest): Long {
        db.lock();val values=arrayOf<Any?>(r.productId,required(r.code,100),required(r.name,255))
        if(id==null)return db.id("INSERT INTO product_template(product_id,code,name) VALUES(?,?,?) RETURNING id",*values)
        check(record("product_template",id)["product_id"].asLong()==r.productId,"Исходную запись существующего шаблона менять нельзя")
        changed(db.execute("UPDATE product_template SET product_id=?,code=?,name=? WHERE id=?",*(values+id)));return id
    }
    @Transactional
    fun saveParameter(template: Long,id: Long?,r: ConfigurationParameterRequest): Long {
        db.lock();record("product_template",template)
        val values=arrayOf<Any?>(required(r.code,100),required(r.name,255),r.dataType,r.unitOfMeasureId,r.enumerationId,r.required,r.minValue,r.maxValue)
        if(id==null)return db.id("INSERT INTO configuration_parameter(code,name,data_type,unit_of_measure_id,enumeration_id,required,min_value,max_value,template_id) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id",*(values+template))
        changed(db.execute("UPDATE configuration_parameter SET code=?,name=?,data_type=?,unit_of_measure_id=?,enumeration_id=?,required=?,min_value=?,max_value=? WHERE id=? AND template_id=?",*(values+id+template)));return id
    }
    @Transactional
    fun saveRule(template: Long,id: Long?,r: ConfigurationRuleRequest): Long {
        db.lock();record("product_template",template)
        return db.id("SELECT orders_save_rule(?,?,?,?,?,?::jsonb)",id,template,required(r.code,100),required(r.name,255),required(r.errorMessage,10000),db.encode(r.expression))
    }
    @Transactional
    fun saveFunction(id: Long?,r: ConstraintFunctionRequest): Long {
        db.lock();if(id!=null)record("constraint_function",id)
        return db.id("SELECT orders_save_function(?,?,?,?,?::jsonb,?::jsonb)",id,required(r.code,100),required(r.name,255),r.resultType,db.encode(r.parameters),db.encode(r.expression))
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun tryFunction(id: Long?, request: FunctionTrialRequest): JsonNode {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        val functionId = saveFunction(id, request.function)
        db.execute("SET CONSTRAINTS orders_validate IMMEDIATE")
        return evaluateFunction(functionId, FunctionEvaluationRequest(request.arguments))
    }
    fun validate(template: Long,r: ValidationRequest): Map<String, Any> {
        record("product_template",template);val errors=db.json("SELECT orders_validation_errors(?,?::jsonb)",template,db.encode(r.values))
        return mapOf("valid" to errors.isEmpty,"errors" to errors)
    }
    fun evaluateFunction(id: Long,r: FunctionEvaluationRequest): JsonNode {
        record("constraint_function",id);return db.json("SELECT eval_function(?,?::jsonb)",id,db.encode(r.arguments))
    }
    @Transactional
    fun createVariant(template: Long,r: VariantRequest): Long {
        db.lock();record("product_template",template)
        return db.id("SELECT create_variant(?,?,?,?::jsonb)",template,required(r.code,100),required(r.name,255),db.encode(r.values))
    }
    @Transactional
    fun updateVariant(id: Long,r: VariantRequest): Long {
        db.lock();record("product_variant",id)
        return db.id("SELECT update_variant(?,?,?,?::jsonb)",id,required(r.code,100),required(r.name,255),db.encode(r.values))
    }
    @Transactional
    fun addLine(order: Long,id: Long?,r: OrderLineRequest): Long {
        db.lock();record("customer_order",order)
        return db.id("SELECT add_to_order(?,?,?,?,?,?)",order,r.productId,r.variantId,whole(r.quantity),optional(r.note,10000),id)
    }
    @Transactional
    fun configuredLine(order: Long,r: ConfiguredOrderLineRequest): Map<String, Long> {
        db.lock();record("customer_order",order)
        val variant=createVariant(r.templateId,VariantRequest(r.code,r.name,r.values))
        val line=addLine(order,r.lineId,OrderLineRequest(r.quantity,variantId=variant,note=r.note))
        return mapOf("id" to line,"variantId" to variant)
    }
    @Transactional
    fun deleteLine(order: Long,id: Long) { db.lock();changed(db.execute("DELETE FROM order_line WHERE id=? AND order_id=?",id,order)) }
    @Transactional
    fun deleteParameter(template: Long,id: Long) { db.lock();changed(db.execute("DELETE FROM configuration_parameter WHERE id=? AND template_id=?",id,template)) }
    @Transactional
    fun deleteRule(template: Long,id: Long) {
        db.lock();val row=db.rows("SELECT to_jsonb(r) FROM configuration_rule r WHERE id=? AND template_id=?",id,template).firstOrNull() ?: missing()
        db.execute("DELETE FROM configuration_rule WHERE id=?",id);db.execute("DELETE FROM expression WHERE id=?",row["root_expression_id"].asLong())
    }
    @Transactional
    fun deleteFunction(id: Long) {
        db.lock();val row=record("constraint_function",id)
        val placeholder=db.id("INSERT INTO expression(kind,constant_value) VALUES('CONST','0') RETURNING id")
        db.execute("UPDATE constraint_function SET root_expression_id=? WHERE id=?",placeholder,id)
        db.execute("DELETE FROM expression WHERE id=?",row["root_expression_id"].asLong())
        db.execute("DELETE FROM formal_parameter WHERE function_id=?",id)
        db.execute("DELETE FROM constraint_function WHERE id=?",id);db.execute("DELETE FROM expression WHERE id=?",placeholder)
    }
    @Transactional
    fun deleteCustomer(id: Long) { db.lock();changed(db.execute("DELETE FROM customer WHERE id=?",id)) }
    @Transactional
    fun deleteOrder(id: Long) { db.lock();changed(db.execute("DELETE FROM customer_order WHERE id=?",id)) }
    @Transactional
    fun deleteVariant(id: Long) { db.lock();changed(db.execute("DELETE FROM product_variant WHERE id=?",id)) }
    @Transactional
    fun deleteTemplate(id: Long) {
        db.lock();record("product_template",id)
        check(db.rows("SELECT to_jsonb(v) FROM product_variant v WHERE template_id=? LIMIT 1",id).isEmpty(),"У шаблона есть варианты исполнения")
        db.rows("SELECT to_jsonb(r) FROM configuration_rule r WHERE template_id=?",id).forEach { deleteRule(id,it["id"].asLong()) }
        db.execute("DELETE FROM configuration_parameter WHERE template_id=?",id);db.execute("DELETE FROM product_template WHERE id=?",id)
    }
    fun document(id: Long): String {
        val data=order(id);val o=data["order"] as JsonNode;val c=o["customer"]
        @Suppress("UNCHECKED_CAST") val lines=data["lines"] as List<JsonNode>
        fun h(s: String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
        fun value(v: JsonNode)=if(v.isNull)"" else if(v.isTextual)v.asText() else v.toString()
        val rows=lines.joinToString("") { l->
            val params=l["parameters"].joinToString("<br>") { p->h(p["name"].asText())+": "+h(value(p["value"]))+" "+h(if(p["unit"].isNull)"" else p["unit"].asText()) }
            "<tr><td>${l["line_number"].asInt()}</td><td>${h(l["code"].asText())}</td><td>${h(l["name"].asText())}</td><td>$params</td><td>${l["quantity"].asInt()}</td><td>${h(value(l["note"]))}</td></tr>"
        }
        return """<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Заказ ${h(o["order_number"].asText())}</title><style>body{font:15px Arial,sans-serif;max-width:1100px;margin:32px auto;padding:16px;color:#172238}table{width:100%;border-collapse:collapse}td,th{border:1px solid #bbc5d2;padding:10px;text-align:left}h1{font-size:24px}@media print{button{display:none}body{margin:0;padding:0}@page{size:A4 landscape;margin:15mm}}</style><button onclick="window.print()">Печать заказа</button><h1>Заказ ${h(o["order_number"].asText())} от ${h(o["order_date"].asText())}</h1><p>Заказчик: ${h(c["name"].asText())}<br>Контакт: ${h(value(c["contact_person"]))} ${h(value(c["phone"]))} ${h(value(c["email"]))}<br>Желаемый срок: ${h(value(o["requested_date"]))}</p><table><thead><tr><th>№</th><th>Код</th><th>Изделие / исполнение</th><th>Параметры исполнения</th><th>Количество</th><th>Примечание</th></tr></thead><tbody>$rows</tbody></table></html>"""
    }
    private fun record(table: String,id: Long): JsonNode {
        // Имена таблиц задаются только константами методов сервиса.
        return db.rows("SELECT to_jsonb(t) FROM $table t WHERE id=?",id).firstOrNull() ?: missing()
    }
    private fun whole(v: BigDecimal): Int { check(v>BigDecimal.ZERO && v<=BigDecimal.valueOf(Int.MAX_VALUE.toLong()) && v.stripTrailingZeros().scale()<=0,"Количество должно быть положительным целым числом");return v.toInt() }
    private fun required(s: String,max: Int): String { val v=s.trim();check(v.isNotEmpty() && v.length<=max,"Введите непустой текст длиной до $max символов");return v }
    private fun optional(s: String?,max: Int): String? { val v=s?.trim()?.takeIf { it.isNotEmpty() };check(v==null||v.length<=max,"Слишком длинное значение");return v }
    private fun check(ok: Boolean,message: String) { if(!ok)throw InvalidSelectionException(message) }
    private fun changed(n: Int) { if(n==0)missing() }
    private fun missing(): Nothing=throw EntityNotFoundException("Запись заказов или конфигуратора не найдена")
}
