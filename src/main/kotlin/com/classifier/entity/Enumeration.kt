package com.classifier.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "enumeration",
    indexes = [Index(name = "idx_enumeration_code", columnList = "code")]
)
class Enumeration(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var code: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enumeration_class_id", nullable = false)
    var enumerationClass: EnumerationClass,

    // Optional link to the classifier node this enumeration describes attributes for
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classifier_node_id")
    var classifierNode: ClassifierNode? = null,

    @OneToMany(mappedBy = "enumeration", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    val values: MutableList<EnumerationValue> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
