package com.classifier.controller

import com.classifier.dto.*
import com.classifier.service.OrderService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val service: OrderService) {
    @GetMapping fun overview()=service.overview()
    @GetMapping("/{id}") fun order(@PathVariable id: Long)=service.order(id)
    @PostMapping fun orderSave(@RequestParam(required=false) id: Long?,@RequestBody r: CustomerOrderRequest)=mapOf("id" to service.saveOrder(id,r))
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteOrder(@PathVariable id: Long)=service.deleteOrder(id)
    @GetMapping("/{id}/document",produces=["text/html;charset=UTF-8"]) fun document(@PathVariable id: Long)=service.document(id)
    @PostMapping("/customers") fun customer(@RequestParam(required=false) id: Long?,@RequestBody r: CustomerRequest)=mapOf("id" to service.customer(id,r))
    @DeleteMapping("/customers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteCustomer(@PathVariable id: Long)=service.deleteCustomer(id)
    @GetMapping("/templates/{id}") fun template(@PathVariable id: Long)=service.template(id)
    @PostMapping("/templates") fun templateSave(@RequestParam(required=false) id: Long?,@RequestBody r: ProductTemplateRequest)=mapOf("id" to service.saveTemplate(id,r))
    @DeleteMapping("/templates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteTemplate(@PathVariable id: Long)=service.deleteTemplate(id)
    @PostMapping("/templates/{template}/parameters") fun parameter(@PathVariable template: Long,@RequestParam(required=false) id: Long?,@RequestBody r: ConfigurationParameterRequest)=mapOf("id" to service.saveParameter(template,id,r))
    @DeleteMapping("/templates/{template}/parameters/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteParameter(@PathVariable template: Long,@PathVariable id: Long)=service.deleteParameter(template,id)
    @PostMapping("/templates/{template}/rules") fun rule(@PathVariable template: Long,@RequestParam(required=false) id: Long?,@RequestBody r: ConfigurationRuleRequest)=mapOf("id" to service.saveRule(template,id,r))
    @DeleteMapping("/templates/{template}/rules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteRule(@PathVariable template: Long,@PathVariable id: Long)=service.deleteRule(template,id)
    @PostMapping("/templates/{template}/validate") fun validate(@PathVariable template: Long,@RequestBody r: ValidationRequest)=service.validate(template,r)
    @PostMapping("/templates/{template}/variants") fun variantSave(@PathVariable template: Long,@RequestBody r: VariantRequest)=mapOf("id" to service.createVariant(template,r))
    @GetMapping("/variants/{id}") fun variant(@PathVariable id: Long)=service.variant(id)
    @PutMapping("/variants/{id}") fun updateVariant(@PathVariable id: Long,@RequestBody r: VariantRequest)=mapOf("id" to service.updateVariant(id,r))
    @DeleteMapping("/variants/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteVariant(@PathVariable id: Long)=service.deleteVariant(id)
    @PostMapping("/functions") fun functionSave(@RequestParam(required=false) id: Long?,@RequestBody r: ConstraintFunctionRequest)=mapOf("id" to service.saveFunction(id,r))
    @PostMapping("/functions/{id}/evaluate") fun evaluate(@PathVariable id: Long,@RequestBody r: FunctionEvaluationRequest)=service.evaluateFunction(id,r)
    @DeleteMapping("/functions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteFunction(@PathVariable id: Long)=service.deleteFunction(id)
    @PostMapping("/{order}/lines") fun line(@PathVariable order: Long,@RequestParam(required=false) id: Long?,@RequestBody r: OrderLineRequest)=mapOf("id" to service.addLine(order,id,r))
    @PostMapping("/{order}/configured-lines") fun configured(@PathVariable order: Long,@RequestBody r: ConfiguredOrderLineRequest)=service.configuredLine(order,r)
    @DeleteMapping("/{order}/lines/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun deleteLine(@PathVariable order: Long,@PathVariable id: Long)=service.deleteLine(order,id)
}
