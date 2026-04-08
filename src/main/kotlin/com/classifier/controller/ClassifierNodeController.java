package com.classifier.controller;

import com.classifier.dto.*;
import com.classifier.mapper.NodeMapper;
import com.classifier.service.ClassifierNodeService;
import com.classifier.service.TreeTraversalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/nodes")
@Tag(name = "Классификатор", description = "Управление деревом классификатора изделий")
@RequiredArgsConstructor
public class ClassifierNodeController {

    private final ClassifierNodeService nodeService;
    private final TreeTraversalService treeService;
    private final NodeMapper mapper;

    @GetMapping("/roots")
    @Operation(summary = "Получить корневые вершины")
    public List<NodeResponse> getRootNodes() {
        return nodeService.getRootNodes().stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить вершину по ID")
    public NodeResponse getById(@PathVariable Long id) {
        return mapper.toResponse(nodeService.getById(id));
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Получить прямых потомков вершины")
    public List<NodeResponse> getChildren(@PathVariable Long id) {
        return nodeService.getChildren(id).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать вершину")
    public NodeResponse create(@Valid @RequestBody CreateNodeRequest request) {
        return mapper.toResponse(nodeService.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Обновить вершину")
    public NodeResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateNodeRequest request) {
        return mapper.toResponse(nodeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить вершину (без потомков)")
    public void delete(@PathVariable Long id) {
        nodeService.delete(id);
    }

    @PatchMapping("/{id}/move")
    @Operation(summary = "Переместить вершину (сменить родителя)")
    public NodeResponse move(@PathVariable Long id,
                             @Valid @RequestBody MoveNodeRequest request) {
        return mapper.toResponse(nodeService.move(id, request.getNewParentId()));
    }

    @PatchMapping("/{id}/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Изменить порядок сортировки вершины среди соседей")
    public void reorder(@PathVariable Long id,
                        @Valid @RequestBody ReorderRequest request) {
        nodeService.reorder(id, request.getNewSortOrder());
    }

    @GetMapping("/{id}/descendants")
    @Operation(summary = "Получить всех потомков вершины (рекурсивно)")
    public List<NodeResponse> getDescendants(@PathVariable Long id) {
        return treeService.getDescendants(id).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/ancestors")
    @Operation(summary = "Получить всех предков вершины (до корня)")
    public List<NodeResponse> getAncestors(@PathVariable Long id) {
        return treeService.getAncestors(id).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/terminals")
    @Operation(summary = "Получить терминальные вершины (листья) поддерева")
    public List<NodeResponse> getTerminals(@PathVariable Long id) {
        return treeService.getTerminals(id).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/tree")
    @Operation(summary = "Получить полное дерево классификатора")
    public List<TreeNodeResponse> getTree() {
        return treeService.buildTree();
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск вершин по коду или названию")
    public List<NodeResponse> search(@RequestParam String query) {
        return treeService.search(query).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/validate-cycles")
    @Operation(summary = "Диагностика циклов в дереве")
    public ValidationResponse validateCycles() {
        return treeService.detectCycles();
    }
}
