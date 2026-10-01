package com.classifier.controller;

import com.classifier.service.CatalogService;
import com.classifier.service.NumericParameterService;
import com.classifier.service.EnumerationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/parameter-options")
public class ParameterOptionsController {
    private final CatalogService catalog;
    private final NumericParameterService numbers;
    private final EnumerationService enums;
    public ParameterOptionsController(CatalogService catalog, NumericParameterService numbers, EnumerationService enums) {
        this.catalog=catalog; this.numbers=numbers; this.enums=enums;
    }
    @GetMapping
    @Transactional(readOnly = true)
    public Map<String,Object> get(@PathVariable Long nodeId) {
        return Map.of("numericParameters",catalog.scopeParameterIds(nodeId,true).stream().map(numbers::getById).toList(),
            "enumerations",catalog.scopeParameterIds(nodeId,false).stream()
                .map(id -> enums.toEnumerationWithValuesResponse(enums.getEnumerationById(id))).toList());
    }
}
