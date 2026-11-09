-- Товарные категории общие для каталога и BOM. При переносе сохраняются ID и коды изделий.
DO $layout$
DECLARE
    electronics_id BIGINT;
    fixture_time TIMESTAMPTZ;
    fixture_ids BIGINT[];
    fixture_enum_id BIGINT;
    entry RECORD;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS (SELECT 1 FROM schema_migration WHERE name='catalog-bom-layout-v1') THEN RETURN; END IF;
    SELECT id INTO electronics_id FROM classifier_node WHERE code='ELECTRONICS' AND node_kind='GROUP';
    IF electronics_id IS NULL THEN RETURN; END IF;

    FOR entry IN
        SELECT n.id, layout.name FROM (VALUES
            ('BOM-EL-KITS','Готовые комплекты','Спецификации'),
            ('BOM-EL-PC-PARTS','Комплектующие для ПК','Комплектующие для ПК'),
            ('BOM-EL-HOME-DEVICES','Устройства умного дома','Умный дом'),
            ('BOM-EL-GAME-DEVICES','Игровая периферия','Игровая периферия'),
            ('BOM-EL-SEC-DEVICES','Оборудование видеонаблюдения','Видеонаблюдение')
        ) AS layout(code,old_name,name)
        JOIN classifier_node n ON n.code=layout.code AND n.name=layout.old_name AND n.node_kind='GROUP'
        JOIN classifier_node parent ON parent.id=n.parent_id AND parent.code='BOM-EL-DEMO'
        WHERE parent.name='Комплекты электроники' AND parent.parent_id IS NULL
    LOOP
        BEGIN
            UPDATE classifier_node SET parent_id=electronics_id,name=entry.name,updated_at=now() WHERE id=entry.id;
            PERFORM catalog_assert_consistent();
        EXCEPTION WHEN check_violation THEN
            RAISE NOTICE 'Категория % осталась на прежнем месте из-за ограничений каталога', entry.name;
        END;
    END LOOP;

    INSERT INTO classifier_node(code,name,node_kind,parent_id,sort_order,created_at,updated_at)
    SELECT c.code,c.name,'GROUP',p.id,c.sort_order,now(),now()
    FROM (VALUES
        ('PC-PROCESSORS','Процессоры','BOM-EL-PC-PARTS',0,'PC-CPU'),
        ('PC-MOTHERBOARDS','Материнские платы','BOM-EL-PC-PARTS',1,'PC-BOARD'),
        ('PC-GRAPHICS','Видеокарты','BOM-EL-PC-PARTS',2,'PC-RTX4060'),
        ('PC-MEMORY','Оперативная память','BOM-EL-PC-PARTS',3,'PC-RAM'),
        ('PC-STORAGE','Накопители','BOM-EL-PC-PARTS',4,'PC-SSD'),
        ('PC-POWER','Блоки питания','BOM-EL-PC-PARTS',5,'PC-PSU'),
        ('PC-COOLERS','Охлаждение','BOM-EL-PC-PARTS',6,'PC-FAN'),
        ('ACC-SCREEN-PROTECTORS','Защитные стёкла','ACCESSORIES',4,'PHONE-GLASS'),
        ('ACC-POWERBANKS','Внешние аккумуляторы','ACCESSORIES',5,'PHONE-POWERBANK')
    ) AS c(code,name,parent_code,sort_order,example)
    JOIN classifier_node p ON p.code=c.parent_code AND p.node_kind='GROUP'
    WHERE EXISTS (SELECT 1 FROM classifier_node n WHERE n.code='BOM-EL-' || c.example)
    ON CONFLICT(code) DO NOTHING;

    FOR entry IN
        SELECT n.id,destination.id AS destination_id,n.code
        FROM (VALUES
            ('PC-CPU','PC-PROCESSORS'),('PC-BOARD','PC-MOTHERBOARDS'),
            ('PC-RTX4060','PC-GRAPHICS'),('PC-RTX4070','PC-GRAPHICS'),
            ('PC-RAM','PC-MEMORY'),('PC-SSD','PC-STORAGE'),('PC-PSU','PC-POWER'),
            ('PC-COOLING','BOM-EL-KITS'),('PC-FAN','PC-COOLERS'),('PC-FAN-CONTROL','PC-COOLERS'),
            ('PHONE-GLASS','ACC-SCREEN-PROTECTORS'),('PHONE-CASE','ACC-CASES'),
            ('PHONE-CHARGER','ACC-CHARGERS'),('PHONE-EARBUDS','AUDIO-HEADPHONES'),
            ('PHONE-POWERBANK','ACC-POWERBANKS')
        ) AS layout(code,destination)
        JOIN classifier_node n ON n.code='BOM-EL-' || layout.code AND n.node_kind='PRODUCT'
        JOIN classifier_node parent ON parent.id=n.parent_id
        JOIN classifier_node destination ON destination.code=layout.destination AND destination.node_kind='GROUP'
        WHERE (layout.code LIKE 'PC-%' AND parent.code='BOM-EL-PC-PARTS' AND parent.parent_id=electronics_id
               AND (destination.parent_id=parent.id OR destination.code='BOM-EL-KITS' AND destination.parent_id=electronics_id))
           OR (layout.code LIKE 'PHONE-%' AND parent.code='BOM-EL-PHONE-ACCESSORIES'
               AND EXISTS (SELECT 1 FROM classifier_node root WHERE root.id=parent.parent_id AND root.code='BOM-EL-DEMO'))
    LOOP
        BEGIN
            UPDATE classifier_node SET parent_id=entry.destination_id,updated_at=now() WHERE id=entry.id;
            PERFORM catalog_assert_consistent();
        EXCEPTION WHEN check_violation THEN
            RAISE NOTICE 'Товар % остался в прежней группе из-за ограничений каталога', entry.code;
        END;
    END LOOP;

    -- Удаляем только пустые служебные группы без собственных настроек.
    FOR entry IN
        SELECT n.id FROM classifier_node n
        WHERE (n.code,n.name) IN (
            ('BOM-EL-PHONE-ACCESSORIES','Аксессуары для смартфонов'),
            ('BOM-EL-DEMO','Комплекты электроники'),
            ('DEMO-EMPTY-GROUP','Пустая группа — ещё без изделий')
        ) AND n.node_kind='GROUP' AND n.unit_of_measure_id IS NULL
          AND n.updated_at=n.created_at
        ORDER BY CASE WHEN n.code='BOM-EL-PHONE-ACCESSORIES' THEN 0 ELSE 1 END
    LOOP
        DELETE FROM classifier_node n WHERE n.id=entry.id
          AND NOT EXISTS (SELECT 1 FROM classifier_node child WHERE child.parent_id=n.id)
          AND NOT EXISTS (SELECT 1 FROM node_numeric_parameter p WHERE p.classifier_node_id=n.id)
          AND NOT EXISTS (SELECT 1 FROM node_enumeration e WHERE e.classifier_node_id=n.id);
    END LOOP;

    -- Старый учебный стенд о заготовках. Сохраняем его при любых правках или новых связях.
    SELECT applied_at INTO fixture_time FROM schema_migration WHERE name='catalog-parameters-demo-v3';
    SELECT array_agg(n.id) INTO fixture_ids
    FROM (VALUES
        ('DEMO-PARAMETERS','Учебный пример: параметры и наследование',NULL),
        ('DEMO-BLANKS','Заготовки: длина и обработка наследуются','DEMO-PARAMETERS'),
        ('DEMO-KITS','Покупные наборы: свой состав параметров','DEMO-PARAMETERS'),
        ('DEMO-BLANK-MATTE','Матовая заготовка 0,5 м','DEMO-BLANKS'),
        ('DEMO-BLANK-POLISHED','Полированная заготовка 1,2 м','DEMO-BLANKS'),
        ('DEMO-KIT-SIX','Набор крепежа: 6 предметов','DEMO-KITS')
    ) AS fixture(code,name,parent_code)
    JOIN classifier_node n ON n.code=fixture.code AND n.name=fixture.name
    LEFT JOIN classifier_node parent ON parent.id=n.parent_id
    WHERE n.created_at=fixture_time AND n.updated_at=fixture_time AND n.sort_order=0
      AND parent.code IS NOT DISTINCT FROM fixture.parent_code
      AND ((n.code LIKE 'DEMO-BLANK-%' OR n.code='DEMO-KIT-SIX') AND n.node_kind='PRODUCT'
           AND n.unit_of_measure_id=(SELECT id FROM unit_of_measure WHERE code='PCS')
           OR n.code IN ('DEMO-PARAMETERS','DEMO-KITS','DEMO-BLANKS') AND n.node_kind='GROUP' AND n.unit_of_measure_id IS NULL);

    IF cardinality(fixture_ids)=6
       AND NOT EXISTS (SELECT 1 FROM classifier_node WHERE parent_id=ANY(fixture_ids) AND id<>ALL(fixture_ids))
       AND NOT EXISTS (SELECT 1 FROM bom_product WHERE node_id=ANY(fixture_ids))
       AND (SELECT jsonb_agg(jsonb_build_array(n.code,p.code,v.value) ORDER BY n.code,p.code)
            FROM node_numeric_value v JOIN classifier_node n ON n.id=v.classifier_node_id
            JOIN numeric_parameter p ON p.id=v.numeric_parameter_id WHERE n.id=ANY(fixture_ids))
           = '[["DEMO-BLANK-MATTE","DEMO-LENGTH",0.5],["DEMO-BLANK-POLISHED","DEMO-LENGTH",1.2],["DEMO-KIT-SIX","DEMO-KIT-COUNT",6]]'
       AND (SELECT jsonb_agg(jsonb_build_array(n.code,e.code,v.code) ORDER BY n.code)
            FROM node_attribute_value a JOIN classifier_node n ON n.id=a.classifier_node_id
            JOIN enumeration e ON e.id=a.enumeration_id JOIN enumeration_value v ON v.id=a.enumeration_value_id
            WHERE n.id=ANY(fixture_ids))
           = '[["DEMO-BLANK-MATTE","DEMO-SURFACE","MATTE"],["DEMO-BLANK-POLISHED","DEMO-SURFACE","POLISHED"]]'
       AND (SELECT jsonb_agg(jsonb_build_array(n.code,p.code,a.excluded) ORDER BY n.code,p.code)
            FROM node_numeric_parameter a JOIN classifier_node n ON n.id=a.classifier_node_id
            JOIN numeric_parameter p ON p.id=a.numeric_parameter_id WHERE n.id=ANY(fixture_ids))
           = '[["DEMO-KITS","DEMO-KIT-COUNT",false],["DEMO-KITS","DEMO-LENGTH",true],["DEMO-PARAMETERS","DEMO-LENGTH",false]]'
       AND (SELECT jsonb_agg(jsonb_build_array(n.code,e.code,a.excluded,a.restrict_values) ORDER BY n.code)
            FROM node_enumeration a JOIN classifier_node n ON n.id=a.classifier_node_id
            JOIN enumeration e ON e.id=a.enumeration_id WHERE n.id=ANY(fixture_ids))
           = '[["DEMO-KITS","DEMO-SURFACE",true,false],["DEMO-PARAMETERS","DEMO-SURFACE",false,false]]'
    THEN
        BEGIN
            DELETE FROM classifier_node WHERE id=ANY(fixture_ids) AND node_kind='PRODUCT';
            DELETE FROM classifier_node WHERE id=ANY(fixture_ids) AND code IN ('DEMO-KITS','DEMO-BLANKS');
            DELETE FROM classifier_node WHERE id=ANY(fixture_ids) AND code='DEMO-PARAMETERS';
        EXCEPTION WHEN foreign_key_violation THEN
            RAISE NOTICE 'Учебный стенд сохранён: на него ссылаются другие данные';
        END;
        DELETE FROM numeric_parameter p
        WHERE (p.code,p.name) IN (('DEMO-LENGTH','Длина заготовки'),('DEMO-KIT-COUNT','Число предметов в наборе'))
          AND p.created_at=fixture_time AND p.updated_at=fixture_time
          AND NOT EXISTS (SELECT 1 FROM node_numeric_parameter a WHERE a.numeric_parameter_id=p.id)
          AND NOT EXISTS (SELECT 1 FROM node_numeric_value v WHERE v.numeric_parameter_id=p.id);

        SELECT e.id INTO fixture_enum_id FROM enumeration e
        WHERE e.code='DEMO-SURFACE' AND e.name='Вид обработки поверхности'
          AND e.created_at=fixture_time AND e.updated_at=fixture_time
          AND NOT EXISTS (SELECT 1 FROM node_enumeration a WHERE a.enumeration_id=e.id)
          AND (SELECT jsonb_agg(jsonb_build_array(v.code,v.name,v.sort_order) ORDER BY v.sort_order)
               FROM enumeration_value v WHERE v.enumeration_id=e.id AND v.created_at=fixture_time AND v.updated_at=fixture_time)
              = '[["MATTE","Матовая",0],["POLISHED","Полированная",1]]'
          AND (SELECT count(*) FROM enumeration_value v WHERE v.enumeration_id=e.id)=2;
        IF fixture_enum_id IS NOT NULL THEN
            BEGIN
                DELETE FROM enumeration_value WHERE enumeration_id=fixture_enum_id;
                DELETE FROM enumeration WHERE id=fixture_enum_id;
                DELETE FROM enumeration_class c WHERE c.code='DEMO-FINISH' AND c.name='Обработка поверхности'
                  AND c.created_at=fixture_time AND c.updated_at=fixture_time
                  AND NOT EXISTS (SELECT 1 FROM enumeration e WHERE e.enumeration_class_id=c.id);
            EXCEPTION WHEN foreign_key_violation THEN
                RAISE NOTICE 'Учебное перечисление сохранено: на него ссылаются другие данные';
            END;
        END IF;
    END IF;

    INSERT INTO schema_migration(name) VALUES ('catalog-bom-layout-v1');
END
$layout$;
