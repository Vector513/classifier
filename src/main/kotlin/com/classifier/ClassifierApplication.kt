package com.classifier

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class ClassifierApplication {

    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Система классификации изделий")
            .version("1.2")
            .description(
                "API для работы с иерархическим классификатором изделий и перечислениями атрибутов. " +
                "Лабораторная работа 1.2 — Моделирование перечислений."
            )
    )
}

fun main(args: Array<String>) {
    runApplication<ClassifierApplication>(*args)
}
