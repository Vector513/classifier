package com.classifier.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OrderRepository(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    fun rows(sql: String, vararg args: Any?): List<JsonNode> = jdbc.query(sql, { rs, _ -> mapper.readTree(rs.getString(1)) }, *args)
    fun json(sql: String, vararg args: Any?): JsonNode = rows(sql, *args).single()
    fun id(sql: String, vararg args: Any?): Long = jdbc.queryForObject(sql, Long::class.java, *args)
    fun execute(sql: String, vararg args: Any?): Int = jdbc.update(sql, *args)
    fun encode(value: Any): String = mapper.writeValueAsString(value)
    fun lock() { jdbc.execute("SELECT pg_advisory_xact_lock(21001)") }
}
