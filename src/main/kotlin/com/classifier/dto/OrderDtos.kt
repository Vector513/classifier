package com.classifier.dto

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate

data class CustomerRequest(val name: String, val contactPerson: String? = null, val phone: String? = null, val email: String? = null)
data class CustomerOrderRequest(val customerId: Long, val orderNumber: String, val orderDate: LocalDate, val requestedDate: LocalDate? = null)
data class ProductTemplateRequest(val productId: Long, val code: String, val name: String)
data class ConfigurationParameterRequest(val code: String, val name: String, val dataType: String,
    val unitOfMeasureId: Long? = null, val enumerationId: Long? = null, val required: Boolean = true,
    val minValue: BigDecimal? = null, val maxValue: BigDecimal? = null)
data class ExpressionSpec(val kind: String, val operator: String? = null, val parameterCode: String? = null,
    val formalCode: String? = null, val functionId: Long? = null, val value: JsonNode? = null,
    val args: List<ExpressionSpec> = emptyList())
data class ConfigurationRuleRequest(val code: String, val name: String, val errorMessage: String, val expression: ExpressionSpec)
data class FormalParameterSpec(val code: String, val dataType: String)
data class ConstraintFunctionRequest(val code: String, val name: String, val resultType: String,
    val parameters: List<FormalParameterSpec>, val expression: ExpressionSpec)
data class VariantRequest(val code: String, val name: String, val values: JsonNode)
data class ValidationRequest(val values: JsonNode)
data class FunctionEvaluationRequest(val arguments: JsonNode)
data class OrderLineRequest(val quantity: BigDecimal, val productId: Long? = null, val variantId: Long? = null, val note: String? = null)
data class ConfiguredOrderLineRequest(val templateId: Long, val code: String, val name: String,
    val values: JsonNode, val quantity: BigDecimal, val lineId: Long? = null, val note: String? = null)
