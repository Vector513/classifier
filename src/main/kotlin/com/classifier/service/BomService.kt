package com.classifier.service

import com.classifier.dto.BomExplosionResponse
import com.classifier.dto.BomLineRequest
import com.classifier.dto.BomLineResponse
import com.classifier.dto.BomProductResponse
import com.classifier.dto.BomSpecificationResponse
import com.classifier.dto.BomTotalResponse
import com.classifier.dto.CreateBomSpecificationRequest
import com.classifier.dto.ProductKind
import com.classifier.dto.RegisterBomProductRequest
import com.classifier.entity.ClassifierNode
import com.classifier.entity.NodeKind
import com.classifier.exception.CyclicMoveException
import com.classifier.exception.DuplicateCodeException
import com.classifier.exception.EntityNotFoundException
import com.classifier.exception.InvalidSelectionException
import com.classifier.repository.BomRepository
import com.classifier.repository.ClassifierNodeRepository
import com.classifier.repository.UnitOfMeasureRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class BomService(
    private val repository: BomRepository,
    private val nodes: ClassifierNodeRepository,
    private val units: UnitOfMeasureRepository
) {
    fun products() = repository.products()

    fun specifications() = repository.specifications()

    fun get(id: Long): BomSpecificationResponse =
        repository.specification(id) ?: throw EntityNotFoundException("Спецификация $id не найдена")

    fun register(request: RegisterBomProductRequest): BomProductResponse {
        repository.lock()
        val node = requireCatalogProduct(request.nodeId)
        val catalogUnitId = node.unitOfMeasure?.id
        if (catalogUnitId != null && catalogUnitId != request.unitId) {
            throw InvalidSelectionException("Используйте единицу измерения, указанную в карточке изделия")
        }
        if (!units.existsById(request.unitId)) {
            throw EntityNotFoundException("Единица измерения ${request.unitId} не найдена")
        }
        if (repository.product(request.nodeId) != null) {
            throw DuplicateCodeException("Изделие уже зарегистрировано")
        }

        repository.register(request)
        return checkNotNull(repository.product(request.nodeId))
    }

    fun create(request: CreateBomSpecificationRequest): BomSpecificationResponse {
        repository.lock()
        registerCatalogProductIfNeeded(request.productId, ProductKind.ASSEMBLY)
        request.baseId?.let { baseId ->
            if (get(baseId).status != RELEASED) {
                throw InvalidSelectionException("Базовая версия должна быть утверждена")
            }
        }

        val specificationId = repository.create(request)
        requireAcyclicExplosion(specificationId)
        return get(specificationId)
    }

    fun lines(id: Long): List<BomLineResponse> {
        get(id)
        return repository.lines(id)
    }

    fun localPositions(id: Long): List<Int> {
        get(id)
        return repository.localPositions(id)
    }

    fun putLine(id: Long, position: Int, request: BomLineRequest): List<BomLineResponse> {
        val draft = lockDraft(id)
        checkPosition(position)
        registerCatalogProductIfNeeded(request.componentId, ProductKind.PURCHASED)

        if (request.componentId == draft.productId) {
            throw CyclicMoveException("Изделие не может входить в собственный состав")
        }
        request.componentSpecificationId?.let { componentSpecificationId ->
            val componentVersion = get(componentSpecificationId)
            if (componentVersion.productId != request.componentId || componentVersion.status != RELEASED) {
                throw InvalidSelectionException("Выберите утвержденную спецификацию указанного компонента")
            }
        }

        repository.putLine(id, position, request)
        requireAcyclicExplosion(id)
        return repository.lines(id)
    }

    fun exclude(id: Long, position: Int) {
        lockDraft(id)
        checkPosition(position)
        if (repository.lines(id).none { it.position == position }) {
            throw EntityNotFoundException("Позиция $position не найдена")
        }
        repository.exclude(id, position)
    }

    fun reset(id: Long, position: Int) {
        lockDraft(id)
        checkPosition(position)
        repository.reset(id, position)
        requireAcyclicExplosion(id)
    }

    fun release(id: Long): BomSpecificationResponse {
        lockDraft(id)
        if (repository.lines(id).isEmpty()) {
            throw InvalidSelectionException("Нельзя утвердить пустую спецификацию")
        }
        requireAcyclicExplosion(id)
        repository.release(id)
        return get(id)
    }

    fun delete(id: Long) {
        lockDraft(id)
        repository.delete(id)
    }

    fun explode(id: Long, quantity: BigDecimal): List<BomExplosionResponse> {
        get(id)
        checkQuantity(quantity)
        return requireAcyclicExplosion(id, quantity)
    }

    fun totals(id: Long, quantity: BigDecimal, classId: Long): List<BomTotalResponse> {
        get(id)
        checkQuantity(quantity)
        if (!nodes.existsById(classId)) {
            throw EntityNotFoundException("Класс ресурсов $classId не найден")
        }
        // SQL пропускает циклические ветви. Проверяем весь состав перед выдачей итога.
        requireAcyclicExplosion(id)
        return repository.totals(id, quantity, classId)
    }

    private fun requireCatalogProduct(id: Long): ClassifierNode {
        val node = nodes.findById(id).orElseThrow { EntityNotFoundException("Узел $id не найден") }
        if (node.nodeKind == NodeKind.GROUP) {
            throw InvalidSelectionException("Выберите модель или изделие из классификатора")
        }
        return node
    }

    private fun registerCatalogProductIfNeeded(id: Long, kind: ProductKind) {
        if (repository.product(id) != null) return

        val unitId = requireCatalogProduct(id).unitOfMeasure?.id
            ?: throw InvalidSelectionException("Укажите единицу измерения в карточке изделия")
        repository.register(RegisterBomProductRequest(id, kind, unitId))
    }

    private fun lockDraft(id: Long): BomSpecificationResponse {
        repository.lock()
        val specification = get(id)
        if (specification.status != DRAFT) {
            throw DuplicateCodeException(
                "Утвержденная версия неизменяема. Создайте новую версию на её основе"
            )
        }
        return specification
    }

    private fun requireAcyclicExplosion(
        id: Long,
        quantity: BigDecimal = BigDecimal.ONE
    ): List<BomExplosionResponse> {
        val explosion = repository.explode(id, quantity)
        if (explosion.any { it.cycle }) {
            throw CyclicMoveException("Обнаружен цикл в составе изделия")
        }
        return explosion
    }

    private fun checkPosition(position: Int) {
        if (position <= 0) {
            throw InvalidSelectionException("Номер позиции должен быть положительным")
        }
    }

    private fun checkQuantity(quantity: BigDecimal) {
        if (quantity <= BigDecimal.ZERO ||
            quantity > MAX_QUANTITY ||
            quantity.stripTrailingZeros().scale() > MAX_FRACTION_DIGITS
        ) {
            throw InvalidSelectionException(
                "Количество должно быть положительным, не более 13 целых и 6 дробных знаков"
            )
        }
    }

    private companion object {
        const val DRAFT = "DRAFT"
        const val RELEASED = "RELEASED"
        const val MAX_FRACTION_DIGITS = 6
        val MAX_QUANTITY = BigDecimal("9999999999999.999999")
    }
}
