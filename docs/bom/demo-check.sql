-- Проверка на отдельной PostgreSQL после первого запуска с демоданными.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP VIEW demo_specs AS
SELECT s.*, replace(n.code,'BOM-EL-','') AS code
FROM bom_specification s JOIN classifier_node n ON n.id=s.product_id
WHERE n.code LIKE 'BOM-EL-%';

DO $check$
DECLARE
    kit RECORD;
    spec_id BIGINT;
    base_spec_id BIGINT;
    catalog_phone BIGINT;
    actual TEXT[];
BEGIN
    IF (SELECT count(*) FROM demo_specs WHERE status='RELEASED' AND revision=1) <> 11 THEN
        RAISE EXCEPTION 'Ожидается 11 утверждённых составов первой версии';
    END IF;
    FOR kit IN
        SELECT * FROM (VALUES
            ('PC-COOLING',NULL,ARRAY['PC-FAN:2','PC-FAN-CONTROL:1'],ARRAY[1,2]),
            ('PC-STD',NULL,ARRAY['PC-CPU:1','PC-BOARD:1','PC-RTX4060:1','PC-RAM:2','PC-SSD:1','PC-PSU:1','PC-COOLING:1'],ARRAY[1,2,3,4,5,6,7]),
            ('PC-PRO','PC-STD',ARRAY['PC-CPU:1','PC-BOARD:1','PC-RTX4070:1','PC-RAM:4','PC-SSD:1','PC-PSU:1','PC-COOLING:1'],ARRAY[3,4]),
            ('HOME-START',NULL,ARRAY['HOME-SPEAKER:1','HOME-BULB:3','HOME-PLUG:2','HOME-MOTION:1','HOME-DOOR:1'],ARRAY[1,2,3,4,5]),
            ('HOME-PRO','HOME-START',ARRAY['HOME-SPEAKER:1','HOME-BULB:5','HOME-PLUG:4','HOME-MOTION:3','HOME-DOOR:1','HOME-CAMERA:1'],ARRAY[2,3,4,6]),
            ('GAME-STD',NULL,ARRAY['GAME-KEYBOARD:1','GAME-MOUSE:1','GAME-HEADPHONES:1','GAME-MAT:1'],ARRAY[1,2,3,4]),
            ('GAME-PREMIUM','GAME-STD',ARRAY['GAME-KEYBOARD:1','GAME-WIRELESS-MOUSE:1','GAME-WIRELESS-HEADSET:1','GAME-MAT:1','GAME-CONTROLLER:1'],ARRAY[2,3,5]),
            ('SEC-STD',NULL,ARRAY['SEC-CAMERA:2','SEC-NVR:1','SEC-HDD1:1','SEC-PSU:1','SEC-CABLE:20'],ARRAY[1,2,3,4,5]),
            ('SEC-PRO','SEC-STD',ARRAY['SEC-CAMERA:4','SEC-NVR:1','SEC-HDD2:1','SEC-PSU:1','SEC-CABLE:40'],ARRAY[1,3,5]),
            ('PHONE-STD',NULL,ARRAY['PHONES-SAMSUNG-S24:1','PHONE-GLASS:1','PHONE-CASE:1','PHONE-CHARGER:1'],ARRAY[1,2,3,4]),
            ('PHONE-PREMIUM','PHONE-STD',ARRAY['PHONES-SAMSUNG-S24:1','PHONE-GLASS:1','PHONE-CASE:1','PHONE-CHARGER:1','PHONE-EARBUDS:1','PHONE-POWERBANK:1'],ARRAY[5,6])
        ) AS v(code,base_code,contents,local_positions)
    LOOP
        SELECT id INTO STRICT spec_id FROM demo_specs WHERE code=kit.code AND revision=1;
        SELECT id INTO base_spec_id FROM demo_specs WHERE code=kit.base_code AND revision=1;
        IF (SELECT base_id FROM demo_specs WHERE id=spec_id) IS DISTINCT FROM base_spec_id THEN
            RAISE EXCEPTION 'Неверная база для %', kit.code;
        END IF;
        IF (SELECT array_agg(position ORDER BY position) FROM bom_line WHERE specification_id=spec_id)
            IS DISTINCT FROM kit.local_positions THEN
            RAISE EXCEPTION 'Неверные локальные позиции для %', kit.code;
        END IF;
        SELECT array_agg(replace(n.code,'BOM-EL-','') || ':' || trim_scale(e.quantity) ORDER BY e.position)
        INTO actual FROM bom_effective(spec_id) e JOIN classifier_node n ON n.id=e.component_id;
        IF actual IS DISTINCT FROM kit.contents THEN
            RAISE EXCEPTION 'Неверный состав для %: %', kit.code,actual;
        END IF;
        IF EXISTS (
            SELECT 1 FROM bom_explode(spec_id,10) e
            JOIN classifier_node n ON n.id=e.component_id
            JOIN unit_of_measure u ON u.id=n.unit_of_measure_id
            WHERE e.cycle OR u.code <> CASE WHEN n.code='BOM-EL-SEC-CABLE' THEN 'M' ELSE 'PCS' END
                OR (e.depth=1 AND e.total_quantity<>10*e.quantity)
        ) THEN
            RAISE EXCEPTION 'Неверный расчёт или единица для %', kit.code;
        END IF;
    END LOOP;

    SELECT id INTO STRICT catalog_phone FROM classifier_node WHERE code='PHONES-SAMSUNG-S24';
    IF NOT EXISTS (
        SELECT 1 FROM demo_specs s CROSS JOIN LATERAL bom_effective(s.id) e
        WHERE s.code='PHONE-STD' AND e.component_id=catalog_phone AND e.component_specification_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Не использован смартфон из каталога';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM demo_specs s CROSS JOIN LATERAL bom_explode(s.id,3) e
        JOIN classifier_node n ON n.id=e.component_id
        WHERE s.code='PC-PRO' AND e.path=ARRAY[7,1] AND n.code='BOM-EL-PC-FAN' AND e.total_quantity=6
    ) THEN
        RAISE EXCEPTION 'Не раскрывается вложенное охлаждение';
    END IF;
    IF (SELECT array_agg(t.total_quantity) FROM demo_specs s
        CROSS JOIN LATERAL bom_totals(s.id,10,(SELECT id FROM classifier_node WHERE code='BOM-EL-GAME-DEVICES')) t
        WHERE s.code='GAME-STD' AND s.revision=1) IS DISTINCT FROM ARRAY[10,10,10,10]::NUMERIC[] THEN
        RAISE EXCEPTION 'На 10 игровых наборов должно быть по 10 компонентов каждого вида';
    END IF;
    SELECT id INTO STRICT spec_id FROM demo_specs WHERE code='GAME-STD' AND revision=2 AND status='DRAFT';
    IF (SELECT array_agg(e.position ORDER BY e.position) FROM bom_effective(spec_id) e)
        IS DISTINCT FROM ARRAY[1,2,3,5] THEN
        RAISE EXCEPTION 'Исключение коврика в черновике не сработало';
    END IF;
END
$check$;

DO $guards$
DECLARE
    cooling_spec BIGINT;
    pc_spec BIGINT;
    cyclic_draft BIGINT;
BEGIN
    SELECT id INTO STRICT cooling_spec FROM demo_specs WHERE code='PC-COOLING' AND revision=1;
    SELECT id INTO STRICT pc_spec FROM demo_specs WHERE code='PC-STD' AND revision=1;
    BEGIN
        UPDATE bom_line SET quantity=5 WHERE specification_id=pc_spec AND position=4;
        RAISE EXCEPTION 'Утверждённая строка доступна для изменения';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    INSERT INTO bom_specification(product_id,revision,base_id,description)
    SELECT product_id,2,id,'Проверка защиты от цикла' FROM demo_specs WHERE id=cooling_spec
    RETURNING id INTO cyclic_draft;
    INSERT INTO bom_line(specification_id,position,component_id,component_specification_id,quantity)
    SELECT cyclic_draft,3,product_id,id,1 FROM demo_specs WHERE id=pc_spec;
    IF NOT EXISTS (SELECT 1 FROM bom_explode(cyclic_draft,1) WHERE cycle) THEN
        RAISE EXCEPTION 'Не найден цикл охлаждение → ПК → охлаждение';
    END IF;
    BEGIN
        UPDATE bom_specification SET status='RELEASED' WHERE id=cyclic_draft;
        RAISE EXCEPTION 'Циклический состав утверждён';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
    DELETE FROM bom_specification WHERE id=cyclic_draft;
END
$guards$;

CREATE TEMP TABLE demo_snapshot AS SELECT * FROM demo_specs WHERE status='RELEASED';
UPDATE classifier_node SET name='Мой игровой набор' WHERE code='BOM-EL-GAME-STD';
DELETE FROM bom_specification WHERE id IN (SELECT id FROM demo_specs WHERE status='DRAFT');
\ir ../../src/main/resources/bom-demo.sql

DO $restart$
BEGIN
    IF EXISTS (SELECT * FROM demo_specs EXCEPT SELECT * FROM demo_snapshot)
        OR EXISTS (SELECT * FROM demo_snapshot EXCEPT SELECT * FROM demo_specs) THEN
        RAISE EXCEPTION 'Повторная загрузка изменила историю или восстановила черновик';
    END IF;
    IF (SELECT name FROM classifier_node WHERE code='BOM-EL-GAME-STD') IS DISTINCT FROM 'Мой игровой набор' THEN
        RAISE EXCEPTION 'Повторная загрузка затёрла пользовательское название';
    END IF;
END
$restart$;
ROLLBACK;
\echo PASS: five kit pairs, inheritance, units, calculations, nesting and repeat seeding
