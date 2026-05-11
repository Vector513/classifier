package com.classifier.service

import com.classifier.dto.*
import com.classifier.entity.NodeNumericParameter
import com.classifier.entity.NodeNumericValue
import com.classifier.entity.NumericParameter
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.HasChildrenException
import com.classifier.exception.ValueOutOfRangeException
import com.classifier.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
@Transactional
class NumericParameterService(
    private val paramRepo: NumericParameterRepository,
    private val nodeParamRepo: NodeNumericParameterRepository,
    private val nodeValueRepo: NodeNumericValueRepository,
    private val nodeRepo: ClassifierNodeRepository,
    private val uomRepo: UnitOfMeasureRepository
) {

    // ─── CRUD числовых параметров ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getAll(): List<NumericParameterResponse> =
        paramRepo.findAllByOrderByName().map(::toParamResponse)

    @Transactional(readOnly = true)
    fun getById(id: Long): NumericParameterResponse =
        toParamResponse(findParam(id))

    fun create(req: CreateNumericParameterRequest): NumericParameterResponse {
        if (paramRepo.existsByCode(req.code)) {
            throw DuplicateCodeException("Числовой параметр с кодом '${req.code}' уже существует")
        }
        validateRange(req.minValue, req.maxValue)
        val uom = req.unitOfMeasureId?.let {
            uomRepo.findById(it).orElseThrow { EntityNotFoundException("Единица измерения id=$it не найдена") }
        }
        val param = paramRepo.save(
            NumericParameter(
                code = req.code,
                name = req.name,
                description = req.description,
                minValue = req.minValue,
                maxValue = req.maxValue,
                unitOfMeasure = uom
            )
        )
        return toParamResponse(param)
    }

    fun update(id: Long, req: UpdateNumericParameterRequest): NumericParameterResponse {
        val param = findParam(id)
        req.code?.let {
            if (it != param.code && paramRepo.existsByCode(it)) {
                throw DuplicateCodeException("Числовой параметр с кодом '$it' уже существует")
            }
            param.code = it
        }
        req.name?.let { param.name = it }
        req.description?.let { param.description = it }

        val newMin = req.minValue ?: param.minValue
        val newMax = req.maxValue ?: param.maxValue
        validateRange(newMin, newMax)
        req.minValue?.let { param.minValue = it }
        req.maxValue?.let { param.maxValue = it }

        req.unitOfMeasureId?.let { uomId ->
            param.unitOfMeasure = uomRepo.findById(uomId)
                .orElseThrow { EntityNotFoundException("Единица измерения id=$uomId не найдена") }
        }
        param.updatedAt = Instant.now()
        return toParamResponse(paramRepo.save(param))
    }

    fun delete(id: Long) {
        findParam(id)
        val usageCount = nodeParamRepo.countByNumericParameterId(id)
        if (usageCount > 0) {
            throw HasChildrenException(
                "Числовой параметр id=$id используется в $usageCount узлах классификатора"
            )
        }
        paramRepo.deleteById(id)
    }

    // ─── Назначение параметра на узел/класс ──────────────────────────────────

    /** Назначить числовой параметр на класс (узел классификатора). */
    fun assignToNode(nodeId: Long, paramId: Long): InheritedNumericParameterResponse {
        val node = nodeRepo.findById(nodeId).orElseThrow {
            EntityNotFoundException("Узел id=$nodeId не найден")
        }
        val param = findParam(paramId)
        if (nodeParamRepo.existsByClassifierNodeIdAndNumericParameterId(nodeId, paramId)) {
            throw DuplicateCodeException(
                "Параметр '${param.code}' уже назначен на узел id=$nodeId"
            )
        }
        nodeParamRepo.save(NodeNumericParameter(classifierNode = node, numericParameter = param))
        return InheritedNumericParameterResponse(
            parameterId = param.id,
            parameterCode = param.code,
            parameterName = param.name,
            description = param.description,
            minValue = param.minValue,
            maxValue = param.maxValue,
            unitOfMeasureName = param.unitOfMeasure?.name,
            definedAtNodeId = node.id,
            definedAtNodeName = node.name,
            isInherited = false
        )
    }

    /** Снять числовой параметр с узла (не удаляет значения у потомков). */
    fun removeFromNode(nodeId: Long, paramId: Long) {
        if (!nodeParamRepo.existsByClassifierNodeIdAndNumericParameterId(nodeId, paramId)) {
            throw EntityNotFoundException(
                "Параметр id=$paramId не назначен на узел id=$nodeId"
            )
        }
        nodeParamRepo.deleteByClassifierNodeIdAndNumericParameterId(nodeId, paramId)
    }

    /** Получить параметры, непосредственно назначенные на узел (без наследования). */
    @Transactional(readOnly = true)
    fun getOwnParameters(nodeId: Long): List<InheritedNumericParameterResponse> {
        val node = nodeRepo.findById(nodeId).orElseThrow {
            EntityNotFoundException("Узел id=$nodeId не найден")
        }
        return nodeParamRepo.findByClassifierNodeIdOrderByNumericParameterId(nodeId)
            .map { nnp ->
                val p = nnp.numericParameter
                InheritedNumericParameterResponse(
                    parameterId = p.id,
                    parameterCode = p.code,
                    parameterName = p.name,
                    description = p.description,
                    minValue = p.minValue,
                    maxValue = p.maxValue,
                    unitOfMeasureName = p.unitOfMeasure?.name,
                    definedAtNodeId = node.id,
                    definedAtNodeName = node.name,
                    isInherited = false
                )
            }
    }

    /**
     * Получить все параметры, действующие для узла, включая унаследованные от предков.
     * Параметры предка перекрываются параметрами потомка при совпадении типа.
     */
    @Transactional(readOnly = true)
    fun getEffectiveParameters(nodeId: Long): List<InheritedNumericParameterResponse> {
        nodeRepo.findById(nodeId).orElseThrow {
            EntityNotFoundException("Узел id=$nodeId не найден")
        }
        // Предки от корня к текущему узлу (ancestors возвращают от текущего к корню)
        val ancestors = nodeRepo.findAncestors(nodeId)
        // Порядок: корень → ... → предок → сам узел
        val orderedNodeIds = (ancestors.map { it.id }.reversed()) + nodeId

        val allAssignments = nodeParamRepo.findByClassifierNodeIdIn(orderedNodeIds)

        // Собираем карту: paramId → assignment.
        // Итерируем в порядке от корня к узлу — потомок перекрывает предка.
        val effectiveMap = linkedMapOf<Long, NodeNumericParameter>()
        for (id in orderedNodeIds) {
            allAssignments.filter { it.classifierNode.id == id }
                .forEach { effectiveMap[it.numericParameter.id] = it }
        }

        val currentNode = nodeRepo.findById(nodeId).get()
        return effectiveMap.values.map { nnp ->
            val p = nnp.numericParameter
            val defNode = nnp.classifierNode
            InheritedNumericParameterResponse(
                parameterId = p.id,
                parameterCode = p.code,
                parameterName = p.name,
                description = p.description,
                minValue = p.minValue,
                maxValue = p.maxValue,
                unitOfMeasureName = p.unitOfMeasure?.name,
                definedAtNodeId = defNode.id,
                definedAtNodeName = defNode.name,
                isInherited = defNode.id != currentNode.id
            )
        }
    }

    // ─── Значения параметров для узлов (изделий) ─────────────────────────────

    /**
     * Установить (или заменить) числовое значение параметра для узла.
     * Значение проверяется на соответствие диапазону параметра.
     */
    fun setValue(nodeId: Long, req: SetNumericValueRequest): NodeNumericValueResponse {
        val node = nodeRepo.findById(nodeId).orElseThrow {
            EntityNotFoundException("Узел id=$nodeId не найден")
        }
        val param = findParam(req.parameterId)
        validateValue(req.value, param.minValue, param.maxValue, param.code)

        val existing = nodeValueRepo.findByClassifierNodeIdAndNumericParameterId(nodeId, req.parameterId)
        val saved = if (existing != null) {
            existing.value = req.value
            existing.updatedAt = Instant.now()
            nodeValueRepo.save(existing)
        } else {
            nodeValueRepo.save(
                NodeNumericValue(classifierNode = node, numericParameter = param, value = req.value)
            )
        }
        return toValueResponse(saved)
    }

    @Transactional(readOnly = true)
    fun getValues(nodeId: Long): List<NodeNumericValueResponse> {
        if (!nodeRepo.existsById(nodeId)) {
            throw EntityNotFoundException("Узел id=$nodeId не найден")
        }
        return nodeValueRepo.findByClassifierNodeIdOrderByNumericParameterId(nodeId)
            .map(::toValueResponse)
    }

    @Transactional(readOnly = true)
    fun getValue(nodeId: Long, paramId: Long): NodeNumericValueResponse =
        toValueResponse(
            nodeValueRepo.findByClassifierNodeIdAndNumericParameterId(nodeId, paramId)
                ?: throw EntityNotFoundException(
                    "Значение параметра id=$paramId для узла id=$nodeId не найдено"
                )
        )

    fun clearValue(nodeId: Long, paramId: Long) {
        if (!nodeValueRepo.existsByClassifierNodeIdAndNumericParameterId(nodeId, paramId)) {
            throw EntityNotFoundException(
                "Значение параметра id=$paramId для узла id=$nodeId не найдено"
            )
        }
        nodeValueRepo.deleteByClassifierNodeIdAndNumericParameterId(nodeId, paramId)
    }

    // ─── Агрегаты ─────────────────────────────────────────────────────────────

    /**
     * Вычислить агрегаты (min, max, avg, count) числового параметра
     * по всем узлам поддерева, начиная с rootNodeId.
     */
    @Transactional(readOnly = true)
    fun getAggregates(rootNodeId: Long, paramId: Long): NumericAggregatesResponse {
        if (!nodeRepo.existsById(rootNodeId)) {
            throw EntityNotFoundException("Узел id=$rootNodeId не найден")
        }
        val param = findParam(paramId)
        val projection = nodeValueRepo.getAggregates(rootNodeId, paramId)
        return NumericAggregatesResponse(
            parameterId = param.id,
            parameterCode = param.code,
            parameterName = param.name,
            rootNodeId = rootNodeId,
            count = projection?.getCnt() ?: 0L,
            minValue = projection?.getMinVal(),
            maxValue = projection?.getMaxVal(),
            avgValue = projection?.getAvgVal()?.setScale(6, RoundingMode.HALF_UP)
        )
    }

    // ─── Фильтрация изделий по параметрам ────────────────────────────────────

    /**
     * Найти все узлы в поддереве classNodeId, у которых значение параметра paramId
     * попадает в заданный диапазон [minVal, maxVal].
     */
    @Transactional(readOnly = true)
    fun filterNodes(
        classNodeId: Long,
        paramId: Long,
        minVal: BigDecimal?,
        maxVal: BigDecimal?
    ): List<NodeNumericValueResponse> {
        if (!nodeRepo.existsById(classNodeId)) {
            throw EntityNotFoundException("Узел id=$classNodeId не найден")
        }
        findParam(paramId)

        val descendants = nodeRepo.findDescendants(classNodeId)
        val descendantIds = descendants.map { it.id }
        if (descendantIds.isEmpty()) return emptyList()

        return nodeValueRepo.findByNodeIdsAndParameterId(descendantIds, paramId)
            .filter { nnv ->
                (minVal == null || nnv.value >= minVal) &&
                (maxVal == null || nnv.value <= maxVal)
            }
            .map(::toValueResponse)
    }

    // ─── Маппинг ──────────────────────────────────────────────────────────────

    fun toParamResponse(p: NumericParameter) = NumericParameterResponse(
        id = p.id,
        code = p.code,
        name = p.name,
        description = p.description,
        minValue = p.minValue,
        maxValue = p.maxValue,
        unitOfMeasureId = p.unitOfMeasure?.id,
        unitOfMeasureName = p.unitOfMeasure?.name,
        createdAt = p.createdAt,
        updatedAt = p.updatedAt
    )

    fun toValueResponse(nnv: NodeNumericValue) = NodeNumericValueResponse(
        id = nnv.id,
        classifierNodeId = nnv.classifierNode.id,
        classifierNodeName = nnv.classifierNode.name,
        parameterId = nnv.numericParameter.id,
        parameterCode = nnv.numericParameter.code,
        parameterName = nnv.numericParameter.name,
        value = nnv.value,
        minValue = nnv.numericParameter.minValue,
        maxValue = nnv.numericParameter.maxValue,
        unitOfMeasureName = nnv.numericParameter.unitOfMeasure?.name,
        createdAt = nnv.createdAt,
        updatedAt = nnv.updatedAt
    )

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    private fun findParam(id: Long) = paramRepo.findById(id).orElseThrow {
        EntityNotFoundException("Числовой параметр id=$id не найден")
    }

    private fun validateRange(min: BigDecimal?, max: BigDecimal?) {
        if (min != null && max != null && min > max) {
            throw ValueOutOfRangeException(
                "Минимальное значение ($min) не может быть больше максимального ($max)"
            )
        }
    }

    private fun validateValue(value: BigDecimal, min: BigDecimal?, max: BigDecimal?, code: String) {
        if (min != null && value < min) {
            throw ValueOutOfRangeException(
                "Значение $value меньше минимально допустимого $min для параметра '$code'"
            )
        }
        if (max != null && value > max) {
            throw ValueOutOfRangeException(
                "Значение $value больше максимально допустимого $max для параметра '$code'"
            )
        }
    }
}
