package com.classifier.dto

import java.math.BigDecimal

data class ManufacturingRouteRequest(val code: String, val productId: Long)
data class RouteStepRequest(val stepNumber: BigDecimal, val name: String, val operationTypeId: Long)
data class RouteInputRequest(val resourceId: Long, val resourceQuantity: BigDecimal, val outputQuantity: BigDecimal,
                             val sourceOperationId: Long? = null)
data class RouteAssetRequest(val assetId: Long, val quantity: BigDecimal)
data class RouteLaborRequest(val laborRateId: Long, val setupMinutes: BigDecimal, val minutesPerUnit: BigDecimal)
data class RouteLookupRequest(val name: String, val code: String? = null, val kind: String? = null,
                              val hourlyRate: BigDecimal? = null)
data class PurchasePriceRequest(val price: BigDecimal)
