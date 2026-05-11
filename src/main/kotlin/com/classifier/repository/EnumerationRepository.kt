package com.classifier.repository

import com.classifier.entity.Enumeration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EnumerationRepository : JpaRepository<Enumeration, Long> {
    fun existsByCode(code: String): Boolean
    fun findByEnumerationClassIdOrderByName(classId: Long): List<Enumeration>
    fun findByClassifierNodeIdOrderByName(nodeId: Long): List<Enumeration>

    /** Все перечисления, привязанные к любому из указанных узлов (для вычисления наследования). */
    @Query("SELECT e FROM Enumeration e WHERE e.classifierNode.id IN :nodeIds")
    fun findByClassifierNodeIdIn(nodeIds: List<Long>): List<Enumeration>
}
