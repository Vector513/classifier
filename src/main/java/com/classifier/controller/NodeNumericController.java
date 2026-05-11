package com.classifier.controller;

import com.classifier.dto.*;
import com.classifier.service.NumericParameterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Эндпоинты для работы с числовыми параметрами в контексте узлов классификатора:
 * — назначение параметров на классы (наследование)
 * — ввод значений параметров для конкретных изделий (узлов)
 * — фильтрация и агрегаты
 */
@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/numeric")
@Tag(name = "Числовые параметры узлов", description = "Назначение параметров, значения изделий, наследование, фильтрация")
@RequiredArgsConstructor
public class NodeNumericController {

    private final NumericParameterService service;

    // ─── Назначение параметров на класс ──────────────────────────────────────

    @GetMapping("/parameters")
    @Operation(summary = "Параметры узла (только собственные, без наследования)")
    public List<InheritedNumericParameterResponse> getOwnParameters(@PathVariable Long nodeId) {
        return service.getOwnParameters(nodeId);
    }

    @GetMapping("/parameters/effective")
    @Operation(
        summary = "Эффективные параметры узла (собственные + унаследованные от предков)",
        description = "Параметры предка перекрываются параметрами потомка при совпадении типа. " +
                      "Поле isInherited=true означает, что параметр определён в одном из предков."
    )
    public List<InheritedNumericParameterResponse> getEffectiveParameters(@PathVariable Long nodeId) {
        return service.getEffectiveParameters(nodeId);
    }

    @PostMapping("/parameters/{paramId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Назначить числовой параметр на класс",
        description = "Назначенный параметр автоматически наследуется всеми дочерними узлами."
    )
    public InheritedNumericParameterResponse assignParameter(@PathVariable Long nodeId,
                                                              @PathVariable Long paramId) {
        return service.assignToNode(nodeId, paramId);
    }

    @DeleteMapping("/parameters/{paramId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Снять числовой параметр с класса")
    public void removeParameter(@PathVariable Long nodeId, @PathVariable Long paramId) {
        service.removeFromNode(nodeId, paramId);
    }

    // ─── Значения параметров изделия ──────────────────────────────────────────

    @GetMapping("/values")
    @Operation(summary = "Получить все числовые значения параметров узла (изделия)")
    public List<NodeNumericValueResponse> getValues(@PathVariable Long nodeId) {
        return service.getValues(nodeId);
    }

    @GetMapping("/values/{paramId}")
    @Operation(summary = "Получить значение конкретного параметра для узла")
    public NodeNumericValueResponse getValue(@PathVariable Long nodeId,
                                              @PathVariable Long paramId) {
        return service.getValue(nodeId, paramId);
    }

    @PutMapping("/values")
    @Operation(
        summary = "Установить числовое значение параметра для узла",
        description = "Создаёт или заменяет значение. " +
                      "Значение проверяется на соответствие диапазону [minValue, maxValue] параметра."
    )
    public NodeNumericValueResponse setValue(@PathVariable Long nodeId,
                                              @RequestBody SetNumericValueRequest request) {
        return service.setValue(nodeId, request);
    }

    @DeleteMapping("/values/{paramId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить значение числового параметра у узла")
    public void clearValue(@PathVariable Long nodeId, @PathVariable Long paramId) {
        service.clearValue(nodeId, paramId);
    }

    // ─── Агрегаты и фильтрация ────────────────────────────────────────────────

    @GetMapping("/aggregates/{paramId}")
    @Operation(
        summary = "Агрегаты числового параметра по поддереву",
        description = "Вычисляет min, max, avg, count по всем узлам поддерева данного узла " +
                      "для указанного числового параметра."
    )
    public NumericAggregatesResponse getAggregates(@PathVariable Long nodeId,
                                                    @PathVariable Long paramId) {
        return service.getAggregates(nodeId, paramId);
    }

    @GetMapping("/filter/{paramId}")
    @Operation(
        summary = "Отбор узлов поддерева по значению числового параметра",
        description = "Возвращает все узлы в поддереве, у которых значение параметра " +
                      "попадает в диапазон [minVal, maxVal]. Граничные параметры опциональны."
    )
    public List<NodeNumericValueResponse> filterNodes(
            @PathVariable Long nodeId,
            @PathVariable Long paramId,
            @Parameter(description = "Нижняя граница (включительно)") @RequestParam(required = false) BigDecimal minVal,
            @Parameter(description = "Верхняя граница (включительно)") @RequestParam(required = false) BigDecimal maxVal) {
        return service.filterNodes(nodeId, paramId, minVal, maxVal);
    }
}
