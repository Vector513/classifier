package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Запрос на выбор значения перечисления для узла классификатора")
data class SelectEnumerationValueRequest(
    @Schema(description = "ID перечисления", example = "1")
    val enumerationId: Long,

    @Schema(description = "ID выбираемого значения перечисления", example = "3")
    val valueId: Long
)

@Schema(description = "Выбранное значение перечисления для узла классификатора")
data class NodeAttributeValueResponse(
    @Schema(description = "Идентификатор записи") val id: Long,
    @Schema(description = "ID узла классификатора") val classifierNodeId: Long,
    @Schema(description = "Название узла классификатора", example = "iPhone 16") val classifierNodeName: String,
    @Schema(description = "ID перечисления") val enumerationId: Long,
    @Schema(description = "Название перечисления", example = "Цвета смартфонов") val enumerationName: String,
    @Schema(description = "ID выбранного значения") val selectedValueId: Long,
    @Schema(description = "Код выбранного значения", example = "BLACK") val selectedValueCode: String,
    @Schema(description = "Название выбранного значения", example = "Чёрный") val selectedValueName: String,
    @Schema(description = "Дата создания") val createdAt: Instant,
    @Schema(description = "Дата последнего обновления") val updatedAt: Instant
)
