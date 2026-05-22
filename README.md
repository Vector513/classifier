# Классификатор изделий

Информационная система для работы с иерархическим справочником изделий с полной поддержкой числовых параметров и параметров-перечислений.
Реализует CRUD, обход дерева, наследование параметров по иерархии, фильтрацию изделий, агрегирование статистики и поиск.

Приложение включает **REST API** и **веб-интерфейс**. Веб-интерфейс отдаётся самим Spring Boot как статические ресурсы — отдельная сборка и запуск фронтенда не требуются.

## Требования

- Java 17+
- Docker и Docker Compose

## Запуск

### 1. Поднять БД

```bash
docker-compose up -d postgres
```

PostgreSQL будет доступен на `localhost:5434` (БД: `classifier`, пользователь/пароль: `classifier`).

### 2. Запустить приложение

```bash
./gradlew bootRun
```

### 3. Открыть веб-интерфейс

Главная точка входа в приложение — откройте в браузере:

```
http://localhost:8082/
```

Никаких дополнительных шагов (`npm install`, сборка фронтенда) не требуется: интерфейс
написан на чистом JavaScript (ES-модули) и отдаётся приложением как статика из каталога
`src/main/resources/static/`.

### 4. Открыть Swagger UI (документация REST API)

```
http://localhost:8082/swagger-ui.html
```

### Запуск целиком в Docker (БД + приложение)

```bash
docker-compose up --build
```

> При запуске через Docker приложение доступно на порту **8080** (маппинг в `docker-compose.yml`).
> Веб-интерфейс: `http://localhost:8080/`
> Swagger UI: `http://localhost:8080/swagger-ui.html`

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

## Веб-интерфейс

После запуска приложения интерфейс открывается по адресу **`http://localhost:8082/`**
(при запуске в Docker — `http://localhost:8080/`). Отдельный сервер для фронтенда
поднимать не нужно.

### Разделы

| Раздел | Адрес | Назначение |
|--------|-------|-----------|
| Главная | `#/` | Обзор справочника: сводные показатели и переход в разделы |
| Классификатор | `#/tree` | Дерево изделий и карточка узла: параметры, значения, анализ |
| Перечисления | `#/enumerations` | Классы перечислений, перечисления и их значения |
| Числовые параметры | `#/numeric-parameters` | Справочник числовых характеристик с диапазонами |
| Единицы измерения | `#/units` | Справочник единиц измерения |
| Поиск и анализ | `#/search` | Поиск изделий с отображением всех параметров |

### Технологии фронтенда

| Компонент | Решение |
|-----------|---------|
| Язык | JavaScript (нативные ES-модули, без шага сборки) |
| Стили | CSS (собственная дизайн-система) |
| Маршрутизация | Клиентская, на основе hash-навигации |
| Доставка | Статические ресурсы Spring Boot (`src/main/resources/static/`) |

Каталог фронтенда:

```
src/main/resources/static/
├── index.html                  — оболочка интерфейса (шапка, меню, подвал)
├── css/styles.css              — дизайн-система
└── js/
    ├── app.js                  — инициализация и маршрутизация
    ├── router.js               — hash-маршрутизатор
    ├── api.js                  — клиент REST API
    ├── ui.js                   — формы, модальные окна, уведомления
    ├── icons.js                — набор иконок интерфейса
    └── views/                  — модули разделов (дерево, перечисления, поиск…)
```

---

## API-эндпоинты

### Узлы классификатора (`/api/v1/nodes`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/roots` | Корневые узлы |
| `GET` | `/{id}` | Узел по ID |
| `GET` | `/{id}/children` | Прямые потомки |
| `POST` | `/` | Создать узел |
| `PATCH` | `/{id}` | Обновить узел |
| `DELETE` | `/{id}` | Удалить узел |
| `PATCH` | `/{id}/move` | Переместить (сменить родителя) |
| `PATCH` | `/{id}/reorder` | Изменить порядок сортировки |
| `GET` | `/{id}/descendants` | Все потомки (рекурсивно) |
| `GET` | `/{id}/ancestors` | Все предки (до корня) |
| `GET` | `/{id}/terminals` | Терминальные узлы поддерева |
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
| `DELETE` | `/{id}` | Удалить класс |
| `GET` | `/{id}/enumerations` | Все перечисления класса |

### Перечисления (`/api/v1/enumerations`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/{id}` | Перечисление со значениями |
| `POST` | `/` | Создать перечисление |
| `PATCH` | `/{id}` | Обновить перечисление |
| `DELETE` | `/{id}` | Удалить перечисление |
| `GET` | `/{id}/values` | Список значений (по порядку) |
| `POST` | `/{id}/values` | Добавить значение |
| `PATCH` | `/{id}/values/{valueId}` | Редактировать значение |
| `DELETE` | `/{id}/values/{valueId}` | Удалить значение |
| `PATCH` | `/{id}/values/{valueId}/reorder` | Изменить порядок значения |

### Атрибуты узлов — перечислимые (`/api/v1/nodes/{nodeId}/attributes`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/` | Все выбранные значения для узла |
| `GET` | `/{enumerationId}` | Выбранное значение конкретного перечисления |
| `PUT` | `/` | Выбрать значение перечисления |
| `DELETE` | `/{enumerationId}` | Снять выбор значения |

### Числовые параметры (`/api/v1/numeric-parameters`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/` | Список всех числовых параметров |
| `POST` | `/` | Создать числовой параметр |
| `GET` | `/{id}` | Получить по ID |
| `PATCH` | `/{id}` | Обновить параметр |
| `DELETE` | `/{id}` | Удалить параметр |

### Числовые значения узлов (`/api/v1/nodes/{nodeId}/numeric`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/parameters` | Собственные числовые параметры узла |
| `GET` | `/parameters/effective` | Все параметры с учётом наследования |
| `POST` | `/parameters/{paramId}` | Назначить параметр узлу |
| `DELETE` | `/parameters/{paramId}` | Снять параметр с узла |
| `GET` | `/values` | Все числовые значения изделия |
| `GET` | `/values/{paramId}` | Значение конкретного параметра |
| `PUT` | `/values` | Установить/обновить значение (paramId в теле запроса) |
| `DELETE` | `/values/{paramId}` | Удалить значение |
| `GET` | `/aggregates/{paramId}` | Агрегаты (min/max/avg/count) по поддереву |
| `GET` | `/filter/{paramId}?minVal=&maxVal=` | Фильтрация по диапазону значений |

### Поиск и анализ изделий

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/v1/items/search?query=...` | Поиск изделий по коду или названию |
| `GET` | `/api/v1/items/{nodeId}/parameters` | Узел с полным набором значений параметров |
| `GET` | `/api/v1/nodes/{nodeId}/enumerations/effective` | Перечисления узла с учётом наследования |
| `GET` | `/api/v1/nodes/{classNodeId}/filter/enum?enumerationId=&valueId=` | Фильтрация по значению перечисления |
| `GET` | `/api/v1/nodes/{classNodeId}/aggregates/enum/{enumerationId}` | Статистика по перечислимому параметру |
| `POST` | `/api/v1/nodes/{nodeId}/filter` | Отбор изделий по нескольким параметрам одновременно (логика «И») |

**Многопараметрическая фильтрация** (`POST /api/v1/nodes/{nodeId}/filter`) принимает
произвольное число условий — по диапазонам числовых параметров и по значениям
перечислений — и возвращает изделия поддерева, удовлетворяющие всем условиям сразу:

```json
{
  "numericCriteria": [
    { "parameterId": 2, "minValue": 4000, "maxValue": null }
  ],
  "enumCriteria": [
    { "enumerationId": 6, "valueId": 22 }
  ]
}
```

---

## Стек

| Компонент | Технология |
|-----------|-----------|
| Языки | Kotlin (сущности, DTO, сервисы) + Java (контроллеры) |
| Фреймворк | Spring Boot 3 |
| БД | PostgreSQL 15 |
| ORM | Spring Data JPA (Hibernate) |
| SQL | Нативные запросы + рекурсивные CTE + хранимые процедуры |
| API-документация | springdoc-openapi (Swagger UI) |
| Сборка | Gradle (Kotlin DSL) + Version Catalog (TOML) |
| Тесты | JUnit 5 + Testcontainers + Mockito + MockMvc |
| Контейнеризация | Docker + Docker Compose |

---

## Архитектура

Layered Architecture — 4 слоя:

```
Controller  →  принимает HTTP-запросы, Swagger-аннотации              (Java)
    ↓
Service     →  бизнес-логика (CRUD, наследование, агрегаты, валидация) (Kotlin)
    ↓
Repository  →  доступ к БД (Spring Data JPA + CTE-запросы)            (Kotlin)
    ↓
Entity      →  JPA-сущности (маппинг на таблицы PostgreSQL)            (Kotlin)
```

---

## Структура проекта

```
src/main/kotlin/com/classifier/
├── ClassifierApplication.kt              — точка входа + OpenAPI-бин
├── entity/
│   ├── ClassifierNode.kt                 — узел классификатора
│   ├── UnitOfMeasure.kt                  — единица измерения
│   ├── EnumerationClass.kt               — класс перечислений (Цвет, ОС…)
│   ├── Enumeration.kt                    — параметр-перечисление
│   ├── EnumerationValue.kt               — значение перечисления
│   ├── NodeAttributeValue.kt             — выбранное значение перечисления у изделия
│   ├── NumericParameter.kt               — числовой параметр (с min/max диапазоном)
│   ├── NodeNumericParameter.kt           — связь узла с числовым параметром
│   └── NodeNumericValue.kt               — числовое значение параметра у изделия
├── dto/
│   ├── NodeRequests.kt                   — CreateNodeRequest, UpdateNodeRequest…
│   ├── NodeResponses.kt                  — NodeResponse, TreeNodeResponse
│   ├── UnitOfMeasureDtos.kt              — UnitOfMeasureRequest/Response
│   ├── EnumerationDtos.kt                — все DTO для перечислений
│   ├── NodeAttributeValueDtos.kt         — SelectEnumerationValueRequest/Response
│   ├── NumericParameterDtos.kt           — DTO числовых параметров и значений
│   ├── ItemDtos.kt                       — NodeWithParametersResponse, агрегаты
│   └── CommonDtos.kt                     — ErrorResponse, ValidationResponse
├── repository/
│   ├── ClassifierNodeRepository.kt       — CTE-запросы для обхода дерева
│   ├── UnitOfMeasureRepository.kt
│   ├── EnumerationRepository.kt
│   ├── EnumerationValueRepository.kt
│   ├── NodeAttributeValueRepository.kt
│   ├── NumericParameterRepository.kt
│   ├── NodeNumericParameterRepository.kt
│   └── NodeNumericValueRepository.kt     — агрегаты через нативный CTE-запрос
├── service/
│   ├── ClassifierNodeService.kt          — CRUD, перемещение, переупорядочивание
│   ├── TreeTraversalService.kt           — потомки, предки, терминальные, циклы
│   ├── UnitOfMeasureService.kt
│   ├── EnumerationService.kt             — перечисления, значения, наследование
│   ├── NodeAttributeValueService.kt      — выбор значения, фильтрация, агрегаты
│   ├── NumericParameterService.kt        — CRUD, назначение, наследование, агрегаты
│   └── ItemSearchService.kt              — поиск изделий с батч-загрузкой параметров
├── controller/
│   └── GlobalExceptionHandler.kt         — обработка ошибок (404, 409, 400, 422)
├── mapper/
│   └── NodeMapper.kt                     — Entity → DTO

└── exception/
    └── Exceptions.kt                     — все исключения (включая ValueOutOfRangeException)

src/main/java/com/classifier/controller/
├── ClassifierNodeController.java         — 14 REST-эндпоинтов
├── UnitOfMeasureController.java          — 5 REST-эндпоинтов
├── EnumerationClassController.java       — 6 REST-эндпоинтов (/api/v1/enumeration-classes)
├── EnumerationController.java            — 9 REST-эндпоинтов
├── NodeAttributeValueController.java     — 4 REST-эндпоинта
├── NumericParameterController.java       — 5 REST-эндпоинтов (CRUD параметров)
├── NodeNumericController.java            — 10 REST-эндпоинтов (значения, наследование, агрегаты)
└── ItemQueryController.java              — 5 REST-эндпоинтов (поиск, фильтрация)

src/main/resources/
├── application.yml                       — конфигурация (БД порт 5434, сервер порт 8082)
├── data.sql                              — начальные данные (узлы, перечисления, числовые параметры и значения)
└── procedures.sql                        — хранимые процедуры (наследование, агрегаты, фильтрация)

src/test/kotlin/com/classifier/
├── repository/                           — интеграционные тесты репозиториев
├── service/                              — unit-тесты сервисов
└── controller/                           — интеграционные тесты контроллеров
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
├── updated_at
└── UNIQUE(classifier_node_id, enumeration_id)

numeric_parameter             node_numeric_parameter       node_numeric_value
├── id (PK)                  ├── id (PK)                  ├── id (PK)
├── code (UNIQUE)            ├── classifier_node_id (FK)  ├── classifier_node_id (FK)
├── name                     ├── numeric_parameter_id(FK) ├── numeric_parameter_id(FK)
├── description              └── UNIQUE(node, param)      ├── value (NUMERIC 19,6)
├── min_value (nullable)                                  ├── created_at
├── max_value (nullable)                                  ├── updated_at
├── unit_of_measure_id (FK)                               └── UNIQUE(node, param)
├── created_at
└── updated_at
```

**Паттерн хранения дерева:** Adjacency List + PostgreSQL `WITH RECURSIVE` CTE.

**Наследование параметров:** при запросе эффективных параметров узла рекурсивно обходятся все предки — параметры, назначенные родителю, автоматически наследуются потомками. Поле `isInherited` в ответе указывает источник.

**Валидация диапазона:** при установке числового значения проверяется попадание в `[minValue, maxValue]`. Нарушение возвращает HTTP 422.

**Агрегаты:** реализованы через нативные CTE-запросы к PostgreSQL — `min`, `max`, `avg`, `count` для числовых параметров и подсчёт по значениям для перечислений.

---

## Начальные данные (`data.sql`)

| Тип | Количество | Примеры |
|-----|-----------|---------|
| Узлы классификатора | 29 | Электроника → Смартфоны → Galaxy S24, iPhone 15 Pro… |
| Единицы измерения | 4 | г, мАч, дюйм, ГБ |
| Классы перечислений | 4 | COLOR, STORAGE, CONNECTOR, OS |
| Перечисления | 6 | Цвет, Операционная система, Тип памяти… |
| Значения перечислений | 24 | Чёрный, Android, iOS, 128 ГБ… |
| Числовые параметры | 5 | WEIGHT, BATTERY, SCREEN_SIZE, RAM, PRICE |
| Назначения числовых параметров | 8 | ELECTRONICS→WEIGHT+PRICE, PHONES→BATTERY+SCREEN+RAM… |
| Числовые значения изделий | 30 | 6 моделей × 5 параметров |

### Примеры тестовых запросов через Swagger UI

```
# Все параметры смартфона Galaxy S24 с наследованием
GET /api/v1/nodes/6/numeric/parameters/effective

# Агрегаты по аккумулятору в классе "Смартфоны"
GET /api/v1/nodes/4/numeric/aggregates/2

# Смартфоны с RAM >= 8 ГБ
GET /api/v1/nodes/4/numeric/filter/4?minVal=8

# Распределение смартфонов по ОС
GET /api/v1/nodes/4/aggregates/enum/2

# Поиск изделий по названию
GET /api/v1/items/search?query=Galaxy
```
