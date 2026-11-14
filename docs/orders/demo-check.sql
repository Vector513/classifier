-- Выполнять psql -v ON_ERROR_STOP=1 на экземпляре с CLASSIFIER_DEMO_ENABLED=true.
-- Исходный набор: src/main/resources/orders-demo.sql. Все изменения откатываются.
BEGIN;
SELECT pg_advisory_xact_lock(21001);
DO $check$
DECLARE tpl BIGINT; fun BIGINT; cust BIGINT; variant BIGINT; ord BIGINT; before_count BIGINT;
BEGIN
 SELECT id INTO STRICT tpl FROM product_template WHERE code='ORDER-DUCT-TEMPLATE';
 SELECT id INTO STRICT fun FROM constraint_function WHERE code='ORDER_RATIO';
 PERFORM orders_require((SELECT count(*)=4 FROM get_config_params(tpl)),'Ожидалось четыре параметра');
 PERFORM orders_require(eval_function(fun,'[500,300,0.5,2]')='true'::jsonb,'Функция должна допустить соотношение 500/300');
 PERFORM orders_require(eval_function(fun,'[1000,100,0.5,2]')='false'::jsonb,'Функция должна отклонить соотношение 1000/100');
 PERFORM orders_require(check_constraints(tpl,'{"h":500,"w":300,"L":1200,"connection":"FLANGE"}'),'Допустимая конфигурация отклонена');
 PERFORM orders_require(NOT check_constraints(tpl,'{"h":500,"w":300,"L":800,"connection":"FLANGE"}'),'Нарушение фланцевого правила не обнаружено');
 PERFORM orders_require(NOT check_constraints(tpl,'{"h":1000,"w":100,"L":1200,"connection":"RAIL"}'),'Нарушение соотношения сторон не обнаружено');
 PERFORM orders_require(NOT check_constraints(tpl,'{"h":500,"w":300,"L":1200,"connection":"UNKNOWN"}'),'Неизвестное перечисление принято');
 SELECT count(*) INTO before_count FROM product_variant;
 BEGIN
  PERFORM create_variant(tpl,'SQL-CHECK-BAD','Ошибочный вариант','{"h":500,"w":300,"L":800,"connection":"FLANGE"}');
  RAISE EXCEPTION 'Недопустимый вариант был сохранён';
 EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: неверное исполнение отклонено';
 END;
 PERFORM orders_require((SELECT count(*)=before_count FROM product_variant),'После ошибки остался вариант');
 INSERT INTO customer(name,phone) VALUES('SQL-проверка ЛР7','+7 000 000-00-07') RETURNING id INTO cust;
 ord:=create_order(cust,'SQL-CHECK-ORDER',CURRENT_DATE);
 variant:=create_variant(tpl,'SQL-CHECK-VALID','Воздуховод SQL','{"h":500,"w":300,"L":1200,"connection":"FLANGE"}');
 PERFORM add_to_order(ord,NULL,variant,3);
 PERFORM add_to_order(ord,(SELECT id FROM classifier_node WHERE code='ORDER-CAP'),NULL,2);
 PERFORM orders_require((SELECT count(*)=2 FROM get_order_contents(ord)),'Состав заказа должен содержать две позиции');
 BEGIN
  PERFORM update_variant(variant,'SQL-CHECK-VALID','Изменённый','{"h":600,"w":300,"L":1200,"connection":"FLANGE"}');
  RAISE EXCEPTION 'Заказанный вариант был изменён';
 EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS: заказанное исполнение защищено';
 END;
 PERFORM orders_assert_consistent();
 RAISE NOTICE 'PASS: параметры, функция, правила, атомарное создание, состав заказа и защита исполнения';
END $check$;
SELECT order_number, line_number, code, name, quantity, parameters
FROM customer_order o CROSS JOIN LATERAL get_order_contents(o.id)
WHERE order_number IN ('ЛР7-0001','SQL-CHECK-ORDER') ORDER BY order_number,line_number;
ROLLBACK;
