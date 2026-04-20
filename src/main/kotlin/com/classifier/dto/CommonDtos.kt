package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Результат проверки дерева на наличие циклов")
data class ValidationResponse(
    @Schema(description = "true, если цикlov нет") val valid: Boolean,
    @Schema(description = "Списки ID вершин, образующих циклы (пусто, если valid=true)") val cycles: List<List<Long>>
)

@Schema(description = "Описание ошибки")
data class ErrorResponse(
    @Schema(description = "HTTP-статус", example = "404") val status: Int,
    @Schema(description = "Тип ошибки", example = "Not Found") val error: String,
    @Schema(description = "Сообщение об ошибке") val message: String,
    @Schema(description = "Время возникновения ошибки") val timestamp: Instant
)
