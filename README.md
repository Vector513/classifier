# Классификатор изделий — Интернет-магазин электроники

REST API сервис для работы с древовидным справочником категорий товаров и моделирования перечислений атрибутов.
Реализует полный CRUD, обход дерева, диагностику циклов, поиск, управление перечислениями и выбор значений атрибутов для узлов.

## Требования

- Java 17+
- Docker и Docker Compose

## Запуск

### 1. Поднять БД

```bash
docker-compose up -d
```

PostgreSQL будет доступен на `localhost:5434` (БД: `classifier`, пользователь/пароль: `classifier`).

### 2. Запустить приложение

```bash
./gradlew bootRun
```

### 3. Открыть Swagger UI

```
http://localhost:8080/swagger-ui.html
```

### Запуск целиком в Docker (БД + приложение)

```bash
docker-compose up --build
```

## Остановка

```bash
# Остановить приложение: Ctrl+C

# Остановить БД
docker-compose down

# Остановить БД и удалить данные
docker-compose down -v
```

## Тестирование

```bash
./gradlew test
```

Требуется запущенный Docker — тесты используют Testcontainers (поднимает временный PostgreSQL).

---

## API-эндпоинты

### Классификатор (`/api/v1/nodes`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/roots` | Корневые вершины |
| `GET` | `/{id}` | Вершина по ID |
| `GET` | `/{id}/children` | Прямые потомки |
| `POST` | `/` | Создать вершину |
| `PATCH` | `/{id}` | Обновить вершину |
| `DELETE` | `/{id}` | Удалить вершину (без потомков) |
| `PATCH` | `/{id}/move` | Переместить (сменить родителя) |
| `PATCH` | `/{id}/reorder` | Изменить порядок сортировки |
| `GET` | `/{id}/descendants` | Все потомки (рекурсивно) |
| `GET` | `/{id}/ancestors` | Все предки (до корня) |
| `GET` | `/{id}/terminals` | Терминальные вершины поддерева |
| `GET` | `/tree` | Полное дерево |
| `GET` | `/search?query=...` | Поиск по коду/названию |
| `POST` | `/validate-cycles` | Диагностика циклов |

### Единицы измерения (`/api/v1/units`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/` | Все единицы |
| `GET` | `/{id}` | По ID |
| `POST` | `/` | Создать |
| `PUT` | `/{id}` | Обновить |
| `DELETE` | `/{id}` | Удалить |

### Классы перечислений (`/api/v1/enumeration-classes`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/` | Все классы перечислений |
| `GET` | `/{id}` | Класс по ID |
| `POST` | `/` | Создать класс |
| `PATCH` | `/{id}` | Обновить класс |
| `DELETE` | `/{id}` | Удалить класс (без перечислений) |
| `GET` | `/{id}/enumerations` | Все перечисления класса |

### Перечисления (`/api/v1/enumerations`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/{id}` | Перечисление со значениями |
| `POST` | `/` | Создать перечисление |
| `PATCH` | `/{id}` | Обновить перечисление |
| `DELETE` | `/{id}` | Удалить перечисление (без значений) |
| `GET` | `/{id}/values` | Список значений (по порядку) |
| `POST` | `/{id}/values` | Добавить значение |
| `PATCH` | `/{id}/values/{valueId}` | Редактировать значение |
| `DELETE` | `/{id}/values/{valueId}` | Удалить значение |
| `PATCH` | `/{id}/values/{valueId}/reorder` | Изменить порядок значения |

### Атрибуты узлов (`/api/v1/nodes/{nodeId}/attributes`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/` | Все выбранные значения для узла |
| `GET` | `/{enumerationId}` | Выбранное значение конкретного перечисления |
| `PUT` | `/` | Выбрать значение перечисления (создаёт или заменяет) |
| `DELETE` | `/{enumerationId}` | Снять выбор значения |

---

## Стек

| Компонент | Технология |
|-----------|-----------|
| Языки | Kotlin (сущности, DTO, сервисы) + Java (контроллеры) |
| Фреймворк | Spring Boot 3 |
| БД | PostgreSQL 15 |
| ORM | Spring Data JPA (Hibernate) |
| API-документация | springdoc-openapi (Swagger UI) |
| Сборка | Gradle (Kotlin DSL) + Version Catalog (TOML) |
| Тесты | JUnit 5 + Testcontainers + Mockito + MockMvc |
| Контейнеризация | Docker + Docker Compose |

---

## Архитектура

Layered Architecture — 4 слоя:

```
Controller  →  принимает HTTP-запросы, Swagger-аннотации   (Java)
    ↓
Service     →  бизнес-логика (CRUD, обход дерева, валидация) (Kotlin)
    ↓
Repository  →  доступ к БД (Spring Data JPA + CTE-запросы)  (Kotlin)
    ↓
Entity      →  JPA-сущности (маппинг на таблицы PostgreSQL)  (Kotlin)
```

---

## Структура проекта

```
src/main/kotlin/com/classifier/
├── ClassifierApplication.kt              — точка входа + OpenAPI-бин
├── entity/
│   ├── ClassifierNode.kt                 — вершина классификатора
│   ├── UnitOfMeasure.kt                  — единица измерения
│   ├── EnumerationClass.kt               — класс перечислений (Цвет, Размер…)
│   ├── Enumeration.kt                    — перечисление (Цвета телефонов)
│   ├── EnumerationValue.kt               — значение перечисления (Чёрный, Белый…)
│   └── NodeAttributeValue.kt             — выбранное значение для узла классификатора
├── dto/
│   ├── NodeRequests.kt                   — CreateNodeRequest, UpdateNodeRequest…
│   ├── NodeResponses.kt                  — NodeResponse, TreeNodeResponse
│   ├── UnitOfMeasureDtos.kt              — UnitOfMeasureRequest/Response
│   ├── EnumerationDtos.kt                — все DTO для перечислений
│   ├── NodeAttributeValueDtos.kt         — SelectEnumerationValueRequest/Response
│   └── CommonDtos.kt                     — ErrorResponse, ValidationResponse
├── repository/
│   ├── ClassifierNodeRepository.kt       — CTE-запросы для обхода дерева
│   ├── UnitOfMeasureRepository.kt
│   ├── EnumerationClassRepository.kt
│   ├── EnumerationRepository.kt
│   ├── EnumerationValueRepository.kt
│   └── NodeAttributeValueRepository.kt
├── service/
│   ├── ClassifierNodeService.kt          — CRUD, перемещение, переупорядочивание
│   ├── TreeTraversalService.kt           — потомки, предки, терминальные, циклы
│   ├── UnitOfMeasureService.kt
│   ├── EnumerationService.kt             — управление перечислениями и значениями
│   └── NodeAttributeValueService.kt      — выбор значения перечисления для узла
├── controller/
│   └── GlobalExceptionHandler.kt         — обработка ошибок (404, 409, 400, 422)
├── mapper/
│   └── NodeMapper.kt                     — Entity → DTO
└── exception/
    └── Exceptions.kt                     — все исключения приложения

src/main/java/com/classifier/controller/
├── ClassifierNodeController.java         — 14 REST-эндпоинтов
├── UnitOfMeasureController.java          — 5 REST-эндпоинтов
├── EnumerationClassController.java       — 6 REST-эндпоинтов
├── EnumerationController.java            — 9 REST-эндпоинтов
└── NodeAttributeValueController.java     — 4 REST-эндпоинта

src/main/resources/
├── application.yml                       — конфигурация (БД, JPA, Swagger)
├── data.sql                              — начальные данные (29 узлов, 4 класса, 6 перечислений, 24 значения)
└── procedures.sql                        — хранимые SQL-процедуры для работы с перечислениями

src/test/kotlin/com/classifier/
├── repository/                           — интеграционные тесты репозиториев (6 файлов)
├── service/                              — unit-тесты сервисов (5 файлов)
└── controller/                           — интеграционные тесты контроллеров (4 файла)
```

---

## Модель данных

```
unit_of_measure              classifier_node
├── id (PK)                  ├── id (PK)
├── code (UNIQUE)            ├── code (UNIQUE)
└── name                     ├── name
                             ├── parent_id (FK → self)
                             ├── sort_order
                             ├── unit_of_measure_id (FK)
                             ├── created_at
                             └── updated_at

enumeration_class            enumeration                  enumeration_value
├── id (PK)                  ├── id (PK)                  ├── id (PK)
├── code (UNIQUE)            ├── code (UNIQUE)            ├── code
├── name                     ├── name                     ├── name
├── description              ├── enumeration_class_id(FK) ├── enumeration_id (FK)
├── created_at               ├── classifier_node_id (FK)  ├── sort_order
└── updated_at               ├── created_at               ├── created_at
                             └── updated_at               └── updated_at

node_attribute_value
├── id (PK)
├── classifier_node_id (FK)
├── enumeration_id (FK)
├── enumeration_value_id (FK)
├── created_at
└── updated_at
  UNIQUE (classifier_node_id, enumeration_id)
```

**Паттерн хранения дерева:** Adjacency List + PostgreSQL `WITH RECURSIVE` CTE.

**Перечисления:** трёхуровневая схема — класс → перечисление → значение с порядком сортировки.

**Выбор атрибута:** один узел может иметь ровно одно выбранное значение для каждого перечисления.

---

## Начальные данные (`data.sql`)

| Тип | Количество | Примеры |
|-----|-----------|---------|
| Узлы классификатора | 29 | Электроника → Смартфоны → Apple → iPhone 16 |
| Единицы измерения | 4 | PCS, KG, M, PACK |
| Классы перечислений | 4 | COLOR, STORAGE, CONNECTOR, OS |
| Перечисления | 6 | Цвета телефонов, Память смартфонов, Разъёмы кабелей… |
| Значения перечислений | 24 | Чёрный, Белый, 128 ГБ, USB-C, iOS… |
