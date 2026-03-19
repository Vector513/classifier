package com.classifier.repository

import com.classifier.entity.UnitOfMeasure
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UnitOfMeasureRepository : JpaRepository<UnitOfMeasure, Long> {

    fun existsByCode(code: String): Boolean
}
