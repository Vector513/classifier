-- Начальные примеры BOM. Один DO-блок: все данные и отметка загрузки атомарны.
-- Повторный запуск не восстанавливает удалённые примеры и не меняет редактированные строки.
DO $seed$
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    CREATE TABLE IF NOT EXISTS bom_seed_history (
        name VARCHAR(100) PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    IF EXISTS(SELECT 1 FROM bom_seed_history WHERE name='bom-demo-v1') THEN RETURN; END IF;
    -- Уже загруженный вручную пример принимаем как есть, включая пользовательские правки.
    IF EXISTS(SELECT 1 FROM classifier_node WHERE code='BOM-DEMO') THEN
        INSERT INTO bom_seed_history(name) VALUES('bom-demo-v1');
        RETURN;
    END IF;
DECLARE root_id BIGINT; materials_id BIGINT; assemblies_id BIGINT;
    unit_pcs BIGINT; unit_kg BIGINT; steel_id BIGINT; bolt_id BIGINT;
    top_id BIGINT; sub_id BIGINT; variant_id BIGINT;
    sub_spec BIGINT; top_spec BIGINT; variant_spec BIGINT;
BEGIN
    IF EXISTS(SELECT 1 FROM classifier_node WHERE code='BOM-DEMO') THEN RETURN; END IF;
    INSERT INTO unit_of_measure(code,name) VALUES('BOM-PCS','штуки (BOM)') RETURNING id INTO unit_pcs;
    INSERT INTO unit_of_measure(code,name) VALUES('BOM-KG','килограммы (BOM)') RETURNING id INTO unit_kg;
    INSERT INTO classifier_node(code,name,sort_order,created_at,updated_at)
        VALUES('BOM-DEMO','Материальные спецификации',0,now(),now()) RETURNING id INTO root_id;
    INSERT INTO classifier_node(code,name,parent_id,sort_order,created_at,updated_at)
        VALUES('BOM-MATERIALS','Материалы',root_id,0,now(),now()) RETURNING id INTO materials_id;
    INSERT INTO classifier_node(code,name,parent_id,sort_order,created_at,updated_at)
        VALUES('BOM-ASSEMBLIES','Сборочные единицы',root_id,1,now(),now()) RETURNING id INTO assemblies_id;
    INSERT INTO classifier_node(code,name,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
        VALUES('BOM-STEEL','Листовая сталь',materials_id,unit_kg,0,now(),now()) RETURNING id INTO steel_id;
    INSERT INTO classifier_node(code,name,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
        VALUES('BOM-BOLT','Болт М6',root_id,unit_pcs,2,now(),now()) RETURNING id INTO bolt_id;
    INSERT INTO classifier_node(code,name,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
        VALUES('BOM-HOUSING','Корпус',assemblies_id,unit_pcs,0,now(),now()) RETURNING id INTO top_id;
    INSERT INTO classifier_node(code,name,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
        VALUES('BOM-BRACKET','Кронштейн',assemblies_id,unit_pcs,1,now(),now()) RETURNING id INTO sub_id;
    INSERT INTO classifier_node(code,name,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
        VALUES('BOM-HOUSING-M','Корпус усиленный',assemblies_id,unit_pcs,2,now(),now()) RETURNING id INTO variant_id;
    INSERT INTO bom_product(node_id,kind,unit_id) VALUES
        (steel_id,'MATERIAL',unit_kg),(bolt_id,'PURCHASED',unit_pcs),
        (top_id,'ASSEMBLY',unit_pcs),(sub_id,'PART',unit_pcs),(variant_id,'ASSEMBLY',unit_pcs);
    INSERT INTO bom_specification(product_id,revision,description)
        VALUES(sub_id,1,'Кронштейн: 0.5 кг стали и 3 болта') RETURNING id INTO sub_spec;
    INSERT INTO bom_line(specification_id,position,component_id,quantity) VALUES
        (sub_spec,1,steel_id,0.5),(sub_spec,2,bolt_id,3);
    UPDATE bom_specification SET status='RELEASED' WHERE id=sub_spec;
    INSERT INTO bom_specification(product_id,revision,description)
        VALUES(top_id,1,'Корпус: два кронштейна, 0.25 кг стали, один болт') RETURNING id INTO top_spec;
    INSERT INTO bom_line(specification_id,position,component_id,component_specification_id,quantity)
        VALUES(top_spec,1,sub_id,sub_spec,2);
    INSERT INTO bom_line(specification_id,position,component_id,quantity) VALUES
        (top_spec,2,steel_id,0.25),(top_spec,3,bolt_id,1);
    UPDATE bom_specification SET status='RELEASED' WHERE id=top_spec;
    INSERT INTO bom_specification(product_id,revision,base_id,description)
        VALUES(variant_id,1,top_spec,'Модификация: 1 кг стали вместо 0.25, исключен отдельный болт') RETURNING id INTO variant_spec;
    INSERT INTO bom_line(specification_id,position,component_id,quantity) VALUES(variant_spec,2,steel_id,1);
    INSERT INTO bom_line(specification_id,position,excluded) VALUES(variant_spec,3,true);
    UPDATE bom_specification SET status='RELEASED' WHERE id=variant_spec;
END;
DECLARE materials_id BIGINT; unit_kg BIGINT; paint_id BIGINT;
    top_id BIGINT; steel_id BIGINT; top_spec BIGINT; draft_spec BIGINT;
BEGIN
    SELECT id INTO materials_id FROM classifier_node WHERE code='BOM-MATERIALS';
    SELECT node_id,unit_id INTO steel_id,unit_kg FROM bom_product
        WHERE node_id=(SELECT id FROM classifier_node WHERE code='BOM-STEEL');
    SELECT id INTO top_id FROM classifier_node WHERE code='BOM-HOUSING';
    SELECT id INTO top_spec FROM bom_specification WHERE product_id=top_id AND revision=1 AND status='RELEASED';
    IF top_spec IS NULL OR materials_id IS NULL OR unit_kg IS NULL THEN
        RAISE EXCEPTION 'Базовый пример BOM неполон: учебный черновик не создан';
    END IF;
    SELECT id INTO paint_id FROM classifier_node WHERE code='BOM-PAINT';
    IF paint_id IS NULL THEN
        INSERT INTO classifier_node(code,name,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
            VALUES('BOM-PAINT','Краска для корпуса',materials_id,unit_kg,1,now(),now()) RETURNING id INTO paint_id;
        INSERT INTO bom_product(node_id,kind,unit_id) VALUES(paint_id,'MATERIAL',unit_kg);
    END IF;
    IF NOT EXISTS(SELECT 1 FROM bom_specification WHERE product_id=top_id AND revision=2) THEN
        INSERT INTO bom_specification(product_id,revision,base_id,description)
            VALUES(top_id,2,top_spec,'Учебный черновик: сталь 0.75 кг, болт исключён, добавлено 0.1 кг краски. Можно изменять позиции и отменять локальные изменения.')
            RETURNING id INTO draft_spec;
        INSERT INTO bom_line(specification_id,position,component_id,quantity) VALUES
            (draft_spec,2,steel_id,0.75),(draft_spec,4,paint_id,0.1);
        INSERT INTO bom_line(specification_id,position,excluded) VALUES(draft_spec,3,true);
    END IF;
END;
    INSERT INTO bom_seed_history(name) VALUES('bom-demo-v1');
END
$seed$;
