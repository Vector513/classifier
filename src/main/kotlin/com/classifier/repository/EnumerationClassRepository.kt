package com.classifier.repository

import com.classifier.entity.EnumerationClass
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EnumerationClassRepository : JpaRepository<EnumerationClass, Long> {
    fun existsByCode(code: String): Boolean
    fun findByCode(code: String): EnumerationClass?
    fun findAllByOrderByName(): List<EnumerationClass>
}
