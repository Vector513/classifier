-- Ручная загрузка комплектов в БД, уже инициализированную приложением.
-- psql ... -v ON_ERROR_STOP=1 -f docs/bom/demo.sql
\ir ../../src/main/resources/bom-demo.sql
\ir ../../src/main/resources/catalog-bom-layout.sql
\ir ../../src/main/resources/catalog-product-details-demo.sql
