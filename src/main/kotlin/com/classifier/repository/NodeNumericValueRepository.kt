package com.classifier.repository

import com.classifier.entity.NodeNumericValue
import com.classifier.dto.NumericAggregatesProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NodeNumericValueRepository : JpaRepository<NodeNumericValue, Long> {

    fun findByClassifierNodeIdOrderByNumericParameterId(nodeId: Long): List<NodeNumericValue>

    fun findByClassifierNodeIdAndNumericParameterId(nodeId: Long, paramId: Long): NodeNumericValue?

    fun existsByClassifierNodeIdAndNumericParameterId(nodeId: Long, paramId: Long): Boolean

    fun deleteByClassifierNodeIdAndNumericParameterId(nodeId: Long, paramId: Long)

    fun countByNumericParameterId(paramId: Long): Long

    /** Значения параметра по списку узлов (для фильтрации и агрегатов). */
    @Query("SELECT nnv FROM NodeNumericValue nnv WHERE nnv.classifierNode.id IN :nodeIds AND nnv.numericParameter.id = :paramId")
    fun findByNodeIdsAndParameterId(nodeIds: List<Long>, paramId: Long): List<NodeNumericValue>

    /** Все числовые значения для списка узлов — для пакетного поиска с параметрами. */
    @Query("SELECT nnv FROM NodeNumericValue nnv WHERE nnv.classifierNode.id IN :nodeIds")
    fun findByClassifierNodeIdIn(nodeIds: List<Long>): List<NodeNumericValue>

    /**
     * Агрегаты числового параметра по всему поддереву (рекурсивный CTE).
     * Возвращает min, max, avg, count по всем значениям в поддереве узла rootNodeId.
     */
    @Query(value = """
        WITH RECURSIVE descendants AS (
            SELECT id FROM classifier_node WHERE id = :rootNodeId
            UNION ALL
            SELECT cn.id FROM classifier_node cn
            JOIN descendants d ON cn.parent_id = d.id
        )
        SELECT
            MIN(nnv.value)  AS minVal,
            MAX(nnv.value)  AS maxVal,
            AVG(nnv.value)  AS avgVal,
            COUNT(*)        AS cnt
        FROM node_numeric_value nnv
        WHERE nnv.classifier_node_id IN (SELECT id FROM descendants)
          AND nnv.numeric_parameter_id = :paramId
    """, nativeQuery = true)
    fun getAggregates(rootNodeId: Long, paramId: Long): NumericAggregatesProjection?
}
