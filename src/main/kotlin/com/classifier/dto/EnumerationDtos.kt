package com.classifier.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

// ── Requests ──────────────────────────────────────────────────────────────────

@Schema(description = "Запрос на создание класса перечисления")
data class CreateEnumerationClassRequest(
    @Schema(description = "Уникальный код класса", example = "COLOR")
    @field:NotBlank @field:Size(max = 100) val code: String,

    @Schema(description = "Название класса", example = "Цвет")
    @field:NotBlank @field:Size(max = 255) val name: String,

    @Schema(description = "Описание класса", example = "Цветовая гамма изделия")
    @field:Size(max = 1000) val description: String? = null
)

@Schema(description = "Запрос на обновление класса перечисления (все поля опциональны)")
data class UpdateEnumerationClassRequest(
    @Schema(description = "Новый код класса", example = "COLOR")
    @field:Size(max = 100) val code: String? = null,

    @Schema(description = "Новое название класса", example = "Цветовая гамма")
    @field:Size(max = 255) val name: String? = null,

    @Schema(description = "Новое описание класса")
    @field:Size(max = 1000) val description: String? = null
)

@Schema(description = "Запрос на создание перечисления")
data class CreateEnumerationRequest(
    @Schema(description = "Уникальный код перечисления", example = "PHONE-COLORS")
    @field:NotBlank @field:Size(max = 100) val code: String,

    @Schema(description = "Название перечисления", example = "Цвета смартфонов")
    @field:NotBlank @field:Size(max = 255) val name: String,

    @Schema(description = "ID класса перечисления", example = "1")
    val enumerationClassId: Long,

    @Schema(description = "ID узла классификатора, к которому привязано перечисление", example = "2")
    val classifierNodeId: Long? = null
)

@Schema(description = "Запрос на обновление перечисления (все поля опциональны)")
data class UpdateEnumerationRequest(
    @Schema(description = "Новый код перечисления", example = "PHONE-COLORS")
    @field:Size(max = 100) val code: String? = null,

    @Schema(description = "Новое название перечисления", example = "Цвета телефонов")
    @field:Size(max = 255) val name: String? = null,

    @Schema(description = "ID узла классификатора", example = "2")
    val classifierNodeId: Long? = null
)

@Schema(description = "Запрос на добавление значения в перечисление")
data class CreateEnumerationValueRequest(
    @Schema(description = "Код значения (уникален внутри перечисления)", example = "BLACK")
    @field:NotBlank @field:Size(max = 100) val code: String,

    @Schema(description = "Отображаемое название значения", example = "Чёрный")
    @field:NotBlank @field:Size(max = 255) val name: String
)

@Schema(description = "Запрос на редактирование значения перечисления")
data class UpdateEnumerationValueRequest(
    @Schema(description = "Новый код значения", example = "BLACK")
    @field:Size(max = 100) val code: String? = null,

    @Schema(description = "Новое название значения", example = "Чёрный матовый")
    @field:Size(max = 255) val name: String? = null
)

@Schema(description = "Запрос на изменение порядка значения в списке")
data class ReorderValueRequest(
    @Schema(description = "Новая позиция (0 = первый)", example = "2")
    val newSortOrder: Int
)

// ── Responses ─────────────────────────────────────────────────────────────────

@Schema(description = "Класс перечисления")
data class EnumerationClassResponse(
    @Schema(description = "Идентификатор") val id: Long,
    @Schema(description = "Уникальный код", example = "COLOR") val code: String,
    @Schema(description = "Название", example = "Цвет") val name: String,
    @Schema(description = "Описание") val description: String?,
    @Schema(description = "Количество перечислений в классе") val enumerationCount: Int,
    @Schema(description = "Дата создания") val createdAt: Instant,
    @Schema(description = "Дата последнего обновления") val updatedAt: Instant
)

@Schema(description = "Перечисление (без значений)")
data class EnumerationResponse(
    @Schema(description = "Идентификатор") val id: Long,
    @Schema(description = "Уникальный код", example = "PHONE-COLORS") val code: String,
    @Schema(description = "Название", example = "Цвета смартфонов") val name: String,
    @Schema(description = "ID класса перечисления") val enumerationClassId: Long,
    @Schema(description = "Название класса перечисления", example = "Цвет") val enumerationClassName: String,
    @Schema(description = "ID узла классификатора (если привязано)") val classifierNodeId: Long?,
    @Schema(description = "Название узла классификатора") val classifierNodeName: String?,
    @Schema(description = "Количество значений") val valueCount: Int,
    @Schema(description = "Дата создания") val createdAt: Instant,
    @Schema(description = "Дата последнего обновления") val updatedAt: Instant
)

@Schema(description = "Перечисление со списком значений")
data class EnumerationWithValuesResponse(
    @Schema(description = "Идентификатор") val id: Long,
    @Schema(description = "Уникальный код", example = "PHONE-COLORS") val code: String,
    @Schema(description = "Название", example = "Цвета смартфонов") val name: String,
    @Schema(description = "ID класса перечисления") val enumerationClassId: Long,
    @Schema(description = "Название класса перечисления", example = "Цвет") val enumerationClassName: String,
    @Schema(description = "ID узла классификатора (если привязано)") val classifierNodeId: Long?,
    @Schema(description = "Название узла классификатора") val classifierNodeName: String?,
    @Schema(description = "Значения перечисления в порядке сортировки") val values: List<EnumerationValueResponse>,
    @Schema(description = "Дата создания") val createdAt: Instant,
    @Schema(description = "Дата последнего обновления") val updatedAt: Instant
)

@Schema(description = "Значение перечисления")
data class EnumerationValueResponse(
    @Schema(description = "Идентификатор") val id: Long,
    @Schema(description = "Код значения", example = "BLACK") val code: String,
    @Schema(description = "Название значения", example = "Чёрный") val name: String,
    @Schema(description = "ID перечисления") val enumerationId: Long,
    @Schema(description = "Порядковый номер (0 = первый)") val sortOrder: Int,
    @Schema(description = "Дата создания") val createdAt: Instant,
    @Schema(description = "Дата последнего обновления") val updatedAt: Instant
)
