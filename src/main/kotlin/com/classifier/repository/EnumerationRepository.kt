package com.classifier.repository

import com.classifier.entity.Enumeration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EnumerationRepository : JpaRepository<Enumeration, Long> {
    fun existsByCode(code: String): Boolean
    fun findByEnumerationClassIdOrderByName(classId: Long): List<Enumeration>
    @Query(value = "SELECT e.* FROM enumeration e JOIN node_enumeration b ON b.enumeration_id=e.id WHERE NOT b.excluded AND b.classifier_node_id=:nodeId ORDER BY e.name", nativeQuery = true)
    fun findByClassifierNodeIdOrderByName(nodeId: Long): List<Enumeration>

    /** Все перечисления, привязанные к любому из указанных узлов (для вычисления наследования). */
    @Query(value = "SELECT DISTINCT e.* FROM enumeration e JOIN node_enumeration b ON b.enumeration_id=e.id WHERE NOT b.excluded AND b.classifier_node_id IN (:nodeIds)", nativeQuery = true)
    fun findByClassifierNodeIdIn(nodeIds: List<Long>): List<Enumeration>
}
