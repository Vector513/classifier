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
     * Фильтрация узлов по нескольким параметрам одновременно.
     * Узел попадает в результат только если удовлетворяет ВСЕМ заданным фильтрам.
     * Поддерживаются числовые диапазоны и точное совпадение по перечислимым параметрам.
     */
    fun searchByMultipleFilters(request: MultiFilterRequest): List<NodeWithParametersResponse> {
        require(request.numericFilters.isNotEmpty() || request.enumFilters.isNotEmpty()) {
            "Должен быть задан хотя бы один фильтр"
        }

        // Получаем список кандидатов: поддерево или весь классификатор
        val allNodes = if (request.rootNodeId != null) {
            val root = nodeRepo.findById(request.rootNodeId).orElseThrow {
                EntityNotFoundException("Узел id=${request.rootNodeId} не найден")
            }
            listOf(root) + nodeRepo.findDescendants(request.rootNodeId)
        } else {
            nodeRepo.findAll()
        }

        if (allNodes.isEmpty()) return emptyList()

        var candidateIds: Set<Long> = allNodes.map { it.id }.toSet()

        // Применяем числовые фильтры (пересечение множеств)
        for (filter in request.numericFilters) {
            val values = nnvRepo.findByNodeIdsAndParameterId(candidateIds.toList(), filter.parameterId)
            val matching = values.filter { nnv ->
                (filter.minValue == null || nnv.value >= filter.minValue) &&
                (filter.maxValue == null || nnv.value <= filter.maxValue)
            }.map { it.classifierNode.id }.toSet()
            candidateIds = candidateIds.intersect(matching)
            if (candidateIds.isEmpty()) return emptyList()
        }

        // Применяем перечислимые фильтры (пересечение множеств)
        for (filter in request.enumFilters) {
            val matching = navRepo.findByNodeIdsAndEnumerationIdAndValueId(
                candidateIds.toList(), filter.enumerationId, filter.valueId
            ).map { it.classifierNode.id }.toSet()
            candidateIds = candidateIds.intersect(matching)
            if (candidateIds.isEmpty()) return emptyList()
        }

        // Загружаем полные данные для найденных узлов
        val matchingNodes = allNodes.filter { it.id in candidateIds }
        val nodeIds = matchingNodes.map { it.id }

        val allEnumAttrs = navRepo.findByClassifierNodeIdIn(nodeIds).groupBy { it.classifierNode.id }
        val allNumericVals = nnvRepo.findByClassifierNodeIdIn(nodeIds).groupBy { it.classifierNode.id }

        return matchingNodes.map { node ->
            NodeWithParametersResponse(
                id = node.id,
                code = node.code,
                name = node.name,
                parentId = node.parent?.id,
                parentName = node.parent?.name,
                enumerationAttributes = (allEnumAttrs[node.id] ?: emptyList()).map(navService::toResponse),
                numericValues = (allNumericVals[node.id] ?: emptyList()).map(numService::toValueResponse)
            )
        }
    }

    /**
     * Получить один узел с полным набором значений его параметров.
     */
    fun getNodeWithParameters(nodeId: Long): NodeWithParametersResponse {
        val node = nodeRepo.findById(nodeId).orElseThrow {
            com.classifier.exception.EntityNotFoundException("Узел id=$nodeId не найден")
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
