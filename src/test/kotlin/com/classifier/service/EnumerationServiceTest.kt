package com.classifier.service

import com.classifier.dto.*
import com.classifier.entity.Enumeration
import com.classifier.entity.EnumerationClass
import com.classifier.entity.EnumerationValue
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.HasChildrenException
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.EnumerationClassRepository
import com.classifier.repository.EnumerationRepository
import com.classifier.repository.EnumerationValueRepository
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
class EnumerationServiceTest {

    @Mock lateinit var classRepo: EnumerationClassRepository
    @Mock lateinit var enumRepo: EnumerationRepository
    @Mock lateinit var valueRepo: EnumerationValueRepository
    @Mock lateinit var nodeRepo: ClassifierNodeRepository

    @InjectMocks
    lateinit var service: EnumerationService

    private fun cls(id: Long = 1, code: String = "COLOR", name: String = "Цвет") =
        EnumerationClass(id = id, code = code, name = name)

    private fun enum(id: Long = 1, code: String = "PHONE-COLORS", cls: EnumerationClass = cls()) =
        Enumeration(id = id, code = code, name = "Цвета телефонов", enumerationClass = cls)

    private fun value(id: Long = 1, code: String = "BLACK", enum: Enumeration = enum(), sortOrder: Int = 0) =
        EnumerationValue(id = id, code = code, name = "Чёрный", enumeration = enum, sortOrder = sortOrder)

    // ── EnumerationClass ──────────────────────────────────────────────────────

    @Test
    fun `createClass succeeds with unique code`() {
        whenever(classRepo.existsByCode("COLOR")).thenReturn(false)
        whenever(classRepo.save(any<EnumerationClass>())).thenAnswer { it.arguments[0] }

        val result = service.createClass(CreateEnumerationClassRequest(code = "COLOR", name = "Цвет"))

        assertEquals("COLOR", result.code)
    }

    @Test
    fun `createClass throws on duplicate code`() {
        whenever(classRepo.existsByCode("COLOR")).thenReturn(true)

        assertThrows<DuplicateCodeException> {
            service.createClass(CreateEnumerationClassRequest(code = "COLOR", name = "Цвет"))
        }
    }

    @Test
    fun `getClassById throws when not found`() {
        whenever(classRepo.findById(99L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.getClassById(99) }
    }

    @Test
    fun `updateClass changes name`() {
        val cls = cls()
        whenever(classRepo.findById(1L)).thenReturn(Optional.of(cls))
        whenever(classRepo.save(any<EnumerationClass>())).thenAnswer { it.arguments[0] }

        val result = service.updateClass(1, UpdateEnumerationClassRequest(name = "Цветовая гамма"))

        assertEquals("Цветовая гамма", result.name)
    }

    @Test
    fun `updateClass throws on duplicate code`() {
        val cls = cls(code = "COLOR")
        whenever(classRepo.findById(1L)).thenReturn(Optional.of(cls))
        whenever(classRepo.existsByCode("OS")).thenReturn(true)

        assertThrows<DuplicateCodeException> {
            service.updateClass(1, UpdateEnumerationClassRequest(code = "OS"))
        }
    }

    @Test
    fun `deleteClass throws when it has enumerations`() {
        val cls = cls()
        cls.enumerations.add(enum(cls = cls))
        whenever(classRepo.findById(1L)).thenReturn(Optional.of(cls))

        assertThrows<HasChildrenException> { service.deleteClass(1) }
    }

    @Test
    fun `deleteClass succeeds when empty`() {
        val cls = cls()
        whenever(classRepo.findById(1L)).thenReturn(Optional.of(cls))

        service.deleteClass(1)

        verify(classRepo).delete(cls)
    }

    // ── Enumeration ───────────────────────────────────────────────────────────

    @Test
    fun `createEnumeration succeeds`() {
        val cls = cls()
        whenever(enumRepo.existsByCode("PHONE-COLORS")).thenReturn(false)
        whenever(classRepo.findById(1L)).thenReturn(Optional.of(cls))
        whenever(enumRepo.save(any<Enumeration>())).thenAnswer { it.arguments[0] }

        val result = service.createEnumeration(
            CreateEnumerationRequest(code = "PHONE-COLORS", name = "Цвета", enumerationClassId = 1)
        )

        assertEquals("PHONE-COLORS", result.code)
        assertEquals(cls, result.enumerationClass)
    }

    @Test
    fun `createEnumeration throws on duplicate code`() {
        whenever(enumRepo.existsByCode("PHONE-COLORS")).thenReturn(true)

        assertThrows<DuplicateCodeException> {
            service.createEnumeration(
                CreateEnumerationRequest(code = "PHONE-COLORS", name = "Цвета", enumerationClassId = 1)
            )
        }
    }

    @Test
    fun `deleteEnumeration throws when it has values`() {
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(enum()))
        whenever(valueRepo.countByEnumerationId(1L)).thenReturn(3L)

        assertThrows<HasChildrenException> { service.deleteEnumeration(1) }
    }

    @Test
    fun `deleteEnumeration succeeds when empty`() {
        val e = enum()
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(e))
        whenever(valueRepo.countByEnumerationId(1L)).thenReturn(0L)

        service.deleteEnumeration(1)

        verify(enumRepo).delete(e)
    }

    // ── EnumerationValue ──────────────────────────────────────────────────────

    @Test
    fun `addValue appends with correct sort order`() {
        val e = enum()
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(e))
        whenever(valueRepo.existsByEnumerationIdAndCode(1L, "BLACK")).thenReturn(false)
        whenever(valueRepo.countByEnumerationId(1L)).thenReturn(2L)
        whenever(valueRepo.save(any<EnumerationValue>())).thenAnswer { it.arguments[0] }
        whenever(enumRepo.save(any<Enumeration>())).thenAnswer { it.arguments[0] }

        val result = service.addValue(1, CreateEnumerationValueRequest(code = "BLACK", name = "Чёрный"))

        assertEquals("BLACK", result.code)
        assertEquals(2, result.sortOrder)
    }

    @Test
    fun `addValue throws on duplicate code within enumeration`() {
        whenever(enumRepo.findById(1L)).thenReturn(Optional.of(enum()))
        whenever(valueRepo.existsByEnumerationIdAndCode(1L, "BLACK")).thenReturn(true)

        assertThrows<DuplicateCodeException> {
            service.addValue(1, CreateEnumerationValueRequest(code = "BLACK", name = "Чёрный"))
        }
    }

    @Test
    fun `updateValue changes name`() {
        val v = value()
        whenever(valueRepo.findById(1L)).thenReturn(Optional.of(v))
        whenever(valueRepo.save(any<EnumerationValue>())).thenAnswer { it.arguments[0] }

        val result = service.updateValue(1, UpdateEnumerationValueRequest(name = "Чёрный матовый"))

        assertEquals("Чёрный матовый", result.name)
    }

    @Test
    fun `reorderValue moves up correctly`() {
        val e = enum()
        val v0 = value(id = 1, code = "BLACK", enum = e, sortOrder = 0)
        val v1 = value(id = 2, code = "WHITE", enum = e, sortOrder = 1)
        val v2 = value(id = 3, code = "GOLD", enum = e, sortOrder = 2)

        whenever(valueRepo.findById(1L)).thenReturn(Optional.of(v0))
        whenever(valueRepo.findByEnumerationIdOrderBySortOrder(e.id)).thenReturn(listOf(v0, v1, v2))
        whenever(valueRepo.saveAll(any<List<EnumerationValue>>())).thenAnswer { it.arguments[0] }

        service.reorderValue(1, 2)

        // BLACK moved from 0 → 2; WHITE and GOLD should shift down by 1
        assertEquals(2, v0.sortOrder)
        assertEquals(0, v1.sortOrder)
        assertEquals(1, v2.sortOrder)
    }

    @Test
    fun `reorderValue clamps to valid range`() {
        val e = enum()
        val v0 = value(id = 1, code = "BLACK", enum = e, sortOrder = 0)
        val v1 = value(id = 2, code = "WHITE", enum = e, sortOrder = 1)

        whenever(valueRepo.findById(1L)).thenReturn(Optional.of(v0))
        whenever(valueRepo.findByEnumerationIdOrderBySortOrder(e.id)).thenReturn(listOf(v0, v1))
        whenever(valueRepo.saveAll(any<List<EnumerationValue>>())).thenAnswer { it.arguments[0] }

        // Target 99, but max index is 1
        service.reorderValue(1, 99)

        assertEquals(1, v0.sortOrder)
    }

    @Test
    fun `deleteValue shifts remaining sort orders`() {
        val e = enum()
        val v0 = value(id = 1, code = "BLACK", enum = e, sortOrder = 0)
        val v1 = value(id = 2, code = "WHITE", enum = e, sortOrder = 1)
        val v2 = value(id = 3, code = "GOLD", enum = e, sortOrder = 2)

        whenever(valueRepo.findById(1L)).thenReturn(Optional.of(v0))
        whenever(valueRepo.findByEnumerationIdOrderBySortOrder(e.id)).thenReturn(listOf(v0, v1, v2))
        whenever(valueRepo.saveAll(any<List<EnumerationValue>>())).thenAnswer { it.arguments[0] }

        service.deleteValue(1)

        verify(valueRepo).delete(v0)
        assertEquals(0, v1.sortOrder)
        assertEquals(1, v2.sortOrder)
    }
}
