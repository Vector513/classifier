package com.classifier.service

import com.classifier.entity.ClassifierNode
import com.classifier.exception.EntityNotFoundException
import com.classifier.repository.ClassifierNodeRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class TreeTraversalServiceTest {

    @Mock
    lateinit var nodeRepo: ClassifierNodeRepository

    @InjectMocks
    lateinit var service: TreeTraversalService

    private fun node(id: Long, code: String, parent: ClassifierNode? = null, sortOrder: Int = 0) =
        ClassifierNode(id = id, code = code, name = code, parent = parent, sortOrder = sortOrder)

    // ── getDescendants ────────────────────────────────────────────────────────

    @Test
    fun `getDescendants returns list from repo`() {
        val root = node(1, "ROOT")
        val child = node(2, "CHILD", parent = root)

        whenever(nodeRepo.existsById(1L)).thenReturn(true)
        whenever(nodeRepo.findDescendants(1L)).thenReturn(listOf(child))

        val result = service.getDescendants(1)

        assertEquals(1, result.size)
        assertEquals("CHILD", result[0].code)
    }

    @Test
    fun `getDescendants throws when node not found`() {
        whenever(nodeRepo.existsById(99L)).thenReturn(false)

        assertThrows<EntityNotFoundException> { service.getDescendants(99) }
    }

    // ── getAncestors ──────────────────────────────────────────────────────────

    @Test
    fun `getAncestors returns path to root`() {
        val root = node(1, "ROOT")
        val mid = node(2, "MID", parent = root)

        whenever(nodeRepo.existsById(3L)).thenReturn(true)
        whenever(nodeRepo.findAncestors(3L)).thenReturn(listOf(mid, root))

        val result = service.getAncestors(3)

        assertEquals(2, result.size)
    }

    @Test
    fun `getAncestors throws when node not found`() {
        whenever(nodeRepo.existsById(99L)).thenReturn(false)

        assertThrows<EntityNotFoundException> { service.getAncestors(99) }
    }

    // ── getTerminals ──────────────────────────────────────────────────────────

    @Test
    fun `getTerminals returns leaf nodes`() {
        val leaf1 = node(3, "LEAF1")
        val leaf2 = node(4, "LEAF2")

        whenever(nodeRepo.existsById(1L)).thenReturn(true)
        whenever(nodeRepo.findTerminals(1L)).thenReturn(listOf(leaf1, leaf2))

        val result = service.getTerminals(1)

        assertEquals(2, result.size)
    }

    @Test
    fun `getTerminals throws when node not found`() {
        whenever(nodeRepo.existsById(99L)).thenReturn(false)

        assertThrows<EntityNotFoundException> { service.getTerminals(99) }
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    fun `search delegates to repo`() {
        val nodes = listOf(node(1, "PHONES"))
        whenever(nodeRepo.searchByQuery("phone")).thenReturn(nodes)

        val result = service.search("phone")

        assertEquals(1, result.size)
        verify(nodeRepo).searchByQuery("phone")
    }

    @Test
    fun `search returns empty for no match`() {
        whenever(nodeRepo.searchByQuery("xyz")).thenReturn(emptyList())

        assertTrue(service.search("xyz").isEmpty())
    }

    // ── buildTree ─────────────────────────────────────────────────────────────

    @Test
    fun `buildTree returns empty when no roots`() {
        whenever(nodeRepo.findByParentIsNullOrderBySortOrder()).thenReturn(emptyList())

        assertTrue(service.buildTree().isEmpty())
    }

    @Test
    fun `buildTree builds nested structure`() {
        val root = node(1, "ROOT", sortOrder = 0)
        val child = node(2, "CHILD", parent = root, sortOrder = 0)

        whenever(nodeRepo.findByParentIsNullOrderBySortOrder()).thenReturn(listOf(root))
        whenever(nodeRepo.findByParentIdOrderBySortOrder(1L)).thenReturn(listOf(child))
        whenever(nodeRepo.findByParentIdOrderBySortOrder(2L)).thenReturn(emptyList())

        val tree = service.buildTree()

        assertEquals(1, tree.size)
        assertEquals("ROOT", tree[0].code)
        assertFalse(tree[0].isTerminal)
        assertEquals(1, tree[0].children.size)
        assertEquals("CHILD", tree[0].children[0].code)
        assertTrue(tree[0].children[0].isTerminal)
    }

    @Test
    fun `buildTree marks leaf nodes as terminal`() {
        val leaf = node(1, "LEAF")
        whenever(nodeRepo.findByParentIsNullOrderBySortOrder()).thenReturn(listOf(leaf))
        whenever(nodeRepo.findByParentIdOrderBySortOrder(1L)).thenReturn(emptyList())

        val tree = service.buildTree()

        assertTrue(tree[0].isTerminal)
        assertTrue(tree[0].children.isEmpty())
    }

    // ── detectCycles ──────────────────────────────────────────────────────────

    @Test
    fun `detectCycles returns valid true for acyclic tree`() {
        val root = node(1, "ROOT")
        val child = node(2, "CHILD", parent = root)

        whenever(nodeRepo.findAll()).thenReturn(listOf(root, child))

        val result = service.detectCycles()

        assertTrue(result.valid)
        assertTrue(result.cycles.isEmpty())
    }

    @Test
    fun `detectCycles returns valid false for cyclic tree`() {
        // Simulate cycle: node A's parent = B, B's parent = A (impossible via JPA normally, but tested at logic level)
        val nodeA = ClassifierNode(id = 1, code = "A", name = "A", sortOrder = 0)
        val nodeB = ClassifierNode(id = 2, code = "B", name = "B", parent = nodeA, sortOrder = 0)
        // Force cycle by reflection — set nodeA.parent = nodeB after nodeB is created
        val parentField = ClassifierNode::class.java.getDeclaredField("parent")
        parentField.isAccessible = true
        parentField.set(nodeA, nodeB)

        whenever(nodeRepo.findAll()).thenReturn(listOf(nodeA, nodeB))

        val result = service.detectCycles()

        assertFalse(result.valid)
        assertEquals(1, result.cycles.size)
    }

    @Test
    fun `detectCycles returns valid true for empty tree`() {
        whenever(nodeRepo.findAll()).thenReturn(emptyList())

        val result = service.detectCycles()

        assertTrue(result.valid)
    }
}
