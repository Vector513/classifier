// Проверять только отдельный экземпляр с демонстрационными данными ЛР7.
// ORDER_TEST_URL=http://127.0.0.1:8082 node docs/orders/ui-smoke.cjs
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const url=process.env.ORDER_TEST_URL;
if(!url)throw new Error('Укажите ORDER_TEST_URL отдельного тестового экземпляра.');
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:1500,height:1050}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const dir=path.join(__dirname,'screenshots');fs.mkdirSync(dir,{recursive:true});
 const stamp=Date.now();let ruleId,fnId,customerId,orderId,variantId;
 async function api(p='',method='GET',data){const r=await page.request.fetch(url+'/api/v1/orders'+p,{method,data});assert(r.ok(),`${r.status()} ${await r.text()}`);return r.status()===204?null:r.json();}
 const modal=page.locator('.modal');
 const root=()=>modal.locator('[data-expression] > .order-expression');
 const child=i=>root().locator(':scope > .order-expression-args > .order-argument > .order-expression').nth(i);
 const head=node=>node.locator(':scope > .order-expression-head');
 async function save(){await modal.getByRole('button',{name:'Сохранить',exact:true}).click();await modal.waitFor({state:'hidden'});}
 try{
  const initial=await api();const tpl=initial.templates.find(t=>t.code==='ORDER-DUCT-TEMPLATE');assert(tpl,'Нет демонстрационного шаблона');
  const ready=initial.catalog.find(n=>n.code==='ORDER-CAP');assert(ready);
  await page.goto(url+'/#/orders');await page.getByRole('heading',{name:'Заказы',exact:true}).waitFor();
  await page.screenshot({path:path.join(dir,'01-orders.png'),fullPage:true});
  await page.goto(url+`/#/orders/templates/${tpl.id}`);await page.getByRole('heading',{name:'Параметры конфигурации',exact:true}).waitFor();
  await page.getByRole('button',{name:'Добавить правило',exact:true}).click();
  await modal.locator('[name="code"]').fill('UI_RULE_'+stamp);await modal.locator('[name="name"]').fill('Проверка высоты UI');
  await modal.locator('[name="errorMessage"]').fill('Высота должна быть больше 100 мм');
  await head(child(0)).getByLabel(/^Вид выражения/).selectOption('PARAMETER');
  await head(child(0)).getByLabel(/^Параметр/).selectOption('h');
  await child(1).getByLabel('Значение константы',{exact:true}).fill('100');await save();
  let detail=await api(`/templates/${tpl.id}`);ruleId=detail.rules.find(r=>r.code==='UI_RULE_'+stamp).id;
  await page.getByText('Проверка высоты UI',{exact:true}).waitFor();await page.screenshot({path:path.join(dir,'02-template.png'),fullPage:true});
  const ruleRow=page.locator('tr').filter({hasText:'Проверка высоты UI'});await ruleRow.getByRole('button',{name:'Изменить',exact:true}).click();
  await child(1).getByLabel('Значение константы',{exact:true}).fill('1000');await modal.getByRole('button',{name:'Сохранить',exact:true}).click();
  await modal.locator('[data-editor-error]').filter({hasText:'Вариант'}).waitFor();
  await page.screenshot({path:path.join(dir,'03-existing-variant-protection.png'),fullPage:true});
  await modal.getByRole('button',{name:'Отмена',exact:true}).click();
  detail=await api(`/templates/${tpl.id}`);assert.equal(detail.rules.find(r=>r.id===ruleId).expression.args[1].value,100);
  await page.goto(url+'/#/orders/functions');await page.getByRole('heading',{name:'Библиотека функций',exact:true}).waitFor();
  await page.getByRole('button',{name:'Новая функция',exact:true}).click();
  const fnName='UI меньше '+stamp;
  await modal.locator('[name="code"]').fill('UI_LESS_'+stamp);await modal.locator('[name="name"]').fill(fnName);
  await modal.getByRole('button',{name:'Добавить формальный аргумент',exact:true}).click();await modal.getByRole('button',{name:'Добавить формальный аргумент',exact:true}).click();
  await head(root()).getByLabel(/^Операция/).selectOption('LT');
  await head(child(0)).getByLabel(/^Вид выражения/).selectOption('FORMAL');
  await head(child(1)).getByLabel(/^Вид выражения/).selectOption('FORMAL');
  await head(child(1)).getByLabel(/^Формальный аргумент/).selectOption('arg2');await save();
  fnId=(await api()).functions.find(f=>f.code==='UI_LESS_'+stamp).id;
  await page.locator('tr').filter({hasText:fnName}).getByRole('button',{name:'Проверить',exact:true}).click();
  await modal.locator('[name="arg1"]').fill('1');await modal.locator('[name="arg2"]').fill('2');await save();
  await page.getByText('Результат: Да',{exact:true}).waitFor();await page.screenshot({path:path.join(dir,'04-functions.png'),fullPage:true});
  await page.goto(url+'/#/orders/customers');await page.getByRole('heading',{name:'Заказчики',exact:true}).waitFor();
  await page.getByRole('button',{name:'Новый заказчик',exact:true}).click();
  await modal.locator('[name="name"]').fill('Заказчик UI '+stamp);await modal.locator('[name="phone"]').fill('+7 900 000-00-07');await save();
  customerId=(await api()).customers.find(c=>c.name==='Заказчик UI '+stamp).id;
  await page.goto(url+'/#/orders');await page.getByRole('button',{name:'Создать заказ',exact:true}).click();
  await modal.locator('[name="customerId"]').selectOption(String(customerId));await modal.locator('[name="orderNumber"]').fill('UI-'+stamp);await modal.locator('[name="orderDate"]').fill('2026-11-14');await save();
  await page.getByRole('heading',{name:'Заказ UI-'+stamp,exact:true}).waitFor();orderId=(await api()).orders.find(o=>o.order_number==='UI-'+stamp).id;
  await page.getByRole('button',{name:'Добавить готовое изделие',exact:true}).click();await modal.locator('[name="productId"]').selectOption(String(ready.id));await modal.locator('[name="quantity"]').fill('2');await save();
  await page.getByRole('button',{name:'Добавить исполнение по шаблону',exact:true}).click();await modal.locator('[name="templateId"]').selectOption(String(tpl.id));await save();
  const params=(await api(`/templates/${tpl.id}`)).parameters;const input=code=>modal.locator(`[name="p_${params.find(p=>p.code===code).id}"]`);
  await modal.locator('[name="code"]').fill('UI-VARIANT-'+stamp);await modal.locator('[name="name"]').fill('Воздуховод UI 500×300×1200');await modal.locator('[name="quantity"]').fill('3');
  await input('h').fill('500');await input('w').fill('300');await input('L').fill('800');await input('connection').selectOption('FLANGE');
  await modal.getByRole('button',{name:'Проверить ограничения',exact:true}).click();await modal.locator('[data-validation-result]').filter({hasText:'больше 1000'}).waitFor();
  await modal.getByRole('button',{name:'Создать и включить в заказ',exact:true}).click();await modal.locator('[data-form-error]').filter({hasText:'больше 1000'}).waitFor();
  assert.equal((await api(`/${orderId}`)).lines.length,1);await page.screenshot({path:path.join(dir,'05-invalid-configuration.png'),fullPage:true});
  await input('L').fill('1200');await modal.getByRole('button',{name:'Проверить ограничения',exact:true}).click();await modal.getByText('Все ограничения выполнены',{exact:true}).waitFor();
  await modal.getByRole('button',{name:'Создать и включить в заказ',exact:true}).click();await modal.waitFor({state:'hidden'});
  await page.getByText('Воздуховод UI 500×300×1200',{exact:true}).waitFor();
  const order=await api(`/${orderId}`);assert.equal(order.lines.length,2);variantId=order.lines.find(l=>l.variant_id).variant_id;assert.equal(order.lines.find(l=>l.variant_id).quantity,3);
  await page.screenshot({path:path.join(dir,'06-order-composition.png'),fullPage:true});
  const popupPromise=page.waitForEvent('popup');await page.getByRole('link',{name:'Печать заказа',exact:true}).click();const print=await popupPromise;await print.waitForLoadState();
  assert((await print.locator('body').innerText()).includes('Воздуховод UI 500×300×1200'));assert((await print.locator('body').innerText()).includes('Фланцах')||(await print.locator('body').innerText()).includes('фланцах'));
  await print.screenshot({path:path.join(dir,'07-print.png'),fullPage:true});await print.close();
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(dir,'08-mobile.png'),fullPage:true});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1),'Горизонтальное переполнение мобильной страницы');
  assert.deepEqual(errors,[]);console.log('PASS: UI-конструктор правила и функции, защита существующих вариантов, заказчик, заказ, готовое изделие, неверная/верная конфигурация, печать, мобильная ширина, отсутствие ошибок JavaScript.');
 }finally{
  if(orderId)await api('/'+orderId,'DELETE');
  if(variantId)await api('/variants/'+variantId,'DELETE');
  if(customerId)await api('/customers/'+customerId,'DELETE');
  if(ruleId){const t=(await api()).templates.find(t=>t.code==='ORDER-DUCT-TEMPLATE');await api(`/templates/${t.id}/rules/${ruleId}`,'DELETE');}
  if(fnId)await api('/functions/'+fnId,'DELETE');
  await browser.close();
 }
})().catch(e=>{console.error(e);process.exitCode=1;});
