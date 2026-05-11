package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

// ─── Наследование перечислений ────────────────────────────────────────────────

@Schema(description = "Перечисление, действующее для узла (собственное или унаследованное от предка)")
data class EffectiveEnumerationResponse(
    val enumerationId: Long,
    val enumerationCode: String,
    val enumerationName: String,
    val enumerationClassName: String,
    val values: List<EnumerationValueResponse>,
    @Schema(description = "ID узла, к которому привязано перечисление") val definedAtNodeId: Long,
    @Schema(description = "Название узла, к которому привязано перечисление") val definedAtNodeName: String,
    @Schema(description = "true — перечисление унаследовано от предка") val isInherited: Boolean
)

// ─── Поиск с параметрами ──────────────────────────────────────────────────────

@Schema(description = "Узел классификатора с полным набором значений параметров")
data class NodeWithParametersResponse(
    val id: Long,
    val code: String,
    val name: String,
    val parentId: Long?,
    val parentName: String?,
    @Schema(description = "Выбранные значения перечислимых параметров")
    val enumerationAttributes: List<NodeAttributeValueResponse>,
    @Schema(description = "Значения числовых параметров")
    val numericValues: List<NodeNumericValueResponse>
)

// ─── Агрегаты по перечислению ─────────────────────────────────────────────────

@Schema(description = "Количество узлов с данным значением перечисления")
data class EnumerationValueCountResponse(
    val valueId: Long,
    val valueCode: String,
    val valueName: String,
    val count: Long
)

@Schema(description = "Распределение узлов поддерева по значениям перечисления")
data class EnumerationAggregatesResponse(
    val enumerationId: Long,
    val enumerationCode: String,
    val enumerationName: String,
    val rootNodeId: Long,
    @Schema(description = "Всего узлов с заполненным параметром") val totalCount: Long,
    @Schema(description = "Количество по каждому значению") val distribution: List<EnumerationValueCountResponse>
)
