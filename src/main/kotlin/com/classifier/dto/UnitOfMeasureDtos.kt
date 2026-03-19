package com.classifier.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UnitOfMeasureRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val code: String,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String
)

data class UnitOfMeasureResponse(
    val id: Long,
    val code: String,
    val name: String
)
