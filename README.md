# Классификатор изделий — Интернет-магазин электроники

REST API сервис для работы с древовидным справочником категорий товаров.
Реализует полный CRUD, обход дерева, диагностику циклов, поиск и переупорядочивание вершин.

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

Приложение будет доступно на `http://localhost:8080`, Swagger UI — `http://localhost:8080/swagger-ui.html`.

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

Требуется Docker — тесты используют Testcontainers (поднимает временный PostgreSQL).

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

## Стек

| Компонент | Технология |
|-----------|-----------|
| Языки | Kotlin + Java |
| Фреймворк | Spring Boot 3 |
| БД | PostgreSQL 15 |
| ORM | Spring Data JPA (Hibernate) |
| API-документация | springdoc-openapi (Swagger UI) |
| Сборка | Gradle (Kotlin DSL) + Version Catalog (TOML) |
| Тесты | JUnit 5 + Testcontainers + MockMvc |
| Контейнеризация | Docker + Docker Compose |

## Архитектура

Layered Architecture (слоистая) — 4 слоя:

```
Controller  →  принимает HTTP-запросы, Swagger-аннотации
    ↓
Service     →  бизнес-логика (CRUD, обход дерева, валидация)
    ↓
Repository  →  доступ к БД (Spring Data JPA + CTE-запросы)
    ↓
Entity      →  JPA-сущности (маппинг на таблицы PostgreSQL)
```

Зависимости однонаправленные: Controller зависит от Service, Service от Repository. DI через конструктор (Spring IoC).

## Структура проекта

```
src/main/kotlin/com/classifier/
├── ClassifierApplication.kt          — точка входа
├── entity/
│   ├── ClassifierNode.kt             — вершина классификатора
│   └── UnitOfMeasure.kt              — единица измерения
├── dto/
│   ├── NodeRequests.kt               — CreateNodeRequest, UpdateNodeRequest, MoveNodeRequest, ReorderRequest
│   ├── NodeResponses.kt              — NodeResponse, TreeNodeResponse
│   ├── UnitOfMeasureDtos.kt          — UnitOfMeasureRequest, UnitOfMeasureResponse
│   └── CommonDtos.kt                 — ErrorResponse, ValidationResponse
├── repository/
│   ├── ClassifierNodeRepository.kt   — CTE-запросы для обхода дерева
│   └── UnitOfMeasureRepository.kt
├── service/
│   ├── ClassifierNodeService.kt      — CRUD, перемещение, переупорядочивание
│   ├── TreeTraversalService.kt       — потомки, предки, терминальные, циклы
│   └── UnitOfMeasureService.kt
├── controller/
│   ├── ClassifierNodeController.kt   — 14 REST-эндпоинтов
│   ├── UnitOfMeasureController.kt    — 5 REST-эндпоинтов
│   └── GlobalExceptionHandler.kt     — обработка ошибок (404, 409, 400, 422)
├── mapper/
│   └── NodeMapper.kt                 — Entity → DTO
└── exception/
    └── Exceptions.kt                 — EntityNotFoundException, DuplicateCodeException и др.

src/main/resources/
├── application.yml                   — конфигурация (БД, JPA, Swagger)
└── data.sql                          — тестовые данные (каталог электроники, 29 категорий)

src/test/kotlin/com/classifier/
├── repository/                       — интеграционные тесты репозиториев
├── service/                          — unit-тесты сервисов
└── controller/                       — интеграционные тесты контроллеров (MockMvc)
```

## Модель данных

```
classifier_node                    unit_of_measure
├── id (PK, BIGSERIAL)             ├── id (PK, BIGSERIAL)
├── code (UNIQUE, VARCHAR)         ├── code (UNIQUE, VARCHAR)
├── name (VARCHAR)                 └── name (VARCHAR)
├── parent_id (FK → self)
├── sort_order (INT)
├── unit_of_measure_id (FK)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)
```

Паттерн хранения дерева: **Adjacency List** (каждая вершина хранит ссылку на родителя).
Обход дерева: PostgreSQL `WITH RECURSIVE` CTE.
