/* Запуск на отдельном экземпляре с демоданными. Нужны Node.js и Playwright.
 * BOM_TEST_URL=http://127.0.0.1:18083 node docs/bom/ui-smoke.cjs
 */
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const url = process.env.BOM_TEST_URL;
if (!url) throw new Error('Укажите BOM_TEST_URL отдельного тестового экземпляра.');

(async () => {
    const browser = await chromium.launch({ headless: true, executablePath: process.env.BOM_TEST_BROWSER });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', err => errors.push(err.message));
    const get = async route => {
        const response = await page.request.get(url + '/api/v1/bom' + route);
        assert(response.ok(), await response.text());
        return response.json();
    };
    const waitDetail = () => page.locator('#bom-calc').waitFor();
    const detail = page.locator('#bom-detail');
    const results = page.locator('#bom-results');
    const totals = results.locator('table').first();
    const calculate = async quantity => {
        await page.locator('#bom-calc [name="quantity"]').fill(quantity);
        await page.locator('#bom-calc button').click();
        await totals.waitFor();
    };
    const checkTotal = async (code, quantity, unit = 'шт.') => {
        const cells = totals.locator('tr').filter({ has: page.getByText(code, { exact: true }) }).locator('td');
        assert.equal(await cells.nth(1).innerText(), quantity);
        assert.equal(await cells.nth(2).innerText(), unit);
    };
    let draftId;
    try {
        const specs = await get('/specifications');
        const products = await get('/products');
        const product = suffix => products.find(p => p.code === 'BOM-EL-' + suffix);
        const base = specs.find(s => s.productId === product('PC-STD').id && s.revision === 1);
        const cases = [
            ['Gaming PC Standard', 'Gaming PC Pro', '3', 'PC-RAM', '6', '12'],
            ['Smart Home Starter Kit', 'Smart Home Pro', '3', 'HOME-MOTION', '3', '9'],
            ['Gaming Set Standard', 'Gaming Set Premium', '10', 'GAME-KEYBOARD', '10', '10'],
            ['Security Camera Kit Standard', 'Security Camera Kit Pro', '3', 'SEC-CABLE', '60', '120', 'м'],
            ['Smartphone Bundle Standard', 'Smartphone Bundle Premium', '3', 'PHONE-CASE', '3', '3'],
        ];
        const openExample = async name => {
            await page.goto(url + '/#/bom');
            await page.locator('#bom-demo-kits').getByRole('link', { name: name + ' · версия 1', exact: false }).click();
            await waitDetail();
            assert.match(await detail.locator('.bom-meta').first().innerText(), /Утверждён.*Версия 1/s);
        };

        for (const [standard, improved, quantity, code, baseTotal, variantTotal, unit] of cases) {
            await openExample(standard);
            assert.match(await detail.locator('.bom-meta').first().innerText(), /Базовый состав/);
            await calculate(quantity);
            await checkTotal('BOM-EL-' + code, baseTotal, unit);
            if (code === 'GAME-KEYBOARD') {
                for (const component of ['MOUSE', 'HEADPHONES', 'MAT']) await checkTotal('BOM-EL-GAME-' + component, '10');
            }
            await openExample(improved);
            assert.match(await detail.locator('.bom-meta').first().innerText(), /Модификация/);
            assert.match(await detail.locator('.bom-changes summary').first().innerText(), /Изменения этой версии/);
            assert.match(await detail.locator('table').first().innerText(), /Из исходного состава/);
            assert.equal(await detail.locator('[data-edit]').count(), 0);
            await calculate(quantity);
            await checkTotal('BOM-EL-' + code, variantTotal, unit);
            if (code === 'PC-RAM') {
                await checkTotal('BOM-EL-PC-FAN', '6');
                await results.locator('summary').click();
                assert.match(await results.innerText(), /7 → 1/);
                await detail.locator('table').first().getByRole('link', { name: /Комплект охлаждения/ }).click();
                await detail.locator('table').first().getByText('Контроллер вентиляторов', { exact: true }).waitFor();
            }
            if (code === 'PHONE-CASE') await checkTotal('PHONES-SAMSUNG-S24', '3');
        }

        await page.goto(url + '/#/bom');
        const search = page.locator('#bom-search');
        await search.fill('нет такого изделия 999');
        assert.match(await page.locator('#bom-specs').innerText(), /ничего не найдено/);
        await search.fill('Gaming Set');
        await page.locator('#bom-status').selectOption('DRAFT');
        const filtered = await page.locator('#bom-specs').innerText();
        assert.match(filtered, /Gaming Set Standard · версия 2/);
        assert.doesNotMatch(filtered, /Утверждён|Gaming Set Premium/);

        await page.goto(url + '/#/bom/' + base.id);
        await waitDetail();
        const classOption = await page.locator('[name="classId"] option').evaluateAll(
            opts => opts.find(option => option.textContent.includes('BOM-EL-PC-PARTS')).value);
        await page.locator('[name="classId"]').selectOption(classOption);
        await calculate('2,5');
        await checkTotal('BOM-EL-PC-RAM', '5');
        await checkTotal('BOM-EL-PC-FAN-CONTROL', '2,5');
        await page.locator('[data-revise]').click();
        await page.locator('.modal [name="description"]').fill('Проверка интерфейса: смена видеокарты');
        await page.getByRole('button', { name: 'Создать черновик', exact: true }).click();
        await page.waitForURL(u => u.hash !== '#/bom/' + base.id);
        draftId = Number(new URL(page.url()).hash.split('/').pop());
        await waitDetail();

        await page.locator('[data-edit="3"]').click();
        assert(await page.locator('[name="position"]').evaluate(el => el.readOnly));
        await page.locator('[name="componentId"]').selectOption(String(product('PC-RTX4070').id));
        assert.equal(await page.locator('[name="componentSpecificationId"] option').count(), 1);
        await page.getByRole('button', { name: 'Сохранить изменение', exact: true }).click();
        await detail.getByText('Заменён', { exact: true }).waitFor();
        await page.locator('[data-exclude="4"]').click();
        await page.locator('[data-reset="4"]').waitFor();
        assert.match(await detail.locator('.bom-changes').first().innerText(), /Убран из состава/);
        await page.locator('[data-reset="4"]').click();
        await page.locator('.modal').getByRole('button', { name: 'Вернуть как было', exact: true }).click();
        await page.locator('[data-edit="4"]').waitFor();

        await page.locator('[data-add]').click();
        await page.locator('[name="componentId"]').selectOption(String(product('PC-COOLING').id));
        await page.locator('[name="componentSpecificationId"]').selectOption({ index: 1 });
        await page.locator('[name="componentId"]').selectOption(String(product('PC-FAN').id));
        assert.equal(await page.locator('[name="componentSpecificationId"]').inputValue(), '');
        await page.locator('[name="position"]').fill('2');
        await page.getByRole('button', { name: 'Добавить в состав', exact: true }).click();
        await page.getByText('Этот номер уже занят.', { exact: false }).waitFor();
        await page.locator('[name="position"]').fill('8');
        await page.getByRole('button', { name: 'Добавить в состав', exact: true }).click();
        await page.locator('[data-edit="8"]').waitFor();

        const original = await get('/specifications/' + base.id + '/lines');
        assert.equal(original.find(l => l.position === 3).code, 'BOM-EL-PC-RTX4060');
        assert.equal(original.length, 7);
        await page.setViewportSize({ width: 900, height: 1000 });
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
        await page.locator('[data-remove]').click();
        await page.locator('.modal').getByRole('button', { name: 'Удалить черновик', exact: true }).click();
        await page.waitForURL(u => u.hash === '#/bom');
        assert(!(await get('/specifications')).some(s => s.id === draftId));
        draftId = null;

        // Берём исполнение телефона из классификатора и создаём для него свой состав.
        const treeResponse = await page.request.get(url + '/api/v1/nodes/tree');
        assert(treeResponse.ok());
        const flatten = nodes => nodes.flatMap(node => [node, ...flatten(node.children || [])]);
        const nodes = flatten(await treeResponse.json());
        const electronics = nodes.find(node => node.code === 'ELECTRONICS');
        const specsGroup = electronics.children.find(node => node.name === 'Спецификации');
        assert.equal(specsGroup.children.length, 11);
        for (const [groupCode, productCode] of [
            ['PC-PROCESSORS', 'BOM-EL-PC-CPU'], ['PC-MOTHERBOARDS', 'BOM-EL-PC-BOARD'],
            ['PC-GRAPHICS', 'BOM-EL-PC-RTX4060'], ['ACC-CHARGERS', 'BOM-EL-PHONE-CHARGER'],
        ]) {
            assert(nodes.find(node => node.code === groupCode).children.some(node => node.code === productCode));
        }
        assert(!nodes.some(node => ['BOM-EL-DEMO', 'DEMO-PARAMETERS', 'DEMO-EMPTY-GROUP'].includes(node.code)));
        const phone = nodes.find(node => node.code === 'ASSORT-S24-128');
        const charger = nodes.find(node => node.code === 'ASSORT-USB-C-CHARGER');
        assert(phone, 'Нужно демонстрационное исполнение Galaxy S24');
        await page.locator('[data-create]').click();
        await page.locator('.modal [name="productId"]').selectOption(String(phone.id));
        await page.locator('.modal [name="description"]').fill('Проверка состава упаковки смартфона');
        await page.getByRole('button', { name: 'Создать черновик', exact: true }).click();
        await page.waitForURL(u => /^#\/bom\/\d+$/.test(u.hash));
        draftId = Number(new URL(page.url()).hash.split('/').pop());
        await waitDetail();
        assert.equal((await get('/specifications/' + draftId)).productId, phone.id);
        await page.locator('[data-add]').click();
        await page.locator('.modal [name="componentId"]').selectOption(String(charger.id));
        await page.getByRole('button', { name: 'Добавить в состав', exact: true }).click();
        await page.locator('[data-edit="1"]').waitFor();
        await calculate('2');
        await checkTotal(charger.code, '2');

        // Папка содержит и составы для товаров из других ветвей каталога.
        await page.goto(url + '/#/tree/' + specsGroup.id);
        const folder = page.locator('[data-specifications]');
        const draftLink = folder.locator(`a[href="#/bom/${draftId}"]`);
        await draftLink.getByText('Черновик', { exact: true }).waitFor();
        assert.equal(await folder.locator('a[href^="#/bom/"]').count(), (await get('/specifications')).length);
        await draftLink.click();
        await page.waitForURL(u => u.hash === '#/bom/' + draftId);
        await detail.getByText('Проверка состава упаковки смартфона', { exact: true }).waitFor();

        const powerSupply = nodes.find(node => node.code === 'BOM-EL-PC-PSU');
        await page.goto(url + '/#/tree/' + powerSupply.id);
        await page.getByRole('tab', { name: 'Числовые', exact: true }).click();
        const power = page.locator('[data-param-row]').filter({ hasText: 'DEMO-POWER' });
        await power.getByText('650', { exact: true }).waitFor();
        await power.locator('.param-row__value-unit').getByText('ватты', { exact: true }).waitFor();
        await page.getByRole('tab', { name: 'Варианты и свойства', exact: true }).click();
        await page.getByRole('button', { name: 'ATX', exact: true, pressed: true }).waitFor();
        assert.deepEqual(errors, []);
        console.log('PASS: five kit pairs, calculations, editing, all versions in the catalog folder and product properties');
    } finally {
        if (draftId) await page.request.delete(url + '/api/v1/bom/specifications/' + draftId);
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
