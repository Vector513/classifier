package com.classifier.service

import com.classifier.dto.UnitOfMeasureRequest
import com.classifier.entity.UnitOfMeasure
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.repository.UnitOfMeasureRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class UnitOfMeasureServiceTest {

    @Mock
    lateinit var unitRepo: UnitOfMeasureRepository

    @InjectMocks
    lateinit var service: UnitOfMeasureService

    @Test
    fun `create with unique code succeeds`() {
        val request = UnitOfMeasureRequest(code = "PCS", name = "штуки")

        whenever(unitRepo.existsByCode("PCS")).thenReturn(false)
        whenever(unitRepo.save(any<UnitOfMeasure>())).thenAnswer { it.arguments[0] }

        val result = service.create(request)

        assertEquals("PCS", result.code)
        assertEquals("штуки", result.name)
    }

    @Test
    fun `create with duplicate code throws`() {
        val request = UnitOfMeasureRequest(code = "PCS", name = "штуки")
        whenever(unitRepo.existsByCode("PCS")).thenReturn(true)

        assertThrows<DuplicateCodeException> { service.create(request) }
    }

    @Test
    fun `getById returns unit`() {
        val unit = UnitOfMeasure(id = 1, code = "PCS", name = "штуки")
        whenever(unitRepo.findById(1L)).thenReturn(Optional.of(unit))

        val result = service.getById(1)

        assertEquals("PCS", result.code)
    }

    @Test
    fun `getById throws when not found`() {
        whenever(unitRepo.findById(999L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.getById(999) }
    }

    @Test
    fun `update changes fields`() {
        val unit = UnitOfMeasure(id = 1, code = "PCS", name = "штуки")
        val request = UnitOfMeasureRequest(code = "KG", name = "килограммы")

        whenever(unitRepo.findById(1L)).thenReturn(Optional.of(unit))
        whenever(unitRepo.existsByCode("KG")).thenReturn(false)
        whenever(unitRepo.save(any<UnitOfMeasure>())).thenAnswer { it.arguments[0] }

        val result = service.update(1, request)

        assertEquals("KG", result.code)
        assertEquals("килограммы", result.name)
    }

    @Test
    fun `delete nonexistent throws`() {
        whenever(unitRepo.existsById(999L)).thenReturn(false)

        assertThrows<EntityNotFoundException> { service.delete(999) }
    }

    @Test
    fun `delete existing succeeds`() {
        whenever(unitRepo.existsById(1L)).thenReturn(true)

        service.delete(1)

        verify(unitRepo).deleteById(1L)
    }
}
