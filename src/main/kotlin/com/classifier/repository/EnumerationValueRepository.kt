package com.classifier.repository

import com.classifier.entity.EnumerationValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EnumerationValueRepository : JpaRepository<EnumerationValue, Long> {
    fun findByEnumerationIdOrderBySortOrder(enumerationId: Long): List<EnumerationValue>
    fun countByEnumerationId(enumerationId: Long): Long
    fun existsByEnumerationIdAndCode(enumerationId: Long, code: String): Boolean
}
