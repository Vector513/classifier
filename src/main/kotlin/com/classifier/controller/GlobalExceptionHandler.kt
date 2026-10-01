package com.classifier.controller

import com.classifier.dto.ErrorResponse
import com.classifier.exception.CyclicMoveException
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.HasChildrenException
import com.classifier.exception.InvalidSelectionException
import com.classifier.exception.ValueOutOfRangeException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleIntegrity(ex: org.springframework.dao.DataIntegrityViolationException) = ErrorResponse(
        status = 409, error = "Conflict",
        message = constraintMessage(ex),
        timestamp = Instant.now()
    )

    private fun constraintMessage(ex: Throwable): String {
        var cause: Throwable? = ex
        while (cause != null) {
            if (cause is java.sql.SQLException && cause.sqlState == "23514")
                return cause.message.orEmpty().substringBefore('\n').removePrefix("ERROR:").removePrefix("ОШИБКА:").trim()
            cause = cause.cause
        }
        return "Операция нарушает связи данных: запись используется или значение не соответствует ограничению"
    }

    @ExceptionHandler(org.springframework.transaction.TransactionSystemException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleTransaction(ex: org.springframework.transaction.TransactionSystemException) = ErrorResponse(
        status=409, error="Conflict", message=constraintMessage(ex), timestamp=Instant.now()
    )

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

    @ExceptionHandler(InvalidSelectionException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleInvalidSelection(ex: InvalidSelectionException) = ErrorResponse(
        status = 422,
        error = "Unprocessable Entity",
        message = ex.message ?: "Некорректный выбор значения",
        timestamp = Instant.now()
    )

    @ExceptionHandler(ValueOutOfRangeException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleValueOutOfRange(ex: ValueOutOfRangeException) = ErrorResponse(
        status = 422,
        error = "Unprocessable Entity",
        message = ex.message ?: "Значение выходит за допустимый диапазон",
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
