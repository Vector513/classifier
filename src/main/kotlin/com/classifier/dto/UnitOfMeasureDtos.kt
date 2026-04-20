package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Запрос на создание / обновление единицы измерения")
data class UnitOfMeasureRequest(
    @Schema(description = "Уникальный код", example = "PCS")
    @field:NotBlank @field:Size(max = 50)
    val code: String,

    @Schema(description = "Название единицы измерения", example = "штуки")
    @field:NotBlank @field:Size(max = 255)
    val name: String
)

@Schema(description = "Единица измерения")
data class UnitOfMeasureResponse(
    @Schema(description = "Идентификатор") val id: Long,
    @Schema(description = "Код", example = "PCS") val code: String,
    @Schema(description = "Название", example = "штуки") val name: String
)
