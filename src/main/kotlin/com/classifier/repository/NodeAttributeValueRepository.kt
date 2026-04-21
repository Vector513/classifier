package com.classifier.repository

import com.classifier.entity.NodeAttributeValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NodeAttributeValueRepository : JpaRepository<NodeAttributeValue, Long> {

    fun findByClassifierNodeIdOrderByEnumerationId(nodeId: Long): List<NodeAttributeValue>

    fun findByClassifierNodeIdAndEnumerationId(nodeId: Long, enumerationId: Long): NodeAttributeValue?

    fun existsByClassifierNodeIdAndEnumerationId(nodeId: Long, enumerationId: Long): Boolean

    fun deleteByClassifierNodeIdAndEnumerationId(nodeId: Long, enumerationId: Long)

    fun countBySelectedValueId(valueId: Long): Long
}
