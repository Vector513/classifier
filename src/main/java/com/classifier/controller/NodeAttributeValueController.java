package com.classifier.controller;

import com.classifier.dto.NodeAttributeValueResponse;
import com.classifier.dto.SelectEnumerationValueRequest;
import com.classifier.service.NodeAttributeValueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/attributes")
@Tag(name = "Атрибуты узлов", description = "Выбор значений перечислений для узлов классификатора")
@RequiredArgsConstructor
public class NodeAttributeValueController {

    private final NodeAttributeValueService service;

    @GetMapping
    @Operation(summary = "Получить все выбранные значения перечислений для узла")
    public List<NodeAttributeValueResponse> getAll(@PathVariable Long nodeId) {
        return service.getNodeAttributes(nodeId).stream()
                .map(service::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{enumerationId}")
    @Operation(summary = "Получить выбранное значение конкретного перечисления для узла")
    public NodeAttributeValueResponse getOne(@PathVariable Long nodeId,
                                             @PathVariable Long enumerationId) {
        return service.toResponse(service.getNodeAttribute(nodeId, enumerationId));
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
        summary = "Выбрать значение перечисления для узла",
        description = "Если значение для данного перечисления уже выбрано — заменяет его. " +
                      "Значение должно принадлежать указанному перечислению."
    )
    public NodeAttributeValueResponse select(@PathVariable Long nodeId,
                                             @RequestBody SelectEnumerationValueRequest request) {
        return service.toResponse(service.selectValue(nodeId, request));
    }

    @DeleteMapping("/{enumerationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Снять выбор значения перечисления с узла")
    public void clear(@PathVariable Long nodeId,
                      @PathVariable Long enumerationId) {
        service.clearNodeAttribute(nodeId, enumerationId);
    }
}
