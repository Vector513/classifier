-- Порядок предков определяется связями дерева, а не значениями ID.
CREATE OR REPLACE FUNCTION catalog_ancestors(p_node BIGINT)
RETURNS TABLE(id BIGINT,depth INTEGER) LANGUAGE SQL STABLE AS $$
    WITH RECURSIVE chain AS (
        SELECT n.id,n.parent_id,0 AS depth,ARRAY[n.id] AS path FROM classifier_node n WHERE n.id=p_node
        UNION ALL SELECT n.id,n.parent_id,c.depth+1,c.path||n.id FROM chain c JOIN classifier_node n ON n.id=c.parent_id WHERE NOT n.id=ANY(c.path)
    ) SELECT id,depth FROM chain
$$;
^^^
CREATE OR REPLACE FUNCTION catalog_enum_applicable(p_node BIGINT,p_enum BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT COALESCE((SELECT NOT b.excluded FROM catalog_ancestors(p_node) a JOIN node_enumeration b ON b.classifier_node_id=a.id WHERE b.enumeration_id=p_enum ORDER BY a.depth LIMIT 1),false)
$$;
^^^
CREATE OR REPLACE FUNCTION catalog_enum_allowed(p_node BIGINT,p_enum BIGINT,p_value BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT catalog_enum_applicable(p_node,p_enum)
      AND EXISTS(SELECT 1 FROM enumeration_value WHERE id=p_value AND enumeration_id=p_enum)
      AND NOT EXISTS (
          SELECT 1 FROM catalog_ancestors(p_node) a JOIN node_enumeration b ON b.classifier_node_id=a.id
          WHERE b.enumeration_id=p_enum AND NOT b.excluded AND b.restrict_values AND NOT EXISTS (
              SELECT 1 FROM node_enumeration_option o WHERE o.classifier_node_id=b.classifier_node_id AND o.enumeration_id=p_enum AND o.enumeration_value_id=p_value
          )
      )
$$;
^^^
CREATE OR REPLACE FUNCTION catalog_numeric_applicable(p_node BIGINT,p_param BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT COALESCE((SELECT NOT b.excluded FROM catalog_ancestors(p_node) a JOIN node_numeric_parameter b ON b.classifier_node_id=a.id WHERE b.numeric_parameter_id=p_param ORDER BY a.depth LIMIT 1),false)
$$;
^^^
CREATE OR REPLACE FUNCTION catalog_assert_consistent() RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM classifier_node WHERE node_kind NOT IN ('GROUP','MODEL','PRODUCT')) THEN
        RAISE EXCEPTION 'Неизвестный тип записи справочника' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM classifier_node n JOIN classifier_node p ON p.id=n.parent_id
        WHERE p.node_kind='PRODUCT' OR (p.node_kind='MODEL' AND n.node_kind<>'PRODUCT')) THEN
        RAISE EXCEPTION 'Изделие не может содержать узлы классификатора; внутри модели допустимы только исполнения' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM classifier_node n JOIN catalog_ancestors(n.parent_id) a ON a.id=n.id) THEN
        RAISE EXCEPTION 'Обнаружен цикл в дереве классификатора' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM classifier_node WHERE node_kind<>'GROUP' AND unit_of_measure_id IS NULL) THEN
        RAISE EXCEPTION 'Для модели и изделия нужна единица измерения' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM classifier_node n JOIN classifier_node p ON p.id=n.parent_id
        WHERE p.node_kind='MODEL' AND n.unit_of_measure_id IS DISTINCT FROM p.unit_of_measure_id) THEN
        RAISE EXCEPTION 'Исполнение и его модель должны иметь одну единицу измерения' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM node_enumeration WHERE excluded AND restrict_values) THEN
        RAISE EXCEPTION 'Исключённая характеристика не может задавать варианты' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM node_enumeration b WHERE b.restrict_values AND NOT EXISTS(
        SELECT 1 FROM node_enumeration_option o WHERE o.classifier_node_id=b.classifier_node_id AND o.enumeration_id=b.enumeration_id)) THEN
        RAISE EXCEPTION 'Ограниченный набор вариантов не может быть пустым' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM node_enumeration_option o JOIN node_enumeration b USING(classifier_node_id,enumeration_id)
        JOIN classifier_node n ON n.id=o.classifier_node_id
        WHERE NOT b.restrict_values OR (catalog_enum_applicable(n.parent_id,o.enumeration_id) AND NOT catalog_enum_allowed(n.parent_id,o.enumeration_id,o.enumeration_value_id))) THEN
        RAISE EXCEPTION 'Варианты узла должны входить в допустимые варианты родительской группы или модели' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM node_attribute_value v WHERE NOT catalog_enum_allowed(v.classifier_node_id,v.enumeration_id,v.enumeration_value_id)) THEN
        RAISE EXCEPTION 'Выбранное значение не относится к характеристике или не разрешено для этого узла' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM numeric_parameter WHERE min_value>max_value) THEN
        RAISE EXCEPTION 'Минимум числового параметра больше максимума' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM node_numeric_value v JOIN numeric_parameter p ON p.id=v.numeric_parameter_id
        WHERE NOT catalog_numeric_applicable(v.classifier_node_id,p.id) OR v.value<p.min_value OR v.value>p.max_value) THEN
        RAISE EXCEPTION 'Числовой параметр не назначен узлу либо значение выходит за допустимые пределы' USING ERRCODE='23514';
    END IF;
    IF to_regclass('bom_product') IS NOT NULL THEN
        IF EXISTS(SELECT 1 FROM bom_product p JOIN classifier_node n ON n.id=p.node_id WHERE n.node_kind='GROUP' OR n.unit_of_measure_id IS NULL) THEN
            RAISE EXCEPTION 'Участником состава может быть модель или изделие с единицей измерения' USING ERRCODE='23514';
        END IF;
    END IF;
END $$;
^^^
CREATE OR REPLACE FUNCTION catalog_validate_trigger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM catalog_assert_consistent();
    RETURN NULL;
END $$;
^^^
CREATE OR REPLACE FUNCTION catalog_lock_trigger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF TG_TABLE_NAME='classifier_node' AND TG_OP='UPDATE' THEN
        IF NEW.unit_of_measure_id IS DISTINCT FROM OLD.unit_of_measure_id AND to_regclass('bom_product') IS NOT NULL THEN
            IF EXISTS(SELECT 1 FROM bom_product WHERE node_id=OLD.id) THEN
                RAISE EXCEPTION 'Единица изделия в составах зафиксирована. Создайте отдельное изделие для другой единицы' USING ERRCODE='23514';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
^^^
DO $$ DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['classifier_node','node_enumeration','node_enumeration_option','node_attribute_value','node_numeric_parameter','node_numeric_value','numeric_parameter','enumeration_value'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS catalog_validate ON %I',t);
        EXECUTE format('CREATE CONSTRAINT TRIGGER catalog_validate AFTER INSERT OR UPDATE OR DELETE ON %I DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION catalog_validate_trigger()',t);
        EXECUTE format('DROP TRIGGER IF EXISTS catalog_lock ON %I',t);
        EXECUTE format('CREATE TRIGGER catalog_lock BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION catalog_lock_trigger()',t);
    END LOOP;
END $$;
^^^
