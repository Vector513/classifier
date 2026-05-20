# Классификатор изделий

REST API сервис для работы с иерархическим справочником изделий с полной поддержкой числовых параметров и параметров-перечислений.
Реализует CRUD, обход дерева, наследование параметров по иерархии, фильтрацию изделий, агрегирование статистики и поиск.

**Задание 1.4** расширяет проект универсальной подсистемой **Хозяйственных операций (ХО)**: иерархический классификатор ХО, конструктор шаблонов с произвольным набором параметров и ролей, создание и заполнение экземпляров ХО, поиск и просмотр характеристик.

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

При старте автоматически выполняются:
- `schema.sql` — DDL новых таблиц ХО (задание 1.4)
- `procedures.sql` — хранимые процедуры (задания 1.2, 1.3, 1.4)
- `data.sql` — начальные данные (классификатор изделий + шаблоны и экземпляры ХО)

### 3. Открыть Swagger UI

```
http://localhost:8082/swagger-ui.html
```

### Запуск целиком в Docker (БД + приложение)

```bash
docker-compose up --build
```

> При запуске через Docker приложение доступно на порту **8080**.
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
| `PUT` | `/values` | Установить/обновить значение |
| `DELETE` | `/values/{paramId}` | Удалить значение |
| `GET` | `/aggregates/{paramId}` | Агрегаты (min/max/avg/count) по поддереву |
| `GET` | `/filter/{paramId}?minVal=&maxVal=` | Фильтрация по диапазону значений |

### Поиск и анализ изделий

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/v1/items/search?query=...` | Поиск изделий по коду или названию |
| `GET` | `/api/v1/items/{nodeId}/parameters` | Узел с полным набором значений параметров |
| `POST` | `/api/v1/items/filter` | Фильтрация по нескольким параметрам сразу |
| `GET` | `/api/v1/nodes/{nodeId}/enumerations/effective` | Перечисления узла с учётом наследования |
| `GET` | `/api/v1/nodes/{classNodeId}/filter/enum?enumerationId=&valueId=` | Фильтрация по значению перечисления |
| `GET` | `/api/v1/nodes/{classNodeId}/aggregates/enum/{enumerationId}` | Статистика по перечислимому параметру |

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
├── ClassifierApplication.kt
├── entity/
│   ├── ClassifierNode.kt
│   ├── UnitOfMeasure.kt
│   ├── EnumerationClass.kt
│   ├── Enumeration.kt
│   ├── EnumerationValue.kt
│   ├── NodeAttributeValue.kt
│   ├── NumericParameter.kt
│   ├── NodeNumericParameter.kt
│   └── NodeNumericValue.kt
├── dto/
│   ├── NodeRequests.kt
│   ├── NodeResponses.kt
│   ├── UnitOfMeasureDtos.kt
│   ├── EnumerationDtos.kt
│   ├── NodeAttributeValueDtos.kt
│   ├── NumericParameterDtos.kt
│   ├── ItemDtos.kt                 — поиск, фильтрация, агрегаты
│   └── CommonDtos.kt
├── repository/
│   ├── ClassifierNodeRepository.kt
│   ├── EnumerationRepository.kt
│   ├── EnumerationValueRepository.kt
│   ├── NodeAttributeValueRepository.kt
│   ├── NumericParameterRepository.kt
│   ├── NodeNumericParameterRepository.kt
│   └── NodeNumericValueRepository.kt
├── service/
│   ├── ClassifierNodeService.kt
│   ├── TreeTraversalService.kt
│   ├── UnitOfMeasureService.kt
│   ├── EnumerationService.kt
│   ├── NodeAttributeValueService.kt
│   ├── NumericParameterService.kt
│   └── ItemSearchService.kt
└── exception/
    └── Exceptions.kt

src/main/java/com/classifier/controller/
├── ClassifierNodeController.java
├── UnitOfMeasureController.java
├── EnumerationClassController.java
├── EnumerationController.java
├── NodeAttributeValueController.java
├── NumericParameterController.java
├── NodeNumericController.java
└── ItemQueryController.java

src/main/resources/
├── application.yml      — конфигурация (БД порт 5434, сервер порт 8082)
├── schema.sql           — DDL таблиц ХО (задание 1.4, выполняется при старте)
├── procedures.sql       — хранимые процедуры (задания 1.2, 1.3, 1.4)
└── data.sql             — начальные данные (изделия + шаблоны и экземпляры ХО)
```

---

## Модель данных

### Классификатор изделий (задания 1.1–1.3)

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
├── classifier_node_id (FK)
├── enumeration_id (FK)
├── enumeration_value_id (FK)
└── UNIQUE(classifier_node_id, enumeration_id)

numeric_parameter             node_numeric_parameter       node_numeric_value
├── id (PK)                  ├── classifier_node_id (FK)  ├── classifier_node_id (FK)
├── code (UNIQUE)            ├── numeric_parameter_id(FK) ├── numeric_parameter_id(FK)
├── name                     └── UNIQUE(node, param)      ├── value (NUMERIC 19,6)
├── min_value (nullable)                                  └── UNIQUE(node, param)
├── max_value (nullable)
└── unit_of_measure_id (FK)
```

### Хозяйственные операции (задание 1.4)

```
ho_class                         ho_parameter_type              ho_role_type
├── id (PK)                      ├── id (PK)                    ├── id (PK)
├── code (UNIQUE)                ├── code (UNIQUE)              ├── code (UNIQUE)
├── name                         ├── name                       ├── name
├── parent_id (FK → self)        ├── data_type (NUMBER/         └── description
└── description                  │   STRING/DATE/ENUM)
                                 ├── min_value (nullable)
                                 ├── max_value (nullable)
                                 └── enum_class_id (FK)

ho_template                      ho_template_parameter          ho_template_role
├── id (PK)                      ├── ho_template_id (FK)        ├── ho_template_id (FK)
├── ho_class_id (FK)             ├── ho_parameter_type_id (FK)  ├── ho_role_type_id (FK)
├── code (UNIQUE)                ├── is_required                ├── is_required
├── name                         ├── sort_order                 ├── sort_order
└── description                  └── UNIQUE(template, param)    └── UNIQUE(template, role)

ho_instance                      ho_instance_value              ho_instance_role
├── id (PK)                      ├── ho_instance_id (FK)        ├── ho_instance_id (FK)
├── ho_template_id (FK)          ├── ho_parameter_type_id (FK)  ├── ho_role_type_id (FK)
├── code (UNIQUE)                ├── numeric_value              ├── counterparty
├── name                         ├── string_value               └── UNIQUE(instance, role)
├── doc_date                     ├── date_value
└── status (DRAFT/ACTIVE/CLOSED) └── UNIQUE(instance, param)
```

**Связь с классификатором изделий:** `ho_parameter_type.enum_class_id` ссылается на `enumeration_class` — параметры типа ENUM переиспользуют существующие справочники перечислений.

---

## SQL-процедуры (`procedures.sql`)

### Задания 1.2–1.3 — классификатор изделий

| Функция / процедура | Описание |
|---------------------|----------|
| `create_enumeration_class(code, name)` | Создать класс перечисления |
| `create_enumeration(code, name, classId, nodeId)` | Создать перечисление |
| `add_enumeration_value(enumId, code, name)` | Добавить значение |
| `reorder_enumeration_value(id, newPos)` | Изменить порядок значения |
| `select_enumeration_value(nodeId, enumId, valueId)` | Выбрать значение для узла |
| `create_numeric_parameter(code, name, min, max)` | Создать числовой параметр |
| `assign_numeric_parameter(nodeId, paramId)` | Назначить параметр узлу |
| `set_numeric_value(nodeId, paramId, value)` | Установить значение (с проверкой диапазона) |
| `get_effective_numeric_parameters(nodeId)` | Параметры с учётом наследования |
| `get_effective_enumerations(nodeId)` | Перечисления с учётом наследования |
| `filter_nodes_by_enum(rootId, enumId, valueId)` | Фильтрация по значению перечисления |
| `filter_nodes_by_numeric(rootId, paramId, min, max)` | Фильтрация по диапазону |
| `get_numeric_aggregates(rootId, paramId)` | Агрегаты (min/max/avg) по поддереву |
| `get_enum_aggregates(rootId, enumId)` | Распределение по значениям перечисления |
| `search_items_with_parameters(query)` | Поиск изделий с выводом всех параметров |

### Задание 1.4 — хозяйственные операции

| Функция / процедура | Описание |
|---------------------|----------|
| `create_ho_class(code, name, parentId)` | Создать узел классификатора ХО |
| `get_ho_class_tree()` | Дерево классификатора ХО |
| `create_ho_template(classId, code, name)` | Создать шаблон ХО |
| `add_template_parameter(templateId, paramTypeId, required)` | Добавить параметр к шаблону |
| `remove_template_parameter(templateId, paramTypeId)` | Удалить параметр из шаблона |
| `add_template_role(templateId, roleTypeId, required)` | Добавить роль к шаблону |
| `get_template_definition(templateId)` | Полный состав шаблона (параметры + роли) |
| `create_ho_instance(templateId, code, name, date)` | Создать экземпляр ХО |
| `set_instance_numeric(instanceId, paramCode, value)` | Установить числовое значение (с проверкой min/max) |
| `set_instance_string(instanceId, paramCode, value)` | Установить строковое значение |
| `set_instance_date(instanceId, paramCode, value)` | Установить значение-дату |
| `assign_instance_role(instanceId, roleCode, counterparty)` | Назначить контрагента на роль |
| `change_ho_status(instanceId, newStatus)` | Изменить статус (DRAFT→ACTIVE→CLOSED) |
| `search_ho_by_class(classCode)` | Поиск ХО по классу (с учётом иерархии) |
| `get_ho_instance_details(instanceId)` | Все характеристики экземпляра ХО |
| `check_ho_instance_completeness(instanceId)` | Проверка заполненности обязательных полей |

---

## Начальные данные (`data.sql`)

### Классификатор изделий

| Тип | Количество | Примеры |
|-----|-----------|---------|
| Узлы классификатора | 29 | Электроника → Смартфоны → Galaxy S24, iPhone 16… |
| Единицы измерения | 4 | шт, кг, м, упак |
| Классы перечислений | 4 | COLOR, STORAGE, CONNECTOR, OS |
| Перечисления | 6 | Цвет смартфонов, ОС, Тип памяти… |
| Значения перечислений | 24 | Чёрный, Android, iOS, 128 ГБ… |
| Числовые параметры | 5 | WEIGHT, BATTERY, SCREEN_SIZE, RAM, PRICE |
| Числовые значения изделий | 30 | 6 моделей × 5 параметров |

### Хозяйственные операции (задание 1.4)

| Тип | Количество | Примеры |
|-----|-----------|---------|
| Классы ХО | 6 | Товарные операции → Отгрузка, Закупка; Денежные → ПКО, РКО |
| Типы параметров | 6 | NOMER_DOC, SUMMA, KOLICHESTVO, SKLAD, OSNOVANIE, DATA_DOGOVORA |
| Типы ролей | 5 | Поставщик, Покупатель, Кассир, Бухгалтер, МОЛ |
| Шаблоны ХО | 3 | Стандартная отгрузка, ПКО, Закупка материалов |
| Экземпляры ХО | 4 | OTGR-2026-001/002, PKO-2026-001/002 |

### Примеры SQL-запросов для тестирования

```sql
-- Дерево классификатора ХО
SELECT * FROM get_ho_class_tree();

-- Состав шаблона "Стандартная отгрузка"
SELECT * FROM get_template_definition(1);

-- Поиск всех отгрузок (включая подклассы)
SELECT * FROM search_ho_by_class('OTGRUZKA');

-- Все характеристики экземпляра ХО №1
SELECT * FROM get_ho_instance_details(1);

-- Проверить заполненность обязательных полей
SELECT * FROM check_ho_instance_completeness(2);

-- Создать новый экземпляр отгрузки
SELECT create_ho_instance(1, 'OTGR-2026-003', 'Тестовая отгрузка', '2026-05-25');

-- Заполнить параметры
CALL set_instance_string(5, 'NOMER_DOC', 'ТН-00003');
CALL set_instance_numeric(5, 'SUMMA', 150000);
CALL assign_instance_role(5, 'SUPPLIER', 'ООО «Поставщик»');
CALL assign_instance_role(5, 'BUYER', 'ИП Иванов А.А.');
CALL assign_instance_role(5, 'MOL', 'Кладовщик П.П.');

-- Активировать
CALL change_ho_status(5, 'ACTIVE');
```

### Примеры тестовых запросов через Swagger UI

```
# Все параметры смартфона iPhone 16 с наследованием
GET /api/v1/nodes/21/numeric/parameters/effective

# Агрегаты по аккумулятору в классе "Смартфоны"
GET /api/v1/nodes/2/numeric/aggregates/2

# Смартфоны с RAM >= 8 ГБ
GET /api/v1/nodes/2/numeric/filter/4?minVal=8

# Распределение смартфонов по ОС
GET /api/v1/nodes/2/aggregates/enum/6

# Поиск изделий по названию
GET /api/v1/items/search?query=Galaxy

# Фильтрация по нескольким параметрам
POST /api/v1/items/filter
{
  "rootNodeId": 2,
  "numericFilters": [{ "parameterId": 4, "minValue": 8 }],
  "enumFilters":   [{ "enumerationId": 6, "valueId": 22 }]
}
```

**Паттерн хранения дерева:** Adjacency List + PostgreSQL `WITH RECURSIVE` CTE.

**Наследование параметров:** при запросе эффективных параметров узла рекурсивно обходятся все предки — параметры родителя автоматически доступны потомкам. Поле `isInherited` в ответе указывает источник.

**Валидация диапазона:** при установке числового значения (как для изделий, так и для ХО) проверяется попадание в `[minValue, maxValue]`. Нарушение возвращает HTTP 422 / исключение в процедуре.

**Поиск ХО по иерархии:** `search_ho_by_class('TOVAR')` вернёт и отгрузки, и закупки — рекурсивный CTE обходит все подклассы.
