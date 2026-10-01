package com.classifier.service

import com.classifier.dto.MultiFilterRequest
import com.classifier.entity.ClassifierNode
import com.classifier.repository.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ItemSearchServiceTest {
    @Mock lateinit var nodeRepo: ClassifierNodeRepository
    @Mock lateinit var navRepo: NodeAttributeValueRepository
    @Mock lateinit var nnvRepo: NodeNumericValueRepository
    @Mock lateinit var navService: NodeAttributeValueService
    @Mock lateinit var numService: NumericParameterService
    @InjectMocks lateinit var service: ItemSearchService

    @Test fun `both filter payload spellings deserialize to one contract`() {
        val mapper=jacksonObjectMapper()
        val old=mapper.readValue<MultiFilterRequest>("""{"rootNodeId":1,"numericFilters":[{"parameterId":2,"minValue":3}],"enumFilters":[{"enumerationId":4,"valueId":5}]}""")
        val current=mapper.readValue<MultiFilterRequest>("""{"rootNodeId":1,"numericCriteria":[{"parameterId":2,"minValue":3}],"enumCriteria":[{"enumerationId":4,"valueId":5}]}""")
        assertEquals(current,old)
    }
    @Test fun `global filter returns leaves while scoped filter limits candidates`() {
        val root=ClassifierNode(id=1,code="ROOT",name="Root")
        val child=ClassifierNode(id=2,code="CHILD",name="Child",parent=root)
        val other=ClassifierNode(id=3,code="OTHER",name="Other")
        whenever(nodeRepo.findAll()).thenReturn(listOf(root,child,other))
        whenever(nodeRepo.findById(1L)).thenReturn(Optional.of(root))
        whenever(nodeRepo.findDescendants(1)).thenReturn(listOf(child))
        assertEquals(listOf(2L,3L),service.searchByMultipleFilters(MultiFilterRequest()).map { it.id })
        assertEquals(listOf(2L),service.searchByMultipleFilters(MultiFilterRequest(rootNodeId=1)).map { it.id })
    }
}
