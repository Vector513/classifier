-- Учебные значения для уже существующих товаров. Единица товара берётся из классификатора.
DO $details$
DECLARE
    item RECORD;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS (SELECT 1 FROM schema_migration WHERE name='catalog-product-details-v1')
       OR NOT EXISTS (SELECT 1 FROM schema_migration WHERE name='catalog-bom-layout-v1') THEN
        RETURN;
    END IF;

    INSERT INTO unit_of_measure(code,name) VALUES ('W','ватты') ON CONFLICT(code) DO NOTHING;
    INSERT INTO numeric_parameter(code,name,description,min_value,max_value,unit_of_measure_id,created_at,updated_at)
    SELECT p.code,p.name,p.description,1,p.max_value,u.id,now(),now()
    FROM (VALUES ('DEMO-POWER','Мощность','Номинальная мощность устройства',3000,'W'),
                 ('DEMO-STORAGE','Ёмкость накопителя','Номинальная ёмкость накопителя',100000,'GB'))
        AS p(code,name,description,max_value,unit_code)
    JOIN unit_of_measure u ON u.code=p.unit_code ON CONFLICT(code) DO NOTHING;

    INSERT INTO enumeration_class(code,name,created_at,updated_at)
    VALUES ('DEMO-PRODUCT-DETAILS','Характеристики электроники',now(),now()) ON CONFLICT(code) DO NOTHING;
    INSERT INTO enumeration(code,name,enumeration_class_id,created_at,updated_at)
    SELECT e.code,e.name,c.id,now(),now()
    FROM (VALUES ('DEMO-INTERFACE','Подключение и интерфейс'),
                 ('DEMO-ACCESSORY-MATERIAL','Материал аксессуара'),
                 ('DEMO-KIT-LEVEL','Комплектация')) AS e(code,name)
    JOIN enumeration_class c ON c.code='DEMO-PRODUCT-DETAILS'
    ON CONFLICT(code) DO NOTHING;

    INSERT INTO enumeration_value(code,name,enumeration_id,sort_order,created_at,updated_at)
    SELECT v.code,v.name,e.id,0,now(),now()
    FROM (VALUES
        ('DEMO-INTERFACE','AM5','AM5'),('DEMO-INTERFACE','DDR5','DDR5'),
        ('DEMO-INTERFACE','PCIE','PCI Express'),('DEMO-INTERFACE','M2','M.2 NVMe'),
        ('DEMO-INTERFACE','ATX','ATX'),('DEMO-INTERFACE','PWM','4-pin PWM'),
        ('DEMO-INTERFACE','USB','USB'),('DEMO-INTERFACE','USB-C','USB-C'),
        ('DEMO-INTERFACE','BT','Bluetooth'),('DEMO-INTERFACE','WIFI','Wi-Fi'),
        ('DEMO-INTERFACE','ZIGBEE','Zigbee'),('DEMO-INTERFACE','RJ45','Ethernet RJ-45'),
        ('DEMO-INTERFACE','SATA','SATA'),('DEMO-INTERFACE','DC','Разъём питания DC'),
        ('DEMO-INTERFACE','JACK','Jack 3,5 мм'),
        ('DEMO-ACCESSORY-MATERIAL','TPU','Термополиуретан'),
        ('DEMO-ACCESSORY-MATERIAL','GLASS','Закалённое стекло'),
        ('DEMO-ACCESSORY-MATERIAL','FABRIC','Ткань'),
        ('DEMO-KIT-LEVEL','STANDARD','Standard'),('DEMO-KIT-LEVEL','PRO','Pro'),
        ('DEMO-KIT-LEVEL','STARTER','Starter'),('DEMO-KIT-LEVEL','PREMIUM','Premium')
    ) AS v(enumeration_code,code,name)
    JOIN enumeration e ON e.code=v.enumeration_code
    ON CONFLICT(enumeration_id,code) DO NOTHING;

    FOR item IN
        SELECT n.id,e.id AS enum_id,v.id AS option_id,p.price,p.mass,p.extra_code,p.extra_value
        FROM (VALUES
            -- Цена и масса указаны на одну единицу товара. Кабель видеонаблюдения учтён на метр.
            ('BOM-EL-PC-STD',84990,9.8,'DEMO-KIT-LEVEL','STANDARD',NULL,NULL),
            ('BOM-EL-PC-PRO',114990,10.4,'DEMO-KIT-LEVEL','PRO',NULL,NULL),
            ('BOM-EL-HOME-START',9990,1.35,'DEMO-KIT-LEVEL','STARTER',NULL,NULL),
            ('BOM-EL-HOME-PRO',16990,2.3,'DEMO-KIT-LEVEL','PRO',NULL,NULL),
            ('BOM-EL-GAME-STD',7990,1.42,'DEMO-KIT-LEVEL','STANDARD',NULL,NULL),
            ('BOM-EL-GAME-PREMIUM',13990,1.61,'DEMO-KIT-LEVEL','PREMIUM',NULL,NULL),
            ('BOM-EL-SEC-STD',16990,2.9,'DEMO-KIT-LEVEL','STANDARD',NULL,NULL),
            ('BOM-EL-SEC-PRO',27990,4.8,'DEMO-KIT-LEVEL','PRO',NULL,NULL),
            ('BOM-EL-PHONE-STD',82990,0.48,'DEMO-KIT-LEVEL','STANDARD',NULL,NULL),
            ('BOM-EL-PHONE-PREMIUM',91990,0.78,'DEMO-KIT-LEVEL','PREMIUM',NULL,NULL),
            ('BOM-EL-PC-CPU',15990,0.06,'DEMO-INTERFACE','AM5',NULL,NULL),
            ('BOM-EL-PC-BOARD',15990,0.9,'DEMO-INTERFACE','AM5',NULL,NULL),
            ('BOM-EL-PC-RTX4060',32990,0.65,'DEMO-INTERFACE','PCIE',NULL,NULL),
            ('BOM-EL-PC-RTX4070',59990,0.95,'DEMO-INTERFACE','PCIE',NULL,NULL),
            ('BOM-EL-PC-RAM',2790,0.04,'DEMO-INTERFACE','DDR5','RAM',8),
            ('BOM-EL-PC-SSD',6990,0.01,'DEMO-INTERFACE','M2','DEMO-STORAGE',1000),
            ('BOM-EL-PC-PSU',6990,1.45,'DEMO-INTERFACE','ATX','DEMO-POWER',650),
            ('BOM-EL-PC-COOLING',2990,0.37,'DEMO-INTERFACE','PWM',NULL,NULL),
            ('BOM-EL-PC-FAN',990,0.15,'DEMO-INTERFACE','PWM',NULL,NULL),
            ('BOM-EL-PC-FAN-CONTROL',1190,0.07,'DEMO-INTERFACE','PWM',NULL,NULL),
            ('BOM-EL-HOME-SPEAKER',4990,0.52,'DEMO-INTERFACE','WIFI','DEMO-POWER',10),
            ('BOM-EL-HOME-BULB',690,0.065,'DEMO-INTERFACE','WIFI','DEMO-POWER',9),
            ('BOM-EL-HOME-PLUG',990,0.09,'DEMO-INTERFACE','WIFI',NULL,NULL),
            ('BOM-EL-HOME-MOTION',1190,0.05,'DEMO-INTERFACE','ZIGBEE',NULL,NULL),
            ('BOM-EL-HOME-DOOR',990,0.04,'DEMO-INTERFACE','ZIGBEE',NULL,NULL),
            ('BOM-EL-HOME-CAMERA',2990,0.25,'DEMO-INTERFACE','WIFI',NULL,NULL),
            ('BOM-EL-GAME-KEYBOARD',3290,0.74,'DEMO-INTERFACE','USB',NULL,NULL),
            ('BOM-EL-GAME-MOUSE',1690,0.095,'DEMO-INTERFACE','USB',NULL,NULL),
            ('BOM-EL-GAME-HEADPHONES',2590,0.27,'DEMO-INTERFACE','JACK',NULL,NULL),
            ('BOM-EL-GAME-MAT',590,0.2,'DEMO-ACCESSORY-MATERIAL','FABRIC',NULL,NULL),
            ('BOM-EL-GAME-WIRELESS-MOUSE',2790,0.09,'DEMO-INTERFACE','BT',NULL,NULL),
            ('BOM-EL-GAME-WIRELESS-HEADSET',4990,0.31,'DEMO-INTERFACE','BT',NULL,NULL),
            ('BOM-EL-GAME-CONTROLLER',3490,0.22,'DEMO-INTERFACE','BT',NULL,NULL),
            ('BOM-EL-SEC-CAMERA',3990,0.37,'DEMO-INTERFACE','RJ45',NULL,NULL),
            ('BOM-EL-SEC-NVR',5990,0.65,'DEMO-INTERFACE','RJ45',NULL,NULL),
            ('BOM-EL-SEC-HDD1',4990,0.42,'DEMO-INTERFACE','SATA','DEMO-STORAGE',1000),
            ('BOM-EL-SEC-HDD2',6490,0.45,'DEMO-INTERFACE','SATA','DEMO-STORAGE',2000),
            ('BOM-EL-SEC-PSU',990,0.24,'DEMO-INTERFACE','DC','DEMO-POWER',24),
            ('BOM-EL-SEC-CABLE',65,0.042,'DEMO-INTERFACE','RJ45',NULL,NULL),
            ('BOM-EL-PHONE-GLASS',590,0.012,'DEMO-ACCESSORY-MATERIAL','GLASS',NULL,NULL),
            ('BOM-EL-PHONE-CASE',990,0.035,'DEMO-ACCESSORY-MATERIAL','TPU',NULL,NULL),
            ('BOM-EL-PHONE-CHARGER',1490,0.075,'DEMO-INTERFACE','USB-C','DEMO-POWER',25),
            ('BOM-EL-PHONE-EARBUDS',3990,0.052,'DEMO-INTERFACE','BT',NULL,NULL),
            ('BOM-EL-PHONE-POWERBANK',2490,0.22,'DEMO-INTERFACE','USB-C','BATTERY',10000),
            ('ASSORT-ASUS-NOTEBOOK',64990,1.7,'DEMO-INTERFACE','WIFI','SCREEN_SIZE',15.6),
            ('ASSORT-IPAD-WIFI',49990,0.48,'DEMO-INTERFACE','WIFI','SCREEN_SIZE',11),
            ('ASSORT-GALAXY-TAB-WIFI',34990,0.49,'DEMO-INTERFACE','WIFI','SCREEN_SIZE',11),
            ('ASSORT-BT-HEADPHONES',5990,0.24,'DEMO-INTERFACE','BT',NULL,NULL),
            ('ASSORT-BT-SPEAKER',3990,0.42,'DEMO-INTERFACE','BT','DEMO-POWER',12),
            ('ASSORT-CLEAR-CASE',990,0.032,'DEMO-ACCESSORY-MATERIAL','TPU',NULL,NULL),
            ('ASSORT-USB-C-CHARGER',1490,0.073,'DEMO-INTERFACE','USB-C','DEMO-POWER',25),
            ('ASSORT-USB-C-CABLE',890,0.085,'CABLE-CONNECTORS','USB-C',NULL,NULL),
            ('ASSORT-IP16PRO-256',119990,0.199,'PHONE-STORAGE','GB256','SCREEN_SIZE',6.3),
            ('ASSORT-IP15-128',69990,0.171,'PHONE-STORAGE','GB128','SCREEN_SIZE',6.1),
            ('ASSORT-S24-128',79990,0.167,'PHONE-STORAGE','GB128','RAM',8),
            ('ASSORT-S24U-256',119990,0.232,'PHONE-STORAGE','GB256','RAM',12),
            ('ASSORT-XIAOMI14-256',69990,0.193,'PHONE-STORAGE','GB256','RAM',12),
            ('ASSORT-MACBOOK-AIR-256',109990,1.24,'LAPTOP-STORAGE','SSD256','SCREEN_SIZE',13.6),
            ('ASSORT-MACBOOK-PRO-512',159990,1.55,'LAPTOP-STORAGE','SSD512','SCREEN_SIZE',14.2),
            ('ASSORT-THINKPAD-512',119990,1.12,'LAPTOP-STORAGE','SSD512','SCREEN_SIZE',14),
            ('ASSORT-YOGA-512',109990,1.4,'LAPTOP-STORAGE','SSD512','SCREEN_SIZE',14)
        ) AS p(code,price,mass,enum_code,option_code,extra_code,extra_value)
        JOIN classifier_node n ON n.code=p.code AND n.node_kind='PRODUCT'
        LEFT JOIN enumeration e ON e.code=p.enum_code
        LEFT JOIN enumeration_value v ON v.enumeration_id=e.id AND v.code=p.option_code
    LOOP
        INSERT INTO node_enumeration(classifier_node_id,enumeration_id)
        SELECT item.id,item.enum_id
        WHERE item.option_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM catalog_ancestors(item.id) a JOIN node_enumeration b ON b.classifier_node_id=a.id
            WHERE b.enumeration_id=item.enum_id
        ) ON CONFLICT DO NOTHING;
        INSERT INTO node_attribute_value(classifier_node_id,enumeration_id,enumeration_value_id,created_at,updated_at)
        SELECT item.id,item.enum_id,item.option_id,now(),now()
        WHERE catalog_enum_allowed(item.id,item.enum_id,item.option_id)
        ON CONFLICT(classifier_node_id,enumeration_id) DO NOTHING;

        INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,created_at)
        SELECT item.id,p.id,now() FROM numeric_parameter p WHERE p.code=item.extra_code AND NOT EXISTS (
            SELECT 1 FROM catalog_ancestors(item.id) a JOIN node_numeric_parameter b ON b.classifier_node_id=a.id
            WHERE b.numeric_parameter_id=p.id
        ) ON CONFLICT DO NOTHING;
        INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at)
        SELECT item.id,p.id,v.value,now(),now()
        FROM (VALUES ('PRICE',item.price),('WEIGHT',item.mass),(item.extra_code,item.extra_value)) AS v(code,value)
        JOIN numeric_parameter p ON p.code=v.code
        WHERE catalog_numeric_applicable(item.id,p.id)
          AND (p.min_value IS NULL OR v.value>=p.min_value)
          AND (p.max_value IS NULL OR v.value<=p.max_value)
        ON CONFLICT(classifier_node_id,numeric_parameter_id) DO NOTHING;
    END LOOP;

    INSERT INTO schema_migration(name) VALUES ('catalog-product-details-v1');
END
$details$;
