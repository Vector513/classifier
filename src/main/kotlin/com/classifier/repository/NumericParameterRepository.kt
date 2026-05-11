package com.classifier.repository

import com.classifier.entity.NumericParameter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NumericParameterRepository : JpaRepository<NumericParameter, Long> {

    fun existsByCode(code: String): Boolean

    fun findAllByOrderByName(): List<NumericParameter>

    @Query("""
        SELECT p FROM NumericParameter p
        WHERE LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY p.name
    """)
    fun searchByQuery(query: String): List<NumericParameter>
}
