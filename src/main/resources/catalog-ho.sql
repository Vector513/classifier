-- Дополнение прежней модели ХО без новых сущностей: типизированное значение ENUM.
DO $migration$
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    ALTER TABLE ho_parameter_type ADD COLUMN IF NOT EXISTS enumeration_id BIGINT REFERENCES enumeration(id) ON DELETE RESTRICT;
    ALTER TABLE ho_instance_value ADD COLUMN IF NOT EXISTS enum_value_id BIGINT REFERENCES enumeration_value(id) ON DELETE RESTRICT;
    IF NOT EXISTS(SELECT 1 FROM schema_migration WHERE name='catalog-ho-v2') THEN
        INSERT INTO schema_migration(name,details) SELECT 'catalog-ho-v2',jsonb_build_object('parameter_types',(SELECT jsonb_agg(to_jsonb(p)) FROM ho_parameter_type p),'values',(SELECT jsonb_agg(to_jsonb(v)) FROM ho_instance_value v));
        IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='ho_parameter_type' AND column_name='enum_class_id') THEN
            UPDATE ho_parameter_type p SET enumeration_id=(SELECT min(id) FROM enumeration WHERE enumeration_class_id=p.enum_class_id HAVING count(*)=1) WHERE p.enum_class_id IS NOT NULL;
            IF EXISTS(SELECT 1 FROM ho_parameter_type WHERE data_type='ENUM' AND enumeration_id IS NULL) THEN
                RAISE EXCEPTION 'У старого ENUM-параметра операции неоднозначный список. Укажите enumeration_id перед продолжением миграции';
            END IF;
            ALTER TABLE ho_parameter_type DROP COLUMN enum_class_id;
        END IF;
    END IF;
END
$migration$;
^^^
CREATE OR REPLACE FUNCTION catalog_validate_ho() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM ho_parameter_type WHERE (data_type='ENUM')<>(enumeration_id IS NOT NULL) OR min_value>max_value) THEN
        RAISE EXCEPTION 'Параметр ENUM требует список вариантов; числовые пределы должны быть согласованы' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM ho_instance_value v JOIN ho_instance i ON i.id=v.ho_instance_id JOIN ho_parameter_type p ON p.id=v.ho_parameter_type_id
        WHERE NOT EXISTS(SELECT 1 FROM ho_template_parameter b WHERE b.ho_template_id=i.ho_template_id AND b.ho_parameter_type_id=p.id)
           OR num_nonnulls(v.numeric_value,v.string_value,v.date_value,v.enum_value_id)<>1
           OR (p.data_type='NUMBER' AND (v.numeric_value IS NULL OR v.numeric_value<p.min_value OR v.numeric_value>p.max_value))
           OR (p.data_type='STRING' AND (v.string_value IS NULL OR btrim(v.string_value)=''))
           OR (p.data_type='DATE' AND v.date_value IS NULL)
           OR (p.data_type='ENUM' AND NOT EXISTS(SELECT 1 FROM enumeration_value e WHERE e.id=v.enum_value_id AND e.enumeration_id=p.enumeration_id))) THEN
        RAISE EXCEPTION 'Значение операции не соответствует типу или шаблону параметра' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM ho_instance_role r JOIN ho_instance i ON i.id=r.ho_instance_id WHERE btrim(r.counterparty)='' OR NOT EXISTS(
        SELECT 1 FROM ho_template_role b WHERE b.ho_template_id=i.ho_template_id AND b.ho_role_type_id=r.ho_role_type_id)) THEN
        RAISE EXCEPTION 'Участник операции должен соответствовать роли в шаблоне' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM ho_instance i JOIN ho_template_parameter b ON b.ho_template_id=i.ho_template_id WHERE i.status<>'DRAFT' AND b.is_required AND NOT EXISTS(
        SELECT 1 FROM ho_instance_value v WHERE v.ho_instance_id=i.id AND v.ho_parameter_type_id=b.ho_parameter_type_id))
       OR EXISTS(SELECT 1 FROM ho_instance i JOIN ho_template_role b ON b.ho_template_id=i.ho_template_id WHERE i.status<>'DRAFT' AND b.is_required AND NOT EXISTS(
        SELECT 1 FROM ho_instance_role r WHERE r.ho_instance_id=i.id AND r.ho_role_type_id=b.ho_role_type_id)) THEN
        RAISE EXCEPTION 'Заполните обязательные параметры и роли перед активацией операции' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
^^^
DO $$ DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['ho_instance','ho_parameter_type','ho_template_parameter','ho_template_role','ho_instance_value','ho_instance_role'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS catalog_ho_validate ON %I',t);
        EXECUTE format('CREATE CONSTRAINT TRIGGER catalog_ho_validate AFTER INSERT OR UPDATE OR DELETE ON %I DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION catalog_validate_ho()',t);
        EXECUTE format('DROP TRIGGER IF EXISTS catalog_lock ON %I',t);
        EXECUTE format('CREATE TRIGGER catalog_lock BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION catalog_lock_trigger()',t);
    END LOOP;
END $$;
^^^
