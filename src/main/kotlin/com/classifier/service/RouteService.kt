package com.classifier.service

import com.classifier.dto.*
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.InvalidSelectionException
import com.classifier.repository.RouteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class RouteService(private val db: RouteRepository) {
    enum class Lookup(val table: String) { TYPES("operation_type"), ASSETS("asset"), RATES("labor_rate") }
    enum class Line(val table: String) { INPUT("operation_input"), ASSET("operation_asset"), LABOR("operation_labor") }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun overview() = mapOf(
        "routes" to db.routes(), "catalog" to db.catalog(), "steps" to db.steps(),
        "types" to lookup(Lookup.TYPES), "assets" to lookup(Lookup.ASSETS), "rates" to lookup(Lookup.RATES)
    )

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun detail(id: Long): Map<String, Any> {
        val route = db.routes().find { it["id"] == id } ?: missing()
        return mapOf("route" to route, "steps" to db.steps().filter { it["route_id"] == id },
            "inputs" to lines(Line.INPUT,id), "assets" to lines(Line.ASSET,id), "labor" to lines(Line.LABOR,id))
    }

    @Transactional
    fun saveRoute(id: Long?, request: ManufacturingRouteRequest): Long {
        lock()
        val code = required(request.code,100,"Введите код маршрута")
        requireResource(request.productId)
        if (id == null) return db.insert("INSERT INTO route(code,product_id) VALUES (?,?)", code,request.productId)
        val existing = db.rows("SELECT product_id FROM route WHERE id=?",id).firstOrNull() ?: missing()
        checkRule(existing["product_id"] == request.productId,"Изделие маршрута менять нельзя")
        changed(db.execute("UPDATE route SET code=? WHERE id=?",code,id))
        return id
    }

    @Transactional
    fun deleteRoute(id: Long) {
        lock()
        changed(db.execute("DELETE FROM route WHERE id=?",id))
    }

    @Transactional
    fun saveStep(routeId: Long, id: Long?, request: RouteStepRequest): Long {
        lock()
        exists("route",routeId)
        val number = whole(request.stepNumber,"Номер шага")
        val name = required(request.name,255,"Введите название шага")
        if (id == null) return db.insert(
            "INSERT INTO route_operation(route_id,step_number,name,operation_type_id) VALUES (?,?,?,?)",
            routeId,number,name,request.operationTypeId)
        changed(db.execute("UPDATE route_operation SET step_number=?,name=?,operation_type_id=? WHERE id=? AND route_id=?",
            number,name,request.operationTypeId,id,routeId))
        return id
    }

    @Transactional
    fun deleteStep(routeId: Long, id: Long) {
        lock()
        changed(db.execute("DELETE FROM route_operation WHERE id=? AND route_id=?",id,routeId))
    }

    @Transactional
    fun saveInput(step: Long, id: Long?, request: RouteInputRequest): Long {
        lock()
        exists("route_operation",step)
        requireResource(request.resourceId)
        decimal(request.resourceQuantity,"Расход",positive=true)
        val output = whole(request.outputQuantity,"Выпуск")
        request.sourceOperationId?.let { validateSource(step,it,request.resourceId) }
        return saveLine(Line.INPUT,step,id,
            listOf("resource_id","resource_quantity","output_quantity","source_operation_id"),
            listOf(request.resourceId,request.resourceQuantity,output,request.sourceOperationId))
    }

    @Transactional
    fun saveAsset(step: Long, id: Long?, request: RouteAssetRequest): Long {
        lock()
        return saveLine(Line.ASSET,step,id,listOf("asset_id","quantity"),
            listOf(request.assetId,whole(request.quantity,"Количество оснащения")))
    }

    @Transactional
    fun saveLabor(step: Long, id: Long?, request: RouteLaborRequest): Long {
        lock()
        decimal(request.setupMinutes,"Время подготовки")
        decimal(request.minutesPerUnit,"Время на изделие")
        return saveLine(Line.LABOR,step,id,listOf("labor_rate_id","setup_minutes","minutes_per_unit"),
            listOf(request.laborRateId,request.setupMinutes,request.minutesPerUnit))
    }

    @Transactional
    fun deleteLine(line: Line, step: Long, id: Long) {
        lock()
        changed(db.execute("DELETE FROM ${line.table} WHERE id=? AND operation_id=?",id,step))
    }

    @Transactional
    fun saveLookup(lookup: Lookup, id: Long?, request: RouteLookupRequest): Long {
        lock()
        val name = required(request.name,255,"Введите название")
        val (columns, values) = when (lookup) {
            Lookup.RATES -> {
                val rate = request.hourlyRate ?: throw InvalidSelectionException("Введите ставку за час")
                decimal(rate,"Ставка")
                listOf("profession","hourly_rate") to listOf(name,rate)
            }
            Lookup.TYPES -> listOf("code","name") to listOf(required(request.code,100,"Введите код"),name)
            Lookup.ASSETS -> {
                checkRule(request.kind in setOf("TOOL","EQUIPMENT"),"Укажите вид оснащения")
                listOf("code","name","kind") to listOf(required(request.code,100,"Введите код"),name,request.kind)
            }
        }
        if (id == null) return insert(lookup.table,columns,values)
        update(lookup.table,id,columns,values)
        return id
    }

    @Transactional
    fun deleteLookup(lookup: Lookup, id: Long) {
        lock()
        changed(db.execute("DELETE FROM ${lookup.table} WHERE id=?",id))
    }

    @Transactional
    fun savePrice(node: Long, price: BigDecimal) {
        lock()
        requireResource(node)
        decimal(price,"Закупочная цена")
        val id = db.rows("SELECT id FROM numeric_parameter WHERE code='PURCHASE_PRICE'").single()["id"]
        if (!db.exists("SELECT 1 WHERE catalog_numeric_applicable(?,?)",node,id)) {
            db.execute("""
                INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,created_at)
                VALUES (?,?,now()) ON CONFLICT (classifier_node_id,numeric_parameter_id) DO UPDATE SET excluded=false
            """,node,id)
        }
        db.execute("""
            INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at)
            VALUES (?,?,?,now(),now()) ON CONFLICT (classifier_node_id,numeric_parameter_id)
            DO UPDATE SET value=excluded.value,updated_at=now()
        """,node,id,price)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun calculate(id: Long, quantity: Int, group: Long?, profession: Long?): Map<String, Any?> {
        exists("route",id)
        checkRule(quantity > 0,"Размер партии должен быть положительным целым числом")
        if (group != null) checkRule(db.exists("SELECT 1 FROM classifier_node WHERE id=? AND node_kind='GROUP'",group),
            "Выберите группу материалов")
        if (profession != null) exists("labor_rate",profession)
        // Вложенный компонент должен быть готов до шага, который его использует.
        val operations = db.rows("""
            SELECT o.id,o.step_number,o.name,t.name AS type_name,n.name AS product_name,s.depth,s.batch_quantity,
                   (SELECT string_agg(a.name || ' × ' || oa.quantity, ', ' ORDER BY a.name)
                    FROM operation_asset oa JOIN asset a ON a.id=oa.asset_id WHERE oa.operation_id=o.id) AS equipment
            FROM route_expand(?,?::numeric) s JOIN route_operation o ON o.id=s.operation_id
            JOIN operation_type t ON t.id=o.operation_type_id JOIN route r ON r.id=o.route_id
            JOIN classifier_node n ON n.id=r.product_id ORDER BY s.path || 9223372036854775807::bigint
        """,id,quantity)
        val materials = db.rows("""
            SELECT n.id,n.name,n.code,u.code AS unit,m.quantity,p.value AS unit_price,
                   round(m.quantity*p.value,2) AS amount,
                   (CAST(? AS bigint) IS NULL OR EXISTS (SELECT 1 FROM catalog_ancestors(n.id) a WHERE a.id=?)) AS matches_filter
            FROM route_material_needs(?,?::numeric) m JOIN classifier_node n ON n.id=m.resource_id
            LEFT JOIN unit_of_measure u ON u.id=n.unit_of_measure_id
            LEFT JOIN numeric_parameter np ON np.code='PURCHASE_PRICE'
            LEFT JOIN node_numeric_value p ON p.classifier_node_id=n.id AND p.numeric_parameter_id=np.id
            ORDER BY n.name,n.id
        """,group,group,id,quantity)
        val labor = db.rows("""
            SELECT r.id,r.profession,m.minutes,r.hourly_rate,round(m.minutes*r.hourly_rate/60,2) AS amount
            FROM route_labor_needs(?,?::numeric) m JOIN labor_rate r ON r.id=m.rate_id ORDER BY r.profession
        """,id,quantity)
        val missing = materials.filter { it["amount"] == null }.map { it["name"] }
        val materialTotal = sum(materials)
        val laborTotal = sum(labor)
        return mapOf("operations" to operations, "materials" to materials.filter { it["matches_filter"] == true },
            "labor" to labor.filter { profession == null || it["id"] == profession },
            "materialTotal" to materialTotal, "laborTotal" to laborTotal,
            "total" to if (missing.isEmpty()) materialTotal+laborTotal else null, "missingPrices" to missing)
    }

    private fun validateSource(step: Long, source: Long, resource: Long) {
        val target = db.rows("SELECT route_id FROM route_operation WHERE id=?",step).single()["route_id"]
        val child = db.rows("""
            SELECT o.route_id,r.product_id FROM route_operation o JOIN route r ON r.id=o.route_id WHERE o.id=?
        """,source).firstOrNull() ?: missing()
        checkRule(child["product_id"] == resource,"Шаг-источник должен изготавливать выбранный ресурс")
        checkRule(!db.exists("""
            WITH RECURSIVE dependencies(route_id) AS (
                SELECT CAST(? AS bigint)
                UNION SELECT src.route_id FROM dependencies d
                JOIN route_operation o ON o.route_id=d.route_id JOIN operation_input i ON i.operation_id=o.id
                JOIN route_operation src ON src.id=i.source_operation_id
            ) SELECT 1 FROM dependencies WHERE route_id=?
        """,child["route_id"],target),"Связь создаёт цикл между маршрутами")
    }

    private fun lines(line: Line, route: Long) = db.rows(
        "SELECT l.* FROM ${line.table} l JOIN route_operation o ON o.id=l.operation_id WHERE o.route_id=? ORDER BY l.id",route)
    private fun lookup(lookup: Lookup) = db.rows("SELECT * FROM ${lookup.table} ORDER BY 2,id")
    private fun saveLine(line: Line, step: Long, id: Long?, columns: List<String>, values: List<Any?>): Long {
        exists("route_operation",step)
        if (id == null) return insert(line.table,listOf("operation_id")+columns,listOf(step)+values)
        val assignments = columns.joinToString(",") { "$it=?" }
        changed(db.execute("UPDATE ${line.table} SET $assignments WHERE id=? AND operation_id=?",
            *(values+id+step).toTypedArray()))
        return id
    }
    private fun insert(table: String, columns: List<String>, values: List<Any?>): Long =
        db.insert("INSERT INTO $table(${columns.joinToString(",")}) VALUES (${columns.joinToString(",") { "?" }})",*values.toTypedArray())
    private fun update(table: String, id: Long, columns: List<String>, values: List<Any?>) =
        changed(db.execute("UPDATE $table SET ${columns.joinToString(",") { "$it=?" }} WHERE id=?",*(values+id).toTypedArray()))
    private fun sum(rows: List<Map<String, Any>>) = rows.sumOf { it["amount"] as? BigDecimal ?: BigDecimal.ZERO }
    private fun lock() { db.rows("SELECT pg_advisory_xact_lock(21001)") }
    private fun exists(table: String,id: Long) { if (!db.exists("SELECT 1 FROM $table WHERE id=?",id)) missing() }
    private fun changed(count: Int) { if (count == 0) missing() }
    private fun missing(): Nothing = throw EntityNotFoundException("Запись не найдена")
    private fun requireResource(id: Long) = checkRule(
        db.exists("SELECT 1 FROM classifier_node WHERE id=? AND node_kind<>'GROUP' AND unit_of_measure_id IS NOT NULL",id),
        "Выберите модель или изделие с единицей измерения")
    private fun required(value: String?, max: Int, message: String): String =
        value?.trim()?.takeIf { it.isNotEmpty() && it.length<=max } ?: throw InvalidSelectionException(message)
    private fun whole(value: BigDecimal, name: String): Int {
        checkRule(value.signum()>0 && value<=BigDecimal(Int.MAX_VALUE) && value.stripTrailingZeros().scale()<=0,
            "$name: введите положительное целое число")
        return value.intValueExact()
    }
    private fun decimal(value: BigDecimal, name: String, positive: Boolean = false) {
        val normalized = value.stripTrailingZeros()
        checkRule(value.signum() >= if (positive) 1 else 0,"$name: недопустимое значение")
        checkRule(normalized.scale()<=6 && normalized.precision()-normalized.scale()<=13,
            "$name: не более 13 цифр до запятой и 6 после")
    }
    private fun checkRule(condition: Boolean,message: String) { if (!condition) throw InvalidSelectionException(message) }
}
