package com.classifier.mapper

import com.classifier.dto.NodeResponse
import com.classifier.dto.UnitOfMeasureResponse
import com.classifier.entity.ClassifierNode
import com.classifier.entity.UnitOfMeasure
import org.springframework.stereotype.Component

@Component
class NodeMapper {

    fun toResponse(node: ClassifierNode): NodeResponse = NodeResponse(
        id = node.id,
        code = node.code,
        name = node.name,
        parentId = node.parent?.id,
        sortOrder = node.sortOrder,
        unitOfMeasure = node.unitOfMeasure?.let { toResponse(it) },
        isTerminal = node.children.isEmpty(),
        childrenCount = node.children.size,
        createdAt = node.createdAt,
        updatedAt = node.updatedAt
    )

    fun toResponse(unit: UnitOfMeasure): UnitOfMeasureResponse = UnitOfMeasureResponse(
        id = unit.id,
        code = unit.code,
        name = unit.name
    )
}
