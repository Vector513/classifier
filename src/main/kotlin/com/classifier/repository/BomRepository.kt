package com.classifier.repository

import com.classifier.dto.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet

/** SQL-функции общие для REST API и самостоятельного запуска из SQL-клиента. */
@Repository
class BomRepository(private val jdbc: JdbcTemplate) {
    private val productSelect = """SELECT p.node_id,n.code,n.name,p.kind,p.unit_id,u.code unit_code
        FROM bom_product p JOIN classifier_node n ON n.id=p.node_id JOIN unit_of_measure u ON u.id=p.unit_id"""
    private val specSelect = """SELECT s.*,n.code product_code,n.name product_name FROM bom_specification s
        JOIN classifier_node n ON n.id=s.product_id"""
    private val lineJoin = """ JOIN classifier_node n ON n.id=e.component_id
        JOIN bom_product p ON p.node_id=n.id JOIN unit_of_measure u ON u.id=p.unit_id """
    private fun product(rs: ResultSet) = BomProductResponse(rs.getLong("node_id"),rs.getString("code"),
        rs.getString("name"),ProductKind.valueOf(rs.getString("kind")),rs.getLong("unit_id"),rs.getString("unit_code"))
    private fun specification(rs: ResultSet) = BomSpecificationResponse(rs.getLong("id"),rs.getLong("product_id"),
        rs.getString("product_code"),rs.getString("product_name"),rs.getInt("revision"),
        rs.getObject("base_id",Long::class.javaObjectType)?.toLong(),rs.getString("status"),rs.getString("description"),
        rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("released_at")?.toInstant())
    private fun line(rs: ResultSet) = BomLineResponse(rs.getLong("source_id"),rs.getInt("position"),
        rs.getLong("component_id"),rs.getString("code"),rs.getString("name"),
        rs.getObject("component_specification_id",Long::class.javaObjectType)?.toLong(),
        rs.getBigDecimal("quantity"),rs.getString("unit_code"))

    fun lock() { jdbc.execute("SELECT pg_advisory_xact_lock(21001)") }
    fun products() = jdbc.query("$productSelect ORDER BY n.code") { rs, _ -> product(rs) }
    fun product(id: Long) = jdbc.query("$productSelect WHERE p.node_id=?", { rs, _ -> product(rs) }, id).firstOrNull()
    fun register(r: RegisterBomProductRequest) {
        jdbc.update("INSERT INTO bom_product(node_id,kind,unit_id) VALUES (?,?,?)",r.nodeId,r.kind.name,r.unitId)
    }
    fun specifications() = jdbc.query("$specSelect ORDER BY s.id DESC") { rs, _ -> specification(rs) }
    fun specification(id: Long) = jdbc.query("$specSelect WHERE s.id=?", { rs, _ -> specification(rs) },id).firstOrNull()
    fun create(r: CreateBomSpecificationRequest): Long = jdbc.queryForObject("""
        INSERT INTO bom_specification(product_id,revision,base_id,description)
        SELECT ?,COALESCE(MAX(revision),0)+1,?,? FROM bom_specification WHERE product_id=? RETURNING id
        """,Long::class.java,r.productId,r.baseId,r.description,r.productId)!!
    fun release(id: Long) { jdbc.update("UPDATE bom_specification SET status='RELEASED' WHERE id=?",id) }
    fun delete(id: Long) { jdbc.update("DELETE FROM bom_specification WHERE id=?",id) }
    fun lines(id: Long) = jdbc.query("SELECT e.*,n.code,n.name,u.code unit_code FROM bom_effective(?) e $lineJoin ORDER BY e.position",
        { rs, _ -> line(rs) },id)
    fun putLine(id: Long, position: Int, r: BomLineRequest) {
        jdbc.update("""INSERT INTO bom_line(specification_id,position,component_id,component_specification_id,quantity)
            VALUES (?,?,?,?,?) ON CONFLICT(specification_id,position) DO UPDATE SET component_id=EXCLUDED.component_id,
            component_specification_id=EXCLUDED.component_specification_id,quantity=EXCLUDED.quantity,excluded=false""",
            id,position,r.componentId,r.componentSpecificationId,r.quantity)
    }
    fun exclude(id: Long, position: Int) {
        jdbc.update("""INSERT INTO bom_line(specification_id,position,excluded) VALUES (?,?,true)
            ON CONFLICT(specification_id,position) DO UPDATE SET excluded=true,component_id=NULL,
            component_specification_id=NULL,quantity=NULL""",id,position)
    }
    fun reset(id: Long, position: Int) { jdbc.update("DELETE FROM bom_line WHERE specification_id=? AND position=?",id,position) }
    fun localPositions(id: Long): List<Int> = jdbc.query("SELECT position FROM bom_line WHERE specification_id=? ORDER BY position",
        { rs, _ -> rs.getInt(1) },id)
    fun explode(id: Long, quantity: BigDecimal) = jdbc.query("""SELECT e.*,n.code,n.name,u.code unit_code
        FROM bom_explode(?,?) e $lineJoin ORDER BY e.path""", { rs, _ ->
        val path = (rs.getArray("path").array as Array<*>).map { (it as Number).toInt() }
        BomExplosionResponse(rs.getInt("depth"),path,line(rs),rs.getBigDecimal("total_quantity"),rs.getBoolean("cycle"))
    },id,quantity)
    fun totals(id: Long, quantity: BigDecimal, classId: Long) = jdbc.query("""SELECT e.*,n.code,n.name,u.code unit_code
        FROM bom_totals(?,?,?) e $lineJoin ORDER BY n.code""", { rs, _ ->
        BomTotalResponse(rs.getLong("component_id"),rs.getString("code"),rs.getString("name"),
            rs.getString("unit_code"),rs.getBigDecimal("total_quantity"))
    },id,quantity,classId)
}
