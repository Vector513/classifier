package com.classifier.dto

import java.time.Instant

data class NodeResponse(
    val id: Long,
    val code: String,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
    val unitOfMeasure: UnitOfMeasureResponse?,
    val isTerminal: Boolean,
    val childrenCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class TreeNodeResponse(
    val id: Long,
    val code: String,
    val name: String,
    val unitOfMeasure: UnitOfMeasureResponse?,
    val isTerminal: Boolean,
    val children: List<TreeNodeResponse>
)
