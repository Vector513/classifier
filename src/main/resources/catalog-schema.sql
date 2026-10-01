-- Catalog v2: сохранение ID, явный смысл узла, независимые назначения перечислений.
DO $migration$
DECLARE snapshot JSONB; fk RECORD;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF to_regclass('schema_migration') IS NULL AND to_regclass('bom_seed_history') IS NOT NULL THEN
        ALTER TABLE bom_seed_history RENAME TO schema_migration;
    END IF;
    CREATE TABLE IF NOT EXISTS schema_migration(name VARCHAR(100) PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now());
    ALTER TABLE schema_migration ADD COLUMN IF NOT EXISTS details JSONB;
    ALTER TABLE classifier_node ADD COLUMN IF NOT EXISTS node_kind VARCHAR(16) NOT NULL DEFAULT 'GROUP';
    CREATE TABLE IF NOT EXISTS node_enumeration (
        classifier_node_id BIGINT NOT NULL REFERENCES classifier_node(id) ON DELETE CASCADE,
        enumeration_id BIGINT NOT NULL REFERENCES enumeration(id) ON DELETE RESTRICT,
        restrict_values BOOLEAN NOT NULL DEFAULT FALSE,
        PRIMARY KEY(classifier_node_id, enumeration_id)
    );
    CREATE UNIQUE INDEX IF NOT EXISTS uq_enum_value_pair ON enumeration_value(id,enumeration_id);
    CREATE UNIQUE INDEX IF NOT EXISTS uq_enum_value_code ON enumeration_value(enumeration_id,code);
    CREATE TABLE IF NOT EXISTS node_enumeration_option (
        classifier_node_id BIGINT NOT NULL,
        enumeration_id BIGINT NOT NULL,
        enumeration_value_id BIGINT NOT NULL,
        PRIMARY KEY(classifier_node_id, enumeration_id, enumeration_value_id),
        FOREIGN KEY(classifier_node_id,enumeration_id) REFERENCES node_enumeration(classifier_node_id,enumeration_id) ON DELETE CASCADE,
        FOREIGN KEY(enumeration_value_id,enumeration_id) REFERENCES enumeration_value(id,enumeration_id) ON DELETE RESTRICT
    );
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='fk_node_attribute_value_pair' AND conrelid='node_attribute_value'::regclass) THEN
        ALTER TABLE node_attribute_value ADD CONSTRAINT fk_node_attribute_value_pair FOREIGN KEY(enumeration_value_id,enumeration_id) REFERENCES enumeration_value(id,enumeration_id) ON DELETE RESTRICT;
    END IF;
    -- Значения и назначения принадлежат записи справочника; при её удалении не должны оставаться сиротами.
    FOR fk IN SELECT c.conname,c.conrelid::regclass AS table_name FROM pg_constraint c
        WHERE c.contype='f' AND c.confrelid='classifier_node'::regclass AND c.confdeltype<>'c'
          AND c.conrelid IN ('node_attribute_value'::regclass,'node_numeric_value'::regclass,'node_numeric_parameter'::regclass) LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',fk.table_name,fk.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY(classifier_node_id) REFERENCES classifier_node(id) ON DELETE CASCADE',fk.table_name,fk.conname);
    END LOOP;
    IF EXISTS (SELECT 1 FROM schema_migration WHERE name='catalog-v2') THEN RETURN; END IF;
    IF EXISTS (
        WITH RECURSIVE paths AS (
            SELECT id,parent_id,ARRAY[id] AS path,FALSE AS cycle FROM classifier_node
            UNION ALL SELECT n.id,n.parent_id,p.path||n.id,n.id=ANY(p.path) FROM paths p JOIN classifier_node n ON n.id=p.parent_id WHERE NOT p.cycle
        ) SELECT 1 FROM paths WHERE cycle
    ) THEN RAISE EXCEPTION 'В исходном дереве есть цикл. Исправьте parent_id перед миграцией'; END IF;

    snapshot=jsonb_build_object('nodes',COALESCE((SELECT jsonb_agg(to_jsonb(n)) FROM classifier_node n),'[]'::jsonb),
        'parameters',COALESCE((SELECT jsonb_agg(to_jsonb(p)) FROM numeric_parameter p),'[]'::jsonb),
        'numeric_values',COALESCE((SELECT jsonb_agg(to_jsonb(v)) FROM node_numeric_value v),'[]'::jsonb));
    -- Однократное определение смысла старых записей. В дальнейшем число детей тип не меняет.
    UPDATE classifier_node n SET node_kind=CASE
        WHEN EXISTS(SELECT 1 FROM classifier_node c WHERE c.parent_id=n.id) THEN 'GROUP'
        WHEN n.code IN ('ELECTRONICS','PHONES','LAPTOPS','TABLETS','AUDIO','ACCESSORIES',
            'PHONES-APPLE','PHONES-SAMSUNG','PHONES-XIAOMI','LAPTOPS-APPLE','LAPTOPS-LENOVO','LAPTOPS-ASUS',
            'TABLETS-APPLE','TABLETS-SAMSUNG','AUDIO-HEADPHONES','AUDIO-SPEAKERS','ACC-CASES','ACC-CHARGERS','ACC-CABLES','ACC-POWERBANKS',
            'BOM-DEMO','BOM-MATERIALS','BOM-ASSEMBLIES') THEN 'GROUP'
        WHEN n.code IN ('PHONES-APPLE-IP16','PHONES-APPLE-IP16PRO','PHONES-APPLE-IP15','PHONES-SAMSUNG-S24','PHONES-SAMSUNG-S24U','PHONES-XIAOMI-14','LAPTOPS-APPLE-AIR','LAPTOPS-APPLE-PRO','LAPTOPS-LENOVO-TP','LAPTOPS-LENOVO-YOGA') THEN 'MODEL'
        ELSE 'PRODUCT' END;
    IF to_regclass('bom_product') IS NOT NULL THEN
        -- Ранее конечная пустая группа могла быть зарегистрирована как изделие. Сохраняем рабочие ссылки.
        UPDATE classifier_node SET node_kind='PRODUCT' WHERE id IN (SELECT node_id FROM bom_product);
        IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='bom_product' AND column_name='unit_id') THEN
            snapshot=snapshot || jsonb_build_object('bom_products',(SELECT jsonb_agg(to_jsonb(p)) FROM bom_product p));
            UPDATE classifier_node n SET unit_of_measure_id=p.unit_id FROM bom_product p WHERE p.node_id=n.id;
            ALTER TABLE bom_product DROP COLUMN unit_id;
        END IF;
    END IF;
    INSERT INTO unit_of_measure(code,name) VALUES('PCS','штуки') ON CONFLICT(code) DO NOTHING;
    WITH RECURSIVE paths AS (
        SELECT id AS owner,id,parent_id,unit_of_measure_id,0 AS depth FROM classifier_node
        UNION ALL SELECT p.owner,n.id,n.parent_id,n.unit_of_measure_id,p.depth+1 FROM paths p JOIN classifier_node n ON n.id=p.parent_id
    ), nearest AS (SELECT DISTINCT ON(owner) owner,unit_of_measure_id FROM paths WHERE unit_of_measure_id IS NOT NULL ORDER BY owner,depth)
    UPDATE classifier_node n SET unit_of_measure_id=COALESCE((SELECT unit_of_measure_id FROM nearest WHERE owner=n.id),(SELECT id FROM unit_of_measure WHERE code='PCS'))
    WHERE n.node_kind<>'GROUP' AND n.unit_of_measure_id IS NULL;
    IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='enumeration' AND column_name='classifier_node_id') THEN
        snapshot=snapshot || jsonb_build_object('enumeration_scopes',(SELECT jsonb_agg(to_jsonb(e)) FROM enumeration e));
        INSERT INTO node_enumeration(classifier_node_id,enumeration_id)
            SELECT classifier_node_id,id FROM enumeration WHERE classifier_node_id IS NOT NULL ON CONFLICT DO NOTHING;
        ALTER TABLE enumeration DROP COLUMN classifier_node_id;
    END IF;
    -- Старые явно сохранённые значения не теряются: недостающие назначения восстанавливаются у владельца.
    WITH RECURSIVE paths AS (
        SELECT id AS owner,id,parent_id FROM classifier_node
        UNION ALL SELECT p.owner,n.id,n.parent_id FROM paths p JOIN classifier_node n ON n.id=p.parent_id
    ) INSERT INTO node_enumeration(classifier_node_id,enumeration_id)
      SELECT v.classifier_node_id,v.enumeration_id FROM node_attribute_value v
      WHERE NOT EXISTS(SELECT 1 FROM paths p JOIN node_enumeration b ON b.classifier_node_id=p.id WHERE p.owner=v.classifier_node_id AND b.enumeration_id=v.enumeration_id)
      ON CONFLICT DO NOTHING;
    WITH RECURSIVE paths AS (
        SELECT id AS owner,id,parent_id FROM classifier_node
        UNION ALL SELECT p.owner,n.id,n.parent_id FROM paths p JOIN classifier_node n ON n.id=p.parent_id
    ) INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,created_at)
      SELECT v.classifier_node_id,v.numeric_parameter_id,now() FROM node_numeric_value v
      WHERE NOT EXISTS(SELECT 1 FROM paths p JOIN node_numeric_parameter b ON b.classifier_node_id=p.id WHERE p.owner=v.classifier_node_id AND b.numeric_parameter_id=v.numeric_parameter_id)
      ON CONFLICT DO NOTHING;
    -- Исправляем только распознанные исходные примеры массы, сохранённые в граммах при единице KG.
    UPDATE node_numeric_value v SET value=v.value/1000
      FROM classifier_node n,numeric_parameter p,unit_of_measure u
      WHERE n.id=v.classifier_node_id AND p.id=v.numeric_parameter_id AND p.unit_of_measure_id=u.id AND p.code='WEIGHT' AND u.code='KG'
      AND ((n.code='PHONES-APPLE-IP16PRO' AND v.value=170) OR (n.code='PHONES-APPLE-IP15' AND v.value=227)
        OR (n.code='PHONES-SAMSUNG-S24' AND v.value=167) OR (n.code='PHONES-SAMSUNG-S24U' AND v.value=232)
        OR (n.code='LAPTOPS-APPLE-AIR' AND v.value=1240) OR (n.code='LAPTOPS-APPLE-PRO' AND v.value=1610));
    -- Старые пределы не должны противоречить уже сохранённым фактам. Исходные определения есть в snapshot.
    UPDATE numeric_parameter p SET min_value=LEAST(p.min_value,(SELECT min(value) FROM node_numeric_value WHERE numeric_parameter_id=p.id))
      WHERE p.min_value>(SELECT min(value) FROM node_numeric_value WHERE numeric_parameter_id=p.id);
    UPDATE numeric_parameter p SET max_value=GREATEST(p.max_value,(SELECT max(value) FROM node_numeric_value WHERE numeric_parameter_id=p.id))
      WHERE p.max_value<(SELECT max(value) FROM node_numeric_value WHERE numeric_parameter_id=p.id);
    INSERT INTO schema_migration(name,details) VALUES('catalog-v2',snapshot);
END
$migration$;
^^^
-- У прежнего примера были отдельные BOM-PCS/BOM-KG для тех же физических единиц.
-- Объединяем только распознанные стандартные записи; произвольно переименованные единицы не угадываем.
DO $units$
DECLARE unit_alias RECORD; target_id BIGINT; had_lock BOOLEAN;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS(SELECT 1 FROM schema_migration WHERE name='catalog-unit-aliases-v2') THEN RETURN; END IF;
    INSERT INTO unit_of_measure(code,name) VALUES('PCS','штуки'),('KG','килограммы') ON CONFLICT(code) DO NOTHING;
    INSERT INTO schema_migration(name,details) SELECT 'catalog-unit-aliases-v2',jsonb_build_object('old_units',jsonb_agg(to_jsonb(u))) FROM unit_of_measure u WHERE u.code IN ('BOM-PCS','BOM-KG');
    SELECT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='classifier_node'::regclass AND tgname='catalog_lock') INTO had_lock;
    IF had_lock THEN ALTER TABLE classifier_node DISABLE TRIGGER catalog_lock; END IF;
    FOR unit_alias IN SELECT * FROM unit_of_measure WHERE (code='BOM-PCS' AND name='штуки (BOM)') OR (code='BOM-KG' AND name='килограммы (BOM)') LOOP
        SELECT id INTO target_id FROM unit_of_measure WHERE code=CASE unit_alias.code WHEN 'BOM-PCS' THEN 'PCS' ELSE 'KG' END;
        UPDATE classifier_node SET unit_of_measure_id=target_id WHERE unit_of_measure_id=unit_alias.id;
        UPDATE numeric_parameter SET unit_of_measure_id=target_id WHERE unit_of_measure_id=unit_alias.id;
        DELETE FROM unit_of_measure WHERE id=unit_alias.id;
    END LOOP;
    IF had_lock THEN
        SET CONSTRAINTS catalog_validate IMMEDIATE;
        ALTER TABLE classifier_node ENABLE TRIGGER catalog_lock;
        SET CONSTRAINTS catalog_validate DEFERRED;
    END IF;
END
$units$;
^^^
COMMENT ON TABLE node_enumeration IS 'Назначение общего перечисления узлу; наследуется по parent_id. restrict_values включает сужение допустимых вариантов';
COMMENT ON TABLE node_enumeration_option IS 'Допустимые варианты модели/группы. Не являются выбранным цветом конкретного исполнения';
COMMENT ON COLUMN classifier_node.node_kind IS 'GROUP — группа; MODEL — модель с исполнениями; PRODUCT — конкретное изделие, материал или исполнение модели';
^^^

-- Исключения наследования хранятся в существующих назначениях.
ALTER TABLE node_enumeration ADD COLUMN IF NOT EXISTS excluded BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE node_numeric_parameter ADD COLUMN IF NOT EXISTS excluded BOOLEAN NOT NULL DEFAULT false;
^^^
