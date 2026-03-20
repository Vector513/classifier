# План: Классификатор изделий — Интернет-магазин электроники

## Context

Практическая работа ПР №1 по архитектуре ПО (ЛЭТИ, преподаватель Дубенецкий В.А.).
Задача — разработать сервис для работы с классификатором изделий (древовидный справочник).
Тематика: **Интернет-магазин электроники** (категории товаров: Смартфоны → Apple → iPhone 15 и т.д.).
Команда: 3 разработчика (Kotlin, Java, C++ — вспомогательный).

---

## Стек технологий

| Компонент | Технология |
|-----------|-----------|
| Язык | Kotlin (ядро) + Java (API) |
| Фреймворк | Spring Boot 3.x |
| ORM | Spring Data JPA (Hibernate) |
| БД | PostgreSQL 15+ |
| Сборка | Gradle (Kotlin DSL) |
| API-доки | springdoc-openapi + Swagger UI |
| Схема БД | Hibernate ddl-auto (автогенерация из Entity) |
| Тесты | JUnit 5 + Testcontainers |

### Почему так распределены языки

**Kotlin (ядро):** data class для DTO (в 3-5x короче Java), Entity с JPA-плагинами (all-open, no-arg), extension functions для маппинга, null safety для бизнес-логики.

**Java (API-поверхность):** контроллеры с многословными Swagger-аннотациями (@Operation, @ApiResponse, @Schema), Repository-интерфейсы (Spring Data JPA), конфигурация OpenAPI.

**Dev 3 (C++):** C++ не работает на JVM, поэтому привлекается к задачам, не требующим конкретного языка: Docker, тестовые данные (data.sql), UML-диаграммы, отчёт.

---

## Архитектура

```
Swagger UI
    │ HTTP/REST
    ▼
Controller Layer (Java)                    ← Java-разраб
  ClassifierNodeController
  UnitOfMeasureController
  GlobalExceptionHandler
    │
    ▼
Service Layer (Kotlin)                     ← Kotlin-разраб
  ClassifierNodeService
  TreeTraversalService
  TreeValidationService
    │
    ▼
Repository Layer (Java)                    ← Java-разраб
  ClassifierNodeRepository
  UnitOfMeasureRepository
    │
    ▼
PostgreSQL
  classifier_node
  unit_of_measure
```

**Kotlin-слой (ядро):** Entity, DTO, Mapper, Service, тесты сервисов
**Java-слой (API):** Controller, Repository, Config, ExceptionHandler, тесты контроллеров

### Dependency Injection (DI)

**Паттерн:** Layered Architecture (3-слойная) с зависимостями сверху вниз:

```
Controller → зависит от Service
Service    → зависит от Repository
Repository → ни от чего (только от Entity)
```

**Реализация:** Spring IoC-контейнер + Constructor Injection (внедрение через конструктор).
Каждый слой помечается аннотацией (`@RestController`, `@Service`, `@Repository`),
Spring автоматически создаёт экземпляры и внедряет зависимости — `@Autowired` не нужен.

```kotlin
// Repository — Spring генерирует реализацию интерфейса автоматически
@Repository
interface ClassifierNodeRepository : JpaRepository<ClassifierNode, Long>

// Service — получает Repository через конструктор
@Service
class ClassifierNodeService(
    private val nodeRepository: ClassifierNodeRepository  // Spring внедрит
)

// Controller — получает Service через конструктор
@RestController
class ClassifierNodeController(
    private val nodeService: ClassifierNodeService        // Spring внедрит
)
```

---

## Структура проекта

```
classifier/
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
│
└── src/
    ├── main/
    │   ├── kotlin/com/classifier/
    │   │   ├── ClassifierApplication.kt          ← Kotlin-разраб
    │   │   ├── entity/
    │   │   │   ├── ClassifierNode.kt              ← Kotlin-разраб
    │   │   │   └── UnitOfMeasure.kt               ← Kotlin-разраб
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   ├── CreateNodeRequest.kt       ← Kotlin-разраб
    │   │   │   │   ├── UpdateNodeRequest.kt       ← Kotlin-разраб
    │   │   │   │   ├── MoveNodeRequest.kt         ← Kotlin-разраб
    │   │   │   │   └── ReorderRequest.kt          ← Kotlin-разраб
    │   │   │   └── response/
    │   │   │       ├── NodeResponse.kt            ← Kotlin-разраб
    │   │   │       ├── TreeNodeResponse.kt        ← Kotlin-разраб
    │   │   │       ├── UnitOfMeasureResponse.kt   ← Kotlin-разраб
    │   │   │       └── ValidationResponse.kt      ← Kotlin-разраб
    │   │   ├── mapper/
    │   │   │   └── NodeMapper.kt                  ← Kotlin-разраб (extension funs)
    │   │   └── service/
    │   │       ├── ClassifierNodeService.kt       ← Kotlin-разраб
    │   │       ├── TreeTraversalService.kt        ← Kotlin-разраб
    │   │       └── TreeValidationService.kt       ← Kotlin-разраб
    │   │
    │   ├── java/com/classifier/
    │   │   ├── config/
    │   │   │   └── OpenApiConfig.java             ← Java-разраб
    │   │   ├── controller/
    │   │   │   ├── ClassifierNodeController.java  ← Java-разраб
    │   │   │   ├── UnitOfMeasureController.java   ← Java-разраб
    │   │   │   └── GlobalExceptionHandler.java    ← Java-разраб
    │   │   └── repository/
    │   │       ├── ClassifierNodeRepository.java  ← Java-разраб
    │   │       └── UnitOfMeasureRepository.java   ← Java-разраб
    │   │
    │   └── resources/
    │       ├── application.yml                    ← Java-разраб
    │       └── data.sql                           ← Dev 3 (C++) — тестовые данные
    │
    └── test/
        ├── kotlin/com/classifier/service/
        │   ├── ClassifierNodeServiceTest.kt       ← Kotlin-разраб
        │   └── TreeValidationServiceTest.kt       ← Kotlin-разраб
        └── java/com/classifier/controller/
            └── ClassifierNodeControllerTest.java  ← Dev 3 (C++)
```

---

## Распределение задач

### Kotlin-разраб (14 файлов — ядро приложения)

| # | Задача | Детали |
|---|--------|--------|
| 1 | Entity: `ClassifierNode.kt`, `UnitOfMeasure.kt` | JPA-сущности с Kotlin-плагинами (all-open, no-arg) |
| 2 | DTO: 7 data class файлов | Request/Response модели, Jakarta Validation аннотации |
| 3 | Mapper: `NodeMapper.kt` | Extension functions `Entity.toResponse()`, `Request.toEntity()` |
| 4 | `ClassifierNodeService.kt` | CRUD, перемещение, переупорядочивание, удаление |
| 5 | `TreeTraversalService.kt` | Поиск потомков, предков, терминальных, построение дерева |
| 6 | `TreeValidationService.kt` | Обнаружение циклов (DFS), проверка уникальности кодов |
| 7 | `ClassifierApplication.kt` | Точка входа |
| 8 | Тесты сервисов (2 файла) | Моки репозиториев, проверка бизнес-логики |

### Java-разраб (7 файлов — API + инфраструктура)

| # | Задача | Детали |
|---|--------|--------|
| 1 | `build.gradle.kts` + `settings.gradle.kts` | Инициализация проекта, зависимости, Kotlin-плагины |
| 2 | `ClassifierNodeRepository.java` | Spring Data JPA + `@Query` с рекурсивными CTE |
| 3 | `UnitOfMeasureRepository.java` | Стандартный CRUD-репозиторий |
| 4 | `ClassifierNodeController.java` | 14 эндпоинтов, подробные Swagger-аннотации |
| 5 | `UnitOfMeasureController.java` | 5 эндпоинтов, Swagger-аннотации |
| 6 | `GlobalExceptionHandler.java` | `@ControllerAdvice`, ErrorResponse DTO |
| 7 | `OpenApiConfig.java` | Настройка Swagger UI: теги, описания, группы, примеры |
| 8 | `application.yml` | PostgreSQL, JPA (ddl-auto), springdoc конфигурация |

### Dev 3 — C++ разраб (вспомогательные задачи, язык не важен)

| # | Задача | Детали |
|---|--------|--------|
| 1 | `docker-compose.yml` | PostgreSQL + pgAdmin |
| 2 | `data.sql` — начальные данные | Единицы измерения + каталог электроники |
| 3 | `V3__insert_test_data.sql` | Реалистичный каталог электроники (30-50 узлов) |
| 4 | Интеграционные тесты контроллеров | Testcontainers + MockMvc, happy path |
| 5 | UML-диаграммы | Use Case, Class Diagram, ER Diagram |
| 6 | Отчёт (.docx, 20+ стр.) | Пояснительная записка по шаблону из задания |

---

## Модель данных

### classifier_node

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | ID |
| code | VARCHAR(100) UNIQUE NOT NULL | Уникальный код ("PHONES", "PHONES-APPLE") |
| name | VARCHAR(255) NOT NULL | Наименование ("Смартфоны", "Apple") |
| parent_id | BIGINT FK → self NULL | Родитель (NULL = корень) |
| sort_order | INT NOT NULL DEFAULT 0 | Порядок среди соседей |
| unit_of_measure_id | BIGINT FK → unit_of_measure NULL | Единица измерения |
| created_at | TIMESTAMP NOT NULL | Создание |
| updated_at | TIMESTAMP NOT NULL | Обновление |

**Индексы:**
- `idx_node_parent_id` — по parent_id (выборка потомков)
- `idx_node_code` — уникальный индекс по code
- `idx_node_sort_order` — по (parent_id, sort_order)

**Паттерн хранения дерева:** Adjacency List + рекурсивные CTE PostgreSQL.

### unit_of_measure

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | ID |
| code | VARCHAR(50) UNIQUE NOT NULL | Код ("PCS", "KG") |
| name | VARCHAR(255) NOT NULL | Наименование ("штуки", "килограммы") |

### Пример дерева — Интернет-магазин электроники

```
Электроника (ELECTRONICS)
├── Смартфоны (PHONES)               ← шт.
│   ├── Apple (PHONES-APPLE)
│   │   ├── iPhone 16 (PHONES-APPLE-IP16)
│   │   └── iPhone 15 (PHONES-APPLE-IP15)
│   ├── Samsung (PHONES-SAMSUNG)
│   │   ├── Galaxy S24 (PHONES-SAMSUNG-S24)
│   │   └── Galaxy A55 (PHONES-SAMSUNG-A55)
│   └── Xiaomi (PHONES-XIAOMI)
├── Ноутбуки (LAPTOPS)               ← шт.
│   ├── Игровые (LAPTOPS-GAMING)
│   ├── Для работы (LAPTOPS-WORK)
│   └── Ультрабуки (LAPTOPS-ULTRA)
├── Аксессуары (ACCESSORIES)
│   ├── Наушники (ACC-HEADPHONES)     ← шт.
│   ├── Чехлы (ACC-CASES)             ← шт.
│   ├── Зарядные устройства (ACC-CHARGERS) ← шт.
│   └── Кабели (ACC-CABLES)           ← м / шт.
├── Комплектующие (COMPONENTS)
│   ├── Процессоры (COMP-CPU)
│   ├── Видеокарты (COMP-GPU)
│   ├── Оперативная память (COMP-RAM)
│   └── SSD/HDD (COMP-STORAGE)
└── Бытовая техника (HOME-TECH)
    ├── Телевизоры (HOME-TV)
    ├── Пылесосы (HOME-VACUUM)
    └── Микроволновки (HOME-MICROWAVE)
```

---

## REST API — Эндпоинты

### Classifier Nodes — `/api/v1/nodes`

| Метод | URL | Описание | Body | Response |
|-------|-----|----------|------|----------|
| POST | `/api/v1/nodes` | Создать вершину | `CreateNodeRequest` | `NodeResponse` 201 |
| GET | `/api/v1/nodes` | Корневые вершины | — | `List<NodeResponse>` 200 |
| GET | `/api/v1/nodes/{id}` | Получить по ID | — | `NodeResponse` 200 |
| PUT | `/api/v1/nodes/{id}` | Обновить | `UpdateNodeRequest` | `NodeResponse` 200 |
| DELETE | `/api/v1/nodes/{id}` | Удалить (запрет если есть потомки) | — | 204 |
| GET | `/api/v1/nodes/{id}/children` | Прямые потомки | — | `List<NodeResponse>` 200 |
| PATCH | `/api/v1/nodes/{id}/move` | Переместить (смена родителя) | `MoveNodeRequest` | `NodeResponse` 200 |
| PATCH | `/api/v1/nodes/{id}/reorder` | Изменить порядок | `ReorderRequest` | 200 |
| GET | `/api/v1/nodes/{id}/descendants` | Все потомки (рекурсивно) | — | `List<NodeResponse>` 200 |
| GET | `/api/v1/nodes/{id}/ancestors` | Все предки (до корня) | — | `List<NodeResponse>` 200 |
| GET | `/api/v1/nodes/{id}/terminals` | Терминальные классы | — | `List<NodeResponse>` 200 |
| GET | `/api/v1/nodes/tree` | Всё дерево (вложенное) | — | `List<TreeNodeResponse>` 200 |
| POST | `/api/v1/nodes/validate-cycles` | Проверка на циклы | — | `ValidationResponse` 200 |
| GET | `/api/v1/nodes/search?query=...` | Поиск по коду/имени | query | `List<NodeResponse>` 200 |

### Units of Measure — `/api/v1/units`

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/units` | Создать |
| GET | `/api/v1/units` | Список всех |
| GET | `/api/v1/units/{id}` | По ID |
| PUT | `/api/v1/units/{id}` | Обновить |
| DELETE | `/api/v1/units/{id}` | Удалить |

### DTO

```
CreateNodeRequest {
    code: String          // уникальное обозначение
    name: String          // наименование
    parentId: Long?       // null = корневая вершина
    unitOfMeasureId: Long? // единица измерения
}

UpdateNodeRequest {
    code: String?
    name: String?
    unitOfMeasureId: Long?
}

MoveNodeRequest {
    newParentId: Long?    // null = сделать корнем
}

ReorderRequest {
    newSortOrder: Int     // новая позиция среди соседей
}

NodeResponse {
    id: Long
    code: String
    name: String
    parentId: Long?
    sortOrder: Int
    unitOfMeasure: UnitOfMeasureResponse?
    isTerminal: Boolean   // true если нет потомков
    childrenCount: Int
    createdAt: Instant
    updatedAt: Instant
}

TreeNodeResponse {
    id: Long
    code: String
    name: String
    unitOfMeasure: UnitOfMeasureResponse?
    isTerminal: Boolean
    children: List<TreeNodeResponse>  // вложенная структура
}

UnitOfMeasureResponse {
    id: Long
    code: String
    name: String
}

ValidationResponse {
    valid: Boolean
    cycles: List<List<Long>>  // список циклов (ID вершин)
}
```

---

## Пошаговый порядок выполнения

Привязка к пунктам задания (1.2. Содержание работы).

---

### Этап 1. Анализ предметной области (п. 1, 7)

> «Провести анализ фрагментов предметной области... с представлением примеров справочников и моделей классов этапа анализа»
> «Выбрать представительный пример фрагмента справочника»

**Что сделать:**
- Описать предметную область: интернет-магазин электроники, структура каталога товаров
- Привести пример реального справочника (дерево категорий из раздела «Пример дерева» выше)
- Построить модель классов этапа **анализа** (концептуальная, без технических деталей)

**Диаграмма — Class Diagram (этап анализа):**
```
┌─────────────────┐       ┌─────────────────────┐
│ КлассИзделия    │       │ ЕдиницаИзмерения    │
├─────────────────┤       ├─────────────────────┤
│ код             │       │ код                 │
│ наименование    │       │ наименование        │
│ порядокСортировки│      └─────────────────────┘
├─────────────────┤
│ добавитьПотомка()│──────── * измеряетсяВ ──────►
│ переместить()    │
│ удалить()        │
│ найтиПотомков() │◄──┐
│ найтиПредков()  │   │ родитель
│ проверитьЦиклы()│   │ 0..1
└─────────────────┘───┘
        │ содержит
        ▼ 0..*
  (рекурсивная связь — потомки)
```

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| Описание предметной области | Dev 3 (C++) | Текст в отчёте (2-3 стр.) |
| Пример справочника электроники | Dev 3 (C++) | Таблица/дерево в отчёте |
| Диаграмма классов этапа анализа | Dev 3 (C++) | UML Class Diagram |

---

### Этап 2. Функциональные требования (п. 2)

> «Сформировать требования к частям проекта... с обязательным представлением диаграмм вариантов использования»

**Что сделать:**
- Описать акторов: Администратор каталога (основной), Система (для валидации)
- Описать варианты использования (Use Cases) на основе функциональных требований

**Диаграмма — Use Case Diagram:**

```
                    ┌──────────────────────────────────────────┐
                    │       Классификатор изделий              │
                    │                                          │
 ┌──────────┐      │  (UC1) Добавить вершину                  │
 │Администра│──────│  (UC2) Переместить вершину (смена родит.) │
 │тор       │──────│  (UC3) Изменить порядок потомков          │
 │каталога  │──────│  (UC4) Удалить вершину                    │
 │          │──────│  (UC5) Указать единицу измерения          │
 │          │──────│  (UC6) Найти всех потомков                │
 │          │──────│  (UC7) Найти всех предков                 │
 │          │──────│  (UC8) Найти терминальные классы          │
 │          │──────│  (UC9) Поиск по коду/имени                │
 └──────────┘      │  (UC10) Просмотр дерева целиком           │
                    │                                          │
                    │  (UC11) Диагностика циклов    ◄──────┐   │
                    │  (UC12) Проверка уникальности ◄──────┤   │
                    └──────────────────────────────────┼───┘   │
                                                       │       │
                                                  ┌────┴─────┐ │
                                                  │ Система  │─┘
                                                  └──────────┘
```

**Описание каждого UC:**

| UC | Название | Предусловие | Основной сценарий | Исключения |
|----|----------|-------------|-------------------|------------|
| UC1 | Добавить вершину | — | Указать код, имя, родителя (опц.), ед. изм. (опц.) → создать | Код не уникален → 409 |
| UC2 | Переместить вершину | Вершина существует | Указать нового родителя → обновить parent_id | Перемещение в собственного потомка → 400 |
| UC3 | Изменить порядок | Вершина существует | Указать новый sort_order → пересчитать порядок соседей | — |
| UC4 | Удалить вершину | Вершина существует | Удалить если нет потомков | Есть потомки → 409 Conflict |
| UC5 | Указать ед. измерения | Вершина существует | Привязать unit_of_measure_id | Ед. измерения не найдена → 404 |
| UC6 | Найти потомков | Вершина существует | Рекурсивный запрос → список | — |
| UC7 | Найти предков | Вершина существует | Рекурсивный запрос → список до корня | — |
| UC8 | Терминальные классы | Вершина существует | Потомки без собственных потомков | — |
| UC9 | Поиск по коду/имени | — | LIKE-запрос по code и name | — |
| UC10 | Просмотр дерева | — | Рекурсивная сборка вложенной структуры | — |
| UC11 | Диагностика циклов | — | DFS по всему дереву → список циклов | — |
| UC12 | Проверка уникальности | — | Проверка UNIQUE constraint на code | — |

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| Диаграмма Use Case | Dev 3 (C++) | UML Use Case Diagram |
| Описание UC1-UC12 | Dev 3 (C++) | Таблица в отчёте |

---

### Этап 3. Модель классов этапа проектирования (п. 3)

> «Разработать необходимые модели классов этапа проектирования с пояснениями принятых решений»

**Что сделать:**
- Построить проектную модель классов (с учётом Spring Boot, JPA, слоёв архитектуры)
- Пояснить решения: почему Layered Architecture, почему Adjacency List, почему Constructor Injection

**Диаграмма — Class Diagram (этап проектирования):**

```
┌─────────────────────────────────────┐
│ «@RestController»                   │
│ ClassifierNodeController            │
├─────────────────────────────────────┤
│ - nodeService: ClassifierNodeService│
├─────────────────────────────────────┤
│ + createNode(req): NodeResponse     │
│ + getNode(id): NodeResponse         │
│ + updateNode(id, req): NodeResponse │
│ + deleteNode(id): void              │
│ + getChildren(id): List<NodeResp>   │
│ + moveNode(id, req): NodeResponse   │
│ + reorder(id, req): void            │
│ + getDescendants(id): List<NodeResp>│
│ + getAncestors(id): List<NodeResp>  │
│ + getTerminals(id): List<NodeResp>  │
│ + getTree(): List<TreeNodeResp>     │
│ + validateCycles(): ValidationResp  │
│ + search(query): List<NodeResp>     │
└──────────────┬──────────────────────┘
               │ использует
               ▼
┌─────────────────────────────────────┐
│ «@Service»                          │
│ ClassifierNodeService               │
├─────────────────────────────────────┤
│ - nodeRepo: ClassifierNodeRepository│
│ - traversal: TreeTraversalService   │
│ - validation: TreeValidationService │
├─────────────────────────────────────┤
│ + create(req): ClassifierNode       │
│ + update(id, req): ClassifierNode   │
│ + delete(id): void                  │
│ + move(id, newParentId): Node       │
│ + reorder(id, newOrder): void       │
└──────────────┬──────────────────────┘
               │ использует
               ▼
┌─────────────────────────────────────┐
│ «@Repository»                       │
│ ClassifierNodeRepository            │
│ extends JpaRepository<Node, Long>   │
├─────────────────────────────────────┤
│ + findByParentIdIsNull(): List<Node>│
│ + findByParentId(id): List<Node>    │
│ + findDescendants(id): List<Node>   │
│ + findAncestors(id): List<Node>     │
│ + findTerminals(id): List<Node>     │
│ + existsByCode(code): Boolean       │
│ + searchByQuery(q): List<Node>      │
└──────────────┬──────────────────────┘
               │ управляет
               ▼
┌─────────────────────────────────────┐
│ «@Entity»                           │
│ ClassifierNode                      │
├─────────────────────────────────────┤
│ - id: Long                          │
│ - code: String                      │
│ - name: String                      │
│ - parent: ClassifierNode?           │
│ - children: List<ClassifierNode>    │
│ - sortOrder: Int                    │
│ - unitOfMeasure: UnitOfMeasure?     │
│ - createdAt: Instant                │
│ - updatedAt: Instant                │
└─────────────────────────────────────┘

┌──────────────────────────┐    ┌──────────────────────────┐
│ TreeTraversalService     │    │ TreeValidationService    │
├──────────────────────────┤    ├──────────────────────────┤
│ - nodeRepo: ...Repository│    │ - nodeRepo: ...Repository│
├──────────────────────────┤    ├──────────────────────────┤
│ + getDescendants(id)     │    │ + detectCycles()         │
│ + getAncestors(id)       │    │ + validateCodeUnique()   │
│ + getTerminals(id)       │    └──────────────────────────┘
│ + buildTree()            │
└──────────────────────────┘
```

**Пояснения принятых решений (включить в отчёт):**

| Решение | Обоснование |
|---------|-------------|
| Layered Architecture (3 слоя) | Чёткое разделение ответственности, простота тестирования каждого слоя отдельно |
| Adjacency List для дерева | Простейший паттерн, PostgreSQL WITH RECURSIVE эффективно решает задачи обхода |
| Constructor Injection (DI) | Spring-стандарт, иммутабельность зависимостей, удобство тестирования с моками |
| Запрет удаления с потомками | Безопасность данных, предсказуемое поведение, простота реализации |
| Kotlin для ядра, Java для API | data class для DTO, null safety для логики; Java — для многословных Swagger-аннотаций |

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| Диаграмма классов проектирования | Kotlin-разраб + Dev 3 | UML Class Diagram |
| Пояснения решений | Kotlin-разраб | Текст в отчёте (2-3 стр.) |

---

### Этап 4. Модель хранения данных (п. 4)

> «Разработать модели хранения данных с представлением диаграмм Сущность-Связь и описанием введённых сущностей, атрибутов и связей»

**Что сделать:**
- Построить ER-диаграмму
- Описать каждую сущность, атрибуты и связи

**Диаграмма — Entity-Relationship (ER):**

```
┌──────────────────────────┐         ┌──────────────────────┐
│ unit_of_measure          │         │ classifier_node      │
├──────────────────────────┤         ├──────────────────────┤
│ PK id: BIGSERIAL         │    ┌───►│ PK id: BIGSERIAL     │
│ UK code: VARCHAR(50)     │    │    │ UK code: VARCHAR(100)│
│    name: VARCHAR(255)    │    │    │    name: VARCHAR(255)│
└──────────┬───────────────┘    │    │ FK parent_id ────────┼──┐
           │                    │    │    sort_order: INT   │  │
           │ 1          0..*    │    │ FK unit_of_measure_id│  │
           └────────────────────┘    │    created_at: TS    │  │
             измеряется в            │    updated_at: TS    │  │
                                     └────────────┬─────────┘  │
                                                  │ 0..*       │
                                                  └────────────┘
                                                  родитель-потомок
                                                  (рекурсивная связь)
```

**Описание связей:**

| Связь | Тип | Описание |
|-------|-----|----------|
| classifier_node → classifier_node (parent_id) | Многие-к-одному (рекурсивная) | Вершина имеет 0 или 1 родителя, может иметь 0..* потомков |
| classifier_node → unit_of_measure | Многие-к-одному | Вершина может иметь 0 или 1 единицу измерения, одна ед. изм. может быть у многих вершин |

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| ER-диаграмма | Dev 3 (C++) | UML ER Diagram |
| Описание сущностей/атрибутов/связей | Dev 3 (C++) | Таблица в отчёте |

---

### Этап 5. Создание БД и метаданные (п. 5)

> «Создать БД и занести необходимые метаданные»

**Что сделать:**
- `docker-compose.yml` для поднятия PostgreSQL
- Entity-классы на Kotlin (Hibernate автоматически создаёт таблицы через `ddl-auto`)
- Тестовые данные: каталог электроники (30-50 узлов) через `data.sql` или `@PostConstruct`

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| `docker-compose.yml` | Dev 3 (C++) | PostgreSQL + pgAdmin |
| Entity-классы | Kotlin-разраб | `ClassifierNode.kt`, `UnitOfMeasure.kt` |
| `application.yml` (ddl-auto, datasource) | Java-разраб | Конфигурация (см. Приложение E) |
| `data.sql` — тестовые данные | Dev 3 (C++) | Каталог электроники (30-50 INSERT) |

---

### Этап 6. SQL-процедуры / Сервисный слой (п. 6)

> «Разработать и описать необходимый набор SQL-процедур, обеспечивающий поддержку описанных выше требований»

В нашей архитектуре SQL-процедуры реализованы как:
- **Repository** — `@Query` с рекурсивными CTE (для потомков/предков/терминальных)
- **Service** — бизнес-логика на Kotlin (CRUD, перемещение, валидация циклов)

**Маппинг требований → реализация:**

| Функциональное требование | Реализация | Слой |
|--------------------------|------------|------|
| Добавление вершины (терм./промеж.) | `ClassifierNodeService.create()` | Service |
| Перемещение (смена родителя) | `ClassifierNodeService.move()` + проверка циклов | Service |
| Изменение порядка потомков | `ClassifierNodeService.reorder()` | Service |
| Удаление вершины | `ClassifierNodeService.delete()` + проверка потомков | Service |
| Указание ед. измерения | `ClassifierNodeService.update()` | Service |
| Диагностика циклов | `TreeValidationService.detectCycles()` — DFS | Service |
| Уникальность обозначений | `ClassifierNodeRepository.existsByCode()` + UNIQUE constraint | Repository + БД |
| Поиск всех потомков | `ClassifierNodeRepository.findDescendants()` — WITH RECURSIVE CTE | Repository |
| Поиск всех предков | `ClassifierNodeRepository.findAncestors()` — WITH RECURSIVE CTE | Repository |
| Поиск терминальных классов | `ClassifierNodeRepository.findTerminals()` — CTE + LEFT JOIN | Repository |

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| Repository с CTE-запросами | Java-разраб | `ClassifierNodeRepository.java` |
| CRUD + перемещение + порядок | Kotlin-разраб | `ClassifierNodeService.kt` |
| Обход дерева | Kotlin-разраб | `TreeTraversalService.kt` |
| Валидация циклов + уникальности | Kotlin-разраб | `TreeValidationService.kt` |
| Контроллеры + Swagger | Java-разраб | `ClassifierNodeController.java` и др. |

---

### Этап 7. Тестирование (п. 8, 11)

> «Выполнить тестирование работоспособности реализованного фрагмента проекта с протоколированием результатов»
> «Продемонстрировать работоспособность разработанного фрагмента приложения»

**Что сделать:**
- Unit-тесты сервисов (Kotlin, JUnit 5)
- Интеграционные тесты контроллеров (Testcontainers + MockMvc)
- Ручное тестирование через Swagger UI + скриншоты

**Тест-план:**

| # | Тест | Тип | Ожидаемый результат |
|---|------|-----|---------------------|
| 1 | Создать корневую категорию "Электроника" | API | 201 Created, id присвоен |
| 2 | Создать подкатегорию "Смартфоны" с parent=Электроника | API | 201, parentId заполнен |
| 3 | Создать вершину с дублирующим code | API | 409 Conflict |
| 4 | Получить дерево целиком | API | Вложенная структура с children |
| 5 | Переместить "Смартфоны" в другую категорию | API | 200, parentId обновлён |
| 6 | Попытка переместить вершину в собственного потомка | API | 400 Bad Request |
| 7 | Изменить порядок потомков | API | 200, sort_order обновлён |
| 8 | Удалить вершину без потомков | API | 204 No Content |
| 9 | Попытка удалить вершину с потомками | API | 409 Conflict |
| 10 | Найти всех потомков "Электроника" | API | Список всех узлов рекурсивно |
| 11 | Найти предков "iPhone 16" | API | [PHONES-APPLE, PHONES, ELECTRONICS] |
| 12 | Найти терминальные классы "PHONES" | API | [IP16, IP15, S24, A55, ...] |
| 13 | Проверка на циклы (корректное дерево) | API | `{ valid: true, cycles: [] }` |
| 14 | Поиск по запросу "apple" | API | Список совпадений |

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| Unit-тесты сервисов | Kotlin-разраб | `*ServiceTest.kt` |
| Интеграционные тесты | Dev 3 (C++) | `ClassifierNodeControllerTest.java` |
| Скриншоты Swagger UI | Dev 3 (C++) | Скриншоты в отчёте |

---

### Этап 8. Оформление отчёта (п. 9, 10)

> «Оформить все материалы в виде отчетов. Отчет представить в виде файла *.docx»

**Структура отчёта (из задания):**

| # | Раздел | Содержание | Объём |
|---|--------|-----------|-------|
| 1 | Введение | Цель работы, постановка задачи | 1 стр. |
| 2 | Анализ предметной области | Описание интернет-магазина электроники, пример справочника, модель классов анализа | 3-4 стр. |
| 3 | Разработка объектной модели этапа проектирования | Диаграмма классов проектирования, пояснения решений (архитектура, DI, паттерн дерева) | 3-4 стр. |
| 4 | Разработка модели хранения | ER-диаграмма, описание таблиц/атрибутов/связей, индексы | 2-3 стр. |
| 5 | Разработка основных процедур | Описание API-эндпоинтов, код Repository-запросов (CTE), код сервисов | 4-5 стр. |
| 6 | Результаты тестирования | Тест-план, скриншоты Swagger UI, протокол результатов | 3-4 стр. |
| 7 | Заключение | Итоги, что реализовано | 1 стр. |
| 8 | Список использованных источников | Книги из задания + документация Spring/PostgreSQL | 1 стр. |
| | **Итого** | | **~20 стр.** |

| Задача | Исполнитель | Результат |
|--------|-------------|-----------|
| Сбор материалов (диаграммы, код, скриншоты) | Все | — |
| Оформление отчёта | Dev 3 (C++) | `Отчёт_ПР1.docx` |

---

## Необходимые диаграммы (сводка)

| # | Диаграмма | Тип UML | Этап | Исполнитель |
|---|-----------|---------|------|-------------|
| 1 | Модель классов этапа анализа | Class Diagram | Этап 1 | Dev 3 |
| 2 | Диаграмма вариантов использования | Use Case Diagram | Этап 2 | Dev 3 |
| 3 | Модель классов этапа проектирования | Class Diagram | Этап 3 | Kotlin + Dev 3 |
| 4 | Диаграмма Сущность-Связь | ER Diagram | Этап 4 | Dev 3 |

Инструменты для диаграмм: StarUML, draw.io, PlantUML (на выбор).

**Готовые PlantUML-файлы** (в `docs/diagrams/`):
- `1_class_analysis.puml` — модель классов этапа анализа
- `2_use_case.puml` — диаграмма вариантов использования (UC1-UC12)
- `3_class_design.puml` — модель классов этапа проектирования (все слои)
- `4_er_diagram.puml` — диаграмма Сущность-Связь

Рендеринг: открыть в IDE с PlantUML-плагином или на [plantuml.com](https://www.plantuml.com/plantuml/uml).

---

## Понедельный график

```
Неделя 1 — Анализ + Фундамент (Этапы 1-2, 5):
  [Dev 3]  Анализ предметной области, пример справочника, Use Case диаграмма
  [Java]   Инициализация проекта: build.gradle.kts, docker-compose, application.yml
  [Kotlin] Entity, DTO (data classes), Application.kt

Неделя 2 — Проектирование + Реализация (Этапы 3-4, 6):
  [Dev 3]  Диаграмма классов проектирования, ER-диаграмма, data.sql
  [Java]   Repository (CTE-запросы), Controller + Swagger-аннотации
  [Kotlin] Сервисы: CRUD, перемещение, переупорядочивание, Mapper

Неделя 3 — Дерево + Тестирование (Этапы 6-7):
  [Dev 3]  Интеграционные тесты, скриншоты Swagger UI
  [Java]   OpenApiConfig, GlobalExceptionHandler, доработка Swagger
  [Kotlin] TreeTraversalService, TreeValidationService + unit-тесты

Неделя 4 — Отчёт + Защита (Этап 8):
  [Dev 3]  Оформление отчёта (.docx, 20+ стр.)
  [Все]    Финальная интеграция, ревью, подготовка к демонстрации
```

---

## Решение по удалению

Удаление вершины **запрещено**, если у неё есть потомки → возвращать 409 Conflict с сообщением.
Сначала нужно удалить/переместить потомков. Это безопаснее и проще в реализации.

---

## Верификация

1. `docker-compose up -d` → PostgreSQL поднимается
2. `./gradlew bootRun` → приложение стартует без ошибок
3. Swagger UI на `http://localhost:8080/swagger-ui.html` — все 19 эндпоинтов видны
4. Через Swagger UI: создать корневую категорию → добавить потомков → проверить дерево
5. `./gradlew test` → все unit + integration тесты проходят
6. Проверить каждое функциональное требование из задания:
   - Добавление терминальной/промежуточной вершины
   - Перемещение (смена родителя)
   - Изменение порядка потомков
   - Удаление (запрет при наличии потомков)
   - Единица измерения
   - Диагностика циклов
   - Уникальность кодов
   - Поиск потомков/предков/терминальных

---

## Приложение A. Зависимости — build.gradle.kts

```kotlin
plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"      // all-open для @Service, @Controller и т.д.
    kotlin("plugin.jpa") version "1.9.24"          // no-arg для @Entity
}

group = "com.classifier"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // Тесты
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
}
```

---

## Приложение B. Примеры SQL-запросов (Repository @Query)

### Поиск всех потомков (WITH RECURSIVE CTE)

```sql
-- findDescendants(nodeId)
WITH RECURSIVE descendants AS (
    SELECT * FROM classifier_node WHERE parent_id = :nodeId
    UNION ALL
    SELECT cn.* FROM classifier_node cn
    JOIN descendants d ON cn.parent_id = d.id
)
SELECT * FROM descendants ORDER BY id
```

### Поиск всех предков (до корня)

```sql
-- findAncestors(nodeId)
WITH RECURSIVE ancestors AS (
    SELECT * FROM classifier_node WHERE id = (
        SELECT parent_id FROM classifier_node WHERE id = :nodeId
    )
    UNION ALL
    SELECT cn.* FROM classifier_node cn
    JOIN ancestors a ON cn.id = a.parent_id
)
SELECT * FROM ancestors ORDER BY id
```

### Поиск терминальных классов (листья)

```sql
-- findTerminals(nodeId) — потомки, у которых нет своих потомков
WITH RECURSIVE descendants AS (
    SELECT * FROM classifier_node WHERE parent_id = :nodeId
    UNION ALL
    SELECT cn.* FROM classifier_node cn
    JOIN descendants d ON cn.parent_id = d.id
)
SELECT d.* FROM descendants d
LEFT JOIN classifier_node child ON child.parent_id = d.id
WHERE child.id IS NULL
```

### Поиск по коду/имени

```sql
-- searchByQuery(query)
SELECT * FROM classifier_node
WHERE LOWER(code) LIKE LOWER(CONCAT('%', :query, '%'))
   OR LOWER(name) LIKE LOWER(CONCAT('%', :query, '%'))
ORDER BY code
```

---

## Приложение C. Примеры JSON (request/response)

### POST /api/v1/nodes — Создать вершину

**Request:**
```json
{
    "code": "PHONES-APPLE",
    "name": "Apple",
    "parentId": 2,
    "unitOfMeasureId": 1
}
```

**Response (201):**
```json
{
    "id": 5,
    "code": "PHONES-APPLE",
    "name": "Apple",
    "parentId": 2,
    "sortOrder": 0,
    "unitOfMeasure": {
        "id": 1,
        "code": "PCS",
        "name": "штуки"
    },
    "isTerminal": true,
    "childrenCount": 0,
    "createdAt": "2026-03-19T12:00:00Z",
    "updatedAt": "2026-03-19T12:00:00Z"
}
```

### GET /api/v1/nodes/tree — Дерево целиком

**Response (200):**
```json
[
    {
        "id": 1,
        "code": "ELECTRONICS",
        "name": "Электроника",
        "unitOfMeasure": null,
        "isTerminal": false,
        "children": [
            {
                "id": 2,
                "code": "PHONES",
                "name": "Смартфоны",
                "unitOfMeasure": { "id": 1, "code": "PCS", "name": "штуки" },
                "isTerminal": false,
                "children": [
                    {
                        "id": 5,
                        "code": "PHONES-APPLE",
                        "name": "Apple",
                        "unitOfMeasure": null,
                        "isTerminal": false,
                        "children": [
                            {
                                "id": 10,
                                "code": "PHONES-APPLE-IP16",
                                "name": "iPhone 16",
                                "unitOfMeasure": null,
                                "isTerminal": true,
                                "children": []
                            }
                        ]
                    }
                ]
            }
        ]
    }
]
```

### PATCH /api/v1/nodes/5/move — Переместить

**Request:**
```json
{
    "newParentId": 3
}
```

### POST /api/v1/nodes/validate-cycles — Проверка циклов

**Response (200) — без циклов:**
```json
{
    "valid": true,
    "cycles": []
}
```

**Response (200) — обнаружены циклы:**
```json
{
    "valid": false,
    "cycles": [[4, 7, 12, 4]]
}
```

### Ошибки

**409 Conflict — удаление с потомками:**
```json
{
    "status": 409,
    "error": "Conflict",
    "message": "Невозможно удалить вершину 'PHONES' (id=2): имеет 3 потомка. Сначала удалите или переместите потомков.",
    "timestamp": "2026-03-19T12:00:00Z"
}
```

**409 Conflict — дублирующий код:**
```json
{
    "status": 409,
    "error": "Conflict",
    "message": "Вершина с кодом 'PHONES' уже существует.",
    "timestamp": "2026-03-19T12:00:00Z"
}
```

**400 Bad Request — перемещение в собственного потомка:**
```json
{
    "status": 400,
    "error": "Bad Request",
    "message": "Невозможно переместить вершину 'PHONES' (id=2) в собственного потомка (id=5).",
    "timestamp": "2026-03-19T12:00:00Z"
}
```

**404 Not Found:**
```json
{
    "status": 404,
    "error": "Not Found",
    "message": "Вершина с id=999 не найдена.",
    "timestamp": "2026-03-19T12:00:00Z"
}
```

---

## Приложение D. Обработка ошибок — ErrorResponse

### Структура

```kotlin
data class ErrorResponse(
    val status: Int,        // HTTP код (400, 404, 409)
    val error: String,      // HTTP название ("Bad Request", "Not Found", "Conflict")
    val message: String,    // Человекочитаемое описание
    val timestamp: Instant  // Время ошибки
)
```

### Таблица кодов ошибок

| HTTP код | Когда | Пример |
|----------|-------|--------|
| 400 Bad Request | Невалидный запрос, перемещение в потомка | code пустой, parentId = собственный потомок |
| 404 Not Found | Вершина/ед. измерения не найдена | GET /nodes/999 |
| 409 Conflict | Нарушение уникальности, удаление с потомками | POST с дублирующим code, DELETE вершины с children |
| 422 Unprocessable Entity | Валидация полей (Jakarta Validation) | name длиннее 255 символов |

### GlobalExceptionHandler (Java)

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EntityNotFoundException ex) { ... }

    @ExceptionHandler(DuplicateCodeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(DuplicateCodeException ex) { ... }

    @ExceptionHandler(HasChildrenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleHasChildren(HasChildrenException ex) { ... }

    @ExceptionHandler(CyclicMoveException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleCyclicMove(CyclicMoveException ex) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) { ... }
}
```

---

## Приложение E. application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/classifier
    username: classifier
    password: classifier
  jpa:
    hibernate:
      ddl-auto: update          # автогенерация схемы из Entity
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  sql:
    init:
      mode: always              # загрузка data.sql при старте

springdoc:
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: method
  api-docs:
    path: /api-docs

server:
  port: 8080
```

---

## Приложение F. docker-compose.yml

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    container_name: classifier-db
    environment:
      POSTGRES_DB: classifier
      POSTGRES_USER: classifier
      POSTGRES_PASSWORD: classifier
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  pgadmin:
    image: dpage/pgadmin4
    container_name: classifier-pgadmin
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@admin.com
      PGADMIN_DEFAULT_PASSWORD: admin
    ports:
      - "5050:80"
    depends_on:
      - postgres

volumes:
  pgdata:
```
