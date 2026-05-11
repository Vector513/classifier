package com.classifier.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * Значение числового параметра для конкретного узла (изделия).
 * Значение должно находиться в диапазоне [minValue, maxValue] параметра.
 * Уникальность: одно значение на пару (узел, параметр).
 */
@Entity
@Table(
    name = "node_numeric_value",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_node_numeric_value",
        columnNames = ["classifier_node_id", "numeric_parameter_id"]
    )],
    indexes = [
        Index(name = "idx_nnv_node_id", columnList = "classifier_node_id"),
        Index(name = "idx_nnv_param_id", columnList = "numeric_parameter_id")
    ]
)
class NodeNumericValue(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classifier_node_id", nullable = false)
    var classifierNode: ClassifierNode,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "numeric_parameter_id", nullable = false)
    var numericParameter: NumericParameter,

    @Column(nullable = false, precision = 19, scale = 6)
    var value: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
