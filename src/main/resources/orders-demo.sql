-- Демонстрация из методички: воздуховод прямоугольного сечения.
-- Однократное дополнение: пользовательские строки не перезаписываются.
DO $demo$
DECLARE pcs BIGINT; mm BIGINT; root BIGINT; model BIGINT; ready BIGINT; cls BIGINT; en BIGINT; tpl BIGINT; cust BIGINT; ord BIGINT; variant BIGINT;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 IF EXISTS(SELECT 1 FROM schema_migration WHERE name='orders-demo-v1') THEN RETURN; END IF;
 INSERT INTO schema_migration(name) VALUES('orders-demo-v1');
 IF EXISTS(SELECT 1 FROM classifier_node WHERE code LIKE 'ORDER-%') OR EXISTS(SELECT 1 FROM constraint_function WHERE code='ORDER_RATIO') OR EXISTS(SELECT 1 FROM enumeration_class WHERE code='ORDER-CONNECTION-CLASS') OR EXISTS(SELECT 1 FROM enumeration WHERE code='ORDER-CONNECTION') OR EXISTS(SELECT 1 FROM product_template WHERE code='ORDER-DUCT-TEMPLATE') OR EXISTS(SELECT 1 FROM product_variant WHERE code='ORDER-DUCT-500-300-1200') OR EXISTS(SELECT 1 FROM customer_order WHERE order_number='ЛР7-0001') THEN
  RAISE NOTICE 'Примеры заказов пропущены: зарезервированные коды ORDER-* заняты';RETURN;
 END IF;
 INSERT INTO unit_of_measure(code,name) VALUES('PCS','штуки'),('MM','миллиметры') ON CONFLICT(code) DO NOTHING;
 SELECT id INTO pcs FROM unit_of_measure WHERE code='PCS';SELECT id INTO mm FROM unit_of_measure WHERE code='MM';
 INSERT INTO classifier_node(code,name,node_kind,sort_order,created_at,updated_at) VALUES('ORDER-DEMO','Заказы на воздуховоды','GROUP',0,now(),now()) RETURNING id INTO root;
 INSERT INTO classifier_node(code,name,node_kind,parent_id,unit_of_measure_id,sort_order,created_at,updated_at) VALUES('ORDER-DUCT','Воздуховод прямой прямоугольный','MODEL',root,pcs,0,now(),now()) RETURNING id INTO model;
 INSERT INTO classifier_node(code,name,node_kind,parent_id,unit_of_measure_id,sort_order,created_at,updated_at) VALUES('ORDER-CAP','Заглушка стандартная','PRODUCT',root,pcs,1,now(),now()) RETURNING id INTO ready;
 INSERT INTO enumeration_class(code,name,created_at,updated_at) VALUES('ORDER-CONNECTION-CLASS','Соединения воздуховодов',now(),now()) RETURNING id INTO cls;
 INSERT INTO enumeration(code,name,enumeration_class_id,created_at,updated_at) VALUES('ORDER-CONNECTION','Тип соединения',cls,now(),now()) RETURNING id INTO en;
 INSERT INTO enumeration_value(code,name,enumeration_id,sort_order,created_at,updated_at) VALUES('FLANGE','На фланцах',en,0,now(),now()),('RAIL','На шине',en,1,now(),now()),('CAP','Заглушка',en,2,now(),now());
 INSERT INTO product_template(product_id,code,name) VALUES(model,'ORDER-DUCT-TEMPLATE','Воздуховод прямой') RETURNING id INTO tpl;
 INSERT INTO configuration_parameter(template_id,code,name,data_type,unit_of_measure_id,required,min_value,max_value) VALUES(tpl,'h','Высота','NUMBER',mm,true,100,1000),(tpl,'w','Ширина','NUMBER',mm,true,100,1000),(tpl,'L','Длина','NUMBER',mm,true,500,2500);
 INSERT INTO configuration_parameter(template_id,code,name,data_type,enumeration_id,required) VALUES(tpl,'connection','Соединение','ENUM',en,true);
 PERFORM orders_save_function(NULL,'ORDER_RATIO','Допустимое соотношение сторон','BOOLEAN',$sig$[{"code": "a", "dataType": "NUMBER"}, {"code": "b", "dataType": "NUMBER"}, {"code": "lower", "dataType": "NUMBER"}, {"code": "upper", "dataType": "NUMBER"}]$sig$::jsonb,$ast${"kind": "OPERATOR", "operator": "AND", "args": [{"kind": "OPERATOR", "operator": "GE", "args": [{"kind": "OPERATOR", "operator": "DIV", "args": [{"kind": "FORMAL", "formalCode": "a"}, {"kind": "FORMAL", "formalCode": "b"}]}, {"kind": "FORMAL", "formalCode": "lower"}]}, {"kind": "OPERATOR", "operator": "LE", "args": [{"kind": "OPERATOR", "operator": "DIV", "args": [{"kind": "FORMAL", "formalCode": "a"}, {"kind": "FORMAL", "formalCode": "b"}]}, {"kind": "FORMAL", "formalCode": "upper"}]}]}$ast$::jsonb);
 PERFORM orders_save_rule(NULL,tpl,'RATIO','Соотношение сторон','Отношение высоты к ширине должно быть от 0,5 до 2',$ast${"kind": "CALL", "functionCode": "ORDER_RATIO", "args": [{"kind": "PARAMETER", "parameterCode": "h"}, {"kind": "PARAMETER", "parameterCode": "w"}, {"kind": "CONST", "value": 0.5}, {"kind": "CONST", "value": 2}]}$ast$::jsonb);
 PERFORM orders_save_rule(NULL,tpl,'LENGTH','Длина участка','Длина должна быть больше 500 и меньше 2500 мм',$ast${"kind": "OPERATOR", "operator": "AND", "args": [{"kind": "OPERATOR", "operator": "GT", "args": [{"kind": "PARAMETER", "parameterCode": "L"}, {"kind": "CONST", "value": 500}]}, {"kind": "OPERATOR", "operator": "LT", "args": [{"kind": "PARAMETER", "parameterCode": "L"}, {"kind": "CONST", "value": 2500}]}]}$ast$::jsonb);
 PERFORM orders_save_rule(NULL,tpl,'FLANGE','Фланцевое соединение','Для фланцевого соединения длина должна быть больше 1000 мм',$ast${"kind": "OPERATOR", "operator": "IMPLIES", "args": [{"kind": "OPERATOR", "operator": "EQ", "args": [{"kind": "PARAMETER", "parameterCode": "connection"}, {"kind": "CONST", "value": "FLANGE"}]}, {"kind": "OPERATOR", "operator": "GT", "args": [{"kind": "PARAMETER", "parameterCode": "L"}, {"kind": "CONST", "value": 1000}]}]}$ast$::jsonb);
 INSERT INTO customer(name,contact_person,phone,email) VALUES('АэроСистемы','Иван Петров','+7 900 000-00-07','orders@example.test') RETURNING id INTO cust;
 INSERT INTO customer_order(customer_id,order_number,order_date,requested_date) VALUES(cust,'ЛР7-0001',CURRENT_DATE,CURRENT_DATE+14) RETURNING id INTO ord;
 variant:=create_variant(tpl,'ORDER-DUCT-500-300-1200','Воздуховод 500×300×1200, на фланцах','{"h":500,"w":300,"L":1200,"connection":"FLANGE"}');
 PERFORM add_to_order(ord,NULL,variant,10,'Изготовить по указанным размерам');
 PERFORM add_to_order(ord,ready,NULL,2,'Готовое изделие из справочника');
END $demo$;
