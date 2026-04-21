package com.classifier.service

import com.classifier.dto.NodeAttributeValueResponse
import com.classifier.dto.SelectEnumerationValueRequest
import com.classifier.entity.NodeAttributeValue
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.InvalidSelectionException
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.EnumerationRepository
import com.classifier.repository.EnumerationValueRepository
import com.classifier.repository.NodeAttributeValueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class NodeAttributeValueService(
    private val navRepo: NodeAttributeValueRepository,
    private val nodeRepo: ClassifierNodeRepository,
    private val enumRepo: EnumerationRepository,
    private val valueRepo: EnumerationValueRepository
) {

    /**
     * Выбрать (или заменить) значение перечисления для узла.
     * Если для данной пары (node, enumeration) запись уже существует —
     * обновляет выбранное значение. Иначе — создаёт новую запись.
     */
    fun selectValue(nodeId: Long, request: SelectEnumerationValueRequest): NodeAttributeValue {
        val node = nodeRepo.findById(nodeId).orElseThrow {
            EntityNotFoundException("Узел классификатора с id=$nodeId не найден")
        }
        val enumeration = enumRepo.findById(request.enumerationId).orElseThrow {
            EntityNotFoundException("Перечисление с id=${request.enumerationId} не найдено")
        }
        val value = valueRepo.findById(request.valueId).orElseThrow {
            EntityNotFoundException("Значение перечисления с id=${request.valueId} не найдено")
        }

        // Проверяем, что значение принадлежит указанному перечислению
        if (value.enumeration.id != enumeration.id) {
            throw InvalidSelectionException(
                "Значение '${value.code}' (id=${value.id}) не принадлежит " +
                "перечислению '${enumeration.code}' (id=${enumeration.id})"
            )
        }

        val existing = navRepo.findByClassifierNodeIdAndEnumerationId(nodeId, request.enumerationId)
        return if (existing != null) {
            existing.selectedValue = value
            existing.updatedAt = Instant.now()
            navRepo.save(existing)
        } else {
            navRepo.save(
                NodeAttributeValue(
                    classifierNode = node,
                    enumeration = enumeration,
                    selectedValue = value
                )
            )
        }
    }

    /** Получить все выбранные значения перечислений для узла. */
    @Transactional(readOnly = true)
    fun getNodeAttributes(nodeId: Long): List<NodeAttributeValue> {
        if (!nodeRepo.existsById(nodeId)) {
            throw EntityNotFoundException("Узел классификатора с id=$nodeId не найден")
        }
        return navRepo.findByClassifierNodeIdOrderByEnumerationId(nodeId)
    }

    /** Получить конкретный выбор (узел + перечисление). */
    @Transactional(readOnly = true)
    fun getNodeAttribute(nodeId: Long, enumerationId: Long): NodeAttributeValue {
        return navRepo.findByClassifierNodeIdAndEnumerationId(nodeId, enumerationId)
            ?: throw EntityNotFoundException(
                "Для узла id=$nodeId не выбрано значение перечисления id=$enumerationId"
            )
    }

    /** Снять выбор значения перечисления с узла. */
    fun clearNodeAttribute(nodeId: Long, enumerationId: Long) {
        if (!navRepo.existsByClassifierNodeIdAndEnumerationId(nodeId, enumerationId)) {
            throw EntityNotFoundException(
                "Для узла id=$nodeId не выбрано значение перечисления id=$enumerationId"
            )
        }
        navRepo.deleteByClassifierNodeIdAndEnumerationId(nodeId, enumerationId)
    }

    fun toResponse(nav: NodeAttributeValue) = NodeAttributeValueResponse(
        id = nav.id,
        classifierNodeId = nav.classifierNode.id,
        classifierNodeName = nav.classifierNode.name,
        enumerationId = nav.enumeration.id,
        enumerationName = nav.enumeration.name,
        selectedValueId = nav.selectedValue.id,
        selectedValueCode = nav.selectedValue.code,
        selectedValueName = nav.selectedValue.name,
        createdAt = nav.createdAt,
        updatedAt = nav.updatedAt
    )
}
