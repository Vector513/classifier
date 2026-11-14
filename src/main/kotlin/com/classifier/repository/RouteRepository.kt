package com.classifier.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class RouteRepository(private val jdbc: JdbcTemplate) {
    fun rows(sql: String, vararg args: Any?) = jdbc.queryForList(sql, *args)
    fun execute(sql: String, vararg args: Any?) = jdbc.update(sql, *args)
    fun insert(sql: String, vararg args: Any?) = jdbc.queryForObject("$sql RETURNING id", Long::class.java, *args)
    fun exists(sql: String, vararg args: Any?) =
        jdbc.queryForObject("SELECT EXISTS ($sql)", Boolean::class.java, *args) == true

    fun catalog() = rows("""
        SELECT n.id,n.code,n.name,n.parent_id,n.node_kind,u.code AS unit,p.value AS price
        FROM classifier_node n LEFT JOIN unit_of_measure u ON u.id=n.unit_of_measure_id
        LEFT JOIN numeric_parameter np ON np.code='PURCHASE_PRICE'
        LEFT JOIN node_numeric_value p ON p.classifier_node_id=n.id AND p.numeric_parameter_id=np.id
        ORDER BY n.name,n.id
    """)

    fun routes() = rows("""
        SELECT r.*,n.name AS product_name,n.code AS product_code,
               (SELECT count(*) FROM route_operation o WHERE o.route_id=r.id) AS step_count
        FROM route r JOIN classifier_node n ON n.id=r.product_id ORDER BY n.name,r.id
    """)

    fun steps() = rows("""
        SELECT o.*,r.product_id,n.name AS product_name,t.name AS type_name
        FROM route_operation o JOIN route r ON r.id=o.route_id
        JOIN classifier_node n ON n.id=r.product_id JOIN operation_type t ON t.id=o.operation_type_id
        ORDER BY r.id,o.step_number
    """)
}
