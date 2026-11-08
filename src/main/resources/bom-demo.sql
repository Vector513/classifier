-- Однократная загрузка комплектов электроники. Составы и отметка создаются атомарно.
DO $seed$
DECLARE
    pcs BIGINT;
    meters BIGINT;
    root_id BIGINT;
    phone_id BIGINT;
    definition RECORD;
    spec_id BIGINT;
    base_spec_id BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS (SELECT 1 FROM schema_migration WHERE name='bom-electronics-demo-v1') THEN
        RETURN;
    END IF;
    -- Зарезервированные коды могли уже использоваться при ручной загрузке.
    -- Сохраняем такие записи как есть, включая переименованные и перемещённые.
    INSERT INTO schema_migration(name) VALUES ('bom-electronics-demo-v1');
    IF EXISTS (SELECT 1 FROM classifier_node WHERE code LIKE 'BOM-EL-%') THEN
        RAISE NOTICE 'Примеры BOM не загружены: коды BOM-EL-* уже заняты';
        RETURN;
    END IF;

    INSERT INTO unit_of_measure(code,name) VALUES ('PCS','штуки'),('M','метры')
        ON CONFLICT(code) DO NOTHING;
    SELECT id INTO STRICT pcs FROM unit_of_measure WHERE code='PCS';
    SELECT id INTO STRICT meters FROM unit_of_measure WHERE code='M';

    SELECT id INTO phone_id
    FROM classifier_node
    WHERE code IN ('PHONES-SAMSUNG-S24','PHONES-SAMSUNG-S24U')
        AND node_kind IN ('MODEL','PRODUCT') AND unit_of_measure_id=pcs
    ORDER BY code LIMIT 1;
    IF phone_id IS NULL THEN
        RAISE NOTICE 'Комплект со смартфоном пропущен: в каталоге нет подходящего Galaxy S24 или S24 Ultra';
    ELSE
        INSERT INTO bom_product(node_id,kind) VALUES (phone_id,'PURCHASED')
            ON CONFLICT(node_id) DO NOTHING;
    END IF;

    INSERT INTO classifier_node(code,name,node_kind,sort_order,created_at,updated_at)
        VALUES ('BOM-EL-DEMO','Комплекты электроники','GROUP',0,now(),now()) RETURNING id INTO root_id;
    INSERT INTO classifier_node(code,name,node_kind,parent_id,sort_order,created_at,updated_at) VALUES
        ('BOM-EL-KITS','Готовые комплекты','GROUP',root_id,0,now(),now()),
        ('BOM-EL-PC-PARTS','Комплектующие для ПК','GROUP',root_id,1,now(),now()),
        ('BOM-EL-HOME-DEVICES','Устройства умного дома','GROUP',root_id,2,now(),now()),
        ('BOM-EL-GAME-DEVICES','Игровая периферия','GROUP',root_id,3,now(),now()),
        ('BOM-EL-SEC-DEVICES','Оборудование видеонаблюдения','GROUP',root_id,4,now(),now()),
        ('BOM-EL-PHONE-ACCESSORIES','Аксессуары для смартфонов','GROUP',root_id,5,now(),now());

    WITH items(code,name,category,kind,unit_id) AS (VALUES
        ('PC-STD','Gaming PC Standard','KITS','ASSEMBLY',pcs),
        ('PC-PRO','Gaming PC Pro','KITS','ASSEMBLY',pcs),
        ('HOME-START','Smart Home Starter Kit','KITS','ASSEMBLY',pcs),
        ('HOME-PRO','Smart Home Pro','KITS','ASSEMBLY',pcs),
        ('GAME-STD','Gaming Set Standard','KITS','ASSEMBLY',pcs),
        ('GAME-PREMIUM','Gaming Set Premium','KITS','ASSEMBLY',pcs),
        ('SEC-STD','Security Camera Kit Standard','KITS','ASSEMBLY',pcs),
        ('SEC-PRO','Security Camera Kit Pro','KITS','ASSEMBLY',pcs),
        ('PHONE-STD','Smartphone Bundle Standard','KITS','ASSEMBLY',pcs),
        ('PHONE-PREMIUM','Smartphone Bundle Premium','KITS','ASSEMBLY',pcs),
        ('PC-CPU','Процессор Ryzen 5','PC-PARTS','PURCHASED',pcs),
        ('PC-BOARD','Материнская плата B650','PC-PARTS','PURCHASED',pcs),
        ('PC-RTX4060','Видеокарта RTX 4060','PC-PARTS','PURCHASED',pcs),
        ('PC-RTX4070','Видеокарта RTX 4070','PC-PARTS','PURCHASED',pcs),
        ('PC-RAM','Оперативная память 8 ГБ','PC-PARTS','PURCHASED',pcs),
        ('PC-SSD','SSD 1 ТБ','PC-PARTS','PURCHASED',pcs),
        ('PC-PSU','Блок питания 650 Вт','PC-PARTS','PURCHASED',pcs),
        ('PC-COOLING','Комплект охлаждения','PC-PARTS','ASSEMBLY',pcs),
        ('PC-FAN','Вентилятор','PC-PARTS','PURCHASED',pcs),
        ('PC-FAN-CONTROL','Контроллер вентиляторов','PC-PARTS','PURCHASED',pcs),
        ('HOME-SPEAKER','Умная колонка','HOME-DEVICES','PURCHASED',pcs),
        ('HOME-BULB','Умная лампочка','HOME-DEVICES','PURCHASED',pcs),
        ('HOME-PLUG','Умная розетка','HOME-DEVICES','PURCHASED',pcs),
        ('HOME-MOTION','Датчик движения','HOME-DEVICES','PURCHASED',pcs),
        ('HOME-DOOR','Датчик открытия двери','HOME-DEVICES','PURCHASED',pcs),
        ('HOME-CAMERA','Камера умного дома','HOME-DEVICES','PURCHASED',pcs),
        ('GAME-KEYBOARD','Игровая клавиатура','GAME-DEVICES','PURCHASED',pcs),
        ('GAME-MOUSE','Игровая мышь','GAME-DEVICES','PURCHASED',pcs),
        ('GAME-HEADPHONES','Игровые наушники','GAME-DEVICES','PURCHASED',pcs),
        ('GAME-MAT','Коврик для мыши','GAME-DEVICES','PURCHASED',pcs),
        ('GAME-WIRELESS-MOUSE','Беспроводная игровая мышь','GAME-DEVICES','PURCHASED',pcs),
        ('GAME-WIRELESS-HEADSET','Беспроводная игровая гарнитура','GAME-DEVICES','PURCHASED',pcs),
        ('GAME-CONTROLLER','Игровой контроллер','GAME-DEVICES','PURCHASED',pcs),
        ('SEC-CAMERA','IP-камера','SEC-DEVICES','PURCHASED',pcs),
        ('SEC-NVR','Видеорегистратор','SEC-DEVICES','PURCHASED',pcs),
        ('SEC-HDD1','Жёсткий диск 1 ТБ','SEC-DEVICES','PURCHASED',pcs),
        ('SEC-HDD2','Жёсткий диск 2 ТБ','SEC-DEVICES','PURCHASED',pcs),
        ('SEC-PSU','Блок питания для видеонаблюдения','SEC-DEVICES','PURCHASED',pcs),
        ('SEC-CABLE','Сетевой кабель для видеонаблюдения','SEC-DEVICES','PURCHASED',meters),
        ('PHONE-GLASS','Защитное стекло','PHONE-ACCESSORIES','PURCHASED',pcs),
        ('PHONE-CASE','Чехол','PHONE-ACCESSORIES','PURCHASED',pcs),
        ('PHONE-CHARGER','Зарядное устройство','PHONE-ACCESSORIES','PURCHASED',pcs),
        ('PHONE-EARBUDS','Беспроводные наушники','PHONE-ACCESSORIES','PURCHASED',pcs),
        ('PHONE-POWERBANK','Внешний аккумулятор','PHONE-ACCESSORIES','PURCHASED',pcs)
    ), inserted AS (
        INSERT INTO classifier_node(code,name,node_kind,parent_id,unit_of_measure_id,sort_order,created_at,updated_at)
        SELECT 'BOM-EL-' || i.code,i.name,'PRODUCT',g.id,i.unit_id,0,now(),now()
        FROM items i JOIN classifier_node g ON g.code='BOM-EL-' || i.category
        WHERE phone_id IS NOT NULL OR i.code NOT LIKE 'PHONE-%'
        RETURNING id,code
    )
    INSERT INTO bom_product(node_id,kind)
    SELECT n.id,i.kind FROM inserted n JOIN items i ON n.code='BOM-EL-' || i.code;

    -- Сначала утверждаем вложенный состав, затем базы, затем версии с локальными отличиями.
    FOR definition IN
        SELECT * FROM (VALUES
            (1,'PC-COOLING',1,NULL,'Два вентилятора и контроллер'),
            (2,'PC-STD',1,NULL,'Базовая сборка игрового компьютера'),
            (2,'HOME-START',1,NULL,'Стартовый комплект умного дома'),
            (2,'GAME-STD',1,NULL,'Базовый набор игровой периферии'),
            (2,'SEC-STD',1,NULL,'Комплект видеонаблюдения с двумя IP-камерами'),
            (2,'PHONE-STD',1,NULL,'Galaxy и аксессуары для защиты и зарядки'),
            (3,'PC-PRO',1,'PC-STD','RTX 4070 вместо RTX 4060, четыре модуля памяти вместо двух'),
            (3,'HOME-PRO',1,'HOME-START','Пять лампочек, четыре розетки, два дополнительных датчика движения и камера'),
            (3,'GAME-PREMIUM',1,'GAME-STD','Беспроводная мышь и гарнитура, добавлен игровой контроллер'),
            (3,'SEC-PRO',1,'SEC-STD','Четыре IP-камеры, диск 2 ТБ и 40 м кабеля'),
            (3,'PHONE-PREMIUM',1,'PHONE-STD','Добавлены беспроводные наушники и внешний аккумулятор'),
            (4,'GAME-STD',2,'GAME-STD','Учебный черновик: исключён коврик, добавлен игровой контроллер')
        ) AS s(step,code,revision,base_code,description)
        WHERE phone_id IS NOT NULL OR s.code NOT LIKE 'PHONE-%'
        ORDER BY step,code
    LOOP
        base_spec_id := NULL;
        IF definition.base_code IS NOT NULL THEN
            SELECT s.id INTO STRICT base_spec_id FROM bom_specification s
                JOIN classifier_node n ON n.id=s.product_id
                WHERE n.code='BOM-EL-' || definition.base_code AND s.revision=1;
        END IF;
        INSERT INTO bom_specification(product_id,revision,base_id,description)
        SELECT id,definition.revision,base_spec_id,definition.description
        FROM classifier_node WHERE code='BOM-EL-' || definition.code
        RETURNING id INTO STRICT spec_id;

        INSERT INTO bom_line(specification_id,position,component_id,component_specification_id,quantity,excluded)
        SELECT spec_id,l.position,
            CASE WHEN l.component='GALAXY' THEN phone_id ELSE n.id END,
            child.id,l.quantity,l.component IS NULL
        FROM (VALUES
            ('PC-COOLING',1,1,'PC-FAN',2,false),
            ('PC-COOLING',1,2,'PC-FAN-CONTROL',1,false),
            ('PC-STD',1,1,'PC-CPU',1,false),
            ('PC-STD',1,2,'PC-BOARD',1,false),
            ('PC-STD',1,3,'PC-RTX4060',1,false),
            ('PC-STD',1,4,'PC-RAM',2,false),
            ('PC-STD',1,5,'PC-SSD',1,false),
            ('PC-STD',1,6,'PC-PSU',1,false),
            ('PC-STD',1,7,'PC-COOLING',1,true),
            ('PC-PRO',1,3,'PC-RTX4070',1,false),
            ('PC-PRO',1,4,'PC-RAM',4,false),
            ('HOME-START',1,1,'HOME-SPEAKER',1,false),
            ('HOME-START',1,2,'HOME-BULB',3,false),
            ('HOME-START',1,3,'HOME-PLUG',2,false),
            ('HOME-START',1,4,'HOME-MOTION',1,false),
            ('HOME-START',1,5,'HOME-DOOR',1,false),
            ('HOME-PRO',1,2,'HOME-BULB',5,false),
            ('HOME-PRO',1,3,'HOME-PLUG',4,false),
            ('HOME-PRO',1,4,'HOME-MOTION',3,false),
            ('HOME-PRO',1,6,'HOME-CAMERA',1,false),
            ('GAME-STD',1,1,'GAME-KEYBOARD',1,false),
            ('GAME-STD',1,2,'GAME-MOUSE',1,false),
            ('GAME-STD',1,3,'GAME-HEADPHONES',1,false),
            ('GAME-STD',1,4,'GAME-MAT',1,false),
            ('GAME-PREMIUM',1,2,'GAME-WIRELESS-MOUSE',1,false),
            ('GAME-PREMIUM',1,3,'GAME-WIRELESS-HEADSET',1,false),
            ('GAME-PREMIUM',1,5,'GAME-CONTROLLER',1,false),
            ('GAME-STD',2,4,NULL,NULL,false),
            ('GAME-STD',2,5,'GAME-CONTROLLER',1,false),
            ('SEC-STD',1,1,'SEC-CAMERA',2,false),
            ('SEC-STD',1,2,'SEC-NVR',1,false),
            ('SEC-STD',1,3,'SEC-HDD1',1,false),
            ('SEC-STD',1,4,'SEC-PSU',1,false),
            ('SEC-STD',1,5,'SEC-CABLE',20,false),
            ('SEC-PRO',1,1,'SEC-CAMERA',4,false),
            ('SEC-PRO',1,3,'SEC-HDD2',1,false),
            ('SEC-PRO',1,5,'SEC-CABLE',40,false),
            ('PHONE-STD',1,1,'GALAXY',1,false),
            ('PHONE-STD',1,2,'PHONE-GLASS',1,false),
            ('PHONE-STD',1,3,'PHONE-CASE',1,false),
            ('PHONE-STD',1,4,'PHONE-CHARGER',1,false),
            ('PHONE-PREMIUM',1,5,'PHONE-EARBUDS',1,false),
            ('PHONE-PREMIUM',1,6,'PHONE-POWERBANK',1,false)
        ) AS l(code,revision,position,component,quantity,nested)
        LEFT JOIN classifier_node n ON n.code='BOM-EL-' || l.component
        LEFT JOIN bom_specification child ON l.nested AND child.product_id=n.id
            AND child.revision=1 AND child.status='RELEASED'
        WHERE l.code=definition.code AND l.revision=definition.revision;

        IF definition.step < 4 THEN
            UPDATE bom_specification SET status='RELEASED' WHERE id=spec_id;
        END IF;
    END LOOP;
END
$seed$;
