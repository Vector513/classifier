-- Только на отдельной тестовой БД после запуска приложения с CLASSIFIER_DEMO_ENABLED=true.
-- Изменения теста откатываются; ошибки прерывают psql.
\set ON_ERROR_STOP on
BEGIN;
DELETE FROM schema_migration WHERE name='bom-demo-bracket-kind-v2';
UPDATE bom_product SET kind='PART' WHERE node_id=(SELECT id FROM classifier_node WHERE code='BOM-BRACKET');
\ir ../../src/main/resources/bom-demo-updates.sql
DO $$
DECLARE top_id BIGINT; materials_id BIGINT; quantity NUMERIC;
BEGIN
    IF (SELECT kind FROM bom_product WHERE node_id=(SELECT id FROM classifier_node WHERE code='BOM-BRACKET')) IS DISTINCT FROM 'ASSEMBLY' THEN
        RAISE EXCEPTION 'Тип учебного кронштейна не исправлен';
    END IF;
    SELECT s.id INTO top_id FROM bom_specification s JOIN classifier_node n ON n.id=s.product_id WHERE n.code='BOM-HOUSING' AND s.revision=1;
    SELECT id INTO materials_id FROM classifier_node WHERE code='BOM-MATERIALS';
    SELECT total_quantity INTO quantity FROM bom_totals(top_id,3,materials_id);
    IF quantity IS DISTINCT FROM 3.75 THEN RAISE EXCEPTION 'Расход изменился: %', quantity; END IF;
END $$;
UPDATE bom_product SET kind='PURCHASED' WHERE node_id=(SELECT id FROM classifier_node WHERE code='BOM-BRACKET');
DELETE FROM bom_specification WHERE status='DRAFT' AND product_id=(SELECT id FROM classifier_node WHERE code='BOM-HOUSING');
\ir ../../src/main/resources/bom-demo.sql
\ir ../../src/main/resources/bom-demo-updates.sql
DO $$ BEGIN
    IF (SELECT kind FROM bom_product WHERE node_id=(SELECT id FROM classifier_node WHERE code='BOM-BRACKET')) IS DISTINCT FROM 'PURCHASED' THEN
        RAISE EXCEPTION 'Повторное исправление перезаписало пользовательский тип';
    END IF;
    IF EXISTS (SELECT 1 FROM bom_specification s JOIN classifier_node n ON n.id=s.product_id WHERE n.code='BOM-HOUSING' AND s.status='DRAFT') THEN
        RAISE EXCEPTION 'Удалённый черновик восстановлен';
    END IF;
END $$;
ROLLBACK;
