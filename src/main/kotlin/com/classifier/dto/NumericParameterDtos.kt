package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

// ─── Числовой параметр ────────────────────────────────────────────────────────

@Schema(description = "Запрос на создание числового параметра")
data class CreateNumericParameterRequest(
    @Schema(description = "Уникальный код параметра", example = "WEIGHT") val code: String,
    @Schema(description = "Название параметра", example = "Масса") val name: String,
    @Schema(description = "Описание параметра") val description: String? = null,
    @Schema(description = "Минимально допустимое значение (null — без ограничения)", example = "0.0") val minValue: BigDecimal? = null,
    @Schema(description = "Максимально допустимое значение (null — без ограничения)", example = "1000.0") val maxValue: BigDecimal? = null,
    @Schema(description = "ID единицы измерения") val unitOfMeasureId: Long? = null
)

@Schema(description = "Запрос на обновление числового параметра (все поля опциональны)")
data class UpdateNumericParameterRequest(
    val code: String? = null,
    val name: String? = null,
    val description: String? = null,
    val minValue: BigDecimal? = null,
    val maxValue: BigDecimal? = null,
    val unitOfMeasureId: Long? = null
)

@Schema(description = "Числовой параметр изделия")
data class NumericParameterResponse(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val minValue: BigDecimal?,
    val maxValue: BigDecimal?,
    val unitOfMeasureId: Long?,
    val unitOfMeasureName: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// ─── Значение числового параметра узла ───────────────────────────────────────

@Schema(description = "Запрос на установку числового значения параметра для узла")
data class SetNumericValueRequest(
    @Schema(description = "ID числового параметра", example = "1") val parameterId: Long,
    @Schema(description = "Значение параметра", example = "183.5") val value: BigDecimal
)

@Schema(description = "Числовое значение параметра для узла классификатора")
data class NodeNumericValueResponse(
    val id: Long,
    val classifierNodeId: Long,
    val classifierNodeName: String,
    val parameterId: Long,
    val parameterCode: String,
    val parameterName: String,
    val value: BigDecimal,
    val minValue: BigDecimal?,
    val maxValue: BigDecimal?,
    val unitOfMeasureName: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// ─── Наследование параметров ──────────────────────────────────────────────────

@Schema(description = "Числовой параметр, действующий для узла (собственный или унаследованный от предка)")
data class InheritedNumericParameterResponse(
    val parameterId: Long,
    val parameterCode: String,
    val parameterName: String,
    val description: String?,
    val minValue: BigDecimal?,
    val maxValue: BigDecimal?,
    val unitOfMeasureName: String?,
    @Schema(description = "ID узла, в котором параметр определён") val definedAtNodeId: Long,
    @Schema(description = "Название узла, в котором параметр определён") val definedAtNodeName: String,
    @Schema(description = "true — параметр унаследован от предка, false — определён на самом узле")
    val isInherited: Boolean
)

// ─── Агрегаты ─────────────────────────────────────────────────────────────────

@Schema(description = "Агрегированные значения числового параметра по поддереву")
data class NumericAggregatesResponse(
    val parameterId: Long,
    val parameterCode: String,
    val parameterName: String,
    val rootNodeId: Long,
    val count: Long,
    val minValue: BigDecimal?,
    val maxValue: BigDecimal?,
    val avgValue: BigDecimal?
)

/** Spring Data projection для нативного запроса агрегатов. */
interface NumericAggregatesProjection {
    fun getMinVal(): BigDecimal?
    fun getMaxVal(): BigDecimal?
    fun getAvgVal(): BigDecimal?
    fun getCnt(): Long
}
