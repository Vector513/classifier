-- =============================================================================
-- SQL-процедуры для работы с подсистемой Перечислений
-- Лабораторная работа 1.2 — Моделирование перечислений
-- СУБД: PostgreSQL 15
-- =============================================================================

-- =============================================================================
-- 1. ФОРМИРОВАНИЕ КЛАССИФИКАТОРА ПЕРЕЧИСЛЕНИЙ
-- =============================================================================

-- Создать новый класс перечисления
-- Возвращает id созданной записи
CREATE OR REPLACE FUNCTION create_enumeration_class(
    p_code        VARCHAR(100),
    p_name        VARCHAR(255),
    p_description VARCHAR(1000) DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM enumeration_class WHERE code = p_code) THEN
        RAISE EXCEPTION 'Класс перечисления с кодом "%" уже существует', p_code;
    END IF;

    INSERT INTO enumeration_class (code, name, description, created_at, updated_at)
    VALUES (p_code, p_name, p_description, NOW(), NOW())
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- Обновить класс перечисления
CREATE OR REPLACE PROCEDURE update_enumeration_class(
    p_id          BIGINT,
    p_code        VARCHAR(100) DEFAULT NULL,
    p_name        VARCHAR(255) DEFAULT NULL,
    p_description VARCHAR(1000) DEFAULT NULL
)
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM enumeration_class WHERE id = p_id) THEN
        RAISE EXCEPTION 'Класс перечисления с id=% не найден', p_id;
    END IF;

    IF p_code IS NOT NULL AND EXISTS (
        SELECT 1 FROM enumeration_class WHERE code = p_code AND id <> p_id
    ) THEN
        RAISE EXCEPTION 'Класс перечисления с кодом "%" уже существует', p_code;
    END IF;

    UPDATE enumeration_class
    SET code        = COALESCE(p_code, code),
        name        = COALESCE(p_name, name),
        description = COALESCE(p_description, description),
        updated_at  = NOW()
    WHERE id = p_id;
END;
$$;

-- Удалить класс перечисления (только если нет привязанных перечислений)
CREATE OR REPLACE PROCEDURE delete_enumeration_class(p_id BIGINT)
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM enumeration_class WHERE id = p_id) THEN
        RAISE EXCEPTION 'Класс перечисления с id=% не найден', p_id;
    END IF;

    IF EXISTS (SELECT 1 FROM enumeration WHERE enumeration_class_id = p_id) THEN
        RAISE EXCEPTION 'Невозможно удалить класс id=%: содержит привязанные перечисления', p_id;
    END IF;

    DELETE FROM enumeration_class WHERE id = p_id;
END;
$$;

-- Получить все классы перечислений
CREATE OR REPLACE FUNCTION get_all_enumeration_classes()
RETURNS TABLE (
    id               BIGINT,
    code             VARCHAR,
    name             VARCHAR,
    description      VARCHAR,
    enumeration_count BIGINT,
    created_at       TIMESTAMP WITH TIME ZONE,
    updated_at       TIMESTAMP WITH TIME ZONE
)
LANGUAGE sql AS $$
    SELECT
        ec.id,
        ec.code,
        ec.name,
        ec.description,
        COUNT(e.id) AS enumeration_count,
        ec.created_at,
        ec.updated_at
    FROM enumeration_class ec
    LEFT JOIN enumeration e ON e.enumeration_class_id = ec.id
    GROUP BY ec.id
    ORDER BY ec.name;
$$;

-- =============================================================================
-- 2. СОЗДАНИЕ НОВОГО ПЕРЕЧИСЛЕНИЯ ЗАДАННОГО КЛАССА
-- =============================================================================

CREATE OR REPLACE FUNCTION create_enumeration(
    p_code                 VARCHAR(100),
    p_name                 VARCHAR(255),
    p_enumeration_class_id BIGINT,
    p_classifier_node_id   BIGINT DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM enumeration WHERE code = p_code) THEN
        RAISE EXCEPTION 'Перечисление с кодом "%" уже существует', p_code;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM enumeration_class WHERE id = p_enumeration_class_id) THEN
        RAISE EXCEPTION 'Класс перечисления с id=% не найден', p_enumeration_class_id;
    END IF;

    IF p_classifier_node_id IS NOT NULL AND
       NOT EXISTS (SELECT 1 FROM classifier_node WHERE id = p_classifier_node_id)
    THEN
        RAISE EXCEPTION 'Узел классификатора с id=% не найден', p_classifier_node_id;
    END IF;

    INSERT INTO enumeration (code, name, enumeration_class_id, classifier_node_id, created_at, updated_at)
    VALUES (p_code, p_name, p_enumeration_class_id, p_classifier_node_id, NOW(), NOW())
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- Вывод перечислений по классу
CREATE OR REPLACE FUNCTION get_enumerations_by_class(p_class_id BIGINT)
RETURNS TABLE (
    id                    BIGINT,
    code                  VARCHAR,
    name                  VARCHAR,
    enumeration_class_id  BIGINT,
    enumeration_class_name VARCHAR,
    classifier_node_id    BIGINT,
    classifier_node_name  VARCHAR,
    value_count           BIGINT,
    created_at            TIMESTAMP WITH TIME ZONE,
    updated_at            TIMESTAMP WITH TIME ZONE
)
LANGUAGE sql AS $$
    SELECT
        e.id,
        e.code,
        e.name,
        ec.id   AS enumeration_class_id,
        ec.name AS enumeration_class_name,
        cn.id   AS classifier_node_id,
        cn.name AS classifier_node_name,
        COUNT(ev.id) AS value_count,
        e.created_at,
        e.updated_at
    FROM enumeration e
    JOIN enumeration_class ec ON ec.id = e.enumeration_class_id
    LEFT JOIN classifier_node cn ON cn.id = e.classifier_node_id
    LEFT JOIN enumeration_value ev ON ev.enumeration_id = e.id
    WHERE e.enumeration_class_id = p_class_id
    GROUP BY e.id, ec.id, ec.name, cn.id, cn.name
    ORDER BY e.name;
$$;

-- =============================================================================
-- 3. РЕДАКТИРОВАНИЕ СПИСКА ЗНАЧЕНИЙ ПЕРЕЧИСЛЕНИЯ
-- =============================================================================

-- Добавить значение в перечисление
CREATE OR REPLACE FUNCTION add_enumeration_value(
    p_enumeration_id BIGINT,
    p_code           VARCHAR(100),
    p_name           VARCHAR(255)
)
RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_id         BIGINT;
    v_sort_order INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM enumeration WHERE id = p_enumeration_id) THEN
        RAISE EXCEPTION 'Перечисление с id=% не найдено', p_enumeration_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM enumeration_value
        WHERE enumeration_id = p_enumeration_id AND code = p_code
    ) THEN
        RAISE EXCEPTION 'Значение с кодом "%" уже существует в перечислении id=%', p_code, p_enumeration_id;
    END IF;

    SELECT COALESCE(MAX(sort_order) + 1, 0)
    INTO v_sort_order
    FROM enumeration_value
    WHERE enumeration_id = p_enumeration_id;

    INSERT INTO enumeration_value (code, name, enumeration_id, sort_order, created_at, updated_at)
    VALUES (p_code, p_name, p_enumeration_id, v_sort_order, NOW(), NOW())
    RETURNING id INTO v_id;

    UPDATE enumeration SET updated_at = NOW() WHERE id = p_enumeration_id;

    RETURN v_id;
END;
$$;

-- Обновить значение перечисления
CREATE OR REPLACE PROCEDURE update_enumeration_value(
    p_id   BIGINT,
    p_code VARCHAR(100) DEFAULT NULL,
    p_name VARCHAR(255) DEFAULT NULL
)
LANGUAGE plpgsql AS $$
DECLARE
    v_enum_id BIGINT;
BEGIN
    SELECT enumeration_id INTO v_enum_id
    FROM enumeration_value WHERE id = p_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Значение перечисления с id=% не найдено', p_id;
    END IF;

    IF p_code IS NOT NULL AND EXISTS (
        SELECT 1 FROM enumeration_value
        WHERE enumeration_id = v_enum_id AND code = p_code AND id <> p_id
    ) THEN
        RAISE EXCEPTION 'Значение с кодом "%" уже существует в перечислении id=%', p_code, v_enum_id;
    END IF;

    UPDATE enumeration_value
    SET code       = COALESCE(p_code, code),
        name       = COALESCE(p_name, name),
        updated_at = NOW()
    WHERE id = p_id;
END;
$$;

-- Удалить значение и сдвинуть sort_order оставшихся
CREATE OR REPLACE PROCEDURE delete_enumeration_value(p_id BIGINT)
LANGUAGE plpgsql AS $$
DECLARE
    v_enum_id    BIGINT;
    v_sort_order INT;
BEGIN
    SELECT enumeration_id, sort_order
    INTO v_enum_id, v_sort_order
    FROM enumeration_value WHERE id = p_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Значение перечисления с id=% не найдено', p_id;
    END IF;

    DELETE FROM enumeration_value WHERE id = p_id;

    -- Сдвигаем все последующие позиции на 1 вниз
    UPDATE enumeration_value
    SET sort_order = sort_order - 1,
        updated_at = NOW()
    WHERE enumeration_id = v_enum_id
      AND sort_order > v_sort_order;

    UPDATE enumeration SET updated_at = NOW() WHERE id = v_enum_id;
END;
$$;

-- =============================================================================
-- 4. ИЗМЕНЕНИЕ ПОРЯДКА ПОЗИЦИЙ В СПИСКЕ ЗНАЧЕНИЙ
-- =============================================================================

CREATE OR REPLACE PROCEDURE reorder_enumeration_value(
    p_id           BIGINT,
    p_new_position INT
)
LANGUAGE plpgsql AS $$
DECLARE
    v_enum_id     BIGINT;
    v_old_pos     INT;
    v_max_pos     INT;
    v_target_pos  INT;
BEGIN
    SELECT enumeration_id, sort_order
    INTO v_enum_id, v_old_pos
    FROM enumeration_value WHERE id = p_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Значение перечисления с id=% не найдено', p_id;
    END IF;

    SELECT MAX(sort_order) INTO v_max_pos
    FROM enumeration_value WHERE enumeration_id = v_enum_id;

    -- Приводим к допустимому диапазону [0, max]
    v_target_pos := GREATEST(0, LEAST(p_new_position, v_max_pos));

    IF v_old_pos = v_target_pos THEN
        RETURN;
    END IF;

    IF v_old_pos < v_target_pos THEN
        -- Двигаем вниз: промежуточные сдвигаются вверх
        UPDATE enumeration_value
        SET sort_order = sort_order - 1,
            updated_at = NOW()
        WHERE enumeration_id = v_enum_id
          AND sort_order > v_old_pos
          AND sort_order <= v_target_pos;
    ELSE
        -- Двигаем вверх: промежуточные сдвигаются вниз
        UPDATE enumeration_value
        SET sort_order = sort_order + 1,
            updated_at = NOW()
        WHERE enumeration_id = v_enum_id
          AND sort_order >= v_target_pos
          AND sort_order < v_old_pos;
    END IF;

    UPDATE enumeration_value
    SET sort_order = v_target_pos, updated_at = NOW()
    WHERE id = p_id;
END;
$$;

-- =============================================================================
-- 5. ВЫВОД ЗНАЧЕНИЙ ПЕРЕЧИСЛЕНИЯ
-- =============================================================================

CREATE OR REPLACE FUNCTION get_enumeration_values(p_enumeration_id BIGINT)
RETURNS TABLE (
    id             BIGINT,
    code           VARCHAR,
    name           VARCHAR,
    enumeration_id BIGINT,
    sort_order     INT,
    created_at     TIMESTAMP WITH TIME ZONE,
    updated_at     TIMESTAMP WITH TIME ZONE
)
LANGUAGE sql AS $$
    SELECT id, code, name, enumeration_id, sort_order, created_at, updated_at
    FROM enumeration_value
    WHERE enumeration_id = p_enumeration_id
    ORDER BY sort_order;
$$;

-- =============================================================================
-- 6. ВЫБОР ЗНАЧЕНИЯ ПЕРЕЧИСЛЕНИЯ ДЛЯ УЗЛА КЛАССИФИКАТОРА
-- =============================================================================

-- Выбрать (или заменить) значение перечисления для узла
CREATE OR REPLACE PROCEDURE select_enumeration_value(
    p_node_id      BIGINT,
    p_enum_id      BIGINT,
    p_value_id     BIGINT
)
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM classifier_node WHERE id = p_node_id) THEN
        RAISE EXCEPTION 'Узел классификатора с id=% не найден', p_node_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM enumeration WHERE id = p_enum_id) THEN
        RAISE EXCEPTION 'Перечисление с id=% не найдено', p_enum_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM enumeration_value
        WHERE id = p_value_id AND enumeration_id = p_enum_id
    ) THEN
        RAISE EXCEPTION 'Значение id=% не принадлежит перечислению id=%', p_value_id, p_enum_id;
    END IF;

    -- Upsert: создать или обновить
    INSERT INTO node_attribute_value
        (classifier_node_id, enumeration_id, enumeration_value_id, created_at, updated_at)
    VALUES
        (p_node_id, p_enum_id, p_value_id, NOW(), NOW())
    ON CONFLICT ON CONSTRAINT uq_node_enumeration
    DO UPDATE SET
        enumeration_value_id = EXCLUDED.enumeration_value_id,
        updated_at           = NOW();
END;
$$;

-- Снять выбор значения перечисления с узла
CREATE OR REPLACE PROCEDURE clear_enumeration_value(
    p_node_id BIGINT,
    p_enum_id BIGINT
)
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM node_attribute_value
        WHERE classifier_node_id = p_node_id AND enumeration_id = p_enum_id
    ) THEN
        RAISE EXCEPTION 'Для узла id=% не задано значение перечисления id=%', p_node_id, p_enum_id;
    END IF;

    DELETE FROM node_attribute_value
    WHERE classifier_node_id = p_node_id AND enumeration_id = p_enum_id;
END;
$$;

-- Получить все выбранные значения узла
CREATE OR REPLACE FUNCTION get_node_attributes(p_node_id BIGINT)
RETURNS TABLE (
    id                   BIGINT,
    classifier_node_id   BIGINT,
    classifier_node_name VARCHAR,
    enumeration_id       BIGINT,
    enumeration_name     VARCHAR,
    selected_value_id    BIGINT,
    selected_value_code  VARCHAR,
    selected_value_name  VARCHAR,
    created_at           TIMESTAMP WITH TIME ZONE,
    updated_at           TIMESTAMP WITH TIME ZONE
)
LANGUAGE sql AS $$
    SELECT
        nav.id,
        cn.id   AS classifier_node_id,
        cn.name AS classifier_node_name,
        e.id    AS enumeration_id,
        e.name  AS enumeration_name,
        ev.id   AS selected_value_id,
        ev.code AS selected_value_code,
        ev.name AS selected_value_name,
        nav.created_at,
        nav.updated_at
    FROM node_attribute_value nav
    JOIN classifier_node  cn ON cn.id  = nav.classifier_node_id
    JOIN enumeration       e  ON e.id   = nav.enumeration_id
    JOIN enumeration_value ev ON ev.id  = nav.enumeration_value_id
    WHERE nav.classifier_node_id = p_node_id
    ORDER BY e.id;
$$;
