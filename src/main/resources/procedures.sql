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

-- =============================================================================
-- 7. ЧИСЛОВЫЕ ПАРАМЕТРЫ ИЗДЕЛИЙ (задание 1.3)
-- =============================================================================

-- Создать числовой параметр
CREATE OR REPLACE FUNCTION create_numeric_parameter(
    p_code        VARCHAR(100),
    p_name        VARCHAR(255),
    p_description VARCHAR(1000) DEFAULT NULL,
    p_min_value   NUMERIC(19,6) DEFAULT NULL,
    p_max_value   NUMERIC(19,6) DEFAULT NULL,
    p_uom_id      BIGINT        DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM numeric_parameter WHERE code = p_code) THEN
        RAISE EXCEPTION 'Числовой параметр с кодом "%" уже существует', p_code;
    END IF;
    IF p_min_value IS NOT NULL AND p_max_value IS NOT NULL AND p_min_value > p_max_value THEN
        RAISE EXCEPTION 'min_value (%) не может быть больше max_value (%)', p_min_value, p_max_value;
    END IF;

    INSERT INTO numeric_parameter (code, name, description, min_value, max_value, unit_of_measure_id, created_at, updated_at)
    VALUES (p_code, p_name, p_description, p_min_value, p_max_value, p_uom_id, NOW(), NOW())
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$;

-- Назначить числовой параметр на узел классификатора
CREATE OR REPLACE PROCEDURE assign_numeric_parameter(
    p_node_id  BIGINT,
    p_param_id BIGINT
)
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM classifier_node WHERE id = p_node_id) THEN
        RAISE EXCEPTION 'Узел id=% не найден', p_node_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM numeric_parameter WHERE id = p_param_id) THEN
        RAISE EXCEPTION 'Числовой параметр id=% не найден', p_param_id;
    END IF;
    IF EXISTS (SELECT 1 FROM node_numeric_parameter WHERE classifier_node_id = p_node_id AND numeric_parameter_id = p_param_id) THEN
        RAISE EXCEPTION 'Параметр id=% уже назначен на узел id=%', p_param_id, p_node_id;
    END IF;
    INSERT INTO node_numeric_parameter (classifier_node_id, numeric_parameter_id, created_at)
    VALUES (p_node_id, p_param_id, NOW());
END;
$$;

-- Установить (upsert) числовое значение параметра для узла (изделия).
-- Проверяет допустимый диапазон.
CREATE OR REPLACE PROCEDURE set_numeric_value(
    p_node_id  BIGINT,
    p_param_id BIGINT,
    p_value    NUMERIC(19,6)
)
LANGUAGE plpgsql AS $$
DECLARE
    v_min NUMERIC(19,6);
    v_max NUMERIC(19,6);
BEGIN
    SELECT min_value, max_value INTO v_min, v_max
    FROM numeric_parameter WHERE id = p_param_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Числовой параметр id=% не найден', p_param_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM classifier_node WHERE id = p_node_id) THEN
        RAISE EXCEPTION 'Узел id=% не найден', p_node_id;
    END IF;
    IF v_min IS NOT NULL AND p_value < v_min THEN
        RAISE EXCEPTION 'Значение % меньше допустимого минимума %', p_value, v_min;
    END IF;
    IF v_max IS NOT NULL AND p_value > v_max THEN
        RAISE EXCEPTION 'Значение % больше допустимого максимума %', p_value, v_max;
    END IF;

    INSERT INTO node_numeric_value (classifier_node_id, numeric_parameter_id, value, created_at, updated_at)
    VALUES (p_node_id, p_param_id, p_value, NOW(), NOW())
    ON CONFLICT ON CONSTRAINT uq_node_numeric_value
    DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();
END;
$$;

-- Получить эффективные (собственные + унаследованные) числовые параметры узла
CREATE OR REPLACE FUNCTION get_effective_numeric_parameters(p_node_id BIGINT)
RETURNS TABLE (
    parameter_id       BIGINT,
    parameter_code     VARCHAR,
    parameter_name     VARCHAR,
    description        VARCHAR,
    min_value          NUMERIC(19,6),
    max_value          NUMERIC(19,6),
    unit_of_measure    VARCHAR,
    defined_at_node_id BIGINT,
    defined_at_node    VARCHAR,
    is_inherited       BOOLEAN
)
LANGUAGE sql AS $$
    WITH RECURSIVE ancestors AS (
        SELECT id, name, parent_id, 0 AS depth
        FROM classifier_node WHERE id = p_node_id
        UNION ALL
        SELECT cn.id, cn.name, cn.parent_id, a.depth + 1
        FROM classifier_node cn
        JOIN ancestors a ON cn.id = a.parent_id
    ),
    -- Собираем все назначения по предкам; ближайший предок имеет меньший depth
    ranked AS (
        SELECT
            np.id          AS param_id,
            np.code        AS param_code,
            np.name        AS param_name,
            np.description,
            np.min_value,
            np.max_value,
            uom.name       AS unit_of_measure,
            a.id           AS node_id,
            a.name         AS node_name,
            a.depth,
            ROW_NUMBER() OVER (PARTITION BY np.id ORDER BY a.depth ASC) AS rn
        FROM ancestors a
        JOIN node_numeric_parameter nnp ON nnp.classifier_node_id = a.id
        JOIN numeric_parameter np       ON np.id = nnp.numeric_parameter_id
        LEFT JOIN unit_of_measure uom   ON uom.id = np.unit_of_measure_id
    )
    SELECT
        param_id,
        param_code,
        param_name,
        description,
        min_value,
        max_value,
        unit_of_measure,
        node_id,
        node_name,
        (depth > 0) AS is_inherited
    FROM ranked WHERE rn = 1
    ORDER BY param_name;
$$;

-- Агрегаты числового параметра по поддереву
CREATE OR REPLACE FUNCTION get_numeric_aggregates(p_root_id BIGINT, p_param_id BIGINT)
RETURNS TABLE (
    parameter_code VARCHAR,
    parameter_name VARCHAR,
    cnt            BIGINT,
    min_val        NUMERIC(19,6),
    max_val        NUMERIC(19,6),
    avg_val        NUMERIC(19,6)
)
LANGUAGE sql AS $$
    WITH RECURSIVE descendants AS (
        SELECT id FROM classifier_node WHERE id = p_root_id
        UNION ALL
        SELECT cn.id FROM classifier_node cn
        JOIN descendants d ON cn.parent_id = d.id
    )
    SELECT
        np.code,
        np.name,
        COUNT(nnv.value),
        MIN(nnv.value),
        MAX(nnv.value),
        ROUND(AVG(nnv.value), 6)
    FROM node_numeric_value nnv
    JOIN numeric_parameter np ON np.id = nnv.numeric_parameter_id
    WHERE nnv.classifier_node_id IN (SELECT id FROM descendants)
      AND nnv.numeric_parameter_id = p_param_id
    GROUP BY np.code, np.name;
$$;

-- =============================================================================
-- 8. ПЕРЕЧИСЛИМЫЕ ПАРАМЕТРЫ — НАСЛЕДОВАНИЕ, ФИЛЬТРАЦИЯ, АГРЕГАТЫ (задание 1.3)
-- =============================================================================

-- Получить эффективные (собственные + унаследованные) перечисления для узла
CREATE OR REPLACE FUNCTION get_effective_enumerations(p_node_id BIGINT)
RETURNS TABLE (
    enumeration_id        BIGINT,
    enumeration_code      VARCHAR,
    enumeration_name      VARCHAR,
    enumeration_class     VARCHAR,
    defined_at_node_id    BIGINT,
    defined_at_node_name  VARCHAR,
    is_inherited          BOOLEAN
)
LANGUAGE sql AS $$
    WITH RECURSIVE ancestors AS (
        SELECT id, name, parent_id, 0 AS depth
        FROM classifier_node WHERE id = p_node_id
        UNION ALL
        SELECT cn.id, cn.name, cn.parent_id, a.depth + 1
        FROM classifier_node cn
        JOIN ancestors a ON cn.id = a.parent_id
    ),
    ranked AS (
        SELECT
            e.id   AS enum_id,
            e.code AS enum_code,
            e.name AS enum_name,
            ec.name AS class_name,
            a.id   AS node_id,
            a.name AS node_name,
            a.depth,
            ROW_NUMBER() OVER (PARTITION BY e.id ORDER BY a.depth ASC) AS rn
        FROM ancestors a
        JOIN enumeration e ON e.classifier_node_id = a.id
        JOIN enumeration_class ec ON ec.id = e.enumeration_class_id
    )
    SELECT enum_id, enum_code, enum_name, class_name, node_id, node_name, (depth > 0)
    FROM ranked WHERE rn = 1
    ORDER BY enum_name;
$$;

-- Отбор изделий в поддереве по значению перечислимого параметра
CREATE OR REPLACE FUNCTION filter_nodes_by_enum(
    p_root_id        BIGINT,
    p_enumeration_id BIGINT,
    p_value_id       BIGINT
)
RETURNS TABLE (
    node_id    BIGINT,
    node_code  VARCHAR,
    node_name  VARCHAR,
    value_code VARCHAR,
    value_name VARCHAR
)
LANGUAGE sql AS $$
    WITH RECURSIVE descendants AS (
        SELECT id FROM classifier_node WHERE id = p_root_id
        UNION ALL
        SELECT cn.id FROM classifier_node cn
        JOIN descendants d ON cn.parent_id = d.id
    )
    SELECT
        cn.id,
        cn.code,
        cn.name,
        ev.code,
        ev.name
    FROM node_attribute_value nav
    JOIN classifier_node  cn ON cn.id  = nav.classifier_node_id
    JOIN enumeration_value ev ON ev.id  = nav.enumeration_value_id
    WHERE nav.classifier_node_id IN (SELECT id FROM descendants)
      AND nav.enumeration_id = p_enumeration_id
      AND nav.enumeration_value_id = p_value_id
    ORDER BY cn.name;
$$;

-- Агрегаты по перечислимому параметру в поддереве (распределение по значениям)
CREATE OR REPLACE FUNCTION get_enum_aggregates(p_root_id BIGINT, p_enumeration_id BIGINT)
RETURNS TABLE (
    value_id   BIGINT,
    value_code VARCHAR,
    value_name VARCHAR,
    cnt        BIGINT
)
LANGUAGE sql AS $$
    WITH RECURSIVE descendants AS (
        SELECT id FROM classifier_node WHERE id = p_root_id
        UNION ALL
        SELECT cn.id FROM classifier_node cn
        JOIN descendants d ON cn.parent_id = d.id
    )
    SELECT
        ev.id,
        ev.code,
        ev.name,
        COUNT(*) AS cnt
    FROM node_attribute_value nav
    JOIN enumeration_value ev ON ev.id = nav.enumeration_value_id
    WHERE nav.classifier_node_id IN (SELECT id FROM descendants)
      AND nav.enumeration_id = p_enumeration_id
    GROUP BY ev.id, ev.code, ev.name
    ORDER BY cnt DESC;
$$;

-- Поиск изделий по коду или названию с возвратом значений всех параметров
CREATE OR REPLACE FUNCTION search_items_with_parameters(p_query VARCHAR)
RETURNS TABLE (
    node_id         BIGINT,
    node_code       VARCHAR,
    node_name       VARCHAR,
    parent_name     VARCHAR,
    param_type      VARCHAR,   -- 'enum' или 'numeric'
    param_name      VARCHAR,
    value_text      VARCHAR
)
LANGUAGE sql AS $$
    -- Перечислимые параметры
    SELECT
        cn.id,
        cn.code,
        cn.name,
        p.name,
        'enum'::VARCHAR,
        e.name,
        ev.name
    FROM classifier_node cn
    LEFT JOIN classifier_node p ON p.id = cn.parent_id
    JOIN node_attribute_value nav ON nav.classifier_node_id = cn.id
    JOIN enumeration e            ON e.id  = nav.enumeration_id
    JOIN enumeration_value ev     ON ev.id = nav.enumeration_value_id
    WHERE LOWER(cn.code) LIKE LOWER(CONCAT('%', p_query, '%'))
       OR LOWER(cn.name) LIKE LOWER(CONCAT('%', p_query, '%'))

    UNION ALL

    -- Числовые параметры
    SELECT
        cn.id,
        cn.code,
        cn.name,
        p.name,
        'numeric'::VARCHAR,
        np.name,
        CAST(nnv.value AS VARCHAR)
    FROM classifier_node cn
    LEFT JOIN classifier_node p  ON p.id  = cn.parent_id
    JOIN node_numeric_value nnv  ON nnv.classifier_node_id = cn.id
    JOIN numeric_parameter np    ON np.id = nnv.numeric_parameter_id
    WHERE LOWER(cn.code) LIKE LOWER(CONCAT('%', p_query, '%'))
       OR LOWER(cn.name) LIKE LOWER(CONCAT('%', p_query, '%'))

    ORDER BY node_name, param_type, param_name;
$$;

-- Отбор узлов поддерева по диапазону числового параметра
CREATE OR REPLACE FUNCTION filter_nodes_by_numeric(
    p_root_id  BIGINT,
    p_param_id BIGINT,
    p_min      NUMERIC(19,6) DEFAULT NULL,
    p_max      NUMERIC(19,6) DEFAULT NULL
)
RETURNS TABLE (
    node_id   BIGINT,
    node_code VARCHAR,
    node_name VARCHAR,
    value     NUMERIC(19,6)
)
LANGUAGE sql AS $$
    WITH RECURSIVE descendants AS (
        SELECT id FROM classifier_node WHERE id = p_root_id
        UNION ALL
        SELECT cn.id FROM classifier_node cn
        JOIN descendants d ON cn.parent_id = d.id
    )
    SELECT
        cn.id,
        cn.code,
        cn.name,
        nnv.value
    FROM node_numeric_value nnv
    JOIN classifier_node cn ON cn.id = nnv.classifier_node_id
    WHERE nnv.classifier_node_id IN (SELECT id FROM descendants)
      AND nnv.numeric_parameter_id = p_param_id
      AND (p_min IS NULL OR nnv.value >= p_min)
      AND (p_max IS NULL OR nnv.value <= p_max)
    ORDER BY nnv.value;
$$;
