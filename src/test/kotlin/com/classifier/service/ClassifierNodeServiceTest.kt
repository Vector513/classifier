package com.classifier.service

import com.classifier.dto.CreateNodeRequest
import com.classifier.dto.UpdateNodeRequest
import com.classifier.entity.ClassifierNode
import com.classifier.entity.UnitOfMeasure
import com.classifier.exception.CyclicMoveException
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.HasChildrenException
import com.classifier.repository.ClassifierNodeRepository
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
class ClassifierNodeServiceTest {

    @Mock
    lateinit var nodeRepo: ClassifierNodeRepository

    @Mock
    lateinit var unitRepo: UnitOfMeasureRepository

    @InjectMocks
    lateinit var service: ClassifierNodeService

    @Test
    fun `create node with unique code succeeds`() {
        val request = CreateNodeRequest(code = "PHONES", name = "Смартфоны")

        whenever(nodeRepo.existsByCode("PHONES")).thenReturn(false)
        whenever(nodeRepo.findByParentIsNullOrderBySortOrder()).thenReturn(emptyList())
        whenever(nodeRepo.save(any<ClassifierNode>())).thenAnswer { it.arguments[0] }

        val result = service.create(request)

        assertEquals("PHONES", result.code)
        assertEquals("Смартфоны", result.name)
        assertNull(result.parent)
    }

    @Test
    fun `create node with duplicate code throws`() {
        val request = CreateNodeRequest(code = "PHONES", name = "Смартфоны")
        whenever(nodeRepo.existsByCode("PHONES")).thenReturn(true)

        assertThrows<DuplicateCodeException> { service.create(request) }
    }

    @Test
    fun `create node with parent`() {
        val parent = ClassifierNode(id = 1, code = "ELECTRONICS", name = "Электроника")
        val request = CreateNodeRequest(code = "PHONES", name = "Смартфоны", parentId = 1)

        whenever(nodeRepo.existsByCode("PHONES")).thenReturn(false)
        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(parent))
        whenever(nodeRepo.countByParentId(1L)).thenReturn(0)
        whenever(nodeRepo.save(any<ClassifierNode>())).thenAnswer { it.arguments[0] }

        val result = service.create(request)

        assertEquals(parent, result.parent)
    }

    @Test
    fun `create node with unit of measure`() {
        val unit = UnitOfMeasure(id = 1, code = "PCS", name = "штуки")
        val request = CreateNodeRequest(code = "PHONES", name = "Смартфоны", unitOfMeasureId = 1)

        whenever(nodeRepo.existsByCode("PHONES")).thenReturn(false)
        whenever(unitRepo.findById(1L)).thenReturn(Optional.of(unit))
        whenever(nodeRepo.findByParentIsNullOrderBySortOrder()).thenReturn(emptyList())
        whenever(nodeRepo.save(any<ClassifierNode>())).thenAnswer { it.arguments[0] }

        val result = service.create(request)

        assertEquals(unit, result.unitOfMeasure)
    }

    @Test
    fun `create node with nonexistent parent throws`() {
        val request = CreateNodeRequest(code = "PHONES", name = "Смартфоны", parentId = 999)

        whenever(nodeRepo.existsByCode("PHONES")).thenReturn(false)
        whenever(nodeRepo.findById(999L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.create(request) }
    }

    @Test
    fun `getById returns node`() {
        val node = ClassifierNode(id = 1, code = "PHONES", name = "Смартфоны")
        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(node))

        val result = service.getById(1)

        assertEquals("PHONES", result.code)
    }

    @Test
    fun `getById throws when not found`() {
        whenever(nodeRepo.findById(999L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service.getById(999) }
    }

    @Test
    fun `update changes fields`() {
        val node = ClassifierNode(id = 1, code = "PHONES", name = "Смартфоны")
        val request = UpdateNodeRequest(code = "MOBILE", name = "Мобильные")

        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(node))
        whenever(nodeRepo.existsByCode("MOBILE")).thenReturn(false)
        whenever(nodeRepo.save(any<ClassifierNode>())).thenAnswer { it.arguments[0] }

        val result = service.update(1, request)

        assertEquals("MOBILE", result.code)
        assertEquals("Мобильные", result.name)
    }

    @Test
    fun `update with duplicate code throws`() {
        val node = ClassifierNode(id = 1, code = "PHONES", name = "Смартфоны")
        val request = UpdateNodeRequest(code = "LAPTOPS")

        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(node))
        whenever(nodeRepo.existsByCode("LAPTOPS")).thenReturn(true)

        assertThrows<DuplicateCodeException> { service.update(1, request) }
    }

    @Test
    fun `delete node without children succeeds`() {
        val node = ClassifierNode(id = 1, code = "PHONES", name = "Смартфоны")

        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(node))
        whenever(nodeRepo.countByParentId(1L)).thenReturn(0)

        service.delete(1)

        verify(nodeRepo).delete(node)
    }

    @Test
    fun `delete node with children throws`() {
        val node = ClassifierNode(id = 1, code = "PHONES", name = "Смартфоны")

        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(node))
        whenever(nodeRepo.countByParentId(1L)).thenReturn(3)

        assertThrows<HasChildrenException> { service.delete(1) }
    }

    @Test
    fun `move to own descendant throws`() {
        val node = ClassifierNode(id = 1, code = "ELECTRONICS", name = "Электроника")
        val descendant = ClassifierNode(id = 5, code = "APPLE", name = "Apple")

        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(node))
        whenever(nodeRepo.findDescendants(1L)).thenReturn(listOf(descendant))

        assertThrows<CyclicMoveException> { service.move(1, 5) }
    }

    @Test
    fun `move to self throws`() {
        val node = ClassifierNode(id = 1, code = "PHONES", name = "Смартфоны")

        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(node))

        assertThrows<CyclicMoveException> { service.move(1, 1) }
    }

    @Test
    fun `move to root sets parent null`() {
        val parent = ClassifierNode(id = 1, code = "ELECTRONICS", name = "Электроника")
        val node = ClassifierNode(id = 2, code = "PHONES", name = "Смартфоны", parent = parent)

        whenever(nodeRepo.findById(2L)).thenReturn(Optional.of(node))
        whenever(nodeRepo.save(any<ClassifierNode>())).thenAnswer { it.arguments[0] }

        val result = service.move(2, null)

        assertNull(result.parent)
    }
}
