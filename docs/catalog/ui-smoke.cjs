// Запускать на тестовом экземпляре: BOM_TEST_URL=http://127.0.0.1:18083 node docs/catalog/ui-smoke.cjs
const assert=require('node:assert/strict');
const path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const url=process.env.BOM_TEST_URL;
if(!url)throw new Error('Укажите URL отдельного тестового экземпляра.');
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:1500,height:1050}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let original,model,color,empty;
 const flatten=ns=>ns.flatMap(n=>[n,...flatten(n.children||[])]);
 try{
  const roots=await (await page.request.get(url+'/api/v1/nodes/tree')).json();
  const nodes=flatten(roots);model=nodes.find(n=>n.code==='PHONES-APPLE-IP16');
  const sku=nodes.find(n=>n.code==='SKU-IP16-BLACK-128');
  const created=await page.request.post(url+'/api/v1/nodes',{data:{code:'CHECK-EMPTY-'+Date.now(),name:'Пустая группа для проверки',nodeKind:'GROUP'}});
  assert(created.ok());empty=await created.json();
  assert.equal(model.nodeKind,'MODEL');assert.equal(sku.nodeKind,'PRODUCT');assert.equal(empty.nodeKind,'GROUP');assert(empty.isTerminal);
  const effective=await (await page.request.get(url+`/api/v1/nodes/${model.id}/enumerations/effective`)).json();
  color=effective.find(e=>e.enumerationCode==='PHONE-COLORS');
  const bindings=await (await page.request.get(url+`/api/v1/nodes/${model.id}/enumeration-bindings`)).json();
  original=bindings.find(b=>b.enumerationId===color.enumerationId);
  await page.goto(url+'/#/tree/'+model.id);
  await page.getByRole('tab',{name:'Варианты и свойства',exact:true}).click();
  const block=page.locator(`[data-enum-block="${color.enumerationId}"]`);
  await block.getByText('Чёрный',{exact:true}).waitFor();
  assert.equal(await block.locator('[data-value]').count(),0,'Модель показывает доступные варианты, не выбор одного цвета');
  assert.equal(await block.locator('.badge').count(),3);
  await page.screenshot({path:path.join(__dirname,'screenshots/model-options.png'),fullPage:true});
  await block.getByRole('button',{name:'Настроить доступные варианты'}).click();
  const black=color.values.find(v=>v.code==='BLACK');
  await page.locator(`[data-option="${black.id}"]`).uncheck();
  await page.getByRole('button',{name:'Сохранить варианты',exact:true}).click();
  await page.locator('[data-options-error]').filter({hasText:'Выбранное значение'}).waitFor();
  assert(await page.locator('.modal').isVisible(),'Несогласованное изменение должно оставить диалог открытым');
  await page.screenshot({path:path.join(__dirname,'screenshots/restriction-protection.png'),fullPage:true});
  await page.getByRole('button',{name:'Отмена',exact:true}).click();
  await page.goto(url+'/#/tree/'+sku.id);
  await page.locator(`[data-enum-block="${color.enumerationId}"] [data-value]`).first().waitFor();
  assert.equal(await page.locator(`[data-value="${black.id}"]`).getAttribute('aria-pressed'),'true');
  assert.equal(await page.locator('.node-detail__meta').getByText('Изделие / исполнение',{exact:true}).count(),1);
  await page.screenshot({path:path.join(__dirname,'screenshots/sku-selected-color.png'),fullPage:true});
  await page.getByRole('button',{name:'Изменить',exact:true}).click();
  assert.equal(await page.locator('[name="nodeKind"]').inputValue(),'PRODUCT');
  await page.getByRole('button',{name:'Отмена',exact:true}).click();
  await page.goto(url+'/#/tree/'+empty.id);
  await page.getByRole('tab',{name:'Обзор',exact:true}).click();
  await page.getByText('Потомков пока нет.',{exact:false}).waitFor();
  assert.equal(await page.getByText('Изделие (лист)',{exact:true}).count(),0);
  assert.deepEqual(errors,[]);
  console.log('PASS: модель с несколькими вариантами; конкретный цвет исполнения; защита ограничения существующих исполнений; явный тип пустой группы; форма типа записи.');
 }finally{
  if(empty)await page.request.delete(url+`/api/v1/nodes/${empty.id}`);
  if(original&&model&&color)await page.request.put(url+`/api/v1/nodes/${model.id}/enumeration-bindings/${color.enumerationId}`,{data:{allowedValueIds:original.restricted?original.allowedValueIds:null}});
  await browser.close();
 }
})().catch(e=>{console.error(e);process.exitCode=1;});
