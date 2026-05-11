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

@RestController
@RequestMapping("/api/v1/numeric-parameters")
@Tag(name = "Числовые параметры", description = "Управление числовыми параметрами изделий")
@RequiredArgsConstructor
public class NumericParameterController {

    private final NumericParameterService service;

    @GetMapping
    @Operation(summary = "Получить все числовые параметры")
    public List<NumericParameterResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить числовой параметр по ID")
    public NumericParameterResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Создать числовой параметр",
        description = "Создаёт новый числовой параметр с опциональными ограничениями min/max и единицей измерения."
    )
    public NumericParameterResponse create(@RequestBody CreateNumericParameterRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Обновить числовой параметр (partial update)")
    public NumericParameterResponse update(@PathVariable Long id,
                                           @RequestBody UpdateNumericParameterRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Удалить числовой параметр",
        description = "Удаление невозможно, если параметр назначен хотя бы на один узел классификатора."
    )
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
