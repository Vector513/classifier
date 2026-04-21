package com.classifier.service

import com.classifier.dto.SelectEnumerationValueRequest
import com.classifier.entity.ClassifierNode
import com.classifier.entity.Enumeration as EnumerationEntity
import com.classifier.entity.EnumerationClass
import com.classifier.entity.EnumerationValue
import com.classifier.entity.NodeAttributeValue
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.InvalidSelectionException
import com.classifier.repository.*
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
class NodeAttributeValueServiceTest {

    @Mock lateinit var navRepo: NodeAttributeValueRepository
    @Mock lateinit var nodeRepo: ClassifierNodeRepository
    @Mock lateinit var enumRepo: EnumerationRepository
    @Mock lateinit var valueRepo: EnumerationValueRepository

    @InjectMocks
    lateinit var service: NodeAttributeValueService

    private val cls = EnumerationClass(id = 1, code = "COLOR", name = "Цвет")
    private val node = ClassifierNode(id = 10, code = "PHONES", name = "Смартфоны", sortOrder = 0)
    private val enum = EnumerationEntity(id = 1, code = "PHONE-COLORS", name = "Цвета телефонов", enumerationClass = cls)
    private val blackValue = EnumerationValue(id = 1, code = "BLACK", name = "Чёрный", enumeration = enum, sortOrder = 0)
    private val whiteValue = EnumerationValue(id = 2, code = "WHITE", name = "Белый", enumeration = enum, sortOrder = 1)

    // ── selectValue ───────────────────────────────────────────────────────────

    @Test
    fun `selectValue creates new record when none exists`() {
        whenever(nodeRepo.findById(10L)).thenReturn(Optional.of(node))
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(enum))
        whenever(valueRepo.findById(1L)).thenReturn(Optional.of(blackValue))
        whenever(navRepo.findByClassifierNodeIdAndEnumerationId(10L, 1L)).thenReturn(null)
        whenever(navRepo.save(any<NodeAttributeValue>())).thenAnswer { it.arguments[0] }

        val result = service.selectValue(10, SelectEnumerationValueRequest(enumerationId = 1, valueId = 1))

        assertEquals(node, result.classifierNode)
        assertEquals(enum, result.enumeration)
        assertEquals(blackValue, result.selectedValue)
        verify(navRepo).save(any())
    }

    @Test
    fun `selectValue updates existing record`() {
        val existing = NodeAttributeValue(
            id = 5, classifierNode = node, enumeration = enum, selectedValue = blackValue
        )
        whenever(nodeRepo.findById(10L)).thenReturn(Optional.of(node))
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(enum))
        whenever(valueRepo.findById(2L)).thenReturn(Optional.of(whiteValue))
        whenever(navRepo.findByClassifierNodeIdAndEnumerationId(10L, 1L)).thenReturn(existing)
        whenever(navRepo.save(any<NodeAttributeValue>())).thenAnswer { it.arguments[0] }

        val result = service.selectValue(10, SelectEnumerationValueRequest(enumerationId = 1, valueId = 2))

        assertEquals(whiteValue, result.selectedValue)
    }

    @Test
    fun `selectValue throws when node not found`() {
        whenever(nodeRepo.findById(99L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            service.selectValue(99, SelectEnumerationValueRequest(enumerationId = 1, valueId = 1))
        }
    }

    @Test
    fun `selectValue throws when enumeration not found`() {
        whenever(nodeRepo.findById(10L)).thenReturn(Optional.of(node))
        whenever(enumRepo.findById(99L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            service.selectValue(10, SelectEnumerationValueRequest(enumerationId = 99, valueId = 1))
        }
    }

    @Test
    fun `selectValue throws when value not found`() {
        whenever(nodeRepo.findById(10L)).thenReturn(Optional.of(node))
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(enum))
        whenever(valueRepo.findById(99L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            service.selectValue(10, SelectEnumerationValueRequest(enumerationId = 1, valueId = 99))
        }
    }

    @Test
    fun `selectValue throws when value does not belong to enumeration`() {
        val otherEnum = EnumerationEntity(id = 2, code = "SIZES", name = "Размеры", enumerationClass = cls)
        val sizeValue = EnumerationValue(id = 10, code = "XL", name = "XL", enumeration = otherEnum, sortOrder = 0)

        whenever(nodeRepo.findById(10L)).thenReturn(Optional.of(node))
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(enum))
        whenever(valueRepo.findById(10L)).thenReturn(Optional.of(sizeValue))

        assertThrows<InvalidSelectionException> {
            service.selectValue(10, SelectEnumerationValueRequest(enumerationId = 1, valueId = 10))
        }
    }

    // ── getNodeAttributes ─────────────────────────────────────────────────────

    @Test
    fun `getNodeAttributes returns list for existing node`() {
        val nav = NodeAttributeValue(classifierNode = node, enumeration = enum, selectedValue = blackValue)
        whenever(nodeRepo.existsById(10L)).thenReturn(true)
        whenever(navRepo.findByClassifierNodeIdOrderByEnumerationId(10L)).thenReturn(listOf(nav))

        val result = service.getNodeAttributes(10)

        assertEquals(1, result.size)
        assertEquals(blackValue, result[0].selectedValue)
    }

    @Test
    fun `getNodeAttributes throws when node not found`() {
        whenever(nodeRepo.existsById(99L)).thenReturn(false)

        assertThrows<EntityNotFoundException> { service.getNodeAttributes(99) }
    }

    // ── getNodeAttribute ──────────────────────────────────────────────────────

    @Test
    fun `getNodeAttribute returns record`() {
        val nav = NodeAttributeValue(classifierNode = node, enumeration = enum, selectedValue = blackValue)
        whenever(navRepo.findByClassifierNodeIdAndEnumerationId(10L, 1L)).thenReturn(nav)

        val result = service.getNodeAttribute(10, 1)

        assertEquals(blackValue, result.selectedValue)
    }

    @Test
    fun `getNodeAttribute throws when not found`() {
        whenever(navRepo.findByClassifierNodeIdAndEnumerationId(10L, 99L)).thenReturn(null)

        assertThrows<EntityNotFoundException> { service.getNodeAttribute(10, 99) }
    }

    // ── clearNodeAttribute ────────────────────────────────────────────────────

    @Test
    fun `clearNodeAttribute deletes existing record`() {
        whenever(navRepo.existsByClassifierNodeIdAndEnumerationId(10L, 1L)).thenReturn(true)

        service.clearNodeAttribute(10, 1)

        verify(navRepo).deleteByClassifierNodeIdAndEnumerationId(10L, 1L)
    }

    @Test
    fun `clearNodeAttribute throws when record not found`() {
        whenever(navRepo.existsByClassifierNodeIdAndEnumerationId(10L, 99L)).thenReturn(false)

        assertThrows<EntityNotFoundException> { service.clearNodeAttribute(10, 99) }
    }
}
