package com.classifier.service

import com.classifier.dto.*
import com.classifier.exception.*
import com.classifier.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class BomService(private val repo: BomRepository, private val nodes: ClassifierNodeRepository,
    private val units: UnitOfMeasureRepository) {
    fun products() = repo.products()
    fun specifications() = repo.specifications()
    fun get(id: Long) = repo.specification(id) ?: throw EntityNotFoundException("Спецификация $id не найдена")
    private fun product(id: Long) = repo.product(id) ?: throw EntityNotFoundException("Изделие $id не зарегистрировано в спецификациях")
    private fun draft(id: Long): BomSpecificationResponse {
        repo.lock()
        return get(id).also {
            if (it.status != "DRAFT") throw DuplicateCodeException("Утвержденная версия неизменяема. Создайте новую версию на её основе")
        }
    }
    fun register(r: RegisterBomProductRequest): BomProductResponse {
        repo.lock()
        if (!nodes.existsById(r.nodeId)) throw EntityNotFoundException("Узел ${r.nodeId} не найден")
        if (nodes.countByParentId(r.nodeId)>0) throw InvalidSelectionException("Выберите изделие — терминальный узел классификатора")
        if (!units.existsById(r.unitId)) throw EntityNotFoundException("Единица измерения ${r.unitId} не найдена")
        if (repo.product(r.nodeId)!=null) throw DuplicateCodeException("Изделие уже зарегистрировано")
        repo.register(r)
        return product(r.nodeId)
    }
    fun create(r: CreateBomSpecificationRequest): BomSpecificationResponse {
        repo.lock()
        product(r.productId)
        r.baseId?.let { if (get(it).status!="RELEASED") throw InvalidSelectionException("Базовая версия должна быть утверждена") }
        val id=repo.create(r)
        validateCycles(id)
        return get(id)
    }
    fun lines(id: Long): List<BomLineResponse> { get(id); return repo.lines(id) }
    fun localPositions(id: Long): List<Int> { get(id); return repo.localPositions(id) }
    fun putLine(id: Long, position: Int, r: BomLineRequest): List<BomLineResponse> {
        val spec=draft(id)
        checkPosition(position)
        product(r.componentId)
        if (r.componentId==spec.productId) throw CyclicMoveException("Изделие не может входить в собственный состав")
        r.componentSpecificationId?.let {
            val child=get(it)
            if (child.productId!=r.componentId || child.status!="RELEASED")
                throw InvalidSelectionException("Выберите утвержденную спецификацию указанного компонента")
        }
        repo.putLine(id,position,r)
        validateCycles(id)
        return repo.lines(id)
    }
    fun exclude(id: Long, position: Int) {
        draft(id); checkPosition(position)
        if (repo.lines(id).none { it.position==position }) throw EntityNotFoundException("Позиция $position не найдена")
        repo.exclude(id,position)
    }
    fun reset(id: Long, position: Int) { draft(id); checkPosition(position); repo.reset(id,position); validateCycles(id) }
    fun release(id: Long): BomSpecificationResponse {
        draft(id)
        if (repo.lines(id).isEmpty()) throw InvalidSelectionException("Нельзя утвердить пустую спецификацию")
        validateCycles(id)
        repo.release(id)
        return get(id)
    }
    fun delete(id: Long) { draft(id); repo.delete(id) }
    fun explode(id: Long, quantity: BigDecimal): List<BomExplosionResponse> {
        get(id); checkQuantity(quantity)
        return repo.explode(id,quantity).also {
            if (it.any { row -> row.cycle }) throw CyclicMoveException("Обнаружен цикл в составе изделия")
        }
    }
    fun totals(id: Long, quantity: BigDecimal, classId: Long): List<BomTotalResponse> {
        get(id); checkQuantity(quantity)
        if (!nodes.existsById(classId)) throw EntityNotFoundException("Класс ресурсов $classId не найден")
        validateCycles(id)
        return repo.totals(id,quantity,classId)
    }
    private fun validateCycles(id: Long) {
        if (repo.explode(id,BigDecimal.ONE).any { it.cycle }) throw CyclicMoveException("Обнаружен цикл в составе изделия")
    }
    private fun checkPosition(position: Int) {
        if (position<=0) throw InvalidSelectionException("Номер позиции должен быть положительным")
    }
    private fun checkQuantity(quantity: BigDecimal) {
        if (quantity<=BigDecimal.ZERO || quantity>BigDecimal("9999999999999.999999") || quantity.stripTrailingZeros().scale()>6)
            throw InvalidSelectionException("Количество должно быть положительным, не более 13 целых и 6 дробных знаков")
    }
}
