-- =============================================================================
-- Задание 1.4 — Расширение схемы для поддержки Хозяйственных операций (ХО)
-- СУБД: PostgreSQL 15
-- =============================================================================

-- Классификатор ХО (иерархический, self-reference)
CREATE TABLE IF NOT EXISTS ho_class (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    parent_id   BIGINT       REFERENCES ho_class(id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Типы параметров ХО (NUMBER, STRING, DATE, ENUM)
CREATE TABLE IF NOT EXISTS ho_parameter_type (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(100) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    data_type     VARCHAR(20)  NOT NULL
                  CHECK (data_type IN ('NUMBER', 'STRING', 'DATE', 'ENUM')),
    min_value     NUMERIC(19,6),
    max_value     NUMERIC(19,6),
    enum_class_id BIGINT       REFERENCES enumeration_class(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Типы ролей в ХО (Поставщик, Покупатель, Кассир и т.д.)
CREATE TABLE IF NOT EXISTS ho_role_type (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Шаблоны ХО (конкретный подкласс операции)
CREATE TABLE IF NOT EXISTS ho_template (
    id          BIGSERIAL    PRIMARY KEY,
    ho_class_id BIGINT       NOT NULL REFERENCES ho_class(id) ON DELETE RESTRICT,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Параметры шаблона ХО
CREATE TABLE IF NOT EXISTS ho_template_parameter (
    id                   BIGSERIAL PRIMARY KEY,
    ho_template_id       BIGINT    NOT NULL REFERENCES ho_template(id)       ON DELETE CASCADE,
    ho_parameter_type_id BIGINT    NOT NULL REFERENCES ho_parameter_type(id) ON DELETE RESTRICT,
    is_required          BOOLEAN   NOT NULL DEFAULT FALSE,
    sort_order           INT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (ho_template_id, ho_parameter_type_id)
);

-- Роли шаблона ХО
CREATE TABLE IF NOT EXISTS ho_template_role (
    id              BIGSERIAL PRIMARY KEY,
    ho_template_id  BIGINT    NOT NULL REFERENCES ho_template(id)  ON DELETE CASCADE,
    ho_role_type_id BIGINT    NOT NULL REFERENCES ho_role_type(id) ON DELETE RESTRICT,
    is_required     BOOLEAN   NOT NULL DEFAULT FALSE,
    sort_order      INT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (ho_template_id, ho_role_type_id)
);

-- Экземпляры ХО
CREATE TABLE IF NOT EXISTS ho_instance (
    id             BIGSERIAL    PRIMARY KEY,
    ho_template_id BIGINT       NOT NULL REFERENCES ho_template(id) ON DELETE RESTRICT,
    code           VARCHAR(100) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    doc_date       DATE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Значения параметров экземпляра ХО
CREATE TABLE IF NOT EXISTS ho_instance_value (
    id                   BIGSERIAL     PRIMARY KEY,
    ho_instance_id       BIGINT        NOT NULL REFERENCES ho_instance(id)       ON DELETE CASCADE,
    ho_parameter_type_id BIGINT        NOT NULL REFERENCES ho_parameter_type(id) ON DELETE RESTRICT,
    numeric_value        NUMERIC(19,6),
    string_value         VARCHAR(1000),
    date_value           DATE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (ho_instance_id, ho_parameter_type_id)
);

-- Назначения контрагентов на роли в экземпляре ХО
CREATE TABLE IF NOT EXISTS ho_instance_role (
    id              BIGSERIAL    PRIMARY KEY,
    ho_instance_id  BIGINT       NOT NULL REFERENCES ho_instance(id)  ON DELETE CASCADE,
    ho_role_type_id BIGINT       NOT NULL REFERENCES ho_role_type(id) ON DELETE RESTRICT,
    counterparty    VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (ho_instance_id, ho_role_type_id)
);
