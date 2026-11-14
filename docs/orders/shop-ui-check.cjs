// Только чтение данных и предварительная проверка конфигураций; новые строки не создаются.
const assert=require('node:assert/strict');
const path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const url=process.env.SHOP_TEST_URL||'http://127.0.0.1:8080';
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1500,height:1050}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  async function api(p='',data){const r=await page.request.fetch(url+'/api/v1/orders'+p,data?{method:'POST',data}:{});assert(r.ok(),`${r.status()} ${await r.text()}`);return r.json();}
  const all=await api();const templates=all.templates.filter(t=>t.code.startsWith('SHOP-'));const orders=all.orders.filter(o=>o.order_number.startsWith('SHOP-2026-'));
  assert.equal(templates.length,6);assert.equal(orders.length,8);
  let variants=0,lines=0;
  for(const t of templates){
   const detail=await api('/templates/'+t.id);assert.equal(detail.parameters.length,6);assert.equal(detail.variants.length,3);
   for(const v of detail.variants){assert((await api(`/templates/${t.id}/validate`,{values:v.values})).valid,v.code);variants++;}
  }
  for(const o of orders){const order=await api('/'+o.id);assert(order.lines.length>=2);for(const l of order.lines){assert(l.quantity>0);if(l.variant_id)assert.equal(l.parameters.length,6);}lines+=order.lines.length;}
  assert.equal(variants,18);assert.equal(lines,23);
  const screenshots=path.join(__dirname,'screenshots');
  await page.goto(url+'/#/orders');await page.getByRole('heading',{name:'Заказы',exact:true}).waitFor();
  await page.getByRole('link',{name:'SHOP-2026-0108',exact:true}).waitFor();await page.screenshot({path:path.join(screenshots,'11-shop-orders.png'),fullPage:true});
  const order=orders.find(o=>o.order_number==='SHOP-2026-0107');await page.goto(url+'/#/orders/'+order.id);await page.getByRole('heading',{name:'Заказ SHOP-2026-0107',exact:true}).waitFor();
  await page.getByText('Мобильный офис · 16 / 512 ГБ, без док-станции',{exact:true}).waitFor();await page.screenshot({path:path.join(screenshots,'12-shop-order.png'),fullPage:true});
  const pc=templates.find(t=>t.code==='SHOP-PC');await page.goto(url+'/#/orders/templates/'+pc.id);await page.getByRole('heading',{name:'Параметры конфигурации',exact:true}).waitFor();
  await page.getByText('ПК для игр и монтажа · 64 ГБ / SSD 2 ТБ',{exact:true}).waitFor();await page.screenshot({path:path.join(screenshots,'13-shop-template.png'),fullPage:true});
  assert.deepEqual(errors,[]);console.log('PASS: 6 шаблонов, 18 допустимых исполнений, 8 заказов, 23 позиции; параметры и количества доступны через API и интерфейс, ошибок JavaScript нет.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
