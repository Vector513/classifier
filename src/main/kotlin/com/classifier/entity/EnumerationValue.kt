package com.classifier.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "enumeration_value",
    indexes = [Index(name = "idx_enum_value_enum_id", columnList = "enumeration_id, sort_order")]
)
class EnumerationValue(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var code: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enumeration_id", nullable = false)
    var enumeration: Enumeration,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
