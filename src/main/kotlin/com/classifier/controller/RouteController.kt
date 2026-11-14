package com.classifier.controller

import com.classifier.dto.*
import com.classifier.service.RouteService
import com.classifier.service.RouteService.Line
import com.classifier.service.RouteService.Lookup
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/routes")
class RouteController(private val routes: RouteService) {
    @GetMapping
    fun overview() = routes.overview()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long) = routes.detail(id)

    @PostMapping
    fun save(@RequestParam(required = false) id: Long?, @RequestBody request: ManufacturingRouteRequest) =
        mapOf("id" to routes.saveRoute(id,request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = routes.deleteRoute(id)

    @PostMapping("/{route}/steps")
    fun step(@PathVariable route: Long, @RequestParam(required = false) id: Long?, @RequestBody request: RouteStepRequest) =
        mapOf("id" to routes.saveStep(route,id,request))

    @DeleteMapping("/{route}/steps/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteStep(@PathVariable route: Long, @PathVariable id: Long) = routes.deleteStep(route,id)

    @PostMapping("/steps/{step}/inputs")
    fun input(@PathVariable step: Long, @RequestParam(required = false) id: Long?, @RequestBody request: RouteInputRequest) =
        mapOf("id" to routes.saveInput(step,id,request))

    @PostMapping("/steps/{step}/assets")
    fun asset(@PathVariable step: Long, @RequestParam(required = false) id: Long?, @RequestBody request: RouteAssetRequest) =
        mapOf("id" to routes.saveAsset(step,id,request))

    @PostMapping("/steps/{step}/labor")
    fun labor(@PathVariable step: Long, @RequestParam(required = false) id: Long?, @RequestBody request: RouteLaborRequest) =
        mapOf("id" to routes.saveLabor(step,id,request))

    @DeleteMapping("/steps/{step}/lines/{line}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLine(@PathVariable step: Long, @PathVariable line: Line, @PathVariable id: Long) =
        routes.deleteLine(line,step,id)

    @PostMapping("/lookups/{lookup}")
    fun lookup(@PathVariable lookup: Lookup, @RequestParam(required = false) id: Long?, @RequestBody request: RouteLookupRequest) =
        mapOf("id" to routes.saveLookup(lookup,id,request))

    @DeleteMapping("/lookups/{lookup}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLookup(@PathVariable lookup: Lookup, @PathVariable id: Long) = routes.deleteLookup(lookup,id)

    @PutMapping("/prices/{node}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun price(@PathVariable node: Long, @RequestBody request: PurchasePriceRequest) = routes.savePrice(node,request.price)

    @GetMapping("/{id}/calculation")
    fun calculate(@PathVariable id: Long, @RequestParam quantity: Int,
                  @RequestParam(required = false) group: Long?, @RequestParam(required = false) profession: Long?) =
        routes.calculate(id,quantity,group,profession)
}
