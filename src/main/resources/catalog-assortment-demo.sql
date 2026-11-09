-- Однократное пополнение типовых разделов и моделей, у которых ещё нет товаров.
DO $demo$
DECLARE
    cable_id BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS (SELECT 1 FROM schema_migration WHERE name='catalog-assortment-demo-v1') THEN
        RETURN;
    END IF;

    WITH examples(parent_code,parent_kind,code,name,unit_code,price) AS (VALUES
        ('LAPTOPS-ASUS','GROUP','ASUS-NOTEBOOK','Ноутбук ASUS VivoBook','PCS',64990),
        ('TABLETS-APPLE','GROUP','IPAD-WIFI','Планшет iPad Wi-Fi','PCS',49990),
        ('TABLETS-SAMSUNG','GROUP','GALAXY-TAB-WIFI','Планшет Galaxy Tab Wi-Fi','PCS',34990),
        ('AUDIO-HEADPHONES','GROUP','BT-HEADPHONES','Беспроводные накладные наушники','PCS',5990),
        ('AUDIO-SPEAKERS','GROUP','BT-SPEAKER','Портативная Bluetooth-колонка','PCS',3990),
        ('ACC-CASES','GROUP','CLEAR-CASE','Прозрачный чехол для Galaxy S24','PCS',990),
        ('ACC-CHARGERS','GROUP','USB-C-CHARGER','Сетевое зарядное устройство USB-C','PCS',1490),
        ('ACC-CABLES','GROUP','USB-C-CABLE','Кабель USB-C, упаковка из 2 шт.','PACK',890),
        ('PHONES-APPLE-IP16PRO','MODEL','IP16PRO-256','iPhone 16 Pro, 256 ГБ',NULL,119990),
        ('PHONES-APPLE-IP15','MODEL','IP15-128','iPhone 15, 128 ГБ',NULL,69990),
        ('PHONES-SAMSUNG-S24','MODEL','S24-128','Galaxy S24, 128 ГБ',NULL,79990),
        ('PHONES-SAMSUNG-S24U','MODEL','S24U-256','Galaxy S24 Ultra, 256 ГБ',NULL,119990),
        ('PHONES-XIAOMI-14','MODEL','XIAOMI14-256','Xiaomi 14, 256 ГБ',NULL,69990),
        ('LAPTOPS-APPLE-AIR','MODEL','MACBOOK-AIR-256','MacBook Air M3, SSD 256 ГБ',NULL,109990),
        ('LAPTOPS-APPLE-PRO','MODEL','MACBOOK-PRO-512','MacBook Pro, SSD 512 ГБ',NULL,159990),
        ('LAPTOPS-LENOVO-TP','MODEL','THINKPAD-512','ThinkPad X1 Carbon, SSD 512 ГБ',NULL,119990),
        ('LAPTOPS-LENOVO-YOGA','MODEL','YOGA-512','Yoga 9i, SSD 512 ГБ',NULL,109990)
    ), inserted AS (
        INSERT INTO classifier_node(code,name,parent_id,node_kind,unit_of_measure_id,sort_order,created_at,updated_at)
        SELECT 'ASSORT-' || e.code,e.name,parent.id,'PRODUCT',
            CASE WHEN parent.node_kind='MODEL' THEN parent.unit_of_measure_id ELSE u.id END,
            0,now(),now()
        FROM examples e
        JOIN classifier_node parent ON parent.code=e.parent_code AND parent.node_kind=e.parent_kind
        LEFT JOIN unit_of_measure u ON u.code=e.unit_code
        WHERE (parent.node_kind='MODEL' OR u.id IS NOT NULL)
          AND NOT EXISTS (
              SELECT 1 FROM classifier_node child
              WHERE child.parent_id=parent.id AND child.node_kind IN ('MODEL','PRODUCT')
          )
        ON CONFLICT(code) DO NOTHING
        RETURNING id,code,parent_id
    ), prices AS (
        INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at)
        SELECT n.id,p.id,e.price,now(),now()
        FROM inserted n
        JOIN examples e ON n.code='ASSORT-' || e.code
        JOIN numeric_parameter p ON p.code='PRICE'
        WHERE catalog_numeric_applicable(n.parent_id,p.id)
          AND (p.min_value IS NULL OR e.price>=p.min_value)
          AND (p.max_value IS NULL OR e.price<=p.max_value)
    )
    SELECT id INTO cable_id FROM inserted WHERE code='ASSORT-USB-C-CABLE';

    INSERT INTO node_attribute_value(classifier_node_id,enumeration_id,enumeration_value_id,created_at,updated_at)
    SELECT cable_id,e.id,v.id,now(),now()
    FROM enumeration e JOIN enumeration_value v ON v.enumeration_id=e.id
    WHERE e.code='CABLE-CONNECTORS' AND v.code='USB-C'
      AND cable_id IS NOT NULL AND catalog_enum_allowed(cable_id,e.id,v.id);

    INSERT INTO schema_migration(name) VALUES ('catalog-assortment-demo-v1');
END
$demo$;
