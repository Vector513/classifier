-- Состав конфигурируемого изделия: условия выбирают компоненты по параметрам,
-- множитель переводит значение параметра в число компонентов (например RAM/8).
CREATE TABLE IF NOT EXISTS configuration_component (
 id BIGSERIAL PRIMARY KEY,
 template_id BIGINT NOT NULL REFERENCES product_template(id) ON DELETE CASCADE,
 component_id BIGINT NOT NULL REFERENCES classifier_node(id),
 quantity NUMERIC(19,6) NOT NULL CHECK (quantity>=0 AND quantity<'Infinity'::numeric),
 quantity_parameter_id BIGINT REFERENCES configuration_parameter(id),
 when_values JSONB NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(when_values)='object'),
 selection_group VARCHAR(100) NOT NULL DEFAULT '',
 required_group BOOLEAN NOT NULL DEFAULT false,
 CHECK (NOT required_group OR btrim(selection_group)<>'')
);
^^^
CREATE OR REPLACE FUNCTION orders_component_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE entry RECORD; param configuration_parameter;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 PERFORM orders_require(EXISTS(SELECT 1 FROM classifier_node WHERE id=NEW.component_id AND node_kind<>'GROUP'),'Выберите компонент изделия');
 PERFORM orders_require(NEW.component_id<>(SELECT product_id FROM product_template WHERE id=NEW.template_id),'Изделие не может входить в собственный состав');
 IF NEW.quantity_parameter_id IS NOT NULL THEN
  PERFORM orders_require(EXISTS(SELECT 1 FROM configuration_parameter WHERE id=NEW.quantity_parameter_id AND template_id=NEW.template_id AND data_type IN ('NUMBER','INTEGER')),'Количество должно ссылаться на числовой параметр этого шаблона');
 END IF;
 FOR entry IN SELECT * FROM jsonb_each(NEW.when_values) LOOP
  SELECT * INTO param FROM configuration_parameter WHERE template_id=NEW.template_id AND code=entry.key;
  PERFORM orders_require(param.id IS NOT NULL AND orders_matches_type(entry.value,param.data_type),'Условие компонента содержит неизвестный параметр или неверный тип');
  IF param.data_type='ENUM' THEN
   PERFORM orders_require(EXISTS(SELECT 1 FROM enumeration_value WHERE enumeration_id=param.enumeration_id AND code=entry.value#>>'{}'),'Условие содержит неизвестное значение перечисления');
  END IF;
 END LOOP;
 RETURN NEW;
END $$;
^^^
DROP TRIGGER IF EXISTS orders_component_guard ON configuration_component;
CREATE TRIGGER orders_component_guard BEFORE INSERT OR UPDATE ON configuration_component FOR EACH ROW EXECUTE FUNCTION orders_component_guard();
^^^
CREATE OR REPLACE FUNCTION orders_component_parameter_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR NEW.code IS DISTINCT FROM OLD.code OR NEW.data_type IS DISTINCT FROM OLD.data_type OR NEW.enumeration_id IS DISTINCT FROM OLD.enumeration_id THEN
  PERFORM orders_require(NOT EXISTS(SELECT 1 FROM configuration_component WHERE template_id=OLD.template_id AND (quantity_parameter_id=OLD.id OR when_values ? OLD.code)),
   'Параметр используется в составе изделия. Сначала измените связанные компоненты');
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
^^^
DROP TRIGGER IF EXISTS orders_component_parameter_guard ON configuration_parameter;
CREATE TRIGGER orders_component_parameter_guard BEFORE UPDATE OR DELETE ON configuration_parameter FOR EACH ROW EXECUTE FUNCTION orders_component_parameter_guard();
^^^
-- Разворачиваем только листья утверждённого состава. Цена промежуточного узла
-- не добавляется повторно поверх стоимости входящих в него деталей.
CREATE OR REPLACE FUNCTION orders_component_resources(p_product BIGINT)
RETURNS TABLE(component_id BIGINT,quantity NUMERIC,issue TEXT) LANGUAGE SQL STABLE AS $$
 WITH selected AS (
  SELECT id FROM bom_specification WHERE product_id=p_product AND status='RELEASED' ORDER BY revision DESC LIMIT 1
 )
 SELECT e.component_id,e.total_quantity,CASE WHEN e.cycle THEN 'Цикл в составе компонента' END
 FROM selected s CROSS JOIN LATERAL bom_explode(s.id,1) e
 WHERE e.component_specification_id IS NULL OR e.cycle
 UNION ALL
 SELECT p_product,1::numeric,CASE WHEN EXISTS(SELECT 1 FROM bom_product WHERE node_id=p_product AND kind='ASSEMBLY')
  THEN 'Нет утверждённого состава компонента' END
 WHERE NOT EXISTS(SELECT 1 FROM selected)
$$;
^^^
CREATE OR REPLACE FUNCTION orders_item_cost(p_product BIGINT,p_variant BIGINT)
RETURNS TABLE(unit_price NUMERIC,components JSONB,issues JSONB) LANGUAGE SQL STABLE AS $$
 WITH variant AS (
  SELECT template_id,orders_variant_values(id) AS config_values FROM product_variant WHERE id=p_variant
 ), rules AS (
  SELECT c.*,v.config_values,q.code AS quantity_code,v.config_values @> c.when_values AS matches
  FROM variant v JOIN configuration_component c ON c.template_id=v.template_id
  LEFT JOIN configuration_parameter q ON q.id=c.quantity_parameter_id
 ), roots AS (
  SELECT p_product AS component_id,1::numeric AS quantity,NULL::text AS issue WHERE p_variant IS NULL
  UNION ALL
  SELECT component_id,quantity*CASE WHEN quantity_parameter_id IS NULL THEN 1 ELSE (config_values->>quantity_code)::numeric END,
   CASE WHEN quantity_parameter_id IS NOT NULL AND NOT (config_values ? quantity_code) THEN 'Не задан параметр количества '||quantity_code END
  FROM rules WHERE matches
 ), resources AS (
  SELECT e.component_id,r.quantity*e.quantity AS quantity,coalesce(r.issue,e.issue) AS issue
  FROM roots r CROSS JOIN LATERAL orders_component_resources(r.component_id) e
  WHERE r.quantity<>0 OR r.issue IS NOT NULL
 ), grouped AS (
  SELECT component_id,sum(quantity) AS quantity,string_agg(DISTINCT issue,'; ') AS issue FROM resources GROUP BY component_id
 ), priced AS (
  SELECT g.*,n.code,n.name,u.code AS unit,
   coalesce(buy.value,retail.value) AS price,
   CASE WHEN buy.value IS NOT NULL THEN 'PURCHASE_PRICE' ELSE 'PRICE' END AS price_source
  FROM grouped g JOIN classifier_node n ON n.id=g.component_id
  LEFT JOIN unit_of_measure u ON u.id=n.unit_of_measure_id
  LEFT JOIN numeric_parameter bp ON bp.code='PURCHASE_PRICE' AND bp.unit_of_measure_id IN (SELECT id FROM unit_of_measure WHERE code='RUB')
  LEFT JOIN node_numeric_value buy ON buy.classifier_node_id=n.id AND buy.numeric_parameter_id=bp.id AND buy.value>=0 AND buy.value<'Infinity'::numeric
  LEFT JOIN numeric_parameter rp ON rp.code='PRICE' AND rp.unit_of_measure_id IN (SELECT id FROM unit_of_measure WHERE code='RUB')
  LEFT JOIN node_numeric_value retail ON retail.classifier_node_id=n.id AND retail.numeric_parameter_id=rp.id AND retail.value>=0 AND retail.value<'Infinity'::numeric
 ), problems AS (
  SELECT issue AS message FROM priced WHERE issue IS NOT NULL
  UNION SELECT 'Не задана цена компонента: '||name FROM priced WHERE price IS NULL
  UNION SELECT 'Некорректное количество компонента: '||name FROM priced WHERE quantity IS NULL OR quantity<0 OR (unit IN ('PCS','BOM-PCS') AND quantity<>trunc(quantity))
  UNION SELECT 'Для исполнения не задан состав шаблона' WHERE p_variant IS NOT NULL AND NOT EXISTS(SELECT 1 FROM rules)
  UNION SELECT 'Не выбран компонент обязательной группы: '||selection_group FROM rules GROUP BY selection_group HAVING bool_or(required_group) AND NOT bool_or(matches)
 )
 SELECT CASE WHEN EXISTS(SELECT 1 FROM problems) THEN NULL ELSE round(coalesce((SELECT sum(quantity*price) FROM priced),0),2) END,
  coalesce((SELECT jsonb_agg(jsonb_build_object('id',component_id,'code',code,'name',name,'quantity',quantity::text,'unit',unit,'unit_price',round(price,2)::text,'price_source',price_source,'amount',round(quantity*price,2)::text) ORDER BY code) FROM priced),'[]'),
  coalesce((SELECT jsonb_agg(message ORDER BY message) FROM problems),'[]')
$$;
^^^
CREATE OR REPLACE VIEW orders_amounts AS
 SELECT o.id AS order_id,count(l.id) AS line_count,
  count(l.id) FILTER (WHERE c.unit_price IS NULL) AS unpriced_line_count,
  coalesce(sum(c.unit_price*l.quantity),0)::text AS priced_subtotal,
  CASE WHEN count(l.id) FILTER (WHERE c.unit_price IS NULL)=0 THEN coalesce(sum(c.unit_price*l.quantity),0)::text END AS total_amount
 FROM customer_order o LEFT JOIN order_line l ON l.order_id=o.id
 LEFT JOIN LATERAL orders_item_cost(l.product_id,l.variant_id) c ON l.id IS NOT NULL GROUP BY o.id;
