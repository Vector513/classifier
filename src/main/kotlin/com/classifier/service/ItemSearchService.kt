package com.classifier.service

import com.classifier.dto.MultiFilterRequest
import com.classifier.dto.NodeWithParametersResponse
import com.classifier.exception.EntityNotFoundException
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.NodeAttributeValueRepository
import com.classifier.repository.NodeNumericValueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ItemSearchService(
    private val nodeRepo: ClassifierNodeRepository,
    private val navRepo: NodeAttributeValueRepository,
    private val nnvRepo: NodeNumericValueRepository,
    private val navService: NodeAttributeValueService,
    private val numService: NumericParameterService
) {

    /**
     * Поиск узлов по коду или названию с возвратом всех значений параметров.
     * Для каждого найденного узла подгружаются перечислимые и числовые атрибуты.
     */
    fun searchWithParameters(query: String): List<NodeWithParametersResponse> {
        val nodes = nodeRepo.searchByQuery(query)
        if (nodes.isEmpty()) return emptyList()

        val nodeIds = nodes.map { it.id }

        // Пакетная загрузка всех атрибутов сразу — без N+1 запросов
        val allEnumAttrs = navRepo.findByClassifierNodeIdIn(nodeIds)
            .groupBy { it.classifierNode.id }

        val allNumericVals = nnvRepo.findByClassifierNodeIdIn(nodeIds)
            .groupBy { it.classifierNode.id }

        return nodes.map { node ->
            NodeWithParametersResponse(
                id = node.id,
                code = node.code,
                name = node.name,
                parentId = node.parent?.id,
                parentName = node.parent?.name,
                enumerationAttributes = (allEnumAttrs[node.id] ?: emptyList())
                    .map(navService::toResponse),
                numericValues = (allNumericVals[node.id] ?: emptyList())
                    .map(numService::toValueResponse)
            )
        }
    }

    /**
     * Отбор изделий поддерева по произвольному числу условий одновременно.
     *
     * Условия могут задаваться как по числовым параметрам (диапазон значений),
     * так и по перечислимым (требуемое значение). Изделие попадает в результат,
     * только если удовлетворяет ВСЕМ условиям сразу (логика «И»).
     *
     * Множество кандидатов начинается со всех узлов поддерева и последовательно
     * сужается каждым условием. Если условий не задано вовсе, возвращаются все
     * изделия раздела — это позволяет просматривать содержимое без фильтрации.
     *
     * В результат включаются только изделия — листовые узлы поддерева.
     */
    fun multiFilter(rootNodeId: Long, request: MultiFilterRequest): List<NodeWithParametersResponse> {
        if (!nodeRepo.existsById(rootNodeId)) {
            throw EntityNotFoundException("Узел id=$rootNodeId не найден")
        }

        val descendants = nodeRepo.findDescendants(rootNodeId)
        if (descendants.isEmpty()) return emptyList()
        val descendantIds = descendants.map { it.id }

        // Начинаем со всех узлов поддерева; при отсутствии условий множество не сужается
        var matched: Set<Long> = descendantIds.toSet()

        // Сужение по числовым условиям
        for (criterion in request.numericCriteria) {
            val satisfying = nnvRepo.findByNodeIdsAndParameterId(descendantIds, criterion.parameterId)
                .filter { value ->
                    (criterion.minValue == null || value.value >= criterion.minValue) &&
                    (criterion.maxValue == null || value.value <= criterion.maxValue)
                }
                .map { it.classifierNode.id }
                .toSet()
            matched = matched intersect satisfying
            if (matched.isEmpty()) return emptyList()
        }

        // Сужение по перечислимым условиям
        for (criterion in request.enumCriteria) {
            val satisfying = navRepo.findByNodeIdsAndEnumerationIdAndValueId(
                descendantIds, criterion.enumerationId, criterion.valueId
            ).map { it.classifierNode.id }.toSet()
            matched = matched intersect satisfying
            if (matched.isEmpty()) return emptyList()
        }

        // В результат попадают только изделия — листовые узлы поддерева.
        // Узел является листовым, если ни один другой узел поддерева не считает его родителем.
        val nonLeafIds = descendants.mapNotNull { it.parent?.id }.toSet()
        val matchedItems = descendants.filter { it.id in matched && it.id !in nonLeafIds }
        if (matchedItems.isEmpty()) return emptyList()

        // Пакетная сборка ответа для отобранных изделий
        val matchedIds = matchedItems.map { it.id }
        val enumAttrs = navRepo.findByClassifierNodeIdIn(matchedIds).groupBy { it.classifierNode.id }
        val numericVals = nnvRepo.findByClassifierNodeIdIn(matchedIds).groupBy { it.classifierNode.id }

        return matchedItems.map { node ->
            NodeWithParametersResponse(
                id = node.id,
                code = node.code,
                name = node.name,
                parentId = node.parent?.id,
                parentName = node.parent?.name,
                enumerationAttributes = (enumAttrs[node.id] ?: emptyList()).map(navService::toResponse),
                numericValues = (numericVals[node.id] ?: emptyList()).map(numService::toValueResponse)
            )
        }
    }

    /**
     * Получить один узел с полным набором значений его параметров.
     */
    fun getNodeWithParameters(nodeId: Long): NodeWithParametersResponse {
        val node = nodeRepo.findById(nodeId).orElseThrow {
            EntityNotFoundException("Узел id=$nodeId не найден")
        }
        val enumAttrs = navRepo.findByClassifierNodeIdOrderByEnumerationId(nodeId)
        val numericVals = nnvRepo.findByClassifierNodeIdOrderByNumericParameterId(nodeId)

        return NodeWithParametersResponse(
            id = node.id,
            code = node.code,
            name = node.name,
            parentId = node.parent?.id,
            parentName = node.parent?.name,
            enumerationAttributes = enumAttrs.map(navService::toResponse),
            numericValues = numericVals.map(numService::toValueResponse)
        )
    }
}
