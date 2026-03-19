package com.classifier.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateNodeRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val code: String,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    val parentId: Long? = null,
    val unitOfMeasureId: Long? = null
)

data class UpdateNodeRequest(
    @field:Size(max = 100)
    val code: String? = null,

    @field:Size(max = 255)
    val name: String? = null,

    val unitOfMeasureId: Long? = null
)

data class MoveNodeRequest(
    val newParentId: Long? = null
)

data class ReorderRequest(
    val newSortOrder: Int
)
