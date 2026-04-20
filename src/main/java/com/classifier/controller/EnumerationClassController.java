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
@RequestMapping("/api/v1/enumeration-classes")
@Tag(name = "Классы перечислений", description = "Управление классификатором типов перечислений (Цвет, Размер, ОС…)")
@RequiredArgsConstructor
public class EnumerationClassController {

    private final EnumerationService service;

    @GetMapping
    @Operation(summary = "Получить все классы перечислений")
    public List<EnumerationClassResponse> getAll() {
        return service.getAllClasses().stream()
                .map(service::toClassResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить класс перечисления по ID")
    public EnumerationClassResponse getById(@PathVariable Long id) {
        return service.toClassResponse(service.getClassById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать новый класс перечисления")
    public EnumerationClassResponse create(@Valid @RequestBody CreateEnumerationClassRequest request) {
        return service.toClassResponse(service.createClass(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Обновить класс перечисления")
    public EnumerationClassResponse update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateEnumerationClassRequest request) {
        return service.toClassResponse(service.updateClass(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить класс перечисления (без привязанных перечислений)")
    public void delete(@PathVariable Long id) {
        service.deleteClass(id);
    }

    @GetMapping("/{id}/enumerations")
    @Operation(summary = "Получить все перечисления данного класса")
    public List<EnumerationResponse> getEnumerations(@PathVariable Long id) {
        return service.getEnumerationsByClass(id).stream()
                .map(service::toEnumerationResponse)
                .collect(Collectors.toList());
    }
}
