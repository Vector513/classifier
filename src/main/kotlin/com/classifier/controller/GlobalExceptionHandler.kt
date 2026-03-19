package com.classifier.controller

import com.classifier.dto.ErrorResponse
import com.classifier.exception.CyclicMoveException
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.HasChildrenException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: EntityNotFoundException) = ErrorResponse(
        status = 404,
        error = "Not Found",
        message = ex.message ?: "Ресурс не найден",
        timestamp = Instant.now()
    )

    @ExceptionHandler(DuplicateCodeException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleDuplicateCode(ex: DuplicateCodeException) = ErrorResponse(
        status = 409,
        error = "Conflict",
        message = ex.message ?: "Дублирующий код",
        timestamp = Instant.now()
    )

    @ExceptionHandler(HasChildrenException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleHasChildren(ex: HasChildrenException) = ErrorResponse(
        status = 409,
        error = "Conflict",
        message = ex.message ?: "Вершина имеет потомков",
        timestamp = Instant.now()
    )

    @ExceptionHandler(CyclicMoveException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleCyclicMove(ex: CyclicMoveException) = ErrorResponse(
        status = 400,
        error = "Bad Request",
        message = ex.message ?: "Циклическое перемещение",
        timestamp = Instant.now()
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleValidation(ex: MethodArgumentNotValidException): ErrorResponse {
        val errors = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage}"
        }
        return ErrorResponse(
            status = 422,
            error = "Unprocessable Entity",
            message = errors,
            timestamp = Instant.now()
        )
    }
}
