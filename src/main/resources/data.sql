-- Единицы измерения
INSERT INTO unit_of_measure (id, code, name) VALUES (1, 'PCS', 'штуки') ON CONFLICT DO NOTHING;
INSERT INTO unit_of_measure (id, code, name) VALUES (2, 'KG', 'килограммы') ON CONFLICT DO NOTHING;
INSERT INTO unit_of_measure (id, code, name) VALUES (3, 'M', 'метры') ON CONFLICT DO NOTHING;
INSERT INTO unit_of_measure (id, code, name) VALUES (4, 'PACK', 'упаковки') ON CONFLICT DO NOTHING;

-- Корень
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (1, 'ELECTRONICS', 'Электроника', NULL, 0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 1: основные категории
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (2, 'PHONES', 'Смартфоны', 1, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (3, 'LAPTOPS', 'Ноутбуки', 1, 1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (4, 'TABLETS', 'Планшеты', 1, 2, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (5, 'AUDIO', 'Аудиотехника', 1, 3, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (6, 'ACCESSORIES', 'Аксессуары', 1, 4, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 2: Смартфоны → бренды
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (7, 'PHONES-APPLE', 'Apple', 2, 0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (8, 'PHONES-SAMSUNG', 'Samsung', 2, 1, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (9, 'PHONES-XIAOMI', 'Xiaomi', 2, 2, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 2: Ноутбуки → бренды
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (10, 'LAPTOPS-APPLE', 'Apple MacBook', 3, 0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (11, 'LAPTOPS-LENOVO', 'Lenovo', 3, 1, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (12, 'LAPTOPS-ASUS', 'ASUS', 3, 2, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 2: Планшеты → бренды
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (13, 'TABLETS-APPLE', 'Apple iPad', 4, 0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (14, 'TABLETS-SAMSUNG', 'Samsung Galaxy Tab', 4, 1, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 2: Аудио → типы
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (15, 'AUDIO-HEADPHONES', 'Наушники', 5, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (16, 'AUDIO-SPEAKERS', 'Колонки', 5, 1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 2: Аксессуары → типы
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (17, 'ACC-CASES', 'Чехлы', 6, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (18, 'ACC-CHARGERS', 'Зарядные устройства', 6, 1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (19, 'ACC-CABLES', 'Кабели', 6, 2, 4, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 3: Apple → модели (терминальные)
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (20, 'PHONES-APPLE-IP16', 'iPhone 16', 7, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (21, 'PHONES-APPLE-IP16PRO', 'iPhone 16 Pro', 7, 1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (22, 'PHONES-APPLE-IP15', 'iPhone 15', 7, 2, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 3: Samsung → модели (терминальные)
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (23, 'PHONES-SAMSUNG-S24', 'Galaxy S24', 8, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (24, 'PHONES-SAMSUNG-S24U', 'Galaxy S24 Ultra', 8, 1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 3: Xiaomi → модели (терминальные)
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (25, 'PHONES-XIAOMI-14', 'Xiaomi 14', 9, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 3: MacBook → модели (терминальные)
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (26, 'LAPTOPS-APPLE-AIR', 'MacBook Air M3', 10, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (27, 'LAPTOPS-APPLE-PRO', 'MacBook Pro M3', 10, 1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Уровень 3: Lenovo → модели (терминальные)
INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (28, 'LAPTOPS-LENOVO-TP', 'ThinkPad X1 Carbon', 11, 0, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO classifier_node (id, code, name, parent_id, sort_order, unit_of_measure_id, created_at, updated_at)
VALUES (29, 'LAPTOPS-LENOVO-YOGA', 'Yoga 9i', 11, 1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Синхронизация sequence с фактическим максимумом id.
-- Берём MAX(id), а не константу, чтобы данные, добавленные через приложение,
-- не приводили к конфликту первичного ключа при последующих запусках.
SELECT setval('classifier_node_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM classifier_node), 1));
SELECT setval('unit_of_measure_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM unit_of_measure), 1));

-- ─── Классы перечислений ───────────────────────────────────────────────────
INSERT INTO enumeration_class (id, code, name, description, created_at, updated_at)
VALUES (1, 'COLOR', 'Цвет', 'Цветовая гамма изделия', NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO enumeration_class (id, code, name, description, created_at, updated_at)
VALUES (2, 'STORAGE', 'Объём памяти', 'Объём встроенной памяти устройства', NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO enumeration_class (id, code, name, description, created_at, updated_at)
VALUES (3, 'CONNECTOR', 'Тип разъёма', 'Тип физического разъёма для подключения', NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO enumeration_class (id, code, name, description, created_at, updated_at)
VALUES (4, 'OS', 'Операционная система', 'Предустановленная операционная система', NOW(), NOW()) ON CONFLICT DO NOTHING;

-- ─── Перечисления ──────────────────────────────────────────────────────────
-- Цвета смартфонов (привязан к узлу PHONES id=2)
INSERT INTO enumeration (id, code, name, enumeration_class_id, classifier_node_id, created_at, updated_at)
VALUES (1, 'PHONE-COLORS', 'Цвета смартфонов', 1, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Цвета ноутбуков (привязан к узлу LAPTOPS id=3)
INSERT INTO enumeration (id, code, name, enumeration_class_id, classifier_node_id, created_at, updated_at)
VALUES (2, 'LAPTOP-COLORS', 'Цвета ноутбуков', 1, 3, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Объём памяти смартфонов
INSERT INTO enumeration (id, code, name, enumeration_class_id, classifier_node_id, created_at, updated_at)
VALUES (3, 'PHONE-STORAGE', 'Память смартфонов', 2, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Объём памяти ноутбуков
INSERT INTO enumeration (id, code, name, enumeration_class_id, classifier_node_id, created_at, updated_at)
VALUES (4, 'LAPTOP-STORAGE', 'Память ноутбуков', 2, 3, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Типы разъёмов для кабелей
INSERT INTO enumeration (id, code, name, enumeration_class_id, classifier_node_id, created_at, updated_at)
VALUES (5, 'CABLE-CONNECTORS', 'Разъёмы кабелей', 3, 19, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Операционные системы смартфонов
INSERT INTO enumeration (id, code, name, enumeration_class_id, classifier_node_id, created_at, updated_at)
VALUES (6, 'PHONE-OS', 'ОС смартфонов', 4, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- ─── Значения: Цвета смартфонов ───────────────────────────────────────────
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (1,  'BLACK',  'Чёрный',      1, 0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (2,  'WHITE',  'Белый',       1, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (3,  'GOLD',   'Золотой',     1, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (4,  'BLUE',   'Синий',       1, 3, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (5,  'GREEN',  'Зелёный',     1, 4, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- ─── Значения: Цвета ноутбуков ────────────────────────────────────────────
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (6,  'SILVER',     'Серебристый', 2, 0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (7,  'SPACE-GRAY', 'Серый космос', 2, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (8,  'MIDNIGHT',   'Полуночный',  2, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- ─── Значения: Память смартфонов ──────────────────────────────────────────
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (9,  'GB64',  '64 ГБ',  3, 0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (10, 'GB128', '128 ГБ', 3, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (11, 'GB256', '256 ГБ', 3, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (12, 'GB512', '512 ГБ', 3, 3, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (13, 'TB1',   '1 ТБ',   3, 4, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- ─── Значения: Память ноутбуков ───────────────────────────────────────────
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (14, 'SSD256',  '256 ГБ SSD',  4, 0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (15, 'SSD512',  '512 ГБ SSD',  4, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (16, 'SSD1TB',  '1 ТБ SSD',    4, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (17, 'SSD2TB',  '2 ТБ SSD',    4, 3, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- ─── Значения: Разъёмы кабелей ────────────────────────────────────────────
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (18, 'USB-C',     'USB-C',      5, 0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (19, 'LIGHTNING', 'Lightning',  5, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (20, 'MICRO-USB', 'Micro-USB',  5, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (21, 'JACK35',    'Jack 3.5 мм', 5, 3, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- ─── Значения: ОС смартфонов ──────────────────────────────────────────────
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (22, 'ANDROID', 'Android', 6, 0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (23, 'IOS',     'iOS',     6, 1, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO enumeration_value (id, code, name, enumeration_id, sort_order, created_at, updated_at)
VALUES (24, 'HARMONY', 'HarmonyOS', 6, 2, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Синхронизация sequences с фактическим максимумом id
SELECT setval('enumeration_class_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM enumeration_class), 1));
SELECT setval('enumeration_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM enumeration), 1));
SELECT setval('enumeration_value_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM enumeration_value), 1));

-- ═══════════════════════════════════════════════════════════════════════════
-- Числовые параметры (задание 1.3)
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── Типы числовых параметров ─────────────────────────────────────────────
INSERT INTO numeric_parameter (id, code, name, description, min_value, max_value, unit_of_measure_id, created_at, updated_at)
VALUES (1, 'WEIGHT',       'Масса',               'Масса изделия', 0.001, 99999.0, 2,    NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO numeric_parameter (id, code, name, description, min_value, max_value, unit_of_measure_id, created_at, updated_at)
VALUES (2, 'BATTERY',      'Ёмкость аккумулятора','Ёмкость в мАч', 100.0, 30000.0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO numeric_parameter (id, code, name, description, min_value, max_value, unit_of_measure_id, created_at, updated_at)
VALUES (3, 'SCREEN_SIZE',  'Диагональ экрана',    'Диагональ в дюймах', 1.0, 20.0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO numeric_parameter (id, code, name, description, min_value, max_value, unit_of_measure_id, created_at, updated_at)
VALUES (4, 'RAM',          'Объём ОЗУ',            'Объём оперативной памяти в ГБ', 1.0, 256.0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

INSERT INTO numeric_parameter (id, code, name, description, min_value, max_value, unit_of_measure_id, created_at, updated_at)
VALUES (5, 'PRICE',        'Цена',                 'Рекомендуемая розничная цена (руб)', 0.01, 9999999.0, NULL, NOW(), NOW()) ON CONFLICT DO NOTHING;

SELECT setval('numeric_parameter_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM numeric_parameter), 1));

-- ─── Назначение числовых параметров на классы (с наследованием) ───────────
-- Все электронные изделия имеют массу и цену (назначаем на корень ELECTRONICS id=1)
INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (1, 1, 1, NOW()) ON CONFLICT DO NOTHING;  -- ELECTRONICS → WEIGHT

INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (2, 1, 5, NOW()) ON CONFLICT DO NOTHING;  -- ELECTRONICS → PRICE

-- Смартфоны и планшеты имеют ёмкость аккумулятора и диагональ (назначаем на PHONES id=2)
INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (3, 2, 2, NOW()) ON CONFLICT DO NOTHING;  -- PHONES → BATTERY

INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (4, 2, 3, NOW()) ON CONFLICT DO NOTHING;  -- PHONES → SCREEN_SIZE

INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (5, 2, 4, NOW()) ON CONFLICT DO NOTHING;  -- PHONES → RAM

-- Ноутбуки имеют ёмкость аккумулятора, диагональ и ОЗУ
INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (6, 3, 2, NOW()) ON CONFLICT DO NOTHING;  -- LAPTOPS → BATTERY

INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (7, 3, 3, NOW()) ON CONFLICT DO NOTHING;  -- LAPTOPS → SCREEN_SIZE

INSERT INTO node_numeric_parameter (id, classifier_node_id, numeric_parameter_id, created_at)
VALUES (8, 3, 4, NOW()) ON CONFLICT DO NOTHING;  -- LAPTOPS → RAM

SELECT setval('node_numeric_parameter_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM node_numeric_parameter), 1));

-- ─── Значения числовых параметров для конкретных моделей (изделий) ────────
-- iPhone 16 (id=21): масса=170г, батарея=3561мАч, экран=6.1", ОЗУ=8ГБ, цена=89990
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (1,  21, 1, 170.0,   NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (2,  21, 2, 3561.0,  NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (3,  21, 3, 6.1,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (4,  21, 4, 8.0,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (5,  21, 5, 89990.0, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- iPhone 16 Pro Max (id=22): масса=227г, батарея=4685мАч, экран=6.9", ОЗУ=8ГБ, цена=139990
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (6,  22, 1, 227.0,   NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (7,  22, 2, 4685.0,  NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (8,  22, 3, 6.9,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (9,  22, 4, 8.0,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (10, 22, 5, 139990.0,NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Galaxy S24 (id=23): масса=167г, батарея=4000мАч, экран=6.2", ОЗУ=8ГБ, цена=79990
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (11, 23, 1, 167.0,   NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (12, 23, 2, 4000.0,  NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (13, 23, 3, 6.2,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (14, 23, 4, 8.0,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (15, 23, 5, 79990.0, NOW(), NOW()) ON CONFLICT DO NOTHING;

-- Galaxy S24 Ultra (id=24): масса=232г, батарея=5000мАч, экран=6.8", ОЗУ=12ГБ, цена=119990
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (16, 24, 1, 232.0,   NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (17, 24, 2, 5000.0,  NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (18, 24, 3, 6.8,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (19, 24, 4, 12.0,    NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (20, 24, 5, 119990.0,NOW(), NOW()) ON CONFLICT DO NOTHING;

-- MacBook Air M3 (id=26): масса=1240г, батарея=52600мАч, экран=15.3", ОЗУ=8ГБ, цена=139990
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (21, 26, 1, 1240.0,  NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (22, 26, 2, 52600.0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (23, 26, 3, 15.3,    NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (24, 26, 4, 8.0,     NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (25, 26, 5, 139990.0,NOW(), NOW()) ON CONFLICT DO NOTHING;

-- MacBook Pro M3 (id=27): масса=1610г, батарея=72400мАч, экран=14.2", ОЗУ=18ГБ, цена=219990
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (26, 27, 1, 1610.0,  NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (27, 27, 2, 72400.0, NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (28, 27, 3, 14.2,    NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (29, 27, 4, 18.0,    NOW(), NOW()) ON CONFLICT DO NOTHING;
INSERT INTO node_numeric_value (id, classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
VALUES (30, 27, 5, 219990.0,NOW(), NOW()) ON CONFLICT DO NOTHING;

SELECT setval('node_numeric_value_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM node_numeric_value), 1));
