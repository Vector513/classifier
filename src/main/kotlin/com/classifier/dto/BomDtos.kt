package com.classifier.dto

import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant

enum class ProductKind { ASSEMBLY, PART, MATERIAL, PURCHASED }
data class RegisterBomProductRequest(
    @field:Positive val nodeId: Long,
    val kind: ProductKind,
    @field:Positive val unitId: Long
)
data class CreateBomSpecificationRequest(
    @field:Positive val productId: Long,
    @field:Positive val baseId: Long? = null,
    @field:NotBlank @field:Size(max = 1000) val description: String
)
data class BomLineRequest(
    @field:Positive val componentId: Long,
    @field:Positive val componentSpecificationId: Long? = null,
    @field:DecimalMin("0.000001") @field:Digits(integer = 13, fraction = 6) val quantity: BigDecimal
)
data class BomProductResponse(val id: Long, val code: String, val name: String,
    val kind: ProductKind, val unitId: Long, val unitCode: String)
data class BomSpecificationResponse(val id: Long, val productId: Long, val productCode: String,
    val productName: String, val revision: Int, val baseId: Long?, val status: String,
    val description: String, val createdAt: Instant, val releasedAt: Instant?)
data class BomLineResponse(val sourceId: Long, val position: Int, val componentId: Long,
    val code: String, val name: String, val componentSpecificationId: Long?,
    val quantity: BigDecimal, val unitCode: String)
data class BomExplosionResponse(val depth: Int, val path: List<Int>, val line: BomLineResponse,
    val totalQuantity: BigDecimal, val cycle: Boolean)
data class BomTotalResponse(val componentId: Long, val code: String, val name: String,
    val unitCode: String, val totalQuantity: BigDecimal)
