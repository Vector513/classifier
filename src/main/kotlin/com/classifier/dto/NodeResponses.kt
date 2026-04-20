package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Вершина классификатора (плоское представление)")
data class NodeResponse(
    @Schema(description = "Идентификатор") val id: Long,
    @Schema(description = "Уникальный код", example = "PHONES-APPLE") val code: String,
    @Schema(description = "Название", example = "Apple") val name: String,
    @Schema(description = "ID родительской вершины (null = корень)") val parentId: Long?,
    @Schema(description = "Порядок среди соседей (0 = первый)") val sortOrder: Int,
    @Schema(description = "Единица измерения (если задана)") val unitOfMeasure: UnitOfMeasureResponse?,
    @Schema(description = "true, если вершина не имеет потомков (листовой узел)") val isTerminal: Boolean,
    @Schema(description = "Количество прямых потомков") val childrenCount: Int,
    @Schema(description = "Дата создания") val createdAt: Instant,
    @Schema(description = "Дата последнего обновления") val updatedAt: Instant
)

@Schema(description = "Вершина классификатора (дерево с потомками)")
data class TreeNodeResponse(
    @Schema(description = "Идентификатор") val id: Long,
    @Schema(description = "Уникальный код", example = "PHONES") val code: String,
    @Schema(description = "Название", example = "Смартфоны") val name: String,
    @Schema(description = "Единица измерения") val unitOfMeasure: UnitOfMeasureResponse?,
    @Schema(description = "true, если вершина является листовым узлом") val isTerminal: Boolean,
    @Schema(description = "Вложенные потомки") val children: List<TreeNodeResponse>
)
