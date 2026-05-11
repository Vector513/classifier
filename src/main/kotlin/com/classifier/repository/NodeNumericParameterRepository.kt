package com.classifier.repository

import com.classifier.entity.NodeNumericParameter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NodeNumericParameterRepository : JpaRepository<NodeNumericParameter, Long> {

    fun findByClassifierNodeIdOrderByNumericParameterId(nodeId: Long): List<NodeNumericParameter>

    fun findByClassifierNodeIdAndNumericParameterId(nodeId: Long, paramId: Long): NodeNumericParameter?

    fun existsByClassifierNodeIdAndNumericParameterId(nodeId: Long, paramId: Long): Boolean

    fun deleteByClassifierNodeIdAndNumericParameterId(nodeId: Long, paramId: Long)

    fun countByNumericParameterId(paramId: Long): Long

    /** Найти все назначения параметров для списка узлов (для вычисления наследования). */
    @Query("SELECT nnp FROM NodeNumericParameter nnp WHERE nnp.classifierNode.id IN :nodeIds")
    fun findByClassifierNodeIdIn(nodeIds: List<Long>): List<NodeNumericParameter>
}
