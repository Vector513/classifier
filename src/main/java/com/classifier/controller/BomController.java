package com.classifier.controller;

import com.classifier.dto.*;
import com.classifier.service.BomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bom")
@Tag(name = "Спецификации изделий", description = "Состав, версии, модификации и нормы расхода")
public class BomController {
    private final BomService service;
    public BomController(BomService service) { this.service = service; }

    @GetMapping("/products")
    public List<BomProductResponse> products() { return service.products(); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Зарегистрировать изделие существующего классификатора")
    public BomProductResponse register(@Valid @RequestBody RegisterBomProductRequest request) { return service.register(request); }
    @GetMapping("/specifications")
    public List<BomSpecificationResponse> specifications() { return service.specifications(); }
    @PostMapping("/specifications") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать черновик, новую версию или модификацию на основе утвержденной версии")
    public BomSpecificationResponse create(@Valid @RequestBody CreateBomSpecificationRequest request) { return service.create(request); }
    @GetMapping("/specifications/{id}")
    public BomSpecificationResponse get(@PathVariable Long id) { return service.get(id); }
    @DeleteMapping("/specifications/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
    @PostMapping("/specifications/{id}/release")
    @Operation(summary = "Утвердить и зафиксировать состав версии")
    public BomSpecificationResponse release(@PathVariable Long id) { return service.release(id); }
    @GetMapping("/specifications/{id}/lines")
    @Operation(summary = "Эффективный состав с учетом базовых позиций и исключений")
    public List<BomLineResponse> lines(@PathVariable Long id) { return service.lines(id); }
    @GetMapping("/specifications/{id}/local-positions")
    public List<Integer> localPositions(@PathVariable Long id) { return service.localPositions(id); }
    @PutMapping("/specifications/{id}/lines/{position}")
    public List<BomLineResponse> putLine(@PathVariable Long id, @PathVariable int position,
            @Valid @RequestBody BomLineRequest request) { return service.putLine(id, position, request); }
    @DeleteMapping("/specifications/{id}/lines/{position}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Исключить позицию, в том числе унаследованную")
    public void exclude(@PathVariable Long id, @PathVariable int position) { service.exclude(id, position); }
    @DeleteMapping("/specifications/{id}/overrides/{position}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Отменить локальное изменение, восстановив базовую позицию")
    public void reset(@PathVariable Long id, @PathVariable int position) { service.reset(id, position); }
    @GetMapping("/specifications/{id}/explosion")
    public List<BomExplosionResponse> explode(@PathVariable Long id, @RequestParam(defaultValue = "1") BigDecimal quantity) {
        return service.explode(id, quantity);
    }
    @GetMapping("/specifications/{id}/totals")
    public List<BomTotalResponse> totals(@PathVariable Long id, @RequestParam(defaultValue = "1") BigDecimal quantity,
            @RequestParam Long classId) { return service.totals(id, quantity, classId); }
}
