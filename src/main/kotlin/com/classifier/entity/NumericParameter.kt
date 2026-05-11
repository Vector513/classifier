package com.classifier.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * Тип числового параметра изделия.
 * Хранит код, название, необязательные ограничения (min/max) и единицу измерения.
 */
@Entity
@Table(
    name = "numeric_parameter",
    indexes = [Index(name = "idx_num_param_code", columnList = "code")]
)
class NumericParameter(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var code: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(length = 1000)
    var description: String? = null,

    /** Нижняя граница допустимых значений (включительно). null — ограничения нет. */
    @Column(name = "min_value", precision = 19, scale = 6)
    var minValue: BigDecimal? = null,

    /** Верхняя граница допустимых значений (включительно). null — ограничения нет. */
    @Column(name = "max_value", precision = 19, scale = 6)
    var maxValue: BigDecimal? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id")
    var unitOfMeasure: UnitOfMeasure? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
