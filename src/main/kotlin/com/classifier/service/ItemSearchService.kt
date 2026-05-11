package com.classifier.service

import com.classifier.dto.NodeWithParametersResponse
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
