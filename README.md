# Классификатор изделий — Интернет-магазин электроники

Сервис для работы с древовидным справочником категорий товаров (REST API).

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

## Остановка

```bash
# Остановить приложение: Ctrl+C

# Остановить БД
docker-compose down

# Остановить БД и удалить данные
docker-compose down -v
```

## Стек

| Компонент | Технология |
|-----------|-----------|
| Языки | Kotlin + Java |
| Фреймворк | Spring Boot 3 |
| БД | PostgreSQL 15 |
| ORM | Spring Data JPA (Hibernate) |
| API-документация | springdoc-openapi (Swagger UI) |
| Сборка | Gradle (Kotlin DSL) + Version Catalog (TOML) |

## Структура проекта

```
src/main/
├── kotlin/com/classifier/
│   ├── ClassifierApplication.kt    — точка входа
│   ├── entity/                     — JPA-сущности
│   ├── dto/                        — запросы/ответы
│   ├── repository/                 — доступ к БД
│   ├── service/                    — бизнес-логика
│   ├── controller/                 — REST-эндпоинты
│   └── exception/                  — обработка ошибок
└── resources/
    └── application.yml             — конфигурация
```
