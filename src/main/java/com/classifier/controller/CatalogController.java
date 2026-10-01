package com.classifier.controller;

import com.classifier.dto.EnumerationBindingRequest;
import com.classifier.dto.EnumerationBindingResponse;
import com.classifier.service.CatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/enumeration-bindings")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }
    @GetMapping
    public List<EnumerationBindingResponse> all(@PathVariable Long nodeId) { return service.bindings(nodeId); }
    @PutMapping("/{enumerationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void put(@PathVariable Long nodeId, @PathVariable Long enumerationId, @RequestBody EnumerationBindingRequest request) {
        service.setBinding(nodeId, enumerationId, request);
    }
    @DeleteMapping("/{enumerationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long nodeId, @PathVariable Long enumerationId) { service.removeBinding(nodeId, enumerationId); }
}
