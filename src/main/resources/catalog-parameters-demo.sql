-- Самостоятельный пример наследования и изменения состава параметров.
-- Выполняется один раз; пользовательские изменения при перезапуске сохраняются.
DO $demo$
DECLARE root_id BIGINT; blanks_id BIGINT; kits_id BIGINT; p_length BIGINT; p_count BIGINT;
    enum_class BIGINT; surface_id BIGINT; matte BIGINT; polished BIGINT; first_id BIGINT; second_id BIGINT; kit_id BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS(SELECT 1 FROM schema_migration WHERE name='catalog-parameters-demo-v3') THEN RETURN; END IF;
    INSERT INTO unit_of_measure(code,name) VALUES('M','метры'),('PCS','штуки') ON CONFLICT(code) DO NOTHING;
    INSERT INTO classifier_node(code,name,node_kind,sort_order,created_at,updated_at) VALUES('DEMO-PARAMETERS','Учебный пример: параметры и наследование','GROUP',0,now(),now()) ON CONFLICT(code) DO NOTHING;
    SELECT id INTO root_id FROM classifier_node WHERE code='DEMO-PARAMETERS';
    INSERT INTO classifier_node(code,name,node_kind,parent_id,sort_order,created_at,updated_at) VALUES
        ('DEMO-BLANKS','Заготовки: длина и обработка наследуются','GROUP',root_id,0,now(),now()),
        ('DEMO-KITS','Покупные наборы: свой состав параметров','GROUP',root_id,0,now(),now()) ON CONFLICT(code) DO NOTHING;
    SELECT id INTO blanks_id FROM classifier_node WHERE code='DEMO-BLANKS';
    SELECT id INTO kits_id FROM classifier_node WHERE code='DEMO-KITS';
    INSERT INTO numeric_parameter(code,name,description,min_value,max_value,unit_of_measure_id,created_at,updated_at) VALUES
        ('DEMO-LENGTH','Длина заготовки','Учебный параметр с допустимым диапазоном от 0,01 до 10 м',0.01,10,(SELECT id FROM unit_of_measure WHERE code='M'),now(),now()),
        ('DEMO-KIT-COUNT','Число предметов в наборе','Характеристика одного набора, а не складской остаток',1,100,(SELECT id FROM unit_of_measure WHERE code='PCS'),now(),now()) ON CONFLICT(code) DO NOTHING;
    SELECT id INTO p_length FROM numeric_parameter WHERE code='DEMO-LENGTH';
    SELECT id INTO p_count FROM numeric_parameter WHERE code='DEMO-KIT-COUNT';
    INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,excluded,created_at) VALUES
        (root_id,p_length,false,now()),(kits_id,p_length,true,now()),(kits_id,p_count,false,now()) ON CONFLICT DO NOTHING;
    INSERT INTO enumeration_class(code,name,created_at,updated_at) VALUES('DEMO-FINISH','Обработка поверхности',now(),now()) ON CONFLICT(code) DO NOTHING;
    SELECT id INTO enum_class FROM enumeration_class WHERE code='DEMO-FINISH';
    INSERT INTO enumeration(code,name,enumeration_class_id,created_at,updated_at) VALUES('DEMO-SURFACE','Вид обработки поверхности',enum_class,now(),now()) ON CONFLICT(code) DO NOTHING;
    SELECT id INTO surface_id FROM enumeration WHERE code='DEMO-SURFACE';
    INSERT INTO enumeration_value(code,name,enumeration_id,sort_order,created_at,updated_at) VALUES
        ('MATTE','Матовая',surface_id,0,now(),now()),('POLISHED','Полированная',surface_id,1,now(),now()) ON CONFLICT DO NOTHING;
    SELECT id INTO matte FROM enumeration_value WHERE enumeration_id=surface_id AND code='MATTE';
    SELECT id INTO polished FROM enumeration_value WHERE enumeration_id=surface_id AND code='POLISHED';
    INSERT INTO node_enumeration(classifier_node_id,enumeration_id,excluded) VALUES(root_id,surface_id,false),(kits_id,surface_id,true) ON CONFLICT DO NOTHING;
    INSERT INTO classifier_node(code,name,node_kind,parent_id,unit_of_measure_id,sort_order,created_at,updated_at) VALUES
        ('DEMO-BLANK-MATTE','Матовая заготовка 0,5 м','PRODUCT',blanks_id,(SELECT id FROM unit_of_measure WHERE code='PCS'),0,now(),now()),
        ('DEMO-BLANK-POLISHED','Полированная заготовка 1,2 м','PRODUCT',blanks_id,(SELECT id FROM unit_of_measure WHERE code='PCS'),0,now(),now()),
        ('DEMO-KIT-SIX','Набор крепежа: 6 предметов','PRODUCT',kits_id,(SELECT id FROM unit_of_measure WHERE code='PCS'),0,now(),now()) ON CONFLICT(code) DO NOTHING;
    SELECT id INTO first_id FROM classifier_node WHERE code='DEMO-BLANK-MATTE';
    SELECT id INTO second_id FROM classifier_node WHERE code='DEMO-BLANK-POLISHED';
    SELECT id INTO kit_id FROM classifier_node WHERE code='DEMO-KIT-SIX';
    INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at) VALUES
        (first_id,p_length,0.5,now(),now()),(second_id,p_length,1.2,now(),now()),(kit_id,p_count,6,now(),now()) ON CONFLICT DO NOTHING;
    INSERT INTO node_attribute_value(classifier_node_id,enumeration_id,enumeration_value_id,created_at,updated_at) VALUES
        (first_id,surface_id,matte,now(),now()),(second_id,surface_id,polished,now(),now()) ON CONFLICT DO NOTHING;
    PERFORM catalog_assert_consistent();
    INSERT INTO schema_migration(name) VALUES('catalog-parameters-demo-v3');
END
$demo$;
