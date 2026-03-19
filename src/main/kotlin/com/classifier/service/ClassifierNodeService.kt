package com.classifier.service

import com.classifier.dto.CreateNodeRequest
import com.classifier.dto.UpdateNodeRequest
import com.classifier.entity.ClassifierNode
import com.classifier.exception.CyclicMoveException
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.HasChildrenException
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.UnitOfMeasureRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class ClassifierNodeService(
    private val nodeRepo: ClassifierNodeRepository,
    private val unitRepo: UnitOfMeasureRepository
) {

    fun getRootNodes(): List<ClassifierNode> =
        nodeRepo.findByParentIsNullOrderBySortOrder()

    fun getById(id: Long): ClassifierNode =
        nodeRepo.findById(id).orElseThrow {
            EntityNotFoundException("Вершина с id=$id не найдена")
        }

    fun getChildren(id: Long): List<ClassifierNode> {
        if (!nodeRepo.existsById(id)) {
            throw EntityNotFoundException("Вершина с id=$id не найдена")
        }
        return nodeRepo.findByParentIdOrderBySortOrder(id)
    }

    fun create(request: CreateNodeRequest): ClassifierNode {
        if (nodeRepo.existsByCode(request.code)) {
            throw DuplicateCodeException("Вершина с кодом '${request.code}' уже существует")
        }

        val parent = request.parentId?.let { parentId ->
            nodeRepo.findById(parentId).orElseThrow {
                EntityNotFoundException("Родительская вершина с id=$parentId не найдена")
            }
        }

        val unit = request.unitOfMeasureId?.let { unitId ->
            unitRepo.findById(unitId).orElseThrow {
                EntityNotFoundException("Единица измерения с id=$unitId не найдена")
            }
        }

        val sortOrder = if (parent != null) {
            nodeRepo.countByParentId(parent.id).toInt()
        } else {
            nodeRepo.findByParentIsNullOrderBySortOrder().size
        }

        return nodeRepo.save(
            ClassifierNode(
                code = request.code,
                name = request.name,
                parent = parent,
                unitOfMeasure = unit,
                sortOrder = sortOrder
            )
        )
    }

    fun update(id: Long, request: UpdateNodeRequest): ClassifierNode {
        val node = getById(id)

        request.code?.let { newCode ->
            if (node.code != newCode && nodeRepo.existsByCode(newCode)) {
                throw DuplicateCodeException("Вершина с кодом '$newCode' уже существует")
            }
            node.code = newCode
        }

        request.name?.let { node.name = it }

        request.unitOfMeasureId?.let { unitId ->
            node.unitOfMeasure = unitRepo.findById(unitId).orElseThrow {
                EntityNotFoundException("Единица измерения с id=$unitId не найдена")
            }
        }

        node.updatedAt = Instant.now()
        return nodeRepo.save(node)
    }

    fun delete(id: Long) {
        val node = getById(id)
        val childrenCount = nodeRepo.countByParentId(id)
        if (childrenCount > 0) {
            throw HasChildrenException(
                "Невозможно удалить вершину '${node.code}' (id=$id): имеет $childrenCount потомка(ов). " +
                        "Сначала удалите или переместите потомков."
            )
        }
        nodeRepo.delete(node)
    }

    fun move(id: Long, newParentId: Long?): ClassifierNode {
        val node = getById(id)

        if (newParentId != null) {
            if (newParentId == id) {
                throw CyclicMoveException("Невозможно переместить вершину '${node.code}' (id=$id) в саму себя")
            }
            val descendants = nodeRepo.findDescendants(id)
            if (descendants.any { it.id == newParentId }) {
                throw CyclicMoveException(
                    "Невозможно переместить вершину '${node.code}' (id=$id) в собственного потомка (id=$newParentId)"
                )
            }
            node.parent = getById(newParentId)
        } else {
            node.parent = null
        }

        node.updatedAt = Instant.now()
        return nodeRepo.save(node)
    }

    fun reorder(id: Long, newSortOrder: Int) {
        val node = getById(id)
        val siblings = if (node.parent != null) {
            nodeRepo.findByParentIdOrderBySortOrder(node.parent!!.id)
        } else {
            nodeRepo.findByParentIsNullOrderBySortOrder()
        }

        val oldSortOrder = node.sortOrder
        if (oldSortOrder == newSortOrder) return

        for (sibling in siblings) {
            if (oldSortOrder < newSortOrder) {
                if (sibling.sortOrder in (oldSortOrder + 1)..newSortOrder) {
                    sibling.sortOrder--
                }
            } else {
                if (sibling.sortOrder in newSortOrder until oldSortOrder) {
                    sibling.sortOrder++
                }
            }
        }

        node.sortOrder = newSortOrder
        node.updatedAt = Instant.now()
        nodeRepo.saveAll(siblings)
    }
}
