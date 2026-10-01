package com.classifier.service

import com.classifier.dto.*
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.InvalidSelectionException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CatalogService(private val jdbc: JdbcTemplate) {
    private fun requireNode(id: Long) {
        if (jdbc.queryForObject("SELECT count(*) FROM classifier_node WHERE id=?",Int::class.java,id)==0)
            throw EntityNotFoundException("Запись справочника $id не найдена")
    }
    fun requireEnumChoice(nodeId: Long, enumerationId: Long, valueId: Long) {
        if (jdbc.queryForObject("SELECT catalog_enum_allowed(?,?,?)",Boolean::class.java,nodeId,enumerationId,valueId)!=true)
            throw InvalidSelectionException("Значение не разрешено для этой группы, модели или исполнения. Проверьте доступные варианты характеристики")
    }
    fun requireNumericParameter(nodeId: Long, parameterId: Long) {
        if (jdbc.queryForObject("SELECT catalog_numeric_applicable(?,?)",Boolean::class.java,nodeId,parameterId)!=true)
            throw InvalidSelectionException("Сначала назначьте числовой параметр этой записи или её родительской группе")
    }
    fun assignedNodeIds(enumerationId: Long): List<Long> = jdbc.query(
        "SELECT classifier_node_id FROM node_enumeration WHERE enumeration_id=? AND NOT excluded ORDER BY classifier_node_id", {r,_->r.getLong(1)},enumerationId)
    fun modelsAllowing(nodeIds: List<Long>, enumerationId: Long, valueId: Long): Set<Long> {
        if (nodeIds.isEmpty()) return emptySet()
        val placeholders=nodeIds.joinToString(",") { "?" }
        return jdbc.query("SELECT id FROM classifier_node WHERE node_kind='MODEL' AND id IN ($placeholders) AND catalog_enum_allowed(id,?,?)",
            {r,_->r.getLong(1)},*(nodeIds+listOf(enumerationId,valueId)).toTypedArray()).toSet()
    }
    /** Параметры всех подклассов в области поиска, даже если у самой группы их нет. */
    fun scopeParameterIds(nodeId: Long, numeric: Boolean): List<Long> {
        requireNode(nodeId)
        val table = if(numeric) "node_numeric_parameter" else "node_enumeration"
        val column = if(numeric) "numeric_parameter_id" else "enumeration_id"
        val applicable = if(numeric) "catalog_numeric_applicable" else "catalog_enum_applicable"
        return jdbc.query("""WITH RECURSIVE scope AS (
            SELECT id FROM classifier_node WHERE id=?
            UNION ALL SELECT n.id FROM classifier_node n JOIN scope s ON n.parent_id=s.id
        ) SELECT DISTINCT b.$column FROM scope s JOIN classifier_node n ON n.id=s.id
            CROSS JOIN LATERAL catalog_ancestors(s.id) a JOIN $table b ON b.classifier_node_id=a.id
            WHERE n.node_kind<>'GROUP' AND $applicable(s.id,b.$column) ORDER BY b.$column""",{r,_->r.getLong(1)},nodeId)
    }
    fun validate() { jdbc.execute("SELECT catalog_assert_consistent()") }

    fun bindings(nodeId: Long): List<EnumerationBindingResponse> {
        requireNode(nodeId)
        return jdbc.query("""SELECT b.*,e.name FROM node_enumeration b JOIN enumeration e ON e.id=b.enumeration_id
            WHERE b.classifier_node_id=? ORDER BY e.name""",{r,_->
            EnumerationBindingResponse(nodeId,r.getLong("enumeration_id"),r.getString("name"),r.getBoolean("restrict_values"),
                jdbc.query("SELECT enumeration_value_id FROM node_enumeration_option WHERE classifier_node_id=? AND enumeration_id=? ORDER BY enumeration_value_id",
                    {v,_->v.getLong(1)},nodeId,r.getLong("enumeration_id")),r.getBoolean("excluded"))
        },nodeId)
    }
    fun setBinding(nodeId: Long, enumerationId: Long, request: EnumerationBindingRequest) {
        jdbc.execute("SELECT pg_advisory_xact_lock(21001)")
        requireNode(nodeId)
        if (jdbc.queryForObject("SELECT count(*) FROM enumeration WHERE id=?",Int::class.java,enumerationId)==0)
            throw EntityNotFoundException("Характеристика $enumerationId не найдена")
        if (request.excluded && request.allowedValueIds!=null) throw InvalidSelectionException("Исключение характеристики не задаёт доступные варианты")
        val ids=request.allowedValueIds?.distinct()
        if (ids!=null && ids.isEmpty()) throw InvalidSelectionException("Выберите хотя бы один допустимый вариант или используйте весь список")
        ids?.forEach { value ->
            if (jdbc.queryForObject("SELECT count(*) FROM enumeration_value WHERE id=? AND enumeration_id=?",Int::class.java,value,enumerationId)==0)
                throw InvalidSelectionException("Вариант $value не относится к указанной характеристике")
        }
        jdbc.update("""INSERT INTO node_enumeration(classifier_node_id,enumeration_id,restrict_values,excluded) VALUES(?,?,?,?)
            ON CONFLICT(classifier_node_id,enumeration_id) DO UPDATE SET restrict_values=EXCLUDED.restrict_values,excluded=EXCLUDED.excluded""",nodeId,enumerationId,ids!=null,request.excluded)
        jdbc.update("DELETE FROM node_enumeration_option WHERE classifier_node_id=? AND enumeration_id=?",nodeId,enumerationId)
        ids?.forEach { jdbc.update("INSERT INTO node_enumeration_option(classifier_node_id,enumeration_id,enumeration_value_id) VALUES(?,?,?)",nodeId,enumerationId,it) }
        validate()
    }
    fun removeBinding(nodeId: Long, enumerationId: Long) {
        jdbc.execute("SELECT pg_advisory_xact_lock(21001)")
        requireNode(nodeId)
        jdbc.update("DELETE FROM node_enumeration WHERE classifier_node_id=? AND enumeration_id=?",nodeId,enumerationId)
        validate()
    }
    fun effectiveEnumerations(nodeId: Long): List<EffectiveEnumerationResponse> {
        requireNode(nodeId)
        return jdbc.query("""SELECT DISTINCT ON(e.id) e.*,c.name AS class_name,n.name AS node_name,a.id AS defined_node,a.depth
            FROM catalog_ancestors(?) a JOIN node_enumeration b ON b.classifier_node_id=a.id
            JOIN enumeration e ON e.id=b.enumeration_id JOIN enumeration_class c ON c.id=e.enumeration_class_id
            JOIN classifier_node n ON n.id=a.id WHERE catalog_enum_applicable(?,e.id) ORDER BY e.id,a.depth""",{r,_->
            val enumerationId=r.getLong("id")
            val values=jdbc.query("""SELECT * FROM enumeration_value WHERE enumeration_id=? AND catalog_enum_allowed(?,?,id) ORDER BY sort_order,id""",{v,_->
                EnumerationValueResponse(v.getLong("id"),v.getString("code"),v.getString("name"),enumerationId,v.getInt("sort_order"),v.getTimestamp("created_at").toInstant(),v.getTimestamp("updated_at").toInstant())
            },enumerationId,nodeId,enumerationId)
            EffectiveEnumerationResponse(enumerationId,r.getString("code"),r.getString("name"),r.getString("class_name"),values,r.getLong("defined_node"),r.getString("node_name"),r.getInt("depth")>0)
        },nodeId,nodeId)
    }
}
