package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Запрос на создание вершины классификатора")
data class CreateNodeRequest(
    @Schema(description = "Уникальный код вершины", example = "PHONES-APPLE")
    @field:NotBlank @field:Size(max = 100)
    val code: String,

    @Schema(description = "Название вершины", example = "Apple")
    @field:NotBlank @field:Size(max = 255)
    val name: String,

    @Schema(description = "ID родительской вершины (null = корневой узел)", example = "2")
    val parentId: Long? = null,

    @Schema(description = "ID единицы измерения", example = "1")
    val unitOfMeasureId: Long? = null
)

@Schema(description = "Запрос на обновление вершины (все поля опциональны)")
data class UpdateNodeRequest(
    @Schema(description = "Новый код вершины", example = "PHONES-APPLE")
    @field:Size(max = 100)
    val code: String? = null,

    @Schema(description = "Новое название вершины", example = "Apple Inc.")
    @field:Size(max = 255)
    val name: String? = null,

    @Schema(description = "ID новой единицы измерения", example = "1")
    val unitOfMeasureId: Long? = null
)

@Schema(description = "Запрос на перемещение вершины (смена родителя)")
data class MoveNodeRequest(
    @Schema(description = "ID нового родителя (null = сделать корневым)", example = "3")
    val newParentId: Long? = null
)

@Schema(description = "Запрос на изменение порядка вершины среди соседей")
data class ReorderRequest(
    @Schema(description = "Новая позиция среди соседей (0 = первый)", example = "2")
    val newSortOrder: Int
)
