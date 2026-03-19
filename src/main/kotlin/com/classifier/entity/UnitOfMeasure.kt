package com.classifier.entity

import jakarta.persistence.*

@Entity
@Table(name = "unit_of_measure")
class UnitOfMeasure(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 50)
    var code: String,

    @Column(nullable = false, length = 255)
    var name: String
)
