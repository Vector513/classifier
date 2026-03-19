package com.classifier

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ClassifierApplication

fun main(args: Array<String>) {
    runApplication<ClassifierApplication>(*args)
}
