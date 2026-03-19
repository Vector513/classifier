package com.classifier.controller

import com.classifier.dto.*
import com.classifier.mapper.NodeMapper
import com.classifier.service.ClassifierNodeService
import com.classifier.service.TreeTraversalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/nodes")
@Tag(name = "Классификатор", description = "Управление деревом классификатора изделий")
class ClassifierNodeController(
    private val nodeService: ClassifierNodeService,
    private val treeService: TreeTraversalService,
    private val mapper: NodeMapper
) {

    @GetMapping("/roots")
    @Operation(summary = "Получить корневые вершины")
    fun getRootNodes(): List<NodeResponse> =
        nodeService.getRootNodes().map { mapper.toResponse(it) }

    @GetMapping("/{id}")
    @Operation(summary = "Получить вершину по ID")
    fun getById(@PathVariable id: Long): NodeResponse =
        mapper.toResponse(nodeService.getById(id))

    @GetMapping("/{id}/children")
    @Operation(summary = "Получить прямых потомков вершины")
    fun getChildren(@PathVariable id: Long): List<NodeResponse> =
        nodeService.getChildren(id).map { mapper.toResponse(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать вершину")
    fun create(@Valid @RequestBody request: CreateNodeRequest): NodeResponse =
        mapper.toResponse(nodeService.create(request))

    @PatchMapping("/{id}")
    @Operation(summary = "Обновить вершину")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateNodeRequest): NodeResponse =
        mapper.toResponse(nodeService.update(id, request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить вершину (без потомков)")
    fun delete(@PathVariable id: Long) = nodeService.delete(id)

    @PatchMapping("/{id}/move")
    @Operation(summary = "Переместить вершину (сменить родителя)")
    fun move(@PathVariable id: Long, @Valid @RequestBody request: MoveNodeRequest): NodeResponse =
        mapper.toResponse(nodeService.move(id, request.newParentId))

    @PatchMapping("/{id}/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Изменить порядок сортировки вершины среди соседей")
    fun reorder(@PathVariable id: Long, @Valid @RequestBody request: ReorderRequest) =
        nodeService.reorder(id, request.newSortOrder)

    @GetMapping("/{id}/descendants")
    @Operation(summary = "Получить всех потомков вершины (рекурсивно)")
    fun getDescendants(@PathVariable id: Long): List<NodeResponse> =
        treeService.getDescendants(id).map { mapper.toResponse(it) }

    @GetMapping("/{id}/ancestors")
    @Operation(summary = "Получить всех предков вершины (до корня)")
    fun getAncestors(@PathVariable id: Long): List<NodeResponse> =
        treeService.getAncestors(id).map { mapper.toResponse(it) }

    @GetMapping("/{id}/terminals")
    @Operation(summary = "Получить терминальные вершины (листья) поддерева")
    fun getTerminals(@PathVariable id: Long): List<NodeResponse> =
        treeService.getTerminals(id).map { mapper.toResponse(it) }

    @GetMapping("/tree")
    @Operation(summary = "Получить полное дерево классификатора")
    fun getTree(): List<TreeNodeResponse> =
        treeService.buildTree()

    @GetMapping("/search")
    @Operation(summary = "Поиск вершин по коду или названию")
    fun search(@RequestParam query: String): List<NodeResponse> =
        treeService.search(query).map { mapper.toResponse(it) }

    @PostMapping("/validate-cycles")
    @Operation(summary = "Диагностика циклов в дереве")
    fun validateCycles(): ValidationResponse =
        treeService.detectCycles()
}
