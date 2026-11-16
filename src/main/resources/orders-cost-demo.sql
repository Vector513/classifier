-- Учебный состав комплектов. Цены задаются ресурсам; итоговых цен исполнений нет.
DO $cost$ DECLARE data JSONB := $data${
 "items": [
  {"code": "SHOP-PART-CPU-ECO","name": "Процессор офисного класса","price": 6500},
  {"code": "SHOP-PART-CPU-PRO","name": "Процессор высокопроизводительного класса","price": 24500},
  {"code": "SHOP-PART-CASE-PC","name": "Корпус системного блока","price": 3900},
  {"code": "SHOP-PART-SSD512","name": "SSD 512 ГБ","price": 2800},
  {"code": "SHOP-PART-SSD2048","name": "SSD 2 ТБ","price": 8200},
  {"code": "SHOP-PART-PSU450","name": "Блок питания 450 Вт","price": 2800},
  {"code": "SHOP-PART-PSU850","name": "Блок питания 850 Вт","price": 6500},
  {"code": "SHOP-PART-WIFI","name": "Модуль Wi-Fi","price": 1200},
  {"code": "SHOP-PART-LAPTOP-BASE","name": "Базовый модуль ноутбука без RAM SSD и зарядного устройства","price": 42000},
  {"code": "SHOP-PART-DOCK","name": "Док-станция USB-C","price": 6500},
  {"code": "SHOP-PART-MONITOR","name": "Монитор 24 дюйма","price": 12900},
  {"code": "SHOP-PART-CHARGER65","name": "Зарядное устройство USB-C 65 Вт","price": 2900},
  {"code": "SHOP-PART-CHARGER100","name": "Зарядное устройство USB-C 100 Вт","price": 4200},
  {"code": "SHOP-PART-CHARGER140","name": "Зарядное устройство USB-C 140 Вт","price": 5900},
  {"code": "SHOP-PART-PHONE128","name": "Смартфон базовый 128 ГБ без аксессуаров","price": 32000},
  {"code": "SHOP-PART-PHONE256","name": "Смартфон базовый 256 ГБ без аксессуаров","price": 39000},
  {"code": "SHOP-PART-PHONE512","name": "Смартфон базовый 512 ГБ без аксессуаров","price": 48000},
  {"code": "SHOP-PART-CHARGER30","name": "Зарядное устройство USB-C 30 Вт","price": 1400},
  {"code": "SHOP-PART-CASE-ARMOR","name": "Усиленный защитный чехол","price": 1600},
  {"code": "SHOP-PART-KEYBOARD-RGB","name": "Игровая клавиатура с RGB","price": 3900},
  {"code": "SHOP-PART-BATTERY-KIT","name": "Комплект аккумуляторов повышенной ёмкости для периферии","price": 1400},
  {"code": "SHOP-PART-DUAL-ADAPTER","name": "Адаптер двухрежимного подключения периферии","price": 600},
  {"code": "SHOP-PART-HUB","name": "Шлюз умного дома","price": 3500},
  {"code": "SHOP-PART-NVR8","name": "Видеорегистратор 8 каналов","price": 7900},
  {"code": "SHOP-PART-NVR16","name": "Видеорегистратор 16 каналов","price": 12900},
  {"code": "SHOP-PART-HDD4","name": "Жёсткий диск 4 ТБ","price": 9500},
  {"code": "SHOP-PART-HDD8","name": "Жёсткий диск 8 ТБ","price": 16900},
  {"code": "SHOP-PART-POE60","name": "PoE-коммутатор с бюджетом 60 Вт","price": 3900},
  {"code": "SHOP-PART-POE120","name": "PoE-коммутатор с бюджетом 120 Вт","price": 6500},
  {"code": "SHOP-PART-POE240","name": "PoE-коммутатор с бюджетом 240 Вт","price": 10900}
 ],
 "rules": [
  {"template": "SHOP-PC","group": "Процессор","component": "SHOP-PART-CPU-ECO","quantity": 1,"parameter": null,"when": {"cpu": "ECO"}},
  {"template": "SHOP-PC","group": "Процессор","component": "BOM-EL-PC-CPU","quantity": 1,"parameter": null,"when": {"cpu": "BALANCED"}},
  {"template": "SHOP-PC","group": "Процессор","component": "SHOP-PART-CPU-PRO","quantity": 1,"parameter": null,"when": {"cpu": "PERFORMANCE"}},
  {"template": "SHOP-PC","group": "Графика","component": "BOM-EL-PC-BOARD","quantity": 0,"parameter": null,"when": {"gpu": "INTEGRATED"}},
  {"template": "SHOP-PC","group": "Графика","component": "BOM-EL-PC-RTX4060","quantity": 1,"parameter": null,"when": {"gpu": "MID"}},
  {"template": "SHOP-PC","group": "Графика","component": "BOM-EL-PC-RTX4070","quantity": 1,"parameter": null,"when": {"gpu": "HIGH"}},
  {"template": "SHOP-PC","group": "Материнская плата","component": "BOM-EL-PC-BOARD","quantity": 1,"parameter": null,"when": {}},
  {"template": "SHOP-PC","group": "Корпус","component": "SHOP-PART-CASE-PC","quantity": 1,"parameter": null,"when": {}},
  {"template": "SHOP-PC","group": "Охлаждение","component": "BOM-EL-PC-COOLING","quantity": 1,"parameter": null,"when": {}},
  {"template": "SHOP-PC","group": "Память","component": "BOM-EL-PC-RAM","quantity": 0.125,"parameter": "ram","when": {}},
  {"template": "SHOP-PC","group": "SSD","component": "SHOP-PART-SSD512","quantity": 1,"parameter": null,"when": {"ssd": 512}},
  {"template": "SHOP-PC","group": "SSD","component": "BOM-EL-PC-SSD","quantity": 1,"parameter": null,"when": {"ssd": 1024}},
  {"template": "SHOP-PC","group": "SSD","component": "SHOP-PART-SSD2048","quantity": 1,"parameter": null,"when": {"ssd": 2048}},
  {"template": "SHOP-PC","group": "Блок питания","component": "SHOP-PART-PSU450","quantity": 1,"parameter": null,"when": {"psu": 450}},
  {"template": "SHOP-PC","group": "Блок питания","component": "BOM-EL-PC-PSU","quantity": 1,"parameter": null,"when": {"psu": 650}},
  {"template": "SHOP-PC","group": "Блок питания","component": "SHOP-PART-PSU850","quantity": 1,"parameter": null,"when": {"psu": 850}},
  {"template": "SHOP-PC","group": "Wi-Fi","component": "SHOP-PART-WIFI","quantity": 1,"parameter": null,"when": {"wifi": true}},
  {"template": "SHOP-PC","group": "Wi-Fi","component": "SHOP-PART-WIFI","quantity": 0,"parameter": null,"when": {"wifi": false}},
  {"template": "SHOP-WORKPLACE","group": "Базовый модуль","component": "SHOP-PART-LAPTOP-BASE","quantity": 1,"parameter": null,"when": {}},
  {"template": "SHOP-WORKPLACE","group": "Память","component": "BOM-EL-PC-RAM","quantity": 0.125,"parameter": "ram","when": {}},
  {"template": "SHOP-WORKPLACE","group": "SSD","component": "SHOP-PART-SSD512","quantity": 1,"parameter": null,"when": {"ssd": 512}},
  {"template": "SHOP-WORKPLACE","group": "SSD","component": "BOM-EL-PC-SSD","quantity": 1,"parameter": null,"when": {"ssd": 1024}},
  {"template": "SHOP-WORKPLACE","group": "SSD","component": "SHOP-PART-SSD2048","quantity": 1,"parameter": null,"when": {"ssd": 2048}},
  {"template": "SHOP-WORKPLACE","group": "Док-станция","component": "SHOP-PART-DOCK","quantity": 1,"parameter": null,"when": {"dock": true}},
  {"template": "SHOP-WORKPLACE","group": "Док-станция","component": "SHOP-PART-DOCK","quantity": 0,"parameter": null,"when": {"dock": false}},
  {"template": "SHOP-WORKPLACE","group": "Мониторы","component": "SHOP-PART-MONITOR","quantity": 1,"parameter": "monitors","when": {}},
  {"template": "SHOP-WORKPLACE","group": "Зарядное устройство","component": "SHOP-PART-CHARGER65","quantity": 1,"parameter": null,"when": {"charger": 65}},
  {"template": "SHOP-WORKPLACE","group": "Зарядное устройство","component": "SHOP-PART-CHARGER100","quantity": 1,"parameter": null,"when": {"charger": 100}},
  {"template": "SHOP-WORKPLACE","group": "Зарядное устройство","component": "SHOP-PART-CHARGER140","quantity": 1,"parameter": null,"when": {"charger": 140}},
  {"template": "SHOP-PHONE","group": "Смартфон","component": "SHOP-PART-PHONE128","quantity": 1,"parameter": null,"when": {"storage": 128}},
  {"template": "SHOP-PHONE","group": "Смартфон","component": "SHOP-PART-PHONE256","quantity": 1,"parameter": null,"when": {"storage": 256}},
  {"template": "SHOP-PHONE","group": "Смартфон","component": "SHOP-PART-PHONE512","quantity": 1,"parameter": null,"when": {"storage": 512}},
  {"template": "SHOP-PHONE","group": "Чехол","component": "BOM-EL-PHONE-CASE","quantity": 0,"parameter": null,"when": {"case": "NONE"}},
  {"template": "SHOP-PHONE","group": "Чехол","component": "BOM-EL-PHONE-CASE","quantity": 1,"parameter": null,"when": {"case": "SILICONE"}},
  {"template": "SHOP-PHONE","group": "Чехол","component": "SHOP-PART-CASE-ARMOR","quantity": 1,"parameter": null,"when": {"case": "ARMOR"}},
  {"template": "SHOP-PHONE","group": "Зарядное устройство","component": "SHOP-PART-CHARGER30","quantity": 0,"parameter": null,"when": {"charger": false,"charger_w": 0}},
  {"template": "SHOP-PHONE","group": "Зарядное устройство","component": "SHOP-PART-CHARGER30","quantity": 1,"parameter": null,"when": {"charger": true,"charger_w": 30}},
  {"template": "SHOP-PHONE","group": "Зарядное устройство","component": "SHOP-PART-CHARGER65","quantity": 1,"parameter": null,"when": {"charger": true,"charger_w": 65}},
  {"template": "SHOP-PHONE","group": "Наушники","component": "BOM-EL-PHONE-EARBUDS","quantity": 1,"parameter": null,"when": {"earbuds": true}},
  {"template": "SHOP-PHONE","group": "Наушники","component": "BOM-EL-PHONE-EARBUDS","quantity": 0,"parameter": null,"when": {"earbuds": false}},
  {"template": "SHOP-GAMING","group": "Клавиатура","component": "BOM-EL-GAME-KEYBOARD","quantity": 1,"parameter": null,"when": {"rgb": false}},
  {"template": "SHOP-GAMING","group": "Клавиатура","component": "SHOP-PART-KEYBOARD-RGB","quantity": 1,"parameter": null,"when": {"rgb": true}},
  {"template": "SHOP-GAMING","group": "Мышь","component": "BOM-EL-GAME-MOUSE","quantity": 1,"parameter": null,"when": {"wireless": false}},
  {"template": "SHOP-GAMING","group": "Мышь","component": "BOM-EL-GAME-WIRELESS-MOUSE","quantity": 1,"parameter": null,"when": {"wireless": true}},
  {"template": "SHOP-GAMING","group": "Гарнитура","component": "BOM-EL-GAME-HEADPHONES","quantity": 1,"parameter": null,"when": {"headset": true,"wireless": false}},
  {"template": "SHOP-GAMING","group": "Гарнитура","component": "BOM-EL-GAME-WIRELESS-HEADSET","quantity": 1,"parameter": null,"when": {"headset": true,"wireless": true}},
  {"template": "SHOP-GAMING","group": "Гарнитура","component": "BOM-EL-GAME-HEADPHONES","quantity": 0,"parameter": null,"when": {"headset": false}},
  {"template": "SHOP-GAMING","group": "Аккумуляторы","component": "SHOP-PART-BATTERY-KIT","quantity": 0,"parameter": null,"when": {"battery": 0}},
  {"template": "SHOP-GAMING","group": "Аккумуляторы","component": "SHOP-PART-BATTERY-KIT","quantity": 0,"parameter": null,"when": {"battery": 40}},
  {"template": "SHOP-GAMING","group": "Аккумуляторы","component": "SHOP-PART-BATTERY-KIT","quantity": 1,"parameter": null,"when": {"battery": 80}},
  {"template": "SHOP-GAMING","group": "Подключение","component": "SHOP-PART-DUAL-ADAPTER","quantity": 0,"parameter": null,"when": {"connection": "WIRED"}},
  {"template": "SHOP-GAMING","group": "Подключение","component": "SHOP-PART-DUAL-ADAPTER","quantity": 0,"parameter": null,"when": {"connection": "RADIO"}},
  {"template": "SHOP-GAMING","group": "Подключение","component": "SHOP-PART-DUAL-ADAPTER","quantity": 0,"parameter": null,"when": {"connection": "BT"}},
  {"template": "SHOP-GAMING","group": "Подключение","component": "SHOP-PART-DUAL-ADAPTER","quantity": 1,"parameter": null,"when": {"connection": "DUAL"}},
  {"template": "SHOP-HOME","group": "Шлюз","component": "SHOP-PART-HUB","quantity": 1,"parameter": null,"when": {"hub": true}},
  {"template": "SHOP-HOME","group": "Шлюз","component": "SHOP-PART-HUB","quantity": 0,"parameter": null,"when": {"hub": false}},
  {"template": "SHOP-HOME","group": "Лампы","component": "BOM-EL-HOME-BULB","quantity": 1,"parameter": "lights","when": {}},
  {"template": "SHOP-HOME","group": "Розетки","component": "BOM-EL-HOME-PLUG","quantity": 1,"parameter": "plugs","when": {}},
  {"template": "SHOP-HOME","group": "Датчики","component": "BOM-EL-HOME-MOTION","quantity": 1,"parameter": "sensors","when": {}},
  {"template": "SHOP-HOME","group": "Камера","component": "BOM-EL-HOME-CAMERA","quantity": 1,"parameter": null,"when": {"camera": true}},
  {"template": "SHOP-HOME","group": "Камера","component": "BOM-EL-HOME-CAMERA","quantity": 0,"parameter": null,"when": {"camera": false}},
  {"template": "SHOP-SECURITY","group": "Камеры","component": "BOM-EL-SEC-CAMERA","quantity": 1,"parameter": "cameras","when": {}},
  {"template": "SHOP-SECURITY","group": "Регистратор","component": "BOM-EL-SEC-NVR","quantity": 1,"parameter": null,"when": {"channels": 4}},
  {"template": "SHOP-SECURITY","group": "Регистратор","component": "SHOP-PART-NVR8","quantity": 1,"parameter": null,"when": {"channels": 8}},
  {"template": "SHOP-SECURITY","group": "Регистратор","component": "SHOP-PART-NVR16","quantity": 1,"parameter": null,"when": {"channels": 16}},
  {"template": "SHOP-SECURITY","group": "Накопитель","component": "BOM-EL-SEC-HDD1","quantity": 1,"parameter": null,"when": {"storage": 1024}},
  {"template": "SHOP-SECURITY","group": "Накопитель","component": "BOM-EL-SEC-HDD2","quantity": 1,"parameter": null,"when": {"storage": 2048}},
  {"template": "SHOP-SECURITY","group": "Накопитель","component": "SHOP-PART-HDD4","quantity": 1,"parameter": null,"when": {"storage": 4096}},
  {"template": "SHOP-SECURITY","group": "Накопитель","component": "SHOP-PART-HDD8","quantity": 1,"parameter": null,"when": {"storage": 8192}},
  {"template": "SHOP-SECURITY","group": "Питание","component": "SHOP-PART-POE60","quantity": 1,"parameter": null,"when": {"poe": true,"poe_budget": 60}},
  {"template": "SHOP-SECURITY","group": "Питание","component": "SHOP-PART-POE120","quantity": 1,"parameter": null,"when": {"poe": true,"poe_budget": 120}},
  {"template": "SHOP-SECURITY","group": "Питание","component": "SHOP-PART-POE240","quantity": 1,"parameter": null,"when": {"poe": true,"poe_budget": 240}},
  {"template": "SHOP-SECURITY","group": "Питание","component": "BOM-EL-SEC-PSU","quantity": 1,"parameter": "cameras","when": {"poe": false}},
  {"template": "SHOP-SECURITY","group": "Кабель","component": "BOM-EL-SEC-CABLE","quantity": 10,"parameter": "cameras","when": {}}
 ]
}$data$::jsonb;
 item JSONB; parent BIGINT; pcs BIGINT; rub BIGINT; node BIGINT; parameter BIGINT; tpl BIGINT;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 IF EXISTS(SELECT 1 FROM schema_migration WHERE name='orders-component-cost-v1') THEN RETURN; END IF;
 IF NOT EXISTS(SELECT 1 FROM numeric_parameter p JOIN unit_of_measure u ON u.id=p.unit_of_measure_id WHERE p.code='PRICE' AND u.code='RUB')
 OR NOT EXISTS(SELECT 1 FROM product_template WHERE code='SHOP-PC')
 OR NOT EXISTS(SELECT 1 FROM classifier_node WHERE code='BOM-EL-PC-CPU') THEN RETURN; END IF;
 SELECT id INTO pcs FROM unit_of_measure WHERE code='PCS'; SELECT id INTO rub FROM unit_of_measure WHERE code='RUB';
 IF pcs IS NULL OR rub IS NULL THEN RETURN; END IF;
 -- Не занимаем и не перезаписываем пользовательские коды.
 IF EXISTS(SELECT 1 FROM classifier_node WHERE code='SHOP-COST-PARTS' OR code IN (SELECT value->>'code' FROM jsonb_array_elements(data->'items'))) THEN RETURN; END IF;
 INSERT INTO classifier_node(code,name,node_kind,sort_order,created_at,updated_at) VALUES('SHOP-COST-PARTS','Компоненты конфигурируемых изделий','GROUP',0,now(),now()) RETURNING id INTO parent;
 FOR item IN SELECT value FROM jsonb_array_elements(data->'items') LOOP
  INSERT INTO classifier_node(code,name,node_kind,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
   VALUES(item->>'code',item->>'name','PRODUCT',parent,pcs,0,now(),now()) RETURNING id INTO node;
  INSERT INTO bom_product(node_id,kind) VALUES(node,'PURCHASED');
  FOR parameter IN SELECT id FROM numeric_parameter WHERE code IN ('PURCHASE_PRICE','PRICE') AND unit_of_measure_id=rub LOOP
   INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,created_at) VALUES(node,parameter,now());
   INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at) VALUES(node,parameter,(item->>'price')::numeric,now(),now());
  END LOOP;
 END LOOP;
 FOR item IN SELECT value FROM jsonb_array_elements(data->'rules') LOOP
  SELECT id INTO tpl FROM product_template WHERE code=item->>'template';
  SELECT id INTO node FROM classifier_node WHERE code=item->>'component';
  IF tpl IS NULL OR node IS NULL THEN RAISE EXCEPTION 'Не найден шаблон или компонент учебного состава: %',item; END IF;
  INSERT INTO configuration_component(template_id,component_id,quantity,quantity_parameter_id,when_values,selection_group,required_group)
   VALUES(tpl,node,(item->>'quantity')::numeric,(SELECT id FROM configuration_parameter WHERE template_id=tpl AND code=item->>'parameter'),item->'when',item->>'group',true);
 END LOOP;
 INSERT INTO schema_migration(name,details) VALUES('orders-component-cost-v1',jsonb_build_object('basis','component prices','rules',jsonb_array_length(data->'rules')));
END $cost$;
