package com.classifier.controller;

import com.classifier.dto.UnitOfMeasureRequest;
import com.classifier.dto.UnitOfMeasureResponse;
import com.classifier.mapper.NodeMapper;
import com.classifier.service.UnitOfMeasureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/units")
@Tag(name = "Единицы измерения", description = "CRUD для единиц измерения")
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitService;
    private final NodeMapper mapper;

    @Autowired
    public UnitOfMeasureController(UnitOfMeasureService unitService, NodeMapper mapper) {
        this.unitService = unitService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Получить все единицы измерения")
    public List<UnitOfMeasureResponse> getAll() {
        return unitService.getAll().stream()
            .map(mapper::toResponse)
            .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить единицу измерения по ID")
    public UnitOfMeasureResponse getById(@PathVariable Long id) {
        return mapper.toResponse(unitService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать единицу измерения")
    public UnitOfMeasureResponse create(@Valid @RequestBody UnitOfMeasureRequest request) {
        return mapper.toResponse(unitService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить единицу измерения")
    public UnitOfMeasureResponse update(@PathVariable Long id,
    @Valid @RequestBody UnitOfMeasureRequest request) {
        return mapper.toResponse(unitService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить единицу измерения")
    public void delete(@PathVariable Long id) {
        unitService.delete(id);
    }
}
