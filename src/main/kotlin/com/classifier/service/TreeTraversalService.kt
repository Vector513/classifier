package com.classifier.service

import com.classifier.dto.TreeNodeResponse
import com.classifier.dto.UnitOfMeasureResponse
import com.classifier.dto.ValidationResponse
import com.classifier.entity.ClassifierNode
import com.classifier.exception.EntityNotFoundException
import com.classifier.repository.ClassifierNodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TreeTraversalService(
    private val nodeRepo: ClassifierNodeRepository
) {

    fun getDescendants(id: Long): List<ClassifierNode> {
        if (!nodeRepo.existsById(id)) {
            throw EntityNotFoundException("Вершина с id=$id не найдена")
        }
        return nodeRepo.findDescendants(id)
    }

    fun getAncestors(id: Long): List<ClassifierNode> {
        if (!nodeRepo.existsById(id)) {
            throw EntityNotFoundException("Вершина с id=$id не найдена")
        }
        return nodeRepo.findAncestors(id)
    }

    fun getTerminals(id: Long): List<ClassifierNode> {
        if (!nodeRepo.existsById(id)) {
            throw EntityNotFoundException("Вершина с id=$id не найдена")
        }
        return nodeRepo.findTerminals(id)
    }

    fun search(query: String): List<ClassifierNode> =
        nodeRepo.searchByQuery(query)

    fun buildTree(): List<TreeNodeResponse> {
        val roots = nodeRepo.findByParentIsNullOrderBySortOrder()
        return roots.map { buildTreeNode(it) }
    }

    private fun buildTreeNode(node: ClassifierNode): TreeNodeResponse {
        val children = nodeRepo.findByParentIdOrderBySortOrder(node.id)
        return TreeNodeResponse(
            id = node.id,
            code = node.code,
            name = node.name,
            unitOfMeasure = node.unitOfMeasure?.let {
                UnitOfMeasureResponse(id = it.id, code = it.code, name = it.name)
            },
            isTerminal = children.isEmpty(),
            children = children.map { buildTreeNode(it) }
        )
    }

    fun detectCycles(): ValidationResponse {
        val allNodes = nodeRepo.findAll()
        val parentMap = allNodes.associate { it.id to it.parent?.id }
        val cycles = mutableListOf<List<Long>>()

        val visited = mutableSetOf<Long>()

        for (nodeId in parentMap.keys) {
            if (nodeId in visited) continue

            val path = mutableListOf<Long>()
            var current: Long? = nodeId

            while (current != null && current !in visited) {
                if (current in path) {
                    val cycleStart = path.indexOf(current)
                    cycles.add(path.subList(cycleStart, path.size) + current)
                    break
                }
                path.add(current)
                current = parentMap[current]
            }

            visited.addAll(path)
        }

        return ValidationResponse(valid = cycles.isEmpty(), cycles = cycles)
    }
}
