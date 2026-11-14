-- 2-2. Товар и единицы измерения берутся из существующего классификатора.
CREATE TABLE IF NOT EXISTS route (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL UNIQUE REFERENCES classifier_node(id)
);
^^^
CREATE TABLE IF NOT EXISTS operation_type (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);
^^^
CREATE TABLE IF NOT EXISTS route_operation (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    step_number INTEGER NOT NULL CHECK (step_number > 0),
    name VARCHAR(255) NOT NULL,
    operation_type_id BIGINT NOT NULL REFERENCES operation_type(id),
    UNIQUE (route_id, step_number)
);
^^^
CREATE TABLE IF NOT EXISTS operation_input (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL REFERENCES route_operation(id) ON DELETE CASCADE,
    resource_id BIGINT NOT NULL REFERENCES classifier_node(id),
    resource_quantity NUMERIC(19,6) NOT NULL CHECK (resource_quantity > 0),
    output_quantity INTEGER NOT NULL CHECK (output_quantity > 0),
    source_operation_id BIGINT REFERENCES route_operation(id),
    CHECK (source_operation_id <> operation_id)
);
^^^
CREATE INDEX IF NOT EXISTS idx_operation_input_operation ON operation_input(operation_id);
^^^
CREATE INDEX IF NOT EXISTS idx_operation_input_source ON operation_input(source_operation_id);
^^^
CREATE TABLE IF NOT EXISTS asset (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('TOOL','EQUIPMENT'))
);
^^^
CREATE TABLE IF NOT EXISTS operation_asset (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL REFERENCES route_operation(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    UNIQUE (operation_id, asset_id)
);
^^^
CREATE TABLE IF NOT EXISTS labor_rate (
    id BIGSERIAL PRIMARY KEY,
    profession VARCHAR(255) NOT NULL UNIQUE,
    hourly_rate NUMERIC(19,6) NOT NULL CHECK (hourly_rate >= 0)
);
^^^
CREATE TABLE IF NOT EXISTS operation_labor (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL REFERENCES route_operation(id) ON DELETE CASCADE,
    labor_rate_id BIGINT NOT NULL REFERENCES labor_rate(id),
    setup_minutes NUMERIC(19,6) NOT NULL CHECK (setup_minutes >= 0),
    minutes_per_unit NUMERIC(19,6) NOT NULL CHECK (minutes_per_unit >= 0),
    UNIQUE (operation_id, labor_rate_id)
);
^^^
-- Для собственного компонента раскрываем только операции до указанного шага включительно.
-- path упорядочивает операции. visited защищает обход на повреждённых данных.
CREATE OR REPLACE FUNCTION route_expand(p_route BIGINT, p_quantity NUMERIC)
RETURNS TABLE(operation_id BIGINT, depth INTEGER, batch_quantity NUMERIC, path BIGINT[])
LANGUAGE SQL STABLE AS $$
    WITH RECURSIVE steps(op_id, depth, batch_quantity, path, visited) AS (
        SELECT o.id, 0, p_quantity, ARRAY[o.step_number::BIGINT], ARRAY[o.id]
        FROM route_operation o WHERE o.route_id=p_route AND p_quantity>0
        UNION ALL
        SELECT child.id, s.depth+1, s.batch_quantity*i.resource_quantity/i.output_quantity,
               s.path || ARRAY[i.id, child.step_number::BIGINT], s.visited || child.id
        FROM steps s
        JOIN operation_input i ON i.operation_id=s.op_id
        JOIN route_operation source ON source.id=i.source_operation_id
        JOIN route_operation child ON child.route_id=source.route_id AND child.step_number<=source.step_number
        WHERE NOT child.id=ANY(s.visited)
    )
    SELECT op_id, depth, batch_quantity, path FROM steps
$$;
^^^
-- В закупку попадают только внешние ресурсы, без повторного учёта собственных узлов.
CREATE OR REPLACE FUNCTION route_material_needs(p_route BIGINT, p_quantity NUMERIC)
RETURNS TABLE(resource_id BIGINT, quantity NUMERIC) LANGUAGE SQL STABLE AS $$
    SELECT i.resource_id, SUM(s.batch_quantity*i.resource_quantity/i.output_quantity)
    FROM route_expand(p_route,p_quantity) s JOIN operation_input i ON i.operation_id=s.operation_id
    WHERE i.source_operation_id IS NULL GROUP BY i.resource_id
$$;
^^^
-- Для каждого шага объединяем потребность из всех ветвей в одну производственную партию.
CREATE OR REPLACE FUNCTION route_labor_needs(p_route BIGINT, p_quantity NUMERIC)
RETURNS TABLE(rate_id BIGINT, minutes NUMERIC) LANGUAGE SQL STABLE AS $$
    WITH batches AS (
        SELECT operation_id, SUM(batch_quantity) quantity
        FROM route_expand(p_route,p_quantity) GROUP BY operation_id
    )
    SELECT l.labor_rate_id, SUM(l.setup_minutes+l.minutes_per_unit*b.quantity)
    FROM batches b JOIN operation_labor l ON l.operation_id=b.operation_id
    GROUP BY l.labor_rate_id
$$;
^^^
-- Код закупочной цены используется также каталогом. Не меняем уже заданные значения.
INSERT INTO unit_of_measure(code,name) VALUES ('RUB','рубли') ON CONFLICT(code) DO NOTHING;
^^^
INSERT INTO numeric_parameter(code,name,description,min_value,unit_of_measure_id,created_at,updated_at)
SELECT 'PURCHASE_PRICE','Закупочная цена','Рубли за единицу ресурса',0,id,now(),now()
FROM unit_of_measure WHERE code='RUB' ON CONFLICT(code) DO NOTHING;
