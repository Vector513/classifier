package com.classifier.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Назначение числового параметра на узел классификатора (класс изделий).
 * Определяет, какие числовые параметры применимы к данному классу и его потомкам.
 * Уникальность: один узел не может иметь дублирующих параметров одного типа.
 */
@Entity
@Table(
    name = "node_numeric_parameter",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_node_numeric_param",
        columnNames = ["classifier_node_id", "numeric_parameter_id"]
    )],
    indexes = [
        Index(name = "idx_nnp_node_id", columnList = "classifier_node_id"),
        Index(name = "idx_nnp_param_id", columnList = "numeric_parameter_id")
    ]
)
class NodeNumericParameter(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classifier_node_id", nullable = false)
    var classifierNode: ClassifierNode,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "numeric_parameter_id", nullable = false)
    var numericParameter: NumericParameter,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
