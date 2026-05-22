package com.classifier.controller;

import com.classifier.dto.*;
import com.classifier.service.EnumerationService;
import com.classifier.service.ItemSearchService;
import com.classifier.service.NodeAttributeValueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Поиск и анализ изделий", description = "Поиск с параметрами, наследование перечислений, фильтрация и агрегаты")
@RequiredArgsConstructor
public class ItemQueryController {

    private final ItemSearchService searchService;
    private final EnumerationService enumerationService;
    private final NodeAttributeValueService navService;

    // ─── Поиск с параметрами ──────────────────────────────────────────────────

    @GetMapping("/api/v1/items/search")
    @Operation(
        summary = "Поиск изделий по коду или названию с возвратом всех параметров",
        description = "Для каждого найденного узла возвращаются выбранные значения " +
                      "перечислимых параметров и числовые значения."
    )
    public List<NodeWithParametersResponse> search(
            @Parameter(description = "Строка поиска (по коду или названию)") @RequestParam String query) {
        return searchService.searchWithParameters(query);
    }

    @GetMapping("/api/v1/items/{nodeId}/parameters")
    @Operation(
        summary = "Получить узел со всеми значениями параметров",
        description = "Возвращает один узел с полным набором его перечислимых и числовых параметров."
    )
    public NodeWithParametersResponse getWithParameters(@PathVariable Long nodeId) {
        return searchService.getNodeWithParameters(nodeId);
    }

    // ─── Наследование перечислений ────────────────────────────────────────────

    @GetMapping("/api/v1/nodes/{nodeId}/enumerations/effective")
    @Operation(
        summary = "Эффективные перечисления узла (собственные + унаследованные от предков)",
        description = "Возвращает все перечисления, применимые к данному узлу. " +
                      "Поле isInherited=true — перечисление определено в одном из предков."
    )
    public List<EffectiveEnumerationResponse> getEffectiveEnumerations(@PathVariable Long nodeId) {
        return enumerationService.getEffectiveEnumerations(nodeId);
    }

    // ─── Фильтрация по перечислению ───────────────────────────────────────────

    @GetMapping("/api/v1/nodes/{classNodeId}/filter/enum")
    @Operation(
        summary = "Отбор изделий класса по значению перечислимого параметра",
        description = "Возвращает все узлы в поддереве classNodeId, у которых " +
                      "выбрано указанное значение (valueId) указанного перечисления (enumerationId)."
    )
    public List<NodeAttributeValueResponse> filterByEnumValue(
            @PathVariable Long classNodeId,
            @Parameter(description = "ID перечисления") @RequestParam Long enumerationId,
            @Parameter(description = "ID значения перечисления") @RequestParam Long valueId) {
        return navService.filterByEnumerationValue(classNodeId, enumerationId, valueId);
    }

    // ─── Фильтрация по нескольким параметрам одновременно ─────────────────────

    @PostMapping("/api/v1/nodes/{nodeId}/filter")
    @Operation(
        summary = "Отбор изделий по нескольким параметрам одновременно",
        description = "Возвращает изделия поддерева узла nodeId, удовлетворяющие сразу всем " +
                      "переданным условиям (логика «И»). Условия задаются по числовым параметрам " +
                      "(диапазон значений) и по перечислимым параметрам (требуемое значение); " +
                      "количество условий произвольное."
    )
    public List<NodeWithParametersResponse> multiFilter(
            @PathVariable Long nodeId,
            @RequestBody MultiFilterRequest request) {
        return searchService.multiFilter(nodeId, request);
    }

    // ─── Агрегаты по перечислению ─────────────────────────────────────────────

    @GetMapping("/api/v1/nodes/{classNodeId}/aggregates/enum/{enumerationId}")
    @Operation(
        summary = "Агрегаты по перечислимому параметру в поддереве",
        description = "Считает количество изделий для каждого значения перечисления " +
                      "во всём поддереве classNodeId. Удобно для анализа: " +
                      "сколько смартфонов чёрного/белого цвета и т.п."
    )
    public EnumerationAggregatesResponse getEnumAggregates(
            @PathVariable Long classNodeId,
            @PathVariable Long enumerationId) {
        return navService.getEnumerationAggregates(classNodeId, enumerationId);
    }
}
