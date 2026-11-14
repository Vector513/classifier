-- Учебный маршрут для каталога демонстрационных комплектов BOM.
DO $seed$
DECLARE
    pc BIGINT;
    kit BIGINT;
    pc_route BIGINT;
    kit_route BIGINT;
    assemble BIGINT;
    inspect BIGINT;
    screwdriver BIGINT;
    station BIGINT;
    fitter BIGINT;
    tester BIGINT;
    step_board BIGINT;
    step_kit BIGINT;
    step_finish BIGINT;
    step_build_kit BIGINT;
    parameter BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS (SELECT 1 FROM schema_migration WHERE name='route-pc-demo-v1') THEN RETURN; END IF;
    SELECT id INTO pc FROM classifier_node WHERE code='BOM-EL-PC-STD';
    SELECT id INTO kit FROM classifier_node WHERE code='BOM-EL-PC-COOLING';
    IF pc IS NULL OR kit IS NULL OR EXISTS (
        SELECT 1 FROM route WHERE product_id IN (pc,kit) OR code IN ('MT-PC-STD','MT-PC-COOLING')
    ) THEN RETURN; END IF;
    INSERT INTO operation_type(code,name) VALUES ('DEMO-ASSEMBLY','Сборка'),('DEMO-TEST','Проверка')
        ON CONFLICT(code) DO NOTHING;
    SELECT id INTO STRICT assemble FROM operation_type WHERE code='DEMO-ASSEMBLY';
    SELECT id INTO STRICT inspect FROM operation_type WHERE code='DEMO-TEST';
    INSERT INTO asset(code,name,kind) VALUES
        ('DEMO-PH2','Крестовая отвёртка PH2','TOOL'),('DEMO-TESTBENCH','Стенд проверки ПК','EQUIPMENT')
        ON CONFLICT(code) DO NOTHING;
    SELECT id INTO STRICT screwdriver FROM asset WHERE code='DEMO-PH2';
    SELECT id INTO STRICT station FROM asset WHERE code='DEMO-TESTBENCH';
    INSERT INTO labor_rate(profession,hourly_rate) VALUES ('Сборщик',600),('Контролёр',720) ON CONFLICT(profession) DO NOTHING;
    SELECT id INTO STRICT fitter FROM labor_rate WHERE profession='Сборщик';
    SELECT id INTO STRICT tester FROM labor_rate WHERE profession='Контролёр';
    INSERT INTO route(code,product_id) VALUES ('MT-PC-STD',pc) RETURNING id INTO pc_route;
    INSERT INTO route(code,product_id) VALUES ('MT-PC-COOLING',kit) RETURNING id INTO kit_route;
    INSERT INTO route_operation(route_id,step_number,name,operation_type_id)
        VALUES (pc_route,10,'Установить плату и комплектующие',assemble) RETURNING id INTO step_board;
    INSERT INTO route_operation(route_id,step_number,name,operation_type_id)
        VALUES (pc_route,20,'Установить комплект охлаждения',assemble) RETURNING id INTO step_kit;
    INSERT INTO route_operation(route_id,step_number,name,operation_type_id)
        VALUES (pc_route,30,'Проверить запуск компьютера',inspect) RETURNING id INTO step_finish;
    INSERT INTO route_operation(route_id,step_number,name,operation_type_id)
        VALUES (kit_route,10,'Собрать вентиляторы и контроллер',assemble) RETURNING id INTO step_build_kit;
    INSERT INTO operation_input(operation_id,resource_id,resource_quantity,output_quantity,source_operation_id)
        VALUES (step_kit,kit,1,1,step_build_kit);
    INSERT INTO operation_input(operation_id,resource_id,resource_quantity,output_quantity)
    SELECT CASE WHEN n.code IN ('BOM-EL-PC-FAN','BOM-EL-PC-FAN-CONTROL') THEN step_build_kit ELSE step_board END,
           n.id,v.quantity,1
    FROM (VALUES ('PC-BOARD',1),('PC-CPU',1),('PC-RTX4060',1),('PC-RAM',2),('PC-SSD',1),('PC-PSU',1),
                 ('PC-FAN',2),('PC-FAN-CONTROL',1)) v(code,quantity)
    JOIN classifier_node n ON n.code='BOM-EL-' || v.code;
    INSERT INTO operation_asset(operation_id,asset_id,quantity)
        VALUES (step_board,screwdriver,1),(step_kit,screwdriver,1),(step_build_kit,screwdriver,1),(step_finish,station,1);
    INSERT INTO operation_labor(operation_id,labor_rate_id,setup_minutes,minutes_per_unit)
        VALUES (step_board,fitter,5,12),(step_kit,fitter,5,2),(step_build_kit,fitter,2,4),(step_finish,tester,2,6);
    SELECT id INTO STRICT parameter FROM numeric_parameter WHERE code='PURCHASE_PRICE';
    INSERT INTO node_numeric_parameter(classifier_node_id,numeric_parameter_id,created_at)
        SELECT DISTINCT resource_id,parameter,now() FROM operation_input WHERE operation_id IN (step_board,step_build_kit)
        ON CONFLICT(classifier_node_id,numeric_parameter_id) DO NOTHING;
    INSERT INTO node_numeric_value(classifier_node_id,numeric_parameter_id,value,created_at,updated_at)
    SELECT n.id,parameter,v.price,now(),now()
    FROM (VALUES ('PC-BOARD',9000),('PC-CPU',11000),('PC-RTX4060',25000),('PC-RAM',1800),('PC-SSD',4500),
                 ('PC-PSU',4200),('PC-FAN',150),('PC-FAN-CONTROL',450)) v(code,price)
    JOIN classifier_node n ON n.code='BOM-EL-' || v.code
    WHERE catalog_numeric_applicable(n.id,parameter)
    ON CONFLICT(classifier_node_id,numeric_parameter_id) DO NOTHING;
    INSERT INTO schema_migration(name) VALUES ('route-pc-demo-v1');
END
$seed$;
