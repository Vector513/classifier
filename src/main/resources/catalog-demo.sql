-- Согласованный пример модели и исполнений + заполненный шаблон хозяйственной операции.
DO $demo$
DECLARE model_id BIGINT; group_id BIGINT; enum_id BIGINT; storage_id BIGINT;
    os_id BIGINT; os_value BIGINT; brand_id BIGINT; policy RECORD;
    pcs BIGINT; kg BIGINT; black BIGINT; white BIGINT; blue BIGINT; gb128 BIGINT; gb256 BIGINT;
    first_id BIGINT; second_id BIGINT; variant_id BIGINT; param_id BIGINT; unit_id BIGINT;
    ho_group BIGINT; template_id BIGINT; op_id BIGINT; role_id BIGINT; ho_param BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS(SELECT 1 FROM schema_migration WHERE name='catalog-demo-v2') THEN RETURN; END IF;
    SELECT id INTO pcs FROM unit_of_measure WHERE code='PCS';
    SELECT id INTO kg FROM unit_of_measure WHERE code='KG';
    SELECT id INTO group_id FROM classifier_node WHERE code='PHONES';
    SELECT id INTO model_id FROM classifier_node WHERE code='PHONES-APPLE-IP16';
    -- Одна характеристика ОС используется разными семействами, каждое задаёт допустимый вариант.
    SELECT id INTO os_id FROM enumeration WHERE code='PHONE-OS';
    FOR policy IN SELECT * FROM (VALUES ('PHONES-APPLE','IOS'),('PHONES-SAMSUNG','ANDROID'),('PHONES-XIAOMI','ANDROID')) AS p(brand_code,os_code) LOOP
        SELECT id INTO brand_id FROM classifier_node WHERE code=policy.brand_code;
        SELECT id INTO os_value FROM enumeration_value WHERE enumeration_id=os_id AND code=policy.os_code;
        IF brand_id IS NOT NULL AND os_value IS NOT NULL AND NOT EXISTS(SELECT 1 FROM node_enumeration WHERE classifier_node_id=brand_id AND enumeration_id=os_id)
           AND NOT EXISTS(SELECT 1 FROM node_attribute_value v WHERE v.enumeration_id=os_id AND v.enumeration_value_id<>os_value
               AND EXISTS(SELECT 1 FROM catalog_ancestors(v.classifier_node_id) a WHERE a.id=brand_id)) THEN
            INSERT INTO node_enumeration(classifier_node_id,enumeration_id,restrict_values) VALUES(brand_id,os_id,true);
            INSERT INTO node_enumeration_option(classifier_node_id,enumeration_id,enumeration_value_id) VALUES(brand_id,os_id,os_value);
        END IF;
    END LOOP;


    -- Изменённые пользователем типы существующих записей автоматически не меняем.
    IF (SELECT node_kind FROM classifier_node WHERE id=model_id)='MODEL' THEN
        SELECT id INTO enum_id FROM enumeration WHERE code='PHONE-COLORS';
        SELECT id INTO storage_id FROM enumeration WHERE code='PHONE-STORAGE';
        SELECT id INTO black FROM enumeration_value WHERE enumeration_id=enum_id AND code='BLACK';
        SELECT id INTO white FROM enumeration_value WHERE enumeration_id=enum_id AND code='WHITE';
        SELECT id INTO blue FROM enumeration_value WHERE enumeration_id=enum_id AND code='BLUE';
        SELECT id INTO gb128 FROM enumeration_value WHERE enumeration_id=storage_id AND code='GB128';
        SELECT id INTO gb256 FROM enumeration_value WHERE enumeration_id=storage_id AND code='GB256';
        IF NOT EXISTS(SELECT 1 FROM node_enumeration WHERE classifier_node_id=model_id AND enumeration_id=enum_id) THEN
            INSERT INTO node_enumeration(classifier_node_id,enumeration_id,restrict_values) VALUES(model_id,enum_id,true);
            INSERT INTO node_enumeration_option(classifier_node_id,enumeration_id,enumeration_value_id)
                SELECT model_id,enum_id,v FROM unnest(ARRAY[black,white,blue]) v WHERE v IS NOT NULL ON CONFLICT DO NOTHING;
            -- Сохранённые ранее свойства модели остаются разрешёнными после добавления учебного примера.
            INSERT INTO node_enumeration_option(classifier_node_id,enumeration_id,enumeration_value_id)
                SELECT model_id,enum_id,v.enumeration_value_id FROM node_attribute_value v
                WHERE v.enumeration_id=enum_id AND EXISTS(SELECT 1 FROM catalog_ancestors(v.classifier_node_id) a WHERE a.id=model_id) ON CONFLICT DO NOTHING;
        END IF;
        IF NOT EXISTS(SELECT 1 FROM node_enumeration WHERE classifier_node_id=model_id AND enumeration_id=storage_id) THEN
            INSERT INTO node_enumeration(classifier_node_id,enumeration_id,restrict_values) VALUES(model_id,storage_id,true);
            INSERT INTO node_enumeration_option(classifier_node_id,enumeration_id,enumeration_value_id)
                SELECT model_id,storage_id,v FROM unnest(ARRAY[gb128,gb256]) v WHERE v IS NOT NULL ON CONFLICT DO NOTHING;
            INSERT INTO node_enumeration_option(classifier_node_id,enumeration_id,enumeration_value_id)
                SELECT model_id,storage_id,v.enumeration_value_id FROM node_attribute_value v
                WHERE v.enumeration_id=storage_id AND EXISTS(SELECT 1 FROM catalog_ancestors(v.classifier_node_id) a WHERE a.id=model_id) ON CONFLICT DO NOTHING;
        END IF;
        IF catalog_enum_allowed(model_id,enum_id,black) AND catalog_enum_allowed(model_id,enum_id,white)
            AND catalog_enum_allowed(model_id,storage_id,gb128) AND catalog_enum_allowed(model_id,storage_id,gb256) THEN
        INSERT INTO classifier_node(code,name,parent_id,node_kind,unit_of_measure_id,sort_order,created_at,updated_at)
            VALUES('SKU-IP16-BLACK-128','iPhone 16 — чёрный, 128 ГБ',model_id,'PRODUCT',pcs,0,now(),now()) ON CONFLICT(code) DO NOTHING;
        INSERT INTO classifier_node(code,name,parent_id,node_kind,unit_of_measure_id,sort_order,created_at,updated_at)
            VALUES('SKU-IP16-WHITE-256','iPhone 16 — белый, 256 ГБ',model_id,'PRODUCT',pcs,1,now(),now()) ON CONFLICT(code) DO NOTHING;
        SELECT id INTO first_id FROM classifier_node WHERE code='SKU-IP16-BLACK-128';
        SELECT id INTO second_id FROM classifier_node WHERE code='SKU-IP16-WHITE-256';
        INSERT INTO node_attribute_value(classifier_node_id,enumeration_id,enumeration_value_id,created_at,updated_at)
            VALUES(first_id,enum_id,black,now(),now()),(second_id,enum_id,white,now(),now()),
                  (first_id,storage_id,gb128,now(),now()),(second_id,storage_id,gb256,now(),now()) ON CONFLICT(classifier_node_id,enumeration_id) DO NOTHING;
        SELECT id INTO param_id FROM numeric_parameter WHERE code='PRICE';
        INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at)
            VALUES(first_id,param_id,79990,now(),now()),(second_id,param_id,89990,now(),now()) ON CONFLICT(classifier_node_id,numeric_parameter_id) DO NOTHING;
        SELECT id INTO param_id FROM numeric_parameter WHERE code='WEIGHT';
        INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at)
            VALUES(first_id,param_id,0.170,now(),now()),(second_id,param_id,0.170,now(),now()) ON CONFLICT(classifier_node_id,numeric_parameter_id) DO NOTHING;
        SELECT id INTO os_value FROM enumeration_value WHERE enumeration_id=os_id AND code='IOS';
        IF catalog_enum_allowed(model_id,os_id,os_value) THEN
            INSERT INTO node_attribute_value(classifier_node_id,enumeration_id,enumeration_value_id,created_at,updated_at)
                VALUES(first_id,os_id,os_value,now(),now()),(second_id,os_id,os_value,now(),now()) ON CONFLICT(classifier_node_id,enumeration_id) DO NOTHING;
        END IF;
        END IF;
    END IF;
    INSERT INTO classifier_node(code,name,parent_id,node_kind,sort_order,created_at,updated_at)
        SELECT 'DEMO-EMPTY-GROUP','Пустая группа — ещё без изделий',id,'GROUP',99,now(),now() FROM classifier_node WHERE code='ELECTRONICS' ON CONFLICT(code) DO NOTHING;
    -- У каждой числовой характеристики примера есть единица измерения.
    INSERT INTO unit_of_measure(code,name) VALUES('MAH','мАч'),('INCH','дюймы'),('GB','ГБ'),('RUB','рубли') ON CONFLICT(code) DO NOTHING;
    UPDATE numeric_parameter SET unit_of_measure_id=(SELECT id FROM unit_of_measure WHERE code=CASE numeric_parameter.code
        WHEN 'BATTERY' THEN 'MAH' WHEN 'SCREEN_SIZE' THEN 'INCH' WHEN 'RAM' THEN 'GB' WHEN 'PRICE' THEN 'RUB' END)
        WHERE code IN ('BATTERY','SCREEN_SIZE','RAM','PRICE') AND unit_of_measure_id IS NULL;
    -- Материалам назначены числовые характеристики, сборке — несколько типов компонентов.
    INSERT INTO numeric_parameter(code,name,description,min_value,max_value,unit_of_measure_id,created_at,updated_at)
        VALUES('DEMO-MATERIAL-PRICE','Цена материала за единицу','Учебная цена за одну единицу учёта материала',0,1000000,(SELECT id FROM unit_of_measure WHERE code='RUB'),now(),now()) ON CONFLICT(code) DO NOTHING;
    INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,created_at)
        SELECT n.id,p.id,now() FROM classifier_node n,numeric_parameter p WHERE n.code='BOM-MATERIALS' AND p.code='DEMO-MATERIAL-PRICE' ON CONFLICT DO NOTHING;
    INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at)
        SELECT n.id,p.id,CASE n.code WHEN 'BOM-STEEL' THEN 120 ELSE 400 END,now(),now() FROM classifier_node n,numeric_parameter p
        WHERE n.code IN ('BOM-STEEL','BOM-PAINT') AND p.code='DEMO-MATERIAL-PRICE' ON CONFLICT(classifier_node_id,numeric_parameter_id) DO NOTHING;
    -- Подсистема ХО остаётся отдельной абстракцией: шаблон и заполненный документ.
    IF to_regclass('ho_class') IS NOT NULL THEN
        INSERT INTO ho_class(code,name,description) VALUES('DEMO-PURCHASE','Закупки','Учебная группа операций') ON CONFLICT(code) DO NOTHING;
        SELECT id INTO ho_group FROM ho_class WHERE code='DEMO-PURCHASE';
        INSERT INTO ho_template(ho_class_id,code,name,description) VALUES(ho_group,'DEMO-MATERIAL-PURCHASE','Закупка материалов','Пример шаблона с числом, текстом и датой') ON CONFLICT(code) DO NOTHING;
        SELECT id INTO template_id FROM ho_template WHERE code='DEMO-MATERIAL-PURCHASE';
        INSERT INTO ho_parameter_type(code,name,data_type,min_value) VALUES('DEMO-AMOUNT','Сумма','NUMBER',0),('DEMO-DOCUMENT','Основание','STRING',NULL),('DEMO-DELIVERY','Дата поставки','DATE',NULL) ON CONFLICT(code) DO NOTHING;
        INSERT INTO ho_template_parameter(ho_template_id,ho_parameter_type_id,is_required,sort_order)
            SELECT template_id,id,true,CASE code WHEN 'DEMO-AMOUNT' THEN 0 WHEN 'DEMO-DOCUMENT' THEN 1 ELSE 2 END FROM ho_parameter_type WHERE code IN ('DEMO-AMOUNT','DEMO-DOCUMENT','DEMO-DELIVERY') ON CONFLICT DO NOTHING;
        INSERT INTO ho_parameter_type(code,name,data_type,enumeration_id)
            SELECT 'DEMO-MATERIAL-COLOR','Цвет маркировки упаковки','ENUM',id FROM enumeration WHERE code='PHONE-COLORS' ON CONFLICT(code) DO NOTHING;
        INSERT INTO ho_template_parameter(ho_template_id,ho_parameter_type_id,is_required,sort_order)
            SELECT template_id,id,false,3 FROM ho_parameter_type WHERE code='DEMO-MATERIAL-COLOR' ON CONFLICT DO NOTHING;
        INSERT INTO ho_role_type(code,name,description) VALUES('DEMO-SUPPLIER','Поставщик','Кто поставляет материалы') ON CONFLICT(code) DO NOTHING;
        SELECT id INTO role_id FROM ho_role_type WHERE code='DEMO-SUPPLIER';
        INSERT INTO ho_template_role(ho_template_id,ho_role_type_id,is_required) VALUES(template_id,role_id,true) ON CONFLICT DO NOTHING;
        INSERT INTO ho_instance(ho_template_id,code,name,doc_date,status) VALUES(template_id,'DEMO-PURCHASE-001','Учебная закупка стали',DATE '2026-11-08','DRAFT') ON CONFLICT(code) DO NOTHING;
        SELECT id INTO op_id FROM ho_instance WHERE code='DEMO-PURCHASE-001';
        INSERT INTO ho_instance_value(ho_instance_id,ho_parameter_type_id,numeric_value,string_value,date_value)
            SELECT op_id,id,CASE WHEN code='DEMO-AMOUNT' THEN 12000 END,CASE WHEN code='DEMO-DOCUMENT' THEN 'Учебная заявка на материалы' END,CASE WHEN code='DEMO-DELIVERY' THEN DATE '2026-11-15' END
            FROM ho_parameter_type WHERE code IN ('DEMO-AMOUNT','DEMO-DOCUMENT','DEMO-DELIVERY') ON CONFLICT DO NOTHING;
        INSERT INTO ho_instance_value(ho_instance_id,ho_parameter_type_id,enum_value_id)
            SELECT op_id,p.id,v.id FROM ho_parameter_type p JOIN enumeration_value v ON v.enumeration_id=p.enumeration_id
            WHERE p.code='DEMO-MATERIAL-COLOR' AND v.code='BLACK' ON CONFLICT DO NOTHING;
        INSERT INTO ho_instance_role(ho_instance_id,ho_role_type_id,counterparty) VALUES(op_id,role_id,'Учебный поставщик «Металл»') ON CONFLICT DO NOTHING;
    END IF;
    INSERT INTO schema_migration(name) VALUES('catalog-demo-v2');
END
$demo$;
