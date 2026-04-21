package com.classifier.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Выбранное значение перечисления для конкретного узла классификатора.
 * Связывает: узел → перечисление → одно выбранное значение.
 * Уникальное ограничение: один узел может иметь только одно выбранное
 * значение для каждого перечисления.
 */
@Entity
@Table(
    name = "node_attribute_value",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_node_enumeration",
        columnNames = ["classifier_node_id", "enumeration_id"]
    )],
    indexes = [
        Index(name = "idx_nav_node_id", columnList = "classifier_node_id"),
        Index(name = "idx_nav_enum_id", columnList = "enumeration_id")
    ]
)
class NodeAttributeValue(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classifier_node_id", nullable = false)
    var classifierNode: ClassifierNode,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enumeration_id", nullable = false)
    var enumeration: Enumeration,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enumeration_value_id", nullable = false)
    var selectedValue: EnumerationValue,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
