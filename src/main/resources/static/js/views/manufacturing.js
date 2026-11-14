import { esc, fmtNum, openForm, confirmDialog, loadingState, toast } from '../ui.js';
import { navigate } from '../router.js';

const base = '/api/v1/routes';
const money = new Intl.NumberFormat('ru-RU', {style:'currency',currency:'RUB'});
const rub = value => value == null ? '—' : esc(money.format(value));
const number = value => esc(fmtNum(value));
const button = (label, action, primary = false) =>
    `<button type="button" class="btn btn--${primary ? 'primary' : 'secondary'}" data-action="${action}">${label}</button>`;
const textField = (name,label,value='',max=255) => ({name,label,value,required:true,maxlength:max});
const numeric = (name,label,value='',integer=false) => ({
    name,label,value,type:'text',required:true,
    hint:integer ? 'Целое число' : 'До 6 знаков после запятой'
});
const select = (name,label,items,value='',required=true) => ({
    name,label,type:'select',required,value,
    options:[{value:'',label:required ? 'Выбрать…' : 'Покупной ресурс'},...items.map(([value,label])=>({value,label}))]
});
const positiveInt = (value,label) => {
    if (!/^\d+$/.test(String(value)) || !Number.isSafeInteger(Number(value)) ||
        Number(value) < 1 || Number(value) > 2147483647) throw new Error(`${label}: введите положительное целое число.`);
    return Number(value);
};
const decimal = (value,label) => {
    const raw = String(value).trim().replace(',','.');
    if (!/^\d{1,13}(?:\.\d{1,6})?$/.test(raw)) throw new Error(`${label}: введите число от нуля, до 6 знаков после запятой.`);
    return raw;
};
async function request(path = '', method = 'GET', body) {
    const response = await fetch(base+path,{method,headers:body ? {'Content-Type':'application/json'} : {},body:body ? JSON.stringify(body) : undefined});
    if (!response.ok) {
        const error = await response.json().catch(()=>null);
        throw new Error(error?.message || `Не удалось выполнить действие (${response.status}).`);
    }
    return response.status === 204 ? null : response.json();
}
const editQuery = row => row ? `?id=${row.id}` : '';
const table = (headers, rows, empty = 'Пока нет записей.') => rows.length
    ? `<div class="table-wrap"><table class="table"><thead><tr>${headers.map(h=>`<th scope="col">${h}</th>`).join('')}</tr></thead>
       <tbody>${rows.map(cells=>`<tr>${cells.map((c,i)=>`<td data-label="${headers[i]}">${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`
    : `<p class="route-empty">${empty}</p>`;
const tabs = [['steps','Операции'],['calculation','Расчёт партии'],['lookups','Справочники']];
let sequence = 0;
let calculationSettings = {quantity:3,group:'',profession:''};

async function renderPage(container, route) {
    const ticket = ++sequence;
    const hash = location.hash;
    const active = () => ticket === sequence && hash === location.hash;
    const segment = route.segments[1];
    const id = /^\d+$/.test(segment || '') ? Number(segment) : null;
    const tab = segment === 'lookups' ? 'lookups' : route.query.tab === 'calculation' ? 'calculation' : 'steps';
    const expanded = new Set(Array.from(container.querySelectorAll('details.route-step[open]'),e=>e.dataset.step));
    container.innerHTML = `<div class="page">${loadingState('Загрузка маршрутов…')}</div>`;
    const [overview, detail] = await Promise.all([request(),id ? request(`/${id}`) : null]);
    if (!active()) return;
    const items = overview.catalog.filter(n=>n.node_kind !== 'GROUP' && n.unit);
    const byId = (rows,id) => rows.find(row=>row.id===id);
    const resource = id => byId(items,id);
    const option = (rows,label = row=>row.name) => rows.map(row=>[row.id,label(row)]);
    const itemOptions = option(items,n=>`${n.name} · ${n.code}`);
    const unit = n => esc(({PCS:'шт.',M:'м',KG:'кг'})[n?.unit] || n?.unit || 'ед.');
    const actions = new Map();
    const on = (label, callback, primary=false) => {
        const key = String(actions.size);
        actions.set(key,callback);
        return button(label,key,primary);
    };
    const refresh = () => active() ? renderPage(container,route) : Promise.resolve();
    const edit = async (title,fields,submit,subtitle,onMount) => {
        const result = await openForm({title,fields,subtitle,onMount,submitLabel:'Сохранить',onSubmit:submit});
        if (result === null || !active()) return;
        toast.success('Изменения сохранены');
        return result;
    };
    const remove = async (title,path) => {
        if (!await confirmDialog({title,message:'Запись будет удалена.',confirmLabel:'Удалить',danger:true})) return;
        await request(path,'DELETE');
        toast.success('Запись удалена');
        return true;
    };
    const createRoute = async () => {
        const taken = new Set(overview.routes.map(r=>r.product_id));
        const available = itemOptions.filter(([id])=>!taken.has(id));
        if (!available.length) throw new Error('Добавь модель или изделие в классификатор. У каждого изделия может быть один маршрут.');
        const result = await edit('Новый маршрут',[
            textField('code','Код маршрута','',100),select('productId','Изделие',available)
        ],v=>request('','POST',{code:v.code,productId:Number(v.productId)}));
        if (result) navigate(`/routes/${result.id}`);
    };
    const sidebar = `<aside class="route-sidebar card">
        <div class="route-sidebar-head"><h2>Изделия</h2>${on('Новый маршрут',createRoute,true)}</div>
        <label class="route-search">Поиск маршрута<input class="input" type="search" data-route-search placeholder="Название или код"></label>
        <div class="route-list">${overview.routes.length ? overview.routes.map(r=>`
            <a class="route-link ${r.id===id ? 'is-selected' : ''}" href="#/routes/${r.id}" ${r.id===id?'aria-current="page"':''}>
                <strong>${esc(r.product_name)}</strong><span>${esc(r.code)} · ${number(r.step_count)} оп.</span>
            </a>`).join('') : '<p class="route-empty">Создай первый маршрут для изделия из каталога.</p>'}
            <p class="route-empty" data-no-matches hidden>Маршруты не найдены.</p></div>
    </aside>`;
    const showLookups = '#/routes/lookups';
    const nav = `<nav class="route-tabs" aria-label="Разделы маршрутной технологии">${tabs.map(([key,label])=>{
        const href = key==='lookups' ? showLookups : id ? `#/routes/${id}${key==='calculation'?'?tab=calculation':''}` : '#/routes';
        return key==='calculation' && !id ? '' : `<a class="${key===tab?'is-selected':''}" href="${href}" ${key===tab?'aria-current="page"':''}>${label}</a>`;
    }).join('')}</nav>`;

    function stepForm(row) {
        const next = (detail.steps.at(-1)?.step_number || 0)+10;
        return edit(row?'Изменить операцию':'Добавить операцию',[
            numeric('stepNumber','Номер шага',row?.step_number ?? next,true),
            textField('name','Название операции',row?.name),
            select('operationTypeId','Вид операции',option(overview.types),row?.operation_type_id ?? '')
        ],v=>request(`/${id}/steps${editQuery(row)}`,'POST',{
            stepNumber:positiveInt(v.stepNumber,'Номер шага'),name:v.name,operationTypeId:Number(v.operationTypeId)
        }), 'Например, установить комплект охлаждения. Номер определяет порядок шага.').then(result=>result && refresh());
    }
    function inputForm(step,row) {
        const sources = overview.steps.filter(s=>s.route_id!==id);
        return edit(row?'Изменить норму расхода':'Добавить расход',[
            select('resourceId','Ресурс',itemOptions,row?.resource_id ?? ''),
            numeric('resourceQuantity','Количество ресурса',row?.resource_quantity ?? '1'),
            numeric('outputQuantity','На сколько изделий',row?.output_quantity ?? '1',true),
            select('sourceOperationId','Шаг изготовления компонента',[],row?.source_operation_id ?? '',false)
        ],v=>request(`/steps/${step}/inputs${editQuery(row)}`,'POST',{
            resourceId:Number(v.resourceId),resourceQuantity:decimal(v.resourceQuantity,'Расход'),
            outputQuantity:positiveInt(v.outputQuantity,'Выпуск'),sourceOperationId:v.sourceOperationId ? Number(v.sourceOperationId) : null
        }), 'Для покупной детали оставь шаг изготовления пустым.',form=>{
            const resourceField=form.elements.resourceId;
            const sourceField=form.elements.sourceOperationId;
            const showSources = selected=>{
                const matches=sources.filter(s=>s.product_id===Number(resourceField.value));
                sourceField.replaceChildren(new Option('Покупной ресурс',''),...matches.map(s=>
                    new Option(`${s.product_name} · ${s.step_number} — ${s.name}`,String(s.id))));
                sourceField.value=matches.some(s=>s.id===selected) ? String(selected) : '';
            };
            showSources(row?.source_operation_id);
            resourceField.addEventListener('change',()=>showSources());
        }).then(result=>result && refresh());
    }
    function assetForm(step,row) {
        return edit(row?'Изменить оснащение':'Добавить оснащение',[
            select('assetId','Инструмент или оборудование',option(overview.assets),row?.asset_id ?? ''),
            numeric('quantity','Количество',row?.quantity ?? 1,true)
        ],v=>request(`/steps/${step}/assets${editQuery(row)}`,'POST',{
            assetId:Number(v.assetId),quantity:positiveInt(v.quantity,'Количество')
        })).then(result=>result && refresh());
    }
    function laborForm(step,row) {
        return edit(row?'Изменить норму труда':'Добавить норму труда',[
            select('laborRateId','Профессия',option(overview.rates,r=>`${r.profession} · ${money.format(r.hourly_rate)}/ч`),row?.labor_rate_id ?? ''),
            numeric('setupMinutes','Подготовка партии, мин',row?.setup_minutes ?? 0),
            numeric('minutesPerUnit','Работа на изделие, мин',row?.minutes_per_unit ?? 0)
        ],v=>request(`/steps/${step}/labor${editQuery(row)}`,'POST',{
            laborRateId:Number(v.laborRateId),setupMinutes:decimal(v.setupMinutes,'Подготовка'),
            minutesPerUnit:decimal(v.minutesPerUnit,'Время на изделие')
        })).then(result=>result && refresh());
    }
    async function priceForm(node) {
        const done = await edit('Закупочная цена',[numeric('price','Цена за единицу, ₽',node.price)],
            async v=>{ await request(`/prices/${node.id}`,'PUT',{price:decimal(v.price,'Цена')}); return true; },node.name);
        if (done) await refresh();
    }
    const lineActions = (type,row,callback) => `<div class="route-actions">${on('Изменить',callback)}
        ${on('Удалить',async()=>{if (await remove('Удалить запись?',`/steps/${row.operation_id}/lines/${type}/${row.id}`)) await refresh();})}</div>`;
    function stepCard(step,index) {
        const inputs = detail.inputs.filter(x=>x.operation_id===step.id);
        const assets = detail.assets.filter(x=>x.operation_id===step.id);
        const labor = detail.labor.filter(x=>x.operation_id===step.id);
        return `<details class="route-step card" data-step="${step.id}" ${expanded.has(String(step.id)) || (!expanded.size && index===0)?'open':''}>
            <summary><span class="route-step-number">${step.step_number}</span><strong>${esc(step.name)}</strong><span class="route-pill">${esc(step.type_name)}</span></summary>
            <div class="route-step-body">
                <div class="route-actions">${on('Изменить шаг',()=>stepForm(step))}
                ${on('Удалить шаг',async()=>{if(await remove('Удалить шаг и его нормы?',`/${id}/steps/${step.id}`)) await refresh();})}</div>
                <section><div class="route-section-head"><h3>Материалы и комплектующие</h3>${on('Добавить расход',()=>inputForm(step.id))}</div>
                ${table(['Ресурс','Норма расхода','Источник','Закупочная цена','Действия'],inputs.map(row=>{
                    const n = resource(row.resource_id);
                    const s = byId(overview.steps,row.source_operation_id);
                    return [esc(n?.name),`${number(row.resource_quantity)} ${unit(n)} на ${number(row.output_quantity)}`,
                        s ? `<a href="#/routes/${s.route_id}">${esc(s.product_name)} · ${s.step_number}</a>` : 'Покупной ресурс',
                        s ? 'По вложенному маршруту' : n ? `${rub(n.price)} ${on(n.price==null?'Задать':'Изменить цену',()=>priceForm(n))}` : '—',
                        lineActions('INPUT',row,()=>inputForm(step.id,row))];
                }))}</section>
                <section><div class="route-section-head"><h3>Оснащение</h3>${on('Добавить оснащение',()=>assetForm(step.id))}</div>
                ${table(['Средство оснащения','Количество','Действия'],assets.map(row=>[
                    esc(byId(overview.assets,row.asset_id)?.name),number(row.quantity),lineActions('ASSET',row,()=>assetForm(step.id,row))
                ]))}</section>
                <section><div class="route-section-head"><h3>Нормы труда</h3>${on('Добавить норму труда',()=>laborForm(step.id))}</div>
                ${table(['Профессия','Подготовка, мин','На изделие, мин','Действия'],labor.map(row=>[
                    esc(byId(overview.rates,row.labor_rate_id)?.profession),number(row.setup_minutes),number(row.minutes_per_unit),
                    lineActions('LABOR',row,()=>laborForm(step.id,row))
                ]))}</section>
            </div></details>`;
    }
    function lookupCard(key,title,rows,headers) {
        const rates = key==='RATES';
        const assets = key==='ASSETS';
        const form = row => edit(row?'Изменить запись':'Добавить запись',[
            ...(rates ? [] : [textField('code','Код',row?.code,100)]),
            textField('name',rates?'Профессия':'Название',rates ? row?.profession : row?.name),
            ...(rates ? [numeric('hourlyRate','Ставка в час, ₽',row?.hourly_rate)] : []),
            ...(assets ? [select('kind','Вид оснащения',[['TOOL','Инструмент'],['EQUIPMENT','Оборудование']],row?.kind ?? '')] : [])
        ],v=>request(`/lookups/${key}${editQuery(row)}`,'POST',{
            ...v,...(rates ? {hourlyRate:decimal(v.hourlyRate,'Ставка')} : {})
        })).then(result=>result && refresh());
        return `<section class="card route-panel"><div class="route-section-head"><h2>${title}</h2>${on('Добавить',()=>form())}</div>
            ${table(headers,rows.map(row=>[...(rates ? [esc(row.profession),rub(row.hourly_rate)] : [
                esc(row.code),esc(row.name),...(assets?[row.kind==='TOOL'?'Инструмент':'Оборудование']:[])
            ]),`<div class="route-actions">${on('Изменить',()=>form(row))}
              ${on('Удалить',async()=>{if(await remove('Удалить запись справочника?',`/lookups/${key}/${row.id}`)) await refresh();})}</div>`]))}
            </section>`;
    }
    const lookupPage = () => `
        ${lookupCard('TYPES','Виды операций',overview.types,['Код','Название','Действия'])}
        ${lookupCard('ASSETS','Инструменты и оборудование',overview.assets,['Код','Название','Вид','Действия'])}
        ${lookupCard('RATES','Тарифы труда',overview.rates,['Профессия','Ставка за час','Действия'])}
        <section class="card route-panel"><div class="route-section-head"><h2>Закупочные цены</h2>${on('Задать цену',async()=>{
            const result = await edit('Закупочная цена',[select('node','Ресурс',itemOptions),numeric('price','Цена за единицу, ₽')],
                async v=>{await request(`/prices/${v.node}`,'PUT',{price:decimal(v.price,'Цена')});return true;});
            if(result) await refresh();
        })}</div>
        ${table(['Ресурс','Цена за единицу','Действия'],items.filter(n=>n.price!=null).map(n=>[
            esc(n.name),rub(n.price),on('Изменить',()=>priceForm(n))
        ]))}</section>`;
    const filterOptions = (rows,value,label) => rows.map(r=>`<option value="${r.id}" ${String(r.id)===String(value)?'selected':''}>${esc(label(r))}</option>`).join('');
    const calculation = () => `<section class="card route-panel">
        <h2>Расчёт партии</h2><p class="text-muted">Материалы и время можно отфильтровать. Стоимость в итогах относится ко всей партии.</p>
        <form class="route-calc-form" data-calculate>
            <label>Количество изделий<input class="input" name="quantity" type="number" min="1" max="2147483647" step="1" required value="${calculationSettings.quantity}"></label>
            <label>Группа материалов<select class="input" name="group"><option value="">Все группы</option>${filterOptions(overview.catalog.filter(n=>n.node_kind==='GROUP'),calculationSettings.group,n=>n.name)}</select></label>
            <label>Профессия<select class="input" name="profession"><option value="">Все профессии</option>${filterOptions(overview.rates,calculationSettings.profession,n=>n.profession)}</select></label>
            <button type="submit" class="btn btn--primary">Рассчитать</button>
        </form>
        <div data-results aria-live="polite"><p class="route-empty">Укажи количество и нажми «Рассчитать».</p></div></section>`;
    const routeHead = () => `<section class="card route-panel route-header">
        <div><span class="route-pill">${esc(detail.route.code)}</span><h2>${esc(detail.route.product_name)}</h2>
            <a href="#/tree/${detail.route.product_id}">Открыть карточку изделия</a></div>
        <div class="route-actions">${on('Изменить код',async()=>{
            const result=await edit('Изменить маршрут',[textField('code','Код маршрута',detail.route.code,100)],
                v=>request(`?id=${id}`,'POST',{code:v.code,productId:detail.route.product_id}));
            if (result) await refresh();
        })}${on('Удалить маршрут',async()=>{if(await remove('Удалить маршрут и его шаги?',`/${id}`)) navigate('/routes');})}</div></section>`;
    container.innerHTML = `<div class="page manufacturing-page"><div class="page-head"><div class="page-head__text">
        <h1>Маршруты изготовления</h1><p class="page-head__sub">Операции сборки, оснащение, нормы расхода и расчёт затрат на партию.</p>
        </div></div><div class="route-layout">${sidebar}<main class="route-content">${nav}
        ${tab==='lookups' ? lookupPage() : detail ? routeHead()+(tab==='calculation' ? calculation() : `
            <div class="route-section-head"><h2>Порядок изготовления</h2>${on('Добавить операцию',()=>stepForm(),true)}</div>
            ${detail.steps.length ? detail.steps.map(stepCard).join('') : '<div class="card route-panel"><p class="route-empty">Добавь первый шаг, чтобы задать материалы, инструменты и время.</p></div>'}`)
            : '<section class="card route-panel"><h2>Выбери изделие</h2><p class="text-muted">Открой маршрут слева, чтобы увидеть операции и рассчитать партию. Карточки товаров и материалов можно добавить в <a href="#/tree">классификаторе</a>.</p></section>'}
        </main></div></div>`;
    const view = container.querySelector('.manufacturing-page');
    view.addEventListener('click',async event=>{
        const trigger = event.target.closest('button[data-action]');
        if (!trigger || trigger.disabled) return;
        trigger.disabled = true;
        try { await actions.get(trigger.dataset.action)?.(); }
        catch (error) { toast.error(error.message); }
        finally { trigger.disabled = false; }
    });
    view.querySelector('[data-route-search]').addEventListener('input',event=>{
        const query=event.target.value.trim().toLocaleLowerCase('ru');
        let visible=0;
        view.querySelectorAll('.route-link').forEach(link=>{
            link.hidden=!link.textContent.toLocaleLowerCase('ru').includes(query);
            if (!link.hidden) visible++;
        });
        view.querySelector('[data-no-matches]').hidden=!query || visible>0;
    });
    view.querySelector('[data-calculate]')?.addEventListener('submit',async event=>{
        event.preventDefault();
        const form=event.currentTarget;
        const results=view.querySelector('[data-results]');
        const submit=form.querySelector('button[type=submit]');
        submit.disabled=true;
        results.innerHTML=loadingState('Расчёт партии…');
        try {
            const values=new FormData(form);
            const quantity=positiveInt(values.get('quantity'),'Размер партии');
            const group=values.get('group');
            const profession=values.get('profession');
            calculationSettings={quantity,group,profession};
            const params=new URLSearchParams({quantity});
            if(group) params.set('group',group);
            if(profession) params.set('profession',profession);
            const data=await request(`/${id}/calculation?${params}`);
            if(active()) results.innerHTML=calculationResults(data,unit);
        } catch(error) {
            if(active()) results.innerHTML=`<p class="route-alert" role="alert">${esc(error.message)}</p>`;
        } finally { submit.disabled=false; }
    });
}
function calculationResults(data,unit) {
    if (!data.operations.length) return '<p class="route-empty">Добавь операции в маршрут, чтобы рассчитать партию.</p>';
    const incomplete=data.missingPrices.length>0;
    return `<div class="route-totals">
        <div><span>${incomplete?'Материалы по известным ценам':'Материалы'}</span><strong>${rub(data.materialTotal)}</strong></div>
        <div><span>Работы</span><strong>${rub(data.laborTotal)}</strong></div>
        <div><span>Итого на партию</span><strong>${incomplete?'Нужны цены':rub(data.total)}</strong></div>
        </div>${incomplete?`<p class="route-alert">Укажи закупочные цены в <a href="#/routes/lookups">справочнике</a>: ${data.missingPrices.map(esc).join(', ')}.</p>`:''}
        <h3>Потребность в материалах</h3>${table(['Ресурс','Количество','Цена за единицу','Стоимость'],data.materials.map(m=>[
            `<a href="#/tree/${m.id}">${esc(m.name)}</a>`,`${number(m.quantity)} ${unit(m)}`,rub(m.unit_price),rub(m.amount)
        ]),'Для выбранной группы материалов расхода нет.')}
        <h3>Рабочее время</h3>${table(['Профессия','Время, мин','Ставка за час','Стоимость'],data.labor.map(l=>[
            esc(l.profession),number(l.minutes),rub(l.hourly_rate),rub(l.amount)
        ]),'Для выбранной профессии норм труда нет.')}
        <h3>Операции на всех уровнях</h3>${table(['Шаг','Изделие','Операция','Количество','Оснащение'],data.operations.map(o=>[
            `<span class="route-level">Уровень ${number(o.depth+1)}</span> ${number(o.step_number)}`,
            esc(o.product_name),`${esc(o.name)} <span class="route-pill">${esc(o.type_name)}</span>`,number(o.batch_quantity),esc(o.equipment || '—')
        ]))}`;
}

export { renderPage as render };

