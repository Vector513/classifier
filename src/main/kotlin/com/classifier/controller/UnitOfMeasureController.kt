package com.classifier.controller

import com.classifier.dto.UnitOfMeasureRequest
import com.classifier.dto.UnitOfMeasureResponse
import com.classifier.mapper.NodeMapper
import com.classifier.service.UnitOfMeasureService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/units")
@Tag(name = "Единицы измерения", description = "CRUD для единиц измерения")
class UnitOfMeasureController(
    private val unitService: UnitOfMeasureService,
    private val mapper: NodeMapper
) {

    @GetMapping
    @Operation(summary = "Получить все единицы измерения")
    fun getAll(): List<UnitOfMeasureResponse> =
        unitService.getAll().map { mapper.toResponse(it) }

    @GetMapping("/{id}")
    @Operation(summary = "Получить единицу измерения по ID")
    fun getById(@PathVariable id: Long): UnitOfMeasureResponse =
        mapper.toResponse(unitService.getById(id))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать единицу измерения")
    fun create(@Valid @RequestBody request: UnitOfMeasureRequest): UnitOfMeasureResponse =
        mapper.toResponse(unitService.create(request))

    @PutMapping("/{id}")
    @Operation(summary = "Обновить единицу измерения")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UnitOfMeasureRequest): UnitOfMeasureResponse =
        mapper.toResponse(unitService.update(id, request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить единицу измерения")
    fun delete(@PathVariable id: Long) = unitService.delete(id)
}
