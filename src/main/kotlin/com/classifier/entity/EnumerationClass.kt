package com.classifier.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "enumeration_class",
    indexes = [Index(name = "idx_enum_class_code", columnList = "code")]
)
class EnumerationClass(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var code: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(length = 1000)
    var description: String? = null,

    @OneToMany(mappedBy = "enumerationClass", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val enumerations: MutableList<Enumeration> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
