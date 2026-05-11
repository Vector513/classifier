package com.classifier.repository

import com.classifier.entity.NodeAttributeValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface NodeAttributeValueRepository : JpaRepository<NodeAttributeValue, Long> {

    fun findByClassifierNodeIdOrderByEnumerationId(nodeId: Long): List<NodeAttributeValue>

    fun findByClassifierNodeIdAndEnumerationId(nodeId: Long, enumerationId: Long): NodeAttributeValue?

    fun existsByClassifierNodeIdAndEnumerationId(nodeId: Long, enumerationId: Long): Boolean

    fun deleteByClassifierNodeIdAndEnumerationId(nodeId: Long, enumerationId: Long)

    fun countBySelectedValueId(valueId: Long): Long

    /** Все выбранные значения для списка узлов — для пакетного поиска с параметрами. */
    @Query("SELECT nav FROM NodeAttributeValue nav WHERE nav.classifierNode.id IN :nodeIds")
    fun findByClassifierNodeIdIn(nodeIds: List<Long>): List<NodeAttributeValue>

    /** Узлы поддерева с конкретным выбранным значением перечисления — для фильтрации. */
    @Query("""
        SELECT nav FROM NodeAttributeValue nav
        WHERE nav.classifierNode.id IN :nodeIds
          AND nav.enumeration.id = :enumerationId
          AND nav.selectedValue.id = :valueId
    """)
    fun findByNodeIdsAndEnumerationIdAndValueId(
        nodeIds: List<Long>,
        enumerationId: Long,
        valueId: Long
    ): List<NodeAttributeValue>

    /** Все выбранные значения конкретного перечисления в поддереве — для агрегатов. */
    @Query("""
        SELECT nav FROM NodeAttributeValue nav
        WHERE nav.classifierNode.id IN :nodeIds
          AND nav.enumeration.id = :enumerationId
    """)
    fun findByNodeIdsAndEnumerationId(nodeIds: List<Long>, enumerationId: Long): List<NodeAttributeValue>
}
