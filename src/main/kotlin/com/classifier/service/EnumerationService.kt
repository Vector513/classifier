package com.classifier.service

import com.classifier.dto.*
import com.classifier.entity.Enumeration
import com.classifier.entity.EnumerationClass
import com.classifier.entity.EnumerationValue
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.HasChildrenException
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.EnumerationClassRepository
import com.classifier.repository.EnumerationRepository
import com.classifier.repository.EnumerationValueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class EnumerationService(
    private val classRepo: EnumerationClassRepository,
    private val enumRepo: EnumerationRepository,
    private val valueRepo: EnumerationValueRepository,
    private val nodeRepo: ClassifierNodeRepository
) {

    // ── EnumerationClass ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getAllClasses(): List<EnumerationClass> = classRepo.findAllByOrderByName()

    @Transactional(readOnly = true)
    fun getClassById(id: Long): EnumerationClass =
        classRepo.findById(id).orElseThrow {
            EntityNotFoundException("Класс перечисления с id=$id не найден")
        }

    fun createClass(request: CreateEnumerationClassRequest): EnumerationClass {
        if (classRepo.existsByCode(request.code)) {
            throw DuplicateCodeException("Класс перечисления с кодом '${request.code}' уже существует")
        }
        return classRepo.save(
            EnumerationClass(
                code = request.code,
                name = request.name,
                description = request.description
            )
        )
    }

    fun updateClass(id: Long, request: UpdateEnumerationClassRequest): EnumerationClass {
        val cls = getClassById(id)
        request.code?.let { newCode ->
            if (cls.code != newCode && classRepo.existsByCode(newCode)) {
                throw DuplicateCodeException("Класс перечисления с кодом '$newCode' уже существует")
            }
            cls.code = newCode
        }
        request.name?.let { cls.name = it }
        request.description?.let { cls.description = it }
        cls.updatedAt = Instant.now()
        return classRepo.save(cls)
    }

    fun deleteClass(id: Long) {
        val cls = getClassById(id)
        if (cls.enumerations.isNotEmpty()) {
            throw HasChildrenException(
                "Невозможно удалить класс '${cls.code}': содержит ${cls.enumerations.size} перечисление(й)"
            )
        }
        classRepo.delete(cls)
    }

    // ── Enumeration ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getEnumerationsByClass(classId: Long): List<Enumeration> {
        if (!classRepo.existsById(classId)) {
            throw EntityNotFoundException("Класс перечисления с id=$classId не найден")
        }
        return enumRepo.findByEnumerationClassIdOrderByName(classId)
    }

    @Transactional(readOnly = true)
    fun getEnumerationsByNode(nodeId: Long): List<Enumeration> {
        if (!nodeRepo.existsById(nodeId)) {
            throw EntityNotFoundException("Узел классификатора с id=$nodeId не найден")
        }
        return enumRepo.findByClassifierNodeIdOrderByName(nodeId)
    }

    @Transactional(readOnly = true)
    fun getEnumerationById(id: Long): Enumeration =
        enumRepo.findById(id).orElseThrow {
            EntityNotFoundException("Перечисление с id=$id не найдено")
        }

    fun createEnumeration(request: CreateEnumerationRequest): Enumeration {
        if (enumRepo.existsByCode(request.code)) {
            throw DuplicateCodeException("Перечисление с кодом '${request.code}' уже существует")
        }
        val cls = getClassById(request.enumerationClassId)
        val node = request.classifierNodeId?.let {
            nodeRepo.findById(it).orElseThrow {
                EntityNotFoundException("Узел классификатора с id=$it не найден")
            }
        }
        return enumRepo.save(
            Enumeration(
                code = request.code,
                name = request.name,
                enumerationClass = cls,
                classifierNode = node
            )
        )
    }

    fun updateEnumeration(id: Long, request: UpdateEnumerationRequest): Enumeration {
        val enum = getEnumerationById(id)
        request.code?.let { newCode ->
            if (enum.code != newCode && enumRepo.existsByCode(newCode)) {
                throw DuplicateCodeException("Перечисление с кодом '$newCode' уже существует")
            }
            enum.code = newCode
        }
        request.name?.let { enum.name = it }
        request.classifierNodeId?.let { nodeId ->
            enum.classifierNode = nodeRepo.findById(nodeId).orElseThrow {
                EntityNotFoundException("Узел классификатора с id=$nodeId не найден")
            }
        }
        enum.updatedAt = Instant.now()
        return enumRepo.save(enum)
    }

    fun deleteEnumeration(id: Long) {
        val enum = getEnumerationById(id)
        val valueCount = valueRepo.countByEnumerationId(id)
        if (valueCount > 0) {
            throw HasChildrenException(
                "Невозможно удалить перечисление '${enum.code}': содержит $valueCount значение(й)"
            )
        }
        enumRepo.delete(enum)
    }

    // ── EnumerationValue ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getValues(enumerationId: Long): List<EnumerationValue> {
        if (!enumRepo.existsById(enumerationId)) {
            throw EntityNotFoundException("Перечисление с id=$enumerationId не найдено")
        }
        return valueRepo.findByEnumerationIdOrderBySortOrder(enumerationId)
    }

    @Transactional(readOnly = true)
    fun getValueById(id: Long): EnumerationValue =
        valueRepo.findById(id).orElseThrow {
            EntityNotFoundException("Значение перечисления с id=$id не найдено")
        }

    fun addValue(enumerationId: Long, request: CreateEnumerationValueRequest): EnumerationValue {
        val enum = getEnumerationById(enumerationId)
        if (valueRepo.existsByEnumerationIdAndCode(enumerationId, request.code)) {
            throw DuplicateCodeException("Значение с кодом '${request.code}' уже существует в этом перечислении")
        }
        val sortOrder = valueRepo.countByEnumerationId(enumerationId).toInt()
        val value = valueRepo.save(
            EnumerationValue(
                code = request.code,
                name = request.name,
                enumeration = enum,
                sortOrder = sortOrder
            )
        )
        enum.updatedAt = Instant.now()
        enumRepo.save(enum)
        return value
    }

    fun updateValue(id: Long, request: UpdateEnumerationValueRequest): EnumerationValue {
        val value = getValueById(id)
        request.code?.let { newCode ->
            if (value.code != newCode &&
                valueRepo.existsByEnumerationIdAndCode(value.enumeration.id, newCode)
            ) {
                throw DuplicateCodeException("Значение с кодом '$newCode' уже существует в этом перечислении")
            }
            value.code = newCode
        }
        request.name?.let { value.name = it }
        value.updatedAt = Instant.now()
        return valueRepo.save(value)
    }

    fun deleteValue(id: Long) {
        val value = getValueById(id)
        val siblings = valueRepo.findByEnumerationIdOrderBySortOrder(value.enumeration.id)
        siblings.filter { it.sortOrder > value.sortOrder }.forEach { it.sortOrder-- }
        valueRepo.delete(value)
        valueRepo.saveAll(siblings.filter { it.id != value.id })
    }

    fun reorderValue(id: Long, newSortOrder: Int) {
        val value = getValueById(id)
        val siblings = valueRepo.findByEnumerationIdOrderBySortOrder(value.enumeration.id)
        val oldOrder = value.sortOrder
        val maxOrder = siblings.size - 1
        val targetOrder = newSortOrder.coerceIn(0, maxOrder)
        if (oldOrder == targetOrder) return

        for (sibling in siblings) {
            if (oldOrder < targetOrder) {
                if (sibling.sortOrder in (oldOrder + 1)..targetOrder) sibling.sortOrder--
            } else {
                if (sibling.sortOrder in targetOrder until oldOrder) sibling.sortOrder++
            }
        }
        value.sortOrder = targetOrder
        value.updatedAt = Instant.now()
        valueRepo.saveAll(siblings)
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    fun toClassResponse(cls: EnumerationClass) = EnumerationClassResponse(
        id = cls.id,
        code = cls.code,
        name = cls.name,
        description = cls.description,
        enumerationCount = cls.enumerations.size,
        createdAt = cls.createdAt,
        updatedAt = cls.updatedAt
    )

    fun toEnumerationResponse(enum: Enumeration) = EnumerationResponse(
        id = enum.id,
        code = enum.code,
        name = enum.name,
        enumerationClassId = enum.enumerationClass.id,
        enumerationClassName = enum.enumerationClass.name,
        classifierNodeId = enum.classifierNode?.id,
        classifierNodeName = enum.classifierNode?.name,
        valueCount = enum.values.size,
        createdAt = enum.createdAt,
        updatedAt = enum.updatedAt
    )

    fun toEnumerationWithValuesResponse(enum: Enumeration) = EnumerationWithValuesResponse(
        id = enum.id,
        code = enum.code,
        name = enum.name,
        enumerationClassId = enum.enumerationClass.id,
        enumerationClassName = enum.enumerationClass.name,
        classifierNodeId = enum.classifierNode?.id,
        classifierNodeName = enum.classifierNode?.name,
        values = enum.values.map { toValueResponse(it) },
        createdAt = enum.createdAt,
        updatedAt = enum.updatedAt
    )

    fun toValueResponse(value: EnumerationValue) = EnumerationValueResponse(
        id = value.id,
        code = value.code,
        name = value.name,
        enumerationId = value.enumeration.id,
        sortOrder = value.sortOrder,
        createdAt = value.createdAt,
        updatedAt = value.updatedAt
    )
}
