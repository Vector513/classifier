package com.classifier.dto

import java.time.Instant

data class ValidationResponse(
    val valid: Boolean,
    val cycles: List<List<Long>>
)

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: Instant
)
