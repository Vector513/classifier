package com.classifier.exception

class EntityNotFoundException(message: String) : RuntimeException(message)

class DuplicateCodeException(message: String) : RuntimeException(message)

class HasChildrenException(message: String) : RuntimeException(message)

class CyclicMoveException(message: String) : RuntimeException(message)

class InvalidSelectionException(message: String) : RuntimeException(message)
