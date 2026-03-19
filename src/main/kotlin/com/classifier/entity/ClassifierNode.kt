package com.classifier.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "classifier_node",
    indexes = [
        Index(name = "idx_node_parent_id", columnList = "parent_id"),
        Index(name = "idx_node_sort_order", columnList = "parent_id, sort_order")
    ]
)
class ClassifierNode(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var code: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: ClassifierNode? = null,

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    val children: MutableList<ClassifierNode> = mutableListOf(),

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id")
    var unitOfMeasure: UnitOfMeasure? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
