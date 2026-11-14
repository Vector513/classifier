-- ЛР7: проверки типов, исполнение деревьев, создание вариантов и заказов.
CREATE OR REPLACE FUNCTION orders_require(ok BOOLEAN, message TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN IF NOT coalesce(ok,false) THEN RAISE EXCEPTION '%',message USING ERRCODE='23514'; END IF; END $$;
^^^
CREATE OR REPLACE FUNCTION orders_value_type(v JSONB) RETURNS TEXT LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE jsonb_typeof(v) WHEN 'number' THEN 'NUMBER' WHEN 'string' THEN 'STRING' WHEN 'boolean' THEN 'BOOLEAN' WHEN 'array' THEN 'LIST' ELSE 'NULL' END
$$;
^^^
CREATE OR REPLACE FUNCTION orders_matches_type(v JSONB, typ TEXT) RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE typ
 WHEN 'NUMBER' THEN jsonb_typeof(v)='number'
 WHEN 'INTEGER' THEN CASE WHEN jsonb_typeof(v)='number' THEN (v#>>'{}')::numeric=trunc((v#>>'{}')::numeric) ELSE false END
 WHEN 'STRING' THEN jsonb_typeof(v)='string'
 WHEN 'ENUM' THEN jsonb_typeof(v)='string'
 WHEN 'BOOLEAN' THEN jsonb_typeof(v)='boolean' ELSE false END
$$;
^^^
CREATE OR REPLACE FUNCTION orders_compatible(actual TEXT, expected TEXT) RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE AS $$
 SELECT actual=expected OR (actual IN ('NUMBER','INTEGER') AND expected IN ('NUMBER','INTEGER')) OR (actual IN ('STRING','ENUM') AND expected IN ('STRING','ENUM'))
$$;
^^^
CREATE OR REPLACE FUNCTION orders_op(op TEXT) RETURNS TEXT LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE upper(btrim(op)) WHEN '+' THEN 'ADD' WHEN '-' THEN 'SUB' WHEN '*' THEN 'MUL' WHEN '/' THEN 'DIV'
 WHEN '=' THEN 'EQ' WHEN '==' THEN 'EQ' WHEN '!=' THEN 'NE' WHEN '<>' THEN 'NE' WHEN '>' THEN 'GT' WHEN '>=' THEN 'GE' WHEN '<' THEN 'LT' WHEN '<=' THEN 'LE'
 ELSE upper(btrim(op)) END
$$;
^^^
CREATE OR REPLACE FUNCTION orders_operator_type(p_op TEXT, types TEXT[]) RETURNS TEXT LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE op TEXT:=orders_op(p_op); n INT:=coalesce(cardinality(types),0); typ TEXT;
BEGIN
 IF op IN ('ADD','SUB','MUL','DIV','MOD','MIN','MAX','ABS','NEG') THEN
  PERFORM orders_require(CASE WHEN op IN ('ABS','NEG') THEN n=1 WHEN op IN ('SUB','DIV','MOD') THEN n=2 ELSE n>=2 END,'Неверное число аргументов операции '||op);
  FOREACH typ IN ARRAY types LOOP PERFORM orders_require(typ IN ('NUMBER','INTEGER'),'Арифметическая операция требует числовых аргументов'); END LOOP;
  RETURN 'NUMBER';
 ELSIF op IN ('AND','OR','NOT','IMPLIES') THEN
  PERFORM orders_require(CASE WHEN op='NOT' THEN n=1 WHEN op='IMPLIES' THEN n=2 ELSE n>=2 END,'Неверное число логических аргументов');
  FOREACH typ IN ARRAY types LOOP PERFORM orders_require(typ='BOOLEAN','Логическая операция требует булевых аргументов'); END LOOP;
  RETURN 'BOOLEAN';
 ELSIF op IN ('EQ','NE','GT','GE','LT','LE','IN') THEN
  PERFORM orders_require(CASE WHEN op='IN' THEN n>=2 ELSE n=2 END,'Неверное число аргументов сравнения');
  FOR i IN 2..n LOOP
   PERFORM orders_require((op='IN' AND i=2 AND n=2 AND types[i]='LIST') OR orders_compatible(types[i],types[1]),'Несовместимые типы аргументов сравнения');
  END LOOP;
  IF op IN ('GT','GE','LT','LE') THEN PERFORM orders_require(types[1] IN ('NUMBER','INTEGER','STRING','ENUM'),'Тип не поддерживает упорядоченное сравнение'); END IF;
  RETURN 'BOOLEAN';
 END IF;
 RAISE EXCEPTION 'Неизвестный оператор: %',p_op USING ERRCODE='23514';
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_eval_operator(p_op TEXT, args JSONB) RETURNS JSONB LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE op TEXT:=orders_op(p_op); types TEXT[]; n INT:=jsonb_array_length(args); a JSONB; b JSONB; item JSONB; v NUMERIC; x NUMERIC; result BOOLEAN; cmp INT;
BEGIN
 SELECT array_agg(orders_value_type(value) ORDER BY ordinality) INTO types FROM jsonb_array_elements(args) WITH ORDINALITY;
 PERFORM orders_operator_type(op,types);
 a:=args->0;b:=args->1;
 IF op IN ('ADD','SUB','MUL','DIV','MOD','MIN','MAX','ABS','NEG') THEN
  v:=(a#>>'{}')::numeric;
  IF op='ABS' THEN RETURN to_jsonb(abs(v)); END IF;
  IF op='NEG' THEN RETURN to_jsonb(-v); END IF;
  FOR i IN 1..n-1 LOOP
   x:=(args->i#>>'{}')::numeric;
   IF op IN ('DIV','MOD') THEN PERFORM orders_require(x<>0,'Деление на ноль в ограничении'); END IF;
   v:=CASE op WHEN 'ADD' THEN v+x WHEN 'SUB' THEN v-x WHEN 'MUL' THEN v*x WHEN 'DIV' THEN v/x WHEN 'MOD' THEN mod(v,x) WHEN 'MIN' THEN least(v,x) WHEN 'MAX' THEN greatest(v,x) END;
  END LOOP;
  RETURN to_jsonb(v);
 ELSIF op IN ('AND','OR','NOT','IMPLIES') THEN
  IF op='NOT' THEN RETURN to_jsonb(NOT (a#>>'{}')::boolean); END IF;
  IF op='IMPLIES' THEN RETURN to_jsonb(NOT (a#>>'{}')::boolean OR (b#>>'{}')::boolean); END IF;
  result:=op='AND';
  FOR item IN SELECT value FROM jsonb_array_elements(args) LOOP
   IF op='AND' THEN result:=result AND (item#>>'{}')::boolean; ELSE result:=result OR (item#>>'{}')::boolean; END IF;
  END LOOP;
  RETURN to_jsonb(result);
 ELSIF op='IN' THEN
  IF n=2 AND jsonb_typeof(b)='array' THEN
   FOR item IN SELECT value FROM jsonb_array_elements(b) LOOP
    PERFORM orders_require(orders_compatible(orders_value_type(item),orders_value_type(a)),'Неверный тип элемента списка IN');
    IF item=a THEN RETURN 'true'::jsonb; END IF;
   END LOOP;
  ELSE FOR i IN 1..n-1 LOOP IF args->i=a THEN RETURN 'true'::jsonb; END IF; END LOOP; END IF;
  RETURN 'false'::jsonb;
 END IF;
 IF op='EQ' THEN RETURN to_jsonb(a=b); END IF;
 IF op='NE' THEN RETURN to_jsonb(a<>b); END IF;
 IF jsonb_typeof(a)='number' THEN
  v:=(a#>>'{}')::numeric;x:=(b#>>'{}')::numeric;cmp:=CASE WHEN v<x THEN -1 WHEN v>x THEN 1 ELSE 0 END;
 ELSE cmp:=CASE WHEN a#>>'{}'<b#>>'{}' THEN -1 WHEN a#>>'{}'>b#>>'{}' THEN 1 ELSE 0 END; END IF;
 RETURN to_jsonb(CASE op WHEN 'GT' THEN cmp>0 WHEN 'GE' THEN cmp>=0 WHEN 'LT' THEN cmp<0 WHEN 'LE' THEN cmp<=0 END);
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_expression_type(p_id BIGINT,p_template BIGINT,p_function BIGINT,seen BIGINT[] DEFAULT '{}',calls BIGINT[] DEFAULT '{}') RETURNS TEXT LANGUAGE plpgsql STABLE AS $$
DECLARE e expression%ROWTYPE; c RECORD; f constraint_function%ROWTYPE; typ TEXT; expected TEXT; types TEXT[]:='{}'; formal formal_parameter%ROWTYPE; n INT:=0;
BEGIN
 PERFORM orders_require(cardinality(seen)<96 AND NOT p_id=ANY(seen),'Цикл или слишком глубокое дерево выражения');
 SELECT * INTO STRICT e FROM expression WHERE id=p_id;seen:=seen||p_id;
 FOR c IN SELECT id,arg_position FROM expression WHERE parent_expression_id=p_id ORDER BY arg_position LOOP
  n:=n+1;PERFORM orders_require(c.arg_position=n,'Аргументы выражения должны иметь последовательные номера');
  types:=array_append(types,orders_expression_type(c.id,p_template,p_function,seen,calls));
 END LOOP;
 IF e.kind IN ('CONST','PARAMETER','FORMAL') THEN PERFORM orders_require(n=0,'Лист выражения не может содержать аргументы'); END IF;
 CASE e.kind
 WHEN 'CONST' THEN RETURN orders_value_type(e.constant_value);
 WHEN 'PARAMETER' THEN
  SELECT data_type INTO typ FROM configuration_parameter WHERE id=e.parameter_id AND template_id=p_template;
  PERFORM orders_require(typ IS NOT NULL,'Параметр выражения не принадлежит шаблону');RETURN typ;
 WHEN 'FORMAL' THEN
  SELECT data_type INTO typ FROM formal_parameter WHERE id=e.formal_parameter_id AND function_id=p_function;
  PERFORM orders_require(typ IS NOT NULL,'Формальный параметр не принадлежит вычисляемой функции');RETURN typ;
 WHEN 'OPERATOR' THEN RETURN orders_operator_type(e.operator,types);
 WHEN 'CALL' THEN
  PERFORM orders_require(NOT e.function_id=ANY(calls) AND cardinality(calls)<48,'Цикл вызовов функций');
  SELECT * INTO STRICT f FROM constraint_function WHERE id=e.function_id;
  PERFORM orders_require(n=(SELECT count(*) FROM formal_parameter WHERE function_id=f.id),'Число аргументов не соответствует сигнатуре функции '||f.code);
  FOR formal IN SELECT * FROM formal_parameter WHERE function_id=f.id ORDER BY argument_number LOOP
   PERFORM orders_require(formal.argument_number<=n AND orders_compatible(types[formal.argument_number],formal.data_type),'Тип аргумента не соответствует функции '||f.code);
  END LOOP;
  typ:=orders_expression_type(f.root_expression_id,NULL,f.id,seen,calls||f.id);
  PERFORM orders_require(orders_compatible(typ,f.result_type),'Тип результата функции не соответствует объявленному');RETURN f.result_type;
 END CASE;
 RAISE EXCEPTION 'Неизвестный вид выражения' USING ERRCODE='23514';
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_eval_expression(p_id BIGINT,p_values JSONB,p_formals JSONB DEFAULT '{}',seen BIGINT[] DEFAULT '{}',calls BIGINT[] DEFAULT '{}') RETURNS JSONB LANGUAGE plpgsql STABLE AS $$
DECLARE e expression%ROWTYPE; c RECORD; f constraint_function%ROWTYPE; formal formal_parameter%ROWTYPE; args JSONB:='[]'; bindings JSONB:='{}'; v JSONB; op TEXT; code TEXT; n INT:=0;
BEGIN
 PERFORM orders_require(cardinality(seen)<96 AND NOT p_id=ANY(seen),'Цикл или слишком глубокое выражение');
 SELECT * INTO STRICT e FROM expression WHERE id=p_id;seen:=seen||p_id;
 IF e.kind='CONST' THEN RETURN e.constant_value;
 ELSIF e.kind='PARAMETER' THEN SELECT p.code INTO code FROM configuration_parameter p WHERE id=e.parameter_id;v:=p_values->code;
  PERFORM orders_require(v IS NOT NULL AND v<>'null'::jsonb,'Не задан параметр '||code);RETURN v;
 ELSIF e.kind='FORMAL' THEN SELECT p.code INTO code FROM formal_parameter p WHERE id=e.formal_parameter_id;v:=p_formals->code;
  PERFORM orders_require(v IS NOT NULL AND v<>'null'::jsonb,'Не задан аргумент '||code);RETURN v;
 END IF;
 op:=orders_op(e.operator);
 FOR c IN SELECT id FROM expression WHERE parent_expression_id=p_id ORDER BY arg_position LOOP
  n:=n+1;v:=orders_eval_expression(c.id,p_values,p_formals,seen,calls);args:=args||jsonb_build_array(v);
  IF e.kind='OPERATOR' AND op IN ('AND','OR','IMPLIES') THEN
   PERFORM orders_require(jsonb_typeof(v)='boolean','Ожидается булев аргумент');
   IF op='AND' AND v='false'::jsonb THEN RETURN 'false'; END IF;
   IF op='OR' AND v='true'::jsonb THEN RETURN 'true'; END IF;
   IF op='IMPLIES' AND n=1 AND v='false'::jsonb THEN RETURN 'true'; END IF;
  END IF;
 END LOOP;
 IF e.kind='OPERATOR' THEN RETURN orders_eval_operator(e.operator,args); END IF;
 PERFORM orders_require(e.kind='CALL' AND NOT e.function_id=ANY(calls) AND cardinality(calls)<48,'Цикл вызовов функции');
 SELECT * INTO STRICT f FROM constraint_function WHERE id=e.function_id;
 PERFORM orders_require(n=(SELECT count(*) FROM formal_parameter WHERE function_id=f.id),'Неверное число фактических аргументов');
 FOR formal IN SELECT * FROM formal_parameter WHERE function_id=f.id ORDER BY argument_number LOOP
  v:=args->(formal.argument_number-1);PERFORM orders_require(orders_matches_type(v,formal.data_type),'Неверный тип аргумента '||formal.code);
  bindings:=bindings||jsonb_build_object(formal.code,v);
 END LOOP;
 v:=orders_eval_expression(f.root_expression_id,p_values,bindings,seen,calls||f.id);
 PERFORM orders_require(orders_matches_type(v,f.result_type),'Неверный тип результата функции '||f.code);RETURN v;
END $$;
^^^
CREATE OR REPLACE FUNCTION eval_function(p_id BIGINT,p_args JSONB) RETURNS JSONB LANGUAGE plpgsql STABLE AS $$
DECLARE f constraint_function%ROWTYPE; formal formal_parameter%ROWTYPE; bindings JSONB:='{}'; v JSONB;
BEGIN
 SELECT * INTO STRICT f FROM constraint_function WHERE id=p_id;
 PERFORM orders_expression_type(f.root_expression_id,NULL,f.id,'{}',ARRAY[f.id]);
 PERFORM orders_require(jsonb_typeof(p_args)='array','Аргументы функции должны быть списком');
 PERFORM orders_require(jsonb_array_length(p_args)=(SELECT count(*) FROM formal_parameter WHERE function_id=p_id),'Неверное число аргументов функции');
 FOR formal IN SELECT * FROM formal_parameter WHERE function_id=p_id ORDER BY argument_number LOOP
  v:=p_args->(formal.argument_number-1);PERFORM orders_require(orders_matches_type(v,formal.data_type),'Неверный тип аргумента '||formal.code);
  bindings:=bindings||jsonb_build_object(formal.code,v);
 END LOOP;
 v:=orders_eval_expression(f.root_expression_id,'{}',bindings,'{}',ARRAY[f.id]);PERFORM orders_require(orders_matches_type(v,f.result_type),'Неверный тип результата функции');RETURN v;
END $$;
^^^
-- Собрать ошибки параметров и правил без записи данных; результат JSONB-массив сообщений
CREATE OR REPLACE FUNCTION orders_validation_errors(p_template BIGINT,p_values JSONB) RETURNS JSONB LANGUAGE plpgsql STABLE AS $$
DECLARE errors JSONB:='[]'; p configuration_parameter%ROWTYPE; r configuration_rule%ROWTYPE; v JSONB; v_code TEXT; n NUMERIC; result JSONB;
BEGIN
 PERFORM orders_require(EXISTS(SELECT 1 FROM product_template WHERE id=p_template),'Шаблон не найден');
 PERFORM orders_require(jsonb_typeof(p_values)='object','Значения параметров должны быть объектом');
 IF NOT EXISTS(SELECT 1 FROM configuration_parameter WHERE template_id=p_template) THEN RETURN jsonb_build_array('В шаблоне ещё не заданы параметры конфигурации'); END IF;
 FOR v_code IN SELECT jsonb_object_keys(p_values) LOOP
  IF NOT EXISTS(SELECT 1 FROM configuration_parameter WHERE template_id=p_template AND configuration_parameter.code=v_code) THEN errors:=errors||jsonb_build_array('Неизвестный параметр: '||v_code); END IF;
 END LOOP;
 FOR p IN SELECT * FROM configuration_parameter WHERE template_id=p_template ORDER BY id LOOP
  v:=p_values->p.code;
  IF v IS NULL OR v='null'::jsonb THEN
   IF p.required THEN errors:=errors||jsonb_build_array('Заполните параметр «'||p.name||'»'); END IF;CONTINUE;
  END IF;
  IF NOT coalesce(orders_matches_type(v,p.data_type),false) THEN errors:=errors||jsonb_build_array('Неверный тип параметра «'||p.name||'»');CONTINUE; END IF;
  IF p.data_type IN ('NUMBER','INTEGER') THEN
   n:=(v#>>'{}')::numeric;
   IF n<p.min_value OR n>p.max_value THEN errors:=errors||jsonb_build_array('Значение «'||p.name||'» выходит за допустимые границы'); END IF;
  ELSIF p.data_type='ENUM' AND NOT EXISTS(SELECT 1 FROM enumeration_value WHERE enumeration_id=p.enumeration_id AND enumeration_value.code=v#>>'{}') THEN errors:=errors||jsonb_build_array('Недопустимое значение перечисления «'||p.name||'»'); END IF;
 END LOOP;
 IF jsonb_array_length(errors)>0 THEN RETURN errors; END IF;
 IF NOT EXISTS(SELECT 1 FROM jsonb_each(p_values) WHERE value<>'null'::jsonb) THEN RETURN jsonb_build_array('Задайте хотя бы одно значение параметра исполнения'); END IF;
 FOR r IN SELECT * FROM configuration_rule WHERE template_id=p_template ORDER BY id LOOP
  BEGIN
   PERFORM orders_require(orders_expression_type(r.root_expression_id,p_template,NULL)='BOOLEAN','Правило должно возвращать булево значение');
   result:=orders_eval_expression(r.root_expression_id,p_values);
   IF result<>'true'::jsonb THEN errors:=errors||jsonb_build_array(r.error_message); END IF;
  EXCEPTION WHEN OTHERS THEN errors:=errors||jsonb_build_array(r.error_message||': '||SQLERRM); END;
 END LOOP;
 RETURN errors;
END $$;
^^^
-- Проверить полный набор параметров шаблона; true только при отсутствии ошибок
CREATE OR REPLACE FUNCTION check_constraints(p_template BIGINT,p_values JSONB) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
 SELECT jsonb_array_length(orders_validation_errors(p_template,p_values))=0
$$;
^^^
CREATE OR REPLACE FUNCTION get_config_params(p_template BIGINT) RETURNS SETOF configuration_parameter LANGUAGE SQL STABLE AS $$
 SELECT * FROM configuration_parameter WHERE template_id=p_template ORDER BY id
$$;
^^^
CREATE OR REPLACE FUNCTION orders_variant_values(p_variant BIGINT) RETURNS JSONB LANGUAGE SQL STABLE AS $$
 SELECT coalesce(jsonb_object_agg(p.code,coalesce(v.value,to_jsonb(e.code))),'{}'::jsonb)
 FROM variant_parameter_value v JOIN configuration_parameter p ON p.id=v.parameter_id LEFT JOIN enumeration_value e ON e.id=v.enumeration_value_id WHERE v.variant_id=p_variant
$$;
^^^
-- Проверить конфигурацию и атомарно сохранить вариант и факты; при ошибке исключение 23514
CREATE OR REPLACE FUNCTION create_variant(p_template BIGINT,p_code TEXT,p_name TEXT,p_values JSONB) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE result BIGINT; p configuration_parameter%ROWTYPE; v JSONB; errors JSONB; enum_id BIGINT;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);errors:=orders_validation_errors(p_template,p_values);
 PERFORM orders_require(jsonb_array_length(errors)=0,coalesce(errors->>0,'Параметры варианта некорректны'));
 INSERT INTO product_variant(template_id,code,name) VALUES(p_template,p_code,p_name) RETURNING id INTO result;
 FOR p IN SELECT * FROM configuration_parameter WHERE template_id=p_template LOOP
  v:=p_values->p.code;IF v IS NULL OR v='null'::jsonb THEN CONTINUE; END IF;
  enum_id:=NULL;
  IF p.data_type='ENUM' THEN SELECT id INTO STRICT enum_id FROM enumeration_value WHERE enumeration_id=p.enumeration_id AND code=v#>>'{}'; END IF;
  INSERT INTO variant_parameter_value(variant_id,parameter_id,value,enumeration_value_id) VALUES(result,p.id,CASE WHEN p.data_type='ENUM' THEN NULL ELSE v END,enum_id);
 END LOOP;
 RETURN result;
END $$;
^^^
-- Добавить либо заменить позицию заказа, проверив XOR изделия и варианта, владельца и количество
CREATE OR REPLACE FUNCTION add_to_order(p_order BIGINT,p_product BIGINT,p_variant BIGINT,p_quantity INTEGER,p_note TEXT DEFAULT NULL,p_line BIGINT DEFAULT NULL) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE result BIGINT; next_number INT;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 PERFORM orders_require(EXISTS(SELECT 1 FROM customer_order WHERE id=p_order),'Заказ не найден');
 PERFORM orders_require(num_nonnulls(p_product,p_variant)=1 AND p_quantity>0,'Укажите одно изделие или вариант и положительное количество');
 IF p_product IS NOT NULL THEN PERFORM orders_require(EXISTS(SELECT 1 FROM classifier_node n WHERE n.id=p_product AND n.node_kind='PRODUCT') AND NOT EXISTS(SELECT 1 FROM product_template WHERE product_id=p_product),'В заказ можно включить только готовое изделие без конфигуратора'); END IF;
 IF p_variant IS NOT NULL THEN PERFORM orders_require(EXISTS(SELECT 1 FROM product_variant WHERE id=p_variant),'Вариант не найден'); END IF;
 IF p_line IS NULL THEN
  SELECT coalesce(max(line_number),0)+1 INTO next_number FROM order_line WHERE order_id=p_order;
  INSERT INTO order_line(order_id,product_id,variant_id,line_number,quantity,note) VALUES(p_order,p_product,p_variant,next_number,p_quantity,p_note) RETURNING id INTO result;
 ELSE
  UPDATE order_line SET product_id=p_product,variant_id=p_variant,quantity=p_quantity,note=p_note WHERE id=p_line AND order_id=p_order RETURNING id INTO result;
  PERFORM orders_require(result IS NOT NULL,'Позиция не найдена в этом заказе');
 END IF;
 RETURN result;
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_save_expression(ast JSONB,p_template BIGINT DEFAULT NULL,p_function BIGINT DEFAULT NULL,p_parent BIGINT DEFAULT NULL,p_position INT DEFAULT NULL,p_depth INT DEFAULT 0) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE result BIGINT; kind TEXT:=upper(ast->>'kind'); par BIGINT; formal BIGINT; fun BIGINT; child JSONB; n INT:=0;
BEGIN
 PERFORM orders_require(p_depth<64 AND jsonb_typeof(ast)='object','Некорректное или слишком глубокое выражение');
 PERFORM orders_require(kind IN ('CONST','PARAMETER','FORMAL','OPERATOR','CALL'),'Выберите вид выражения');
 IF kind='PARAMETER' THEN
  SELECT id INTO par FROM configuration_parameter WHERE template_id=p_template AND (CASE WHEN ast ? 'parameterCode' THEN code=ast->>'parameterCode' ELSE id=(ast->>'parameterId')::bigint END);
  PERFORM orders_require(par IS NOT NULL,'Параметр выражения не принадлежит шаблону');
 ELSIF kind='FORMAL' THEN
  SELECT id INTO formal FROM formal_parameter WHERE function_id=p_function AND (CASE WHEN ast ? 'formalCode' THEN code=ast->>'formalCode' ELSE id=(ast->>'formalParameterId')::bigint END);
  PERFORM orders_require(formal IS NOT NULL,'Выберите формальный аргумент своей функции');
 ELSIF kind='CALL' THEN
  SELECT id INTO fun FROM constraint_function WHERE (CASE WHEN ast ? 'functionCode' THEN code=ast->>'functionCode' ELSE id=(ast->>'functionId')::bigint END);
  PERFORM orders_require(fun IS NOT NULL,'Вызываемая функция не найдена');
 END IF;
 INSERT INTO expression(parent_expression_id,parameter_id,function_id,formal_parameter_id,kind,operator,constant_value,arg_position)
 VALUES(p_parent,par,fun,formal,kind,CASE WHEN kind='OPERATOR' THEN orders_op(ast->>'operator') END,CASE WHEN kind='CONST' THEN ast->'value' END,p_position) RETURNING id INTO result;
 PERFORM orders_require(NOT ast ? 'args' OR jsonb_typeof(ast->'args')='array','Аргументы выражения должны быть списком');
 PERFORM orders_require(coalesce(jsonb_array_length(ast->'args'),0)<=100,'Слишком много аргументов');
 FOR child IN SELECT value FROM jsonb_array_elements(coalesce(ast->'args','[]')) LOOP n:=n+1;PERFORM orders_save_expression(child,p_template,p_function,result,n,p_depth+1); END LOOP;
 RETURN result;
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_expression_json(p_id BIGINT,seen BIGINT[] DEFAULT '{}') RETURNS JSONB LANGUAGE plpgsql STABLE AS $$
DECLARE e expression%ROWTYPE; args JSONB; result JSONB;
BEGIN
 PERFORM orders_require(NOT p_id=ANY(seen) AND cardinality(seen)<96,'Цикл выражения');SELECT * INTO STRICT e FROM expression WHERE id=p_id;
 SELECT coalesce(jsonb_agg(orders_expression_json(id,seen||p_id) ORDER BY arg_position),'[]') INTO args FROM expression WHERE parent_expression_id=p_id;
 result:=jsonb_build_object('kind',e.kind,'args',args);
 IF e.kind='CONST' THEN result:=result||jsonb_build_object('value',e.constant_value);
 ELSIF e.kind='PARAMETER' THEN result:=result||jsonb_build_object('parameterCode',(SELECT code FROM configuration_parameter WHERE id=e.parameter_id));
 ELSIF e.kind='FORMAL' THEN result:=result||jsonb_build_object('formalCode',(SELECT code FROM formal_parameter WHERE id=e.formal_parameter_id));
 ELSIF e.kind='OPERATOR' THEN result:=result||jsonb_build_object('operator',e.operator);
 ELSIF e.kind='CALL' THEN result:=result||jsonb_build_object('functionId',e.function_id); END IF;
 RETURN result;
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_save_rule(p_id BIGINT,p_template BIGINT,p_code TEXT,p_name TEXT,p_message TEXT,ast JSONB) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE root BIGINT; old_root BIGINT; result BIGINT;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 IF p_id IS NOT NULL THEN
  SELECT root_expression_id INTO old_root FROM configuration_rule WHERE id=p_id AND template_id=p_template;
  PERFORM orders_require(old_root IS NOT NULL,'Правило не найдено в этом шаблоне');
 END IF;
 root:=orders_save_expression(ast,p_template);
 PERFORM orders_require(orders_expression_type(root,p_template,NULL)='BOOLEAN','Правило должно возвращать булево значение');
 IF p_id IS NULL THEN
  INSERT INTO configuration_rule(template_id,root_expression_id,code,name,error_message) VALUES(p_template,root,p_code,p_name,p_message) RETURNING id INTO result;
 ELSE
  UPDATE configuration_rule SET root_expression_id=root,code=p_code,name=p_name,error_message=p_message WHERE id=p_id RETURNING id INTO result;
  DELETE FROM expression WHERE id=old_root;
 END IF;
 RETURN result;
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_save_function(p_id BIGINT,p_code TEXT,p_name TEXT,p_type TEXT,signature JSONB,ast JSONB) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE result BIGINT; old_root BIGINT; placeholder BIGINT; root BIGINT; arg JSONB; n INT:=0;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 PERFORM orders_require(jsonb_typeof(signature)='array','Сигнатура должна быть списком');
 PERFORM orders_require(jsonb_array_length(signature)<=100,'Некорректная сигнатура');
 INSERT INTO expression(kind,constant_value) VALUES('CONST','0') RETURNING id INTO placeholder;
 IF p_id IS NULL THEN
  INSERT INTO constraint_function(root_expression_id,code,name,result_type) VALUES(placeholder,p_code,p_name,p_type) RETURNING id INTO result;
 ELSE
  SELECT root_expression_id INTO old_root FROM constraint_function WHERE id=p_id;PERFORM orders_require(old_root IS NOT NULL,'Функция не найдена');
  result:=p_id;UPDATE constraint_function SET root_expression_id=placeholder,code=p_code,name=p_name,result_type=p_type WHERE id=result;
  DELETE FROM expression WHERE id=old_root;
  DELETE FROM formal_parameter WHERE function_id=result;
 END IF;
 FOR arg IN SELECT value FROM jsonb_array_elements(signature) LOOP
  n:=n+1;INSERT INTO formal_parameter(function_id,argument_number,code,data_type) VALUES(result,n,arg->>'code',arg->>'dataType');
 END LOOP;
 root:=orders_save_expression(ast,NULL,result);
 PERFORM orders_require(orders_compatible(orders_expression_type(root,NULL,result,'{}',ARRAY[result]),p_type),'Тип результата тела не совпадает с типом функции');
 UPDATE constraint_function SET root_expression_id=root WHERE id=result;DELETE FROM expression WHERE id=placeholder;
 RETURN result;
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_assert_consistent() RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE r RECORD; errors JSONB; typ TEXT; count_all BIGINT; count_reachable BIGINT;
BEGIN
 PERFORM orders_require(NOT EXISTS(SELECT 1 FROM product_template t JOIN classifier_node n ON n.id=t.product_id WHERE n.node_kind='GROUP'),'Группа классификатора не может быть шаблоном изделия');
 PERFORM orders_require(NOT EXISTS(SELECT 1 FROM order_line l JOIN classifier_node n ON n.id=l.product_id WHERE n.node_kind<>'PRODUCT' OR EXISTS(SELECT 1 FROM product_template t WHERE t.product_id=n.id)),'В заказ включено изделие, требующее конфигурирования, или группа');
 PERFORM orders_require(NOT EXISTS(SELECT 1 FROM variant_parameter_value v JOIN product_variant pv ON pv.id=v.variant_id JOIN configuration_parameter p ON p.id=v.parameter_id LEFT JOIN enumeration_value ev ON ev.id=v.enumeration_value_id
  WHERE pv.template_id<>p.template_id OR (p.data_type='ENUM' AND (v.value IS NOT NULL OR ev.enumeration_id IS DISTINCT FROM p.enumeration_id)) OR (p.data_type<>'ENUM' AND (v.enumeration_value_id IS NOT NULL OR NOT coalesce(orders_matches_type(v.value,p.data_type),false)))), 'Значение не соответствует параметру или шаблону варианта');
 PERFORM orders_require(NOT EXISTS(SELECT 1 FROM expression e WHERE
  (CASE WHEN e.parent_expression_id IS NULL THEN 0 ELSE 1 END)+(SELECT count(*) FROM constraint_function f WHERE f.root_expression_id=e.id)+(SELECT count(*) FROM configuration_rule cr WHERE cr.root_expression_id=e.id)<>1), 'Выражение должно иметь ровно одного владельца: родителя, правило или функцию');
 WITH RECURSIVE reachable(id) AS (
  SELECT root_expression_id FROM configuration_rule UNION SELECT root_expression_id FROM constraint_function
  UNION SELECT e.id FROM expression e JOIN reachable reached ON e.parent_expression_id=reached.id
 ) SELECT count(*) INTO count_reachable FROM reachable;
 SELECT count(*) INTO count_all FROM expression;PERFORM orders_require(count_all=count_reachable,'Обнаружен цикл или недоступное выражение');
 FOR r IN SELECT * FROM constraint_function LOOP
  PERFORM orders_require(NOT EXISTS(SELECT 1 FROM formal_parameter WHERE function_id=r.id AND argument_number>(SELECT count(*) FROM formal_parameter WHERE function_id=r.id)),'Номера формальных аргументов должны идти подряд');
  typ:=orders_expression_type(r.root_expression_id,NULL,r.id,'{}',ARRAY[r.id]);PERFORM orders_require(orders_compatible(typ,r.result_type),'Неверный тип результата функции '||r.code);
 END LOOP;
 FOR r IN SELECT * FROM configuration_rule LOOP
  PERFORM orders_require(orders_expression_type(r.root_expression_id,r.template_id,NULL)='BOOLEAN','Правило '||r.code||' должно возвращать булево значение');
 END LOOP;
 FOR r IN SELECT * FROM product_variant LOOP
  errors:=orders_validation_errors(r.template_id,orders_variant_values(r.id));
  PERFORM orders_require(jsonb_array_length(errors)=0,'Вариант '||r.code||': '||coalesce(errors->>0,''));
 END LOOP;
END $$;
^^^
CREATE OR REPLACE FUNCTION orders_lock_trigger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN PERFORM pg_advisory_xact_lock(21001);RETURN NULL;END $$;
^^^
CREATE OR REPLACE FUNCTION orders_validate_trigger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN PERFORM orders_assert_consistent();RETURN NULL;END $$;
^^^
CREATE OR REPLACE FUNCTION orders_protect_ordered_variant() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE variant BIGINT;
BEGIN
 IF TG_TABLE_NAME='product_variant' THEN variant:=OLD.id;
 ELSE variant:=CASE WHEN TG_OP='INSERT' THEN NEW.variant_id ELSE OLD.variant_id END; END IF;
 PERFORM orders_require(NOT EXISTS(SELECT 1 FROM order_line WHERE variant_id=variant),'Вариант уже используется в заказе: создайте новое исполнение');
 IF TG_TABLE_NAME='variant_parameter_value' THEN
  IF TG_OP='UPDATE' THEN
   IF NEW.variant_id<>OLD.variant_id THEN
    PERFORM orders_require(NOT EXISTS(SELECT 1 FROM order_line WHERE variant_id=NEW.variant_id),'Нельзя добавлять значения в заказанный вариант');
   END IF;
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;
^^^
DO $$ DECLARE t TEXT;
BEGIN
 FOREACH t IN ARRAY ARRAY['customer','customer_order','product_template','product_variant','configuration_parameter','variant_parameter_value','order_line','constraint_function','formal_parameter','expression','configuration_rule'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS orders_lock ON %I',t);
  EXECUTE format('CREATE TRIGGER orders_lock BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH STATEMENT EXECUTE FUNCTION orders_lock_trigger()',t);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['customer','customer_order','product_template','product_variant','configuration_parameter','variant_parameter_value','order_line','constraint_function','formal_parameter','expression','configuration_rule','classifier_node','enumeration_value'] LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS orders_validate ON %I',t);
  EXECUTE format('CREATE CONSTRAINT TRIGGER orders_validate AFTER INSERT OR UPDATE OR DELETE ON %I DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION orders_validate_trigger()',t);
 END LOOP;
END $$;
^^^
DROP TRIGGER IF EXISTS orders_protect_variant ON product_variant;
^^^
CREATE TRIGGER orders_protect_variant BEFORE UPDATE OR DELETE ON product_variant FOR EACH ROW EXECUTE FUNCTION orders_protect_ordered_variant();
^^^
DROP TRIGGER IF EXISTS orders_protect_value ON variant_parameter_value;
^^^
CREATE TRIGGER orders_protect_value BEFORE INSERT OR UPDATE OR DELETE ON variant_parameter_value FOR EACH ROW EXECUTE FUNCTION orders_protect_ordered_variant();
^^^
-- Создать заказ заданного заказчика и вернуть идентификатор
CREATE OR REPLACE FUNCTION create_order(p_customer BIGINT,p_number TEXT,p_date DATE,p_requested DATE DEFAULT NULL) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE result BIGINT;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 INSERT INTO customer_order(customer_id,order_number,order_date,requested_date) VALUES(p_customer,p_number,p_date,p_requested) RETURNING id INTO result;
 RETURN result;
END $$;
^^^
CREATE OR REPLACE FUNCTION get_order_contents(p_order BIGINT)
RETURNS TABLE(id BIGINT,order_id BIGINT,product_id BIGINT,variant_id BIGINT,line_number INTEGER,quantity INTEGER,note TEXT,code TEXT,name TEXT,template_name TEXT,parameters JSONB)
LANGUAGE SQL STABLE AS $$
 SELECT l.id,l.order_id,l.product_id,l.variant_id,l.line_number,l.quantity,l.note,coalesce(n.code,v.code)::text,coalesce(n.name,v.name)::text,t.name::text,
 coalesce((SELECT jsonb_agg(jsonb_build_object('code',p.code,'name',p.name,'value',coalesce(pv.value,to_jsonb(ev.name)),'unit',u.code) ORDER BY p.id)
 FROM variant_parameter_value pv JOIN configuration_parameter p ON p.id=pv.parameter_id LEFT JOIN enumeration_value ev ON ev.id=pv.enumeration_value_id LEFT JOIN unit_of_measure u ON u.id=p.unit_of_measure_id WHERE pv.variant_id=v.id),'[]')
 FROM order_line l LEFT JOIN classifier_node n ON n.id=l.product_id LEFT JOIN product_variant v ON v.id=l.variant_id LEFT JOIN product_template t ON t.id=v.template_id WHERE l.order_id=p_order ORDER BY l.line_number
$$;
^^^
-- Изменить ещё не заказанный вариант после проверки; заказанное исполнение защищено от изменения
CREATE OR REPLACE FUNCTION update_variant(p_variant BIGINT,p_code TEXT,p_name TEXT,p_values JSONB) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE tpl BIGINT; p configuration_parameter%ROWTYPE; v JSONB; errors JSONB; enum_id BIGINT;
BEGIN
 PERFORM pg_advisory_xact_lock(21001);
 SELECT template_id INTO tpl FROM product_variant WHERE id=p_variant;PERFORM orders_require(tpl IS NOT NULL,'Вариант не найден');
 PERFORM orders_require(NOT EXISTS(SELECT 1 FROM order_line WHERE variant_id=p_variant),'Вариант уже используется в заказе: создайте новое исполнение');
 errors:=orders_validation_errors(tpl,p_values);PERFORM orders_require(jsonb_array_length(errors)=0,coalesce(errors->>0,'Вариант некорректен'));
 UPDATE product_variant SET code=p_code,name=p_name WHERE id=p_variant;
 DELETE FROM variant_parameter_value WHERE variant_id=p_variant;
 FOR p IN SELECT * FROM configuration_parameter WHERE template_id=tpl LOOP
  v:=p_values->p.code;IF v IS NULL OR v='null'::jsonb THEN CONTINUE; END IF;
  enum_id:=NULL;IF p.data_type='ENUM' THEN SELECT id INTO STRICT enum_id FROM enumeration_value WHERE enumeration_id=p.enumeration_id AND code=v#>>'{}'; END IF;
  INSERT INTO variant_parameter_value(variant_id,parameter_id,value,enumeration_value_id) VALUES(p_variant,p.id,CASE WHEN p.data_type='ENUM' THEN NULL ELSE v END,enum_id);
 END LOOP;
 RETURN p_variant;
END $$;
