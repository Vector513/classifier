package com.classifier.repository

import com.classifier.entity.ClassifierNode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ClassifierNodeRepository : JpaRepository<ClassifierNode, Long> {

    fun findByParentIsNullOrderBySortOrder(): List<ClassifierNode>

    fun findByParentIdOrderBySortOrder(parentId: Long): List<ClassifierNode>

    fun existsByCode(code: String): Boolean

    fun countByParentId(parentId: Long): Long

    @Query(
        value = """
            WITH RECURSIVE descendants AS (
                SELECT * FROM classifier_node WHERE parent_id = :nodeId
                UNION ALL
                SELECT cn.* FROM classifier_node cn
                JOIN descendants d ON cn.parent_id = d.id
            )
            SELECT * FROM descendants ORDER BY id
        """,
        nativeQuery = true
    )
    fun findDescendants(nodeId: Long): List<ClassifierNode>

    @Query(
        value = """
            WITH RECURSIVE ancestors AS (
                SELECT * FROM classifier_node WHERE id = (
                    SELECT parent_id FROM classifier_node WHERE id = :nodeId
                )
                UNION ALL
                SELECT cn.* FROM classifier_node cn
                JOIN ancestors a ON cn.id = a.parent_id
            )
            SELECT * FROM ancestors ORDER BY id
        """,
        nativeQuery = true
    )
    fun findAncestors(nodeId: Long): List<ClassifierNode>

    @Query(
        value = """
            WITH RECURSIVE descendants AS (
                SELECT * FROM classifier_node WHERE parent_id = :nodeId
                UNION ALL
                SELECT cn.* FROM classifier_node cn
                JOIN descendants d ON cn.parent_id = d.id
            )
            SELECT d.* FROM descendants d
            LEFT JOIN classifier_node child ON child.parent_id = d.id
            WHERE child.id IS NULL
        """,
        nativeQuery = true
    )
    fun findTerminals(nodeId: Long): List<ClassifierNode>

    @Query(
        value = """
            SELECT * FROM classifier_node
            WHERE LOWER(code) LIKE LOWER(CONCAT('%%', :query, '%%'))
               OR LOWER(name) LIKE LOWER(CONCAT('%%', :query, '%%'))
            ORDER BY code
        """,
        nativeQuery = true
    )
    fun searchByQuery(query: String): List<ClassifierNode>
}
