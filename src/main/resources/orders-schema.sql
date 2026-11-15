-- ЛР7. Физическая модель точно по сохранённой ER-диаграмме.
-- Варианты самостоятельны; прежние таблицы не изменяются и не очищаются.
SELECT pg_advisory_xact_lock(21001);
^^^
CREATE TABLE IF NOT EXISTS customer (
 id BIGSERIAL PRIMARY KEY,
 name VARCHAR(255) NOT NULL CHECK (btrim(name)<>''),
 contact_person VARCHAR(255), phone VARCHAR(40), email VARCHAR(254),
 CHECK (nullif(btrim(phone),'') IS NOT NULL OR nullif(btrim(email),'') IS NOT NULL)
);
^^^
CREATE TABLE IF NOT EXISTS customer_order (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES customer(id),
 order_number VARCHAR(100) NOT NULL UNIQUE CHECK (btrim(order_number)<>''),
 order_date DATE NOT NULL, requested_date DATE,
 CHECK (requested_date IS NULL OR requested_date>=order_date)
);
^^^
CREATE TABLE IF NOT EXISTS product_template (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL UNIQUE REFERENCES classifier_node(id),
 code VARCHAR(100) NOT NULL UNIQUE CHECK (btrim(code)<>''),
 name VARCHAR(255) NOT NULL CHECK (btrim(name)<>'')
);
^^^
CREATE TABLE IF NOT EXISTS product_variant (
 id BIGSERIAL PRIMARY KEY,
 template_id BIGINT NOT NULL REFERENCES product_template(id),
 code VARCHAR(100) NOT NULL UNIQUE CHECK (btrim(code)<>''),
 name VARCHAR(255) NOT NULL CHECK (btrim(name)<>'')
);
^^^
CREATE TABLE IF NOT EXISTS configuration_parameter (
 id BIGSERIAL PRIMARY KEY,
 template_id BIGINT NOT NULL REFERENCES product_template(id),
 code VARCHAR(100) NOT NULL CHECK (code ~ '^[A-Za-z_][A-Za-z0-9_]*$'),
 name VARCHAR(255) NOT NULL CHECK (btrim(name)<>''),
 data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('NUMBER','INTEGER','STRING','BOOLEAN','ENUM')),
 unit_of_measure_id BIGINT REFERENCES unit_of_measure(id),
 enumeration_id BIGINT REFERENCES enumeration(id),
 required BOOLEAN NOT NULL DEFAULT true,
 min_value NUMERIC, max_value NUMERIC,
 UNIQUE (template_id,code),
 CHECK ((data_type='ENUM')=(enumeration_id IS NOT NULL)),
 CHECK (data_type IN ('NUMBER','INTEGER') OR (min_value IS NULL AND max_value IS NULL AND unit_of_measure_id IS NULL)),
 CHECK (min_value IS NULL OR max_value IS NULL OR min_value<=max_value)
);
^^^
CREATE TABLE IF NOT EXISTS variant_parameter_value (
 id BIGSERIAL PRIMARY KEY,
 variant_id BIGINT NOT NULL REFERENCES product_variant(id) ON DELETE CASCADE,
 parameter_id BIGINT NOT NULL REFERENCES configuration_parameter(id),
 value JSONB,
 enumeration_value_id BIGINT REFERENCES enumeration_value(id),
 UNIQUE (variant_id,parameter_id),
 CHECK (num_nonnulls(value,enumeration_value_id)=1),
 CHECK (value IS NULL OR jsonb_typeof(value) IN ('number','string','boolean'))
);
^^^
CREATE TABLE IF NOT EXISTS order_line (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
 product_id BIGINT REFERENCES classifier_node(id),
 variant_id BIGINT REFERENCES product_variant(id),
 line_number INTEGER NOT NULL CHECK (line_number>0),
 quantity INTEGER NOT NULL CHECK (quantity>0),
 note TEXT,
 UNIQUE (order_id,line_number),
 CHECK (num_nonnulls(product_id,variant_id)=1)
);
^^^
CREATE TABLE IF NOT EXISTS constraint_function (
 id BIGSERIAL PRIMARY KEY,
 root_expression_id BIGINT NOT NULL UNIQUE,
 code VARCHAR(100) NOT NULL UNIQUE CHECK (code ~ '^[A-Za-z_][A-Za-z0-9_]*$'),
 name VARCHAR(255) NOT NULL CHECK (btrim(name)<>''),
 result_type VARCHAR(20) NOT NULL CHECK (result_type IN ('NUMBER','INTEGER','STRING','BOOLEAN','ENUM'))
);
^^^
CREATE TABLE IF NOT EXISTS formal_parameter (
 id BIGSERIAL PRIMARY KEY,
 function_id BIGINT NOT NULL REFERENCES constraint_function(id),
 argument_number INTEGER NOT NULL CHECK (argument_number>0),
 code VARCHAR(100) NOT NULL CHECK (code ~ '^[A-Za-z_][A-Za-z0-9_]*$'),
 data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('NUMBER','INTEGER','STRING','BOOLEAN','ENUM')),
 UNIQUE (function_id,argument_number), UNIQUE (function_id,code)
);
^^^
ALTER TABLE formal_parameter ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
^^^
CREATE TABLE IF NOT EXISTS expression (
 id BIGSERIAL PRIMARY KEY,
 parent_expression_id BIGINT REFERENCES expression(id) ON DELETE CASCADE,
 parameter_id BIGINT REFERENCES configuration_parameter(id),
 function_id BIGINT REFERENCES constraint_function(id),
 formal_parameter_id BIGINT REFERENCES formal_parameter(id),
 kind VARCHAR(20) NOT NULL CHECK (kind IN ('CONST','PARAMETER','FORMAL','OPERATOR','CALL')),
 operator VARCHAR(30), constant_value JSONB,
 arg_position INTEGER,
 UNIQUE (parent_expression_id,arg_position),
 CHECK ((parent_expression_id IS NULL)=(arg_position IS NULL)),
 CHECK (arg_position IS NULL OR arg_position>0),
 CHECK (parent_expression_id IS DISTINCT FROM id),
 CHECK (
  (kind='CONST' AND constant_value IS NOT NULL AND jsonb_typeof(constant_value) IN ('number','string','boolean','array') AND num_nonnulls(parameter_id,function_id,formal_parameter_id,operator)=0) OR
  (kind='PARAMETER' AND parameter_id IS NOT NULL AND num_nonnulls(function_id,formal_parameter_id,operator,constant_value)=0) OR
  (kind='FORMAL' AND formal_parameter_id IS NOT NULL AND num_nonnulls(parameter_id,function_id,operator,constant_value)=0) OR
  (kind='CALL' AND function_id IS NOT NULL AND num_nonnulls(parameter_id,formal_parameter_id,operator,constant_value)=0) OR
  (kind='OPERATOR' AND operator IS NOT NULL AND num_nonnulls(parameter_id,function_id,formal_parameter_id,constant_value)=0)
 )
);
^^^
CREATE TABLE IF NOT EXISTS configuration_rule (
 id BIGSERIAL PRIMARY KEY,
 template_id BIGINT NOT NULL REFERENCES product_template(id),
 root_expression_id BIGINT NOT NULL UNIQUE REFERENCES expression(id),
 code VARCHAR(100) NOT NULL CHECK (code ~ '^[A-Za-z_][A-Za-z0-9_]*$'),
 name VARCHAR(255) NOT NULL CHECK (btrim(name)<>''),
 error_message TEXT NOT NULL CHECK (btrim(error_message)<>''),
 UNIQUE (template_id,code)
);
^^^
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='fk_constraint_function_root') THEN
  ALTER TABLE constraint_function ADD CONSTRAINT fk_constraint_function_root FOREIGN KEY(root_expression_id) REFERENCES expression(id);
 END IF;
END $$;
^^^
CREATE INDEX IF NOT EXISTS idx_expression_parent ON expression(parent_expression_id);
CREATE INDEX IF NOT EXISTS idx_configuration_parameter_template ON configuration_parameter(template_id);
CREATE INDEX IF NOT EXISTS idx_configuration_rule_template ON configuration_rule(template_id);
CREATE INDEX IF NOT EXISTS idx_product_variant_template ON product_variant(template_id);
CREATE INDEX IF NOT EXISTS idx_variant_parameter_variant ON variant_parameter_value(variant_id);
CREATE INDEX IF NOT EXISTS idx_order_line_variant ON order_line(variant_id);

^^^
COMMENT ON TABLE customer IS 'Заказчик: наименование и хотя бы один способ связи';

^^^
COMMENT ON TABLE customer_order IS 'Заказ на готовые изделия и/или проверенные варианты исполнения';

^^^
COMMENT ON TABLE product_template IS 'Профиль конфигурирования одной записи классификатора; код и имя независимы от справочника';

^^^
COMMENT ON TABLE configuration_parameter IS 'Параметр шаблона: тип, единица, перечисление, обязательность, числовые границы';

^^^
COMMENT ON TABLE product_variant IS 'Самостоятельный вариант исполнения шаблона; не является узлом классификатора';

^^^
COMMENT ON TABLE variant_parameter_value IS 'Факт варианта: JSONB-скаляр либо ссылка на значение перечисления; ровно один на параметр';

^^^
COMMENT ON TABLE order_line IS 'Позиция заказа: ровно одно из готового изделия и варианта исполнения; целое положительное количество';

^^^
COMMENT ON TABLE constraint_function IS 'Пользовательская функция: сигнатура формальных аргументов, тип результата и корень тела';

^^^
COMMENT ON TABLE formal_parameter IS 'Формальный аргумент функции; argument_number задаёт порядок подстановки фактических значений';

^^^
COMMENT ON TABLE expression IS 'Узел дерева: CONST, PARAMETER, FORMAL, OPERATOR или CALL; arg_position задаёт порядок аргументов';

^^^
COMMENT ON TABLE configuration_rule IS 'Предикат допустимости вариантов одного шаблона с сообщением о нарушении';

^^^
COMMENT ON COLUMN variant_parameter_value.value IS 'JSONB number/string/boolean для не-ENUM; SQL NULL для ENUM';
^^^
COMMENT ON COLUMN expression.constant_value IS 'JSONB-скаляр либо список констант для IN; не содержит исполняемого SQL';
^^^
COMMENT ON COLUMN expression.parent_expression_id IS 'Дерево принадлежит ровно одному корню правила или функции; циклы запрещены';
