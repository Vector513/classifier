package com.classifier.dto

/** null — все варианты, разрешённые родителем; непустой список — дополнительное сужение. */
data class EnumerationBindingRequest(val allowedValueIds: List<Long>? = null, val excluded: Boolean = false)
data class EnumerationBindingResponse(val nodeId: Long, val enumerationId: Long, val enumerationName: String,
    val restricted: Boolean, val allowedValueIds: List<Long>, val excluded: Boolean = false)
