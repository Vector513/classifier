-- Задание 2.1. Дополнение существующего классификатора, PostgreSQL 15.
-- Разделитель ^^^ позволяет загружать тела PL/pgSQL через Spring ScriptUtils.
CREATE TABLE IF NOT EXISTS bom_product (
    node_id BIGINT PRIMARY KEY REFERENCES classifier_node(id) ON DELETE RESTRICT,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ASSEMBLY','PART','MATERIAL','PURCHASED')),
    unit_id BIGINT NOT NULL REFERENCES unit_of_measure(id) ON DELETE RESTRICT
);
COMMENT ON TABLE bom_product IS 'Тип изделия и фиксированная единица норм расхода; узел существующего справочника';
CREATE TABLE IF NOT EXISTS bom_specification (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES bom_product(node_id) ON DELETE RESTRICT,
    revision INTEGER NOT NULL CHECK (revision > 0),
    base_id BIGINT REFERENCES bom_specification(id) ON DELETE RESTRICT,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','RELEASED')),
    description VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at TIMESTAMPTZ,
    UNIQUE(product_id, revision), CHECK(base_id IS NULL OR base_id <> id)
);
CREATE TABLE IF NOT EXISTS bom_line (
    specification_id BIGINT NOT NULL REFERENCES bom_specification(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position > 0),
    component_id BIGINT REFERENCES bom_product(node_id) ON DELETE RESTRICT,
    component_specification_id BIGINT REFERENCES bom_specification(id) ON DELETE RESTRICT,
    quantity NUMERIC(19,6),
    excluded BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY(specification_id, position),
    CHECK ((excluded AND component_id IS NULL AND quantity IS NULL AND component_specification_id IS NULL)
        OR (NOT excluded AND component_id IS NOT NULL AND quantity > 0 AND quantity IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_bom_line_component ON bom_line(component_id);
COMMENT ON TABLE bom_specification IS 'Версия: черновик или неизменяемая утвержденная спецификация. base_id фиксирует базу изменения/модификации';
COMMENT ON TABLE bom_line IS 'Локальные позиции: добавление, замена по номеру позиции или исключение из базового состава';
^^^
-- Ближайшее определение позиции имеет приоритет; исключения скрывают базовую строку.
CREATE OR REPLACE FUNCTION bom_effective(p_id BIGINT)
RETURNS TABLE(source_id BIGINT, "position" INTEGER, component_id BIGINT, component_specification_id BIGINT, quantity NUMERIC)
LANGUAGE SQL STABLE AS $$
    WITH RECURSIVE bases AS (
        SELECT id, base_id, 0 AS depth, ARRAY[id] AS path FROM bom_specification WHERE id = p_id
        UNION ALL
        SELECT s.id, s.base_id, b.depth+1, b.path || s.id
        FROM bases b JOIN bom_specification s ON s.id=b.base_id WHERE NOT s.id=ANY(b.path)
    ), nearest AS (
        SELECT DISTINCT ON (l.position) l.*, b.depth
        FROM bases b JOIN bom_line l ON l.specification_id=b.id ORDER BY l.position,b.depth
    )
    SELECT specification_id, position, component_id, component_specification_id, quantity
    FROM nearest WHERE NOT excluded ORDER BY position
$$;
^^^
-- Каждая ветвь сохраняется отдельно: общий компонент в разных ветвях нельзя удалять DISTINCT.
-- Количество накапливается произведением норм; cycle сообщает о цикле по изделиям.
CREATE OR REPLACE FUNCTION bom_explode(p_id BIGINT, p_quantity NUMERIC DEFAULT 1)
RETURNS TABLE(depth INTEGER, path INTEGER[], source_id BIGINT, "position" INTEGER, component_id BIGINT,
    component_specification_id BIGINT, quantity NUMERIC, total_quantity NUMERIC, cycle BOOLEAN)
LANGUAGE SQL STABLE AS $$
    WITH RECURSIVE tree AS (
        SELECT 1 AS depth, ARRAY[e.position] AS path, e.source_id, e.position, e.component_id,
            e.component_specification_id, e.quantity, e.quantity*p_quantity AS total_quantity,
            ARRAY[s.product_id,e.component_id] AS products, e.component_id=s.product_id AS cycle
        FROM bom_specification s CROSS JOIN LATERAL bom_effective(s.id) e WHERE s.id=p_id
        UNION ALL
        SELECT t.depth+1, t.path || e.position, e.source_id, e.position, e.component_id,
            e.component_specification_id, e.quantity, t.total_quantity*e.quantity,
            t.products || e.component_id, e.component_id=ANY(t.products)
        FROM tree t CROSS JOIN LATERAL bom_effective(t.component_specification_id) e WHERE NOT t.cycle
    ) SELECT depth,path,source_id,position,component_id,component_specification_id,quantity,total_quantity,cycle
      FROM tree ORDER BY path
$$;
^^^
-- Суммы по каждому ресурсу в заданном классе и его подклассах, включая сам выбранный узел.
-- Единицы разных ресурсов не складываются. Учитываются и промежуточные компоненты класса.
CREATE OR REPLACE FUNCTION bom_totals(p_id BIGINT, p_quantity NUMERIC, p_class BIGINT)
RETURNS TABLE(component_id BIGINT, total_quantity NUMERIC)
LANGUAGE SQL STABLE AS $$
    WITH RECURSIVE classes AS (
        SELECT id FROM classifier_node WHERE id=p_class
        UNION
        SELECT n.id FROM classifier_node n JOIN classes c ON n.parent_id=c.id
    ) SELECT e.component_id,SUM(e.total_quantity)
      FROM bom_explode(p_id,p_quantity) e JOIN classes c ON c.id=e.component_id
      WHERE NOT e.cycle GROUP BY e.component_id ORDER BY e.component_id
$$;
^^^
-- Защита истории также при прямых SQL-изменениях. Ссылка только на уже утвержденные версии
-- делает граф версий ацикличным; дополнительно проверяем циклы физических изделий.
CREATE OR REPLACE FUNCTION bom_guard_specification() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF TG_OP <> 'INSERT' AND OLD.status='RELEASED' THEN
        RAISE EXCEPTION 'Утвержденная спецификация неизменяема' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF TG_OP='UPDATE' AND (NEW.product_id<>OLD.product_id OR NEW.base_id IS DISTINCT FROM OLD.base_id OR NEW.revision<>OLD.revision) THEN
        RAISE EXCEPTION 'Изделие, база и номер версии неизменяемы' USING ERRCODE='23514';
    END IF;
    IF NEW.base_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM bom_specification WHERE id=NEW.base_id AND status='RELEASED') THEN
        RAISE EXCEPTION 'База должна быть утверждена' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' AND NEW.status<>'DRAFT' THEN
        RAISE EXCEPTION 'Новая версия должна быть черновиком' USING ERRCODE='23514';
    END IF;
    IF NEW.status='RELEASED' THEN
        IF NOT EXISTS(SELECT 1 FROM bom_effective(NEW.id)) THEN
            RAISE EXCEPTION 'Нельзя утвердить пустой состав' USING ERRCODE='23514';
        END IF;
        IF EXISTS(SELECT 1 FROM bom_explode(NEW.id,1) WHERE cycle) THEN
            RAISE EXCEPTION 'Обнаружен цикл в составе изделия' USING ERRCODE='23514';
        END IF;
        NEW.released_at=now();
    END IF;
    RETURN NEW;
END $$;
^^^
CREATE OR REPLACE FUNCTION bom_guard_line() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE owner_id BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF TG_OP='DELETE' THEN owner_id=OLD.specification_id; ELSE owner_id=NEW.specification_id; END IF;
    IF EXISTS(SELECT 1 FROM bom_specification WHERE id=owner_id AND status='RELEASED')
       OR (TG_OP='UPDATE' AND NEW.specification_id<>OLD.specification_id) THEN
        RAISE EXCEPTION 'Строки утвержденной спецификации неизменяемы' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF NOT NEW.excluded THEN
        IF NEW.component_id=(SELECT product_id FROM bom_specification WHERE id=owner_id) THEN
            RAISE EXCEPTION 'Изделие не может входить в себя' USING ERRCODE='23514';
        END IF;
        IF NEW.component_specification_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM bom_specification WHERE id=NEW.component_specification_id
                AND product_id=NEW.component_id AND status='RELEASED') THEN
            RAISE EXCEPTION 'Выберите утвержденную спецификацию этого компонента' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
^^^
DROP TRIGGER IF EXISTS bom_specification_guard ON bom_specification;
CREATE TRIGGER bom_specification_guard BEFORE INSERT OR UPDATE OR DELETE ON bom_specification
    FOR EACH ROW EXECUTE FUNCTION bom_guard_specification();
DROP TRIGGER IF EXISTS bom_line_guard ON bom_line;
CREATE TRIGGER bom_line_guard BEFORE INSERT OR UPDATE OR DELETE ON bom_line
    FOR EACH ROW EXECUTE FUNCTION bom_guard_line();
^^^
