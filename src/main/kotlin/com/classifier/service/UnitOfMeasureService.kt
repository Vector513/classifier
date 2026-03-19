package com.classifier.service

import com.classifier.dto.UnitOfMeasureRequest
import com.classifier.entity.UnitOfMeasure
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.repository.UnitOfMeasureRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UnitOfMeasureService(
    private val unitRepo: UnitOfMeasureRepository
) {

    fun getAll(): List<UnitOfMeasure> = unitRepo.findAll()

    fun getById(id: Long): UnitOfMeasure =
        unitRepo.findById(id).orElseThrow {
            EntityNotFoundException("Единица измерения с id=$id не найдена")
        }

    fun create(request: UnitOfMeasureRequest): UnitOfMeasure {
        if (unitRepo.existsByCode(request.code)) {
            throw DuplicateCodeException("Единица измерения с кодом '${request.code}' уже существует")
        }
        return unitRepo.save(UnitOfMeasure(code = request.code, name = request.name))
    }

    fun update(id: Long, request: UnitOfMeasureRequest): UnitOfMeasure {
        val unit = getById(id)
        if (unit.code != request.code && unitRepo.existsByCode(request.code)) {
            throw DuplicateCodeException("Единица измерения с кодом '${request.code}' уже существует")
        }
        unit.code = request.code
        unit.name = request.name
        return unitRepo.save(unit)
    }

    fun delete(id: Long) {
        if (!unitRepo.existsById(id)) {
            throw EntityNotFoundException("Единица измерения с id=$id не найдена")
        }
        unitRepo.deleteById(id)
    }
}
