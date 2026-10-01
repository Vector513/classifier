package com.classifier.service

import com.classifier.dto.CreateNodeRequest
import com.classifier.dto.UpdateNodeRequest
import com.classifier.entity.NodeKind
import com.classifier.exception.InvalidSelectionException
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

        validateParent(request.nodeKind, parent)
        val effectiveUnit = unit ?: if (request.nodeKind != NodeKind.GROUP) parent?.unitOfMeasure else null
        if (request.nodeKind != NodeKind.GROUP && effectiveUnit == null)
            throw InvalidSelectionException("Укажите единицу измерения для модели или изделия")
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
                unitOfMeasure = effectiveUnit,
                nodeKind = request.nodeKind,
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

        request.nodeKind?.let { kind ->
            validateParent(kind, node.parent)
            if (kind == NodeKind.PRODUCT && nodeRepo.countByParentId(id)>0)
                throw InvalidSelectionException("Сначала перенесите дочерние записи: у изделия не может быть потомков")
            if (kind == NodeKind.MODEL && nodeRepo.findByParentIdOrderBySortOrder(id).any { it.nodeKind != NodeKind.PRODUCT })
                throw InvalidSelectionException("Внутри модели допустимы только исполнения")
            node.nodeKind=kind
        }
        if (node.nodeKind != NodeKind.GROUP && node.unitOfMeasure == null)
            throw InvalidSelectionException("Для модели или изделия нужна единица измерения")
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
            val target = getById(newParentId)
            validateParent(node.nodeKind, target)
            node.parent = target
        } else {
            node.parent = null
        }

        node.updatedAt = Instant.now()
        return nodeRepo.save(node)
    }

    private fun validateParent(kind: NodeKind, parent: ClassifierNode?) {
        if (parent?.nodeKind == NodeKind.PRODUCT)
            throw InvalidSelectionException("В изделие нельзя добавить запись классификатора. Его физические компоненты задаются в разделе составов")
        if (parent?.nodeKind == NodeKind.MODEL && kind != NodeKind.PRODUCT)
            throw InvalidSelectionException("Внутри модели создаются только конкретные исполнения")
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
