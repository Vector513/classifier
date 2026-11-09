package com.classifier.repository

import com.classifier.dto.BomExplosionResponse
import com.classifier.dto.BomLineRequest
import com.classifier.dto.BomLineResponse
import com.classifier.dto.BomProductResponse
import com.classifier.dto.BomSpecificationResponse
import com.classifier.dto.BomTotalResponse
import com.classifier.dto.CreateBomSpecificationRequest
import com.classifier.dto.ProductKind
import com.classifier.dto.RegisterBomProductRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet

/** SQL-функции общие для REST API и самостоятельного запуска из SQL-клиента. */
@Repository
class BomRepository(private val jdbc: JdbcTemplate) {
    private val productQuery = """
        SELECT p.node_id, n.code, n.name, p.kind,
               n.unit_of_measure_id AS unit_id, u.code AS unit_code
        FROM bom_product p
        JOIN classifier_node n ON n.id = p.node_id
        JOIN unit_of_measure u ON u.id = n.unit_of_measure_id
    """.trimIndent()

    private val specificationQuery = """
        SELECT s.*, n.code AS product_code, n.name AS product_name
        FROM bom_specification s
        JOIN classifier_node n ON n.id = s.product_id
    """.trimIndent()

    private val componentJoins = """
        JOIN classifier_node n ON n.id = e.component_id
        JOIN bom_product p ON p.node_id = n.id
        JOIN unit_of_measure u ON u.id = n.unit_of_measure_id
    """.trimIndent()

    fun lock() {
        // Тот же ключ используют триггеры в bom.sql. Блокировка держится до конца транзакции.
        jdbc.execute("SELECT pg_advisory_xact_lock(21001)")
    }

    fun products(): List<BomProductResponse> =
        jdbc.query("$productQuery ORDER BY n.code") { rs, _ -> readProduct(rs) }

    fun product(id: Long): BomProductResponse? =
        jdbc.query(
            "$productQuery WHERE p.node_id = ?",
            { rs, _ -> readProduct(rs) },
            id
        ).firstOrNull()

    fun register(request: RegisterBomProductRequest) {
        jdbc.update(
            "UPDATE classifier_node SET unit_of_measure_id = ? WHERE id = ?",
            request.unitId, request.nodeId
        )
        jdbc.update(
            "INSERT INTO bom_product (node_id, kind) VALUES (?, ?)",
            request.nodeId, request.kind.name
        )
    }

    fun specifications(): List<BomSpecificationResponse> =
        jdbc.query("$specificationQuery ORDER BY s.id DESC") { rs, _ -> readSpecification(rs) }

    fun specification(id: Long): BomSpecificationResponse? =
        jdbc.query(
            "$specificationQuery WHERE s.id = ?",
            { rs, _ -> readSpecification(rs) },
            id
        ).firstOrNull()

    fun create(request: CreateBomSpecificationRequest): Long {
        val sql = """
            INSERT INTO bom_specification (product_id, revision, base_id, description)
            SELECT ?, COALESCE(MAX(revision), 0) + 1, ?, ?
            FROM bom_specification
            WHERE product_id = ?
            RETURNING id
        """.trimIndent()
        return checkNotNull(
            jdbc.queryForObject(
                sql, Long::class.java,
                request.productId, request.baseId, request.description, request.productId
            )
        )
    }

    fun release(id: Long) {
        jdbc.update("UPDATE bom_specification SET status = 'RELEASED' WHERE id = ?", id)
    }

    fun delete(id: Long) {
        jdbc.update("DELETE FROM bom_specification WHERE id = ?", id)
    }

    fun lines(id: Long): List<BomLineResponse> {
        val sql = """
            SELECT e.*, n.code, n.name, u.code AS unit_code
            FROM bom_effective(?) e
            $componentJoins
            ORDER BY e.position
        """.trimIndent()
        return jdbc.query(sql, { rs, _ -> readLine(rs) }, id)
    }

    fun putLine(id: Long, position: Int, request: BomLineRequest) {
        val sql = """
            INSERT INTO bom_line (specification_id, position, component_id, component_specification_id, quantity)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (specification_id, position) DO UPDATE
            SET component_id = EXCLUDED.component_id,
                component_specification_id = EXCLUDED.component_specification_id,
                quantity = EXCLUDED.quantity,
                excluded = false
        """.trimIndent()
        jdbc.update(sql, id, position, request.componentId, request.componentSpecificationId, request.quantity)
    }

    fun exclude(id: Long, position: Int) {
        // Запись с признаком excluded скрывает позицию в действующем составе.
        val sql = """
            INSERT INTO bom_line (specification_id, position, excluded)
            VALUES (?, ?, true)
            ON CONFLICT (specification_id, position) DO UPDATE
            SET excluded = true,
                component_id = NULL,
                component_specification_id = NULL,
                quantity = NULL
        """.trimIndent()
        jdbc.update(sql, id, position)
    }

    fun reset(id: Long, position: Int) {
        jdbc.update("DELETE FROM bom_line WHERE specification_id = ? AND position = ?", id, position)
    }

    fun localPositions(id: Long): List<Int> =
        jdbc.query(
            "SELECT position FROM bom_line WHERE specification_id = ? ORDER BY position",
            { rs, _ -> rs.getInt(1) },
            id
        )

    fun explode(id: Long, quantity: BigDecimal): List<BomExplosionResponse> {
        val sql = """
            SELECT e.*, n.code, n.name, u.code AS unit_code
            FROM bom_explode(?, ?) e
            $componentJoins
            ORDER BY e.path
        """.trimIndent()
        return jdbc.query(sql, { rs, _ -> readExplosion(rs) }, id, quantity)
    }

    fun totals(id: Long, quantity: BigDecimal, classId: Long): List<BomTotalResponse> {
        val sql = """
            SELECT e.*, n.code, n.name, u.code AS unit_code
            FROM bom_totals(?, ?, ?) e
            $componentJoins
            ORDER BY n.code
        """.trimIndent()
        return jdbc.query(sql, { rs, _ -> readTotal(rs) }, id, quantity, classId)
    }

    private fun readProduct(rs: ResultSet) = BomProductResponse(
        id = rs.getLong("node_id"),
        code = rs.getString("code"),
        name = rs.getString("name"),
        kind = ProductKind.valueOf(rs.getString("kind")),
        unitId = rs.getLong("unit_id"),
        unitCode = rs.getString("unit_code")
    )

    private fun readSpecification(rs: ResultSet) = BomSpecificationResponse(
        id = rs.getLong("id"),
        productId = rs.getLong("product_id"),
        productCode = rs.getString("product_code"),
        productName = rs.getString("product_name"),
        revision = rs.getInt("revision"),
        baseId = rs.getObject("base_id", Long::class.javaObjectType),
        status = rs.getString("status"),
        description = rs.getString("description"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        releasedAt = rs.getTimestamp("released_at")?.toInstant()
    )

    private fun readLine(rs: ResultSet) = BomLineResponse(
        sourceId = rs.getLong("source_id"),
        position = rs.getInt("position"),
        componentId = rs.getLong("component_id"),
        code = rs.getString("code"),
        name = rs.getString("name"),
        componentSpecificationId = rs.getObject("component_specification_id", Long::class.javaObjectType),
        quantity = rs.getBigDecimal("quantity"),
        unitCode = rs.getString("unit_code")
    )

    private fun readExplosion(rs: ResultSet): BomExplosionResponse {
        val path = (rs.getArray("path").array as Array<*>).map { (it as Number).toInt() }
        return BomExplosionResponse(
            depth = rs.getInt("depth"),
            path = path,
            line = readLine(rs),
            totalQuantity = rs.getBigDecimal("total_quantity"),
            cycle = rs.getBoolean("cycle")
        )
    }

    private fun readTotal(rs: ResultSet) = BomTotalResponse(
        componentId = rs.getLong("component_id"),
        code = rs.getString("code"),
        name = rs.getString("name"),
        unitCode = rs.getString("unit_code"),
        totalQuantity = rs.getBigDecimal("total_quantity")
    )
}
