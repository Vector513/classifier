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

-- Сброс sequence чтобы новые записи получали id > 29
SELECT setval('classifier_node_id_seq', 29);
SELECT setval('unit_of_measure_id_seq', 4);
