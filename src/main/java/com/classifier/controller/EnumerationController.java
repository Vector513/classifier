package com.classifier.controller;

import com.classifier.dto.*;
import com.classifier.service.EnumerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/enumerations")
@Tag(name = "Перечисления", description = "Управление перечислениями и их значениями (создание, редактирование, порядок)")
@RequiredArgsConstructor
public class EnumerationController {

    private final EnumerationService service;

    @GetMapping("/{id}")
    @Operation(summary = "Получить перечисление по ID вместе со всеми значениями")
    public EnumerationWithValuesResponse getById(@PathVariable Long id) {
        return service.toEnumerationWithValuesResponse(service.getEnumerationById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать новое перечисление заданного класса")
    public EnumerationResponse create(@Valid @RequestBody CreateEnumerationRequest request) {
        return service.toEnumerationResponse(service.createEnumeration(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Обновить перечисление")
    public EnumerationResponse update(@PathVariable Long id,
                                      @Valid @RequestBody UpdateEnumerationRequest request) {
        return service.toEnumerationResponse(service.updateEnumeration(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить перечисление (без значений)")
    public void delete(@PathVariable Long id) {
        service.deleteEnumeration(id);
    }

    @GetMapping("/{id}/values")
    @Operation(summary = "Получить список значений перечисления (в порядке сортировки)")
    public List<EnumerationValueResponse> getValues(@PathVariable Long id) {
        return service.getValues(id).stream()
                .map(service::toValueResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/values")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить новое значение в перечисление")
    public EnumerationValueResponse addValue(@PathVariable Long id,
                                             @Valid @RequestBody CreateEnumerationValueRequest request) {
        return service.toValueResponse(service.addValue(id, request));
    }

    @PatchMapping("/{id}/values/{valueId}")
    @Operation(summary = "Редактировать значение перечисления")
    public EnumerationValueResponse updateValue(@PathVariable Long id,
                                                @PathVariable Long valueId,
                                                @Valid @RequestBody UpdateEnumerationValueRequest request) {
        return service.toValueResponse(service.updateValue(valueId, request));
    }

    @DeleteMapping("/{id}/values/{valueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить значение перечисления")
    public void deleteValue(@PathVariable Long id, @PathVariable Long valueId) {
        service.deleteValue(valueId);
    }

    @PatchMapping("/{id}/values/{valueId}/reorder")
    @Operation(summary = "Изменить порядок расположения значения в списке")
    public EnumerationValueResponse reorderValue(@PathVariable Long id,
                                                 @PathVariable Long valueId,
                                                 @RequestBody ReorderValueRequest request) {
        service.reorderValue(valueId, request.getNewSortOrder());
        return service.toValueResponse(service.getValueById(valueId));
    }
}
