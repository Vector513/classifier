-- Исправление типа только исходного учебного кронштейна: он собирается со сталью и болтами.
-- Рецептуры, версии и пользовательские изделия не изменяются. Повторное выполнение безопасно.
DO $fix$
BEGIN
    PERFORM pg_advisory_xact_lock(21001);
    IF EXISTS (SELECT 1 FROM schema_migration WHERE name='bom-demo-bracket-kind-v2') THEN RETURN; END IF;
    UPDATE bom_product p SET kind='ASSEMBLY'
    FROM classifier_node n JOIN classifier_node parent ON parent.id=n.parent_id
    WHERE p.node_id=n.id AND n.code='BOM-BRACKET' AND n.name='Кронштейн'
      AND parent.code='BOM-ASSEMBLIES' AND p.kind='PART'
      AND EXISTS (
          SELECT 1 FROM bom_specification s
          WHERE s.product_id=p.node_id AND s.revision=1 AND s.base_id IS NULL
            AND s.status='RELEASED'
            AND (SELECT count(*) FROM bom_line l WHERE l.specification_id=s.id)=2
            AND EXISTS (SELECT 1 FROM bom_line l JOIN classifier_node c ON c.id=l.component_id
                WHERE l.specification_id=s.id AND c.code='BOM-STEEL' AND l.quantity=0.5)
            AND EXISTS (SELECT 1 FROM bom_line l JOIN classifier_node c ON c.id=l.component_id
                WHERE l.specification_id=s.id AND c.code='BOM-BOLT' AND l.quantity=3)
      );
    INSERT INTO schema_migration(name) VALUES('bom-demo-bracket-kind-v2');
END
$fix$;
