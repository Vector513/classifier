import { api } from '../api.js';
import { esc, fmtNum, fmtDate, openForm, toast, confirmDialog, loadingState } from '../ui.js';
import { navigate } from '../router.js';

const kinds = { ASSEMBLY: 'Сборочная единица', PART: 'Деталь', MATERIAL: 'Материал', PURCHASED: 'Покупное изделие' };
const button = (label, attrs = '') => `<button type="button" class="btn btn--secondary" ${attrs}>${label}</button>`;
const table = (heads, rows) => `<div class="table-wrap"><table class="table"><thead><tr>${heads.map(h=>`<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.join('') : `<tr><td colspan="${heads.length}">Нет данных</td></tr>`}</tbody></table></div>`;
const row = cells => `<tr>${cells.map(c=>`<td>${c}</td>`).join('')}</tr>`;
const label = s => `${s.productCode} · версия ${s.revision} · #${s.id}`;
const flatten = nodes => nodes.flatMap(n => [n, ...flatten(n.children || [])]);

export async function render(container, route) {
    container.innerHTML = `<div class="page">${loadingState('Загрузка спецификаций…')}</div>`;
    const [products, specs, roots, units] = await Promise.all([
        api.bom.products(), api.bom.specifications(), api.nodes.tree(), api.units.all(),
    ]);
    const nodes = flatten(roots);
    const selected = Number(route.segments[1]);
    const spec = specs.find(s => s.id === selected);
    const refresh = () => render(container, route);
    const act = async fn => { try { await fn(); } catch (err) { toast.error(err.message); } };
    const form = async options => {
        const initialHash = window.location.hash;
        if (await openForm(options) !== null && window.location.hash === initialHash) await refresh();
    };
    const options = products.map(p => ({value:p.id,label:`${p.code} — ${p.name} (${p.unitCode})`}));
    const released = specs.filter(s => s.status === 'RELEASED');
    container.innerHTML = `<div class="page bom-page">
        <div class="page-head"><div class="page-head__text"><h1>Спецификации изделий</h1>
        <div class="page-head__sub">Состав, изменения и нормы расхода. Изделия и классы ресурсов — из общего классификатора.</div></div>
        <div class="page-head__actions">${button('Добавить изделие','data-register')}${button('Создать спецификацию','data-create')}</div></div>
        <div class="card"><h2>Версии и модификации</h2>${table(['Изделие / версия','Статус','База','Описание изменения'],specs.map(s=>row([
            `<a href="#/bom/${s.id}">${esc(label(s))}</a>`,s.status==='DRAFT'?'Черновик':'Утверждена',
            s.baseId?`<a href="#/bom/${s.baseId}">#${s.baseId}</a>`:'—',esc(s.description)])))}</div>
        <div id="bom-detail"></div>
        <details class="card"><summary>Справочник изделий (${products.length})</summary>
        <p>Новые изделия создаются в <a href="#/tree">классификаторе</a>, затем регистрируются здесь с типом и единицей нормы расхода.</p>
        ${table(['Код','Название','Тип','Единица'],products.map(p=>row([esc(p.code),esc(p.name),kinds[p.kind],esc(p.unitCode)])))}</details>
    </div>`;
    container.querySelector('[data-register]').onclick = () => act(()=>form({
        title:'Добавить изделие в спецификации', fields:[
            {name:'nodeId',label:'Изделие классификатора',type:'select',required:true,full:true,options:
                nodes.filter(n=>!(n.children||[]).length && !products.some(p=>p.id===n.id)).map(n=>({value:n.id,label:`${n.code} — ${n.name}`}))},
            {name:'kind',label:'Тип',type:'select',required:true,options:Object.entries(kinds).map(([value,label])=>({value,label}))},
            {name:'unitId',label:'Единица нормы расхода',type:'select',required:true,options:units.map(u=>({value:u.id,label:`${u.code} — ${u.name}`}))},
        ], onSubmit:v=>api.bom.register({nodeId:Number(v.nodeId),kind:v.kind,unitId:Number(v.unitId)})
    }));
    const create = base => act(()=>form({title:base?'Новая версия или модификация':'Новая спецификация',fields:[
        {name:'productId',label:'Изделие',type:'select',required:true,options,value:base?.productId},
        {name:'baseId',label:'Базовая утвержденная версия',type:'select',value:base?.id,options:[{value:'',label:'Без базы'},...released.map(s=>({value:s.id,label:label(s)}))]},
        {name:'description',label:'Описание изменения',required:true,full:true,maxlength:1000},
    ],onSubmit:async v=>{
        const result=await api.bom.create({productId:Number(v.productId),baseId:v.baseId?Number(v.baseId):null,description:v.description});
        navigate(`/bom/${result.id}`);
    }}));
    container.querySelector('[data-create]').onclick=()=>create(null);
    if (!spec) {
        if (selected) container.querySelector('#bom-detail').textContent='Спецификация не найдена.';
        return;
    }
    const [lines, local] = await Promise.all([api.bom.lines(spec.id),api.bom.localPositions(spec.id)]);
    const editable = spec.status==='DRAFT';
    const detail=container.querySelector('#bom-detail');
    detail.innerHTML=`<section class="card"><div class="page-head"><div class="page-head__text"><h2>${esc(label(spec))}</h2>
        <p>${esc(spec.productName)} · ${esc(spec.description)}</p><p>Создана: ${fmtDate(spec.createdAt)}${spec.releasedAt?` · Утверждена: ${fmtDate(spec.releasedAt)}`:''}</p></div>
        <div class="page-head__actions">${editable?button('Добавить позицию','data-add')+button('Утвердить','data-release')+button('Удалить черновик','data-remove'):button('Создать изменение / модификацию','data-revise')}</div></div>
        <p>Норма задаётся на одну единицу изделия. Без версии компонента он учитывается как ресурс без раскрытия состава.</p>
        ${table(['Позиция','Компонент','Норма','Версия компонента','Источник',...(editable?['Действия']:[])],lines.map(l=>row([
            l.position,`${esc(l.code)} — ${esc(l.name)}`,`${fmtNum(l.quantity)} ${esc(l.unitCode)}`,
            l.componentSpecificationId?`<a href="#/bom/${l.componentSpecificationId}">#${l.componentSpecificationId}</a>`:'Без раскрытия',
            l.sourceId===spec.id?'Собственная':`База #${l.sourceId}`,
            ...(editable?[button('Изменить',`data-edit="${l.position}"`)+button('Исключить',`data-exclude="${l.position}"`)]:[])])))}
        ${editable&&local.length?`<p>Отменить локальное изменение (восстановить базовую позицию): ${local.map(p=>button(String(p),`data-reset="${p}"`)).join(' ')}</p>`:''}
        </section>
        <section class="card"><h2>Раскрытие состава и сводные нормы</h2>
        <form id="bom-calc" class="form-grid"><label class="field">Количество изделий<input class="input" name="quantity" type="number" value="1" min="0.000001" step="any" required></label>
        <label class="field">Класс ресурсов<select class="select" name="classId" required>${nodes.map(n=>`<option value="${n.id}">${esc(n.code)} — ${esc(n.name)}</option>`).join('')}</select></label>
        <button class="btn btn--primary" type="submit">Рассчитать</button></form><div id="bom-results" aria-live="polite"></div></section>`;
    const lineForm = line => act(()=>form({title:line?'Изменить позицию':'Добавить позицию',fields:[
        {name:'position',label:'Номер позиции',type:'number',min:1,step:1,required:true,value:line?.position??Math.max(0,...lines.map(l=>l.position),...local)+1},
        {name:'componentId',label:'Компонент',type:'select',required:true,options:options.filter(o=>o.value!==spec.productId),value:line?.componentId},
        {name:'quantity',label:'Норма расхода',required:true,value:line?.quantity??'1',hint:'Положительное число, до 6 знаков после точки.'},
        {name:'componentSpecificationId',label:'Утвержденная версия выбранного компонента',type:'select',value:line?.componentSpecificationId,options:[{value:'',label:'Без раскрытия состава'},...released.filter(s=>s.productId!==spec.productId).map(s=>({value:s.id,label:label(s)}))]},
    ],onSubmit:async v=>{
        if(line && Number(v.position)!==line.position) throw {field:'position',message:'Для перенумерации исключите старую позицию и добавьте новую.'};
        await api.bom.putLine(spec.id,Number(v.position),{componentId:Number(v.componentId),quantity:String(v.quantity).replace(',','.'),componentSpecificationId:v.componentSpecificationId?Number(v.componentSpecificationId):null});
    }}));
    detail.querySelector('[data-add]')?.addEventListener('click',()=>lineForm(null));
    detail.querySelector('[data-revise]')?.addEventListener('click',()=>create(spec));
    detail.querySelector('[data-release]')?.addEventListener('click',()=>act(async()=>{
        if(await confirmDialog({title:'Утвердить спецификацию?',message:'Состав версии будет зафиксирован. Дальнейшие изменения оформляются новой версией.',confirmLabel:'Утвердить'})){
            await api.bom.release(spec.id); await refresh();
        }
    }));
    detail.querySelector('[data-remove]')?.addEventListener('click',()=>act(async()=>{
        if(await confirmDialog({title:'Удалить черновик?',message:'Локальные изменения этого черновика будут удалены.',danger:true,confirmLabel:'Удалить'})){
            await api.bom.remove(spec.id); navigate('/bom');
        }
    }));
    detail.querySelectorAll('[data-edit]').forEach(b=>b.onclick=()=>lineForm(lines.find(l=>l.position===Number(b.dataset.edit))));
    detail.querySelectorAll('[data-exclude]').forEach(b=>b.onclick=()=>act(async()=>{await api.bom.exclude(spec.id,Number(b.dataset.exclude));await refresh();}));
    detail.querySelectorAll('[data-reset]').forEach(b=>b.onclick=()=>act(async()=>{await api.bom.reset(spec.id,Number(b.dataset.reset));await refresh();}));
    detail.querySelector('#bom-calc').onsubmit=e=>{e.preventDefault();act(async()=>{
        const data=new FormData(e.target), quantity=data.get('quantity'), classId=Number(data.get('classId'));
        const results=detail.querySelector('#bom-results');
        results.innerHTML=loadingState('Расчёт…');
        try {
            const [explosion,totals]=await Promise.all([api.bom.explode(spec.id,quantity),api.bom.totals(spec.id,quantity,classId)]);
            results.innerHTML=`<h3>Все вхождения на полную глубину</h3>${table(['Путь позиций','Уровень','Компонент','Норма','Расход'],explosion.map(e=>row([
                e.path.join(' → '),e.depth,`${esc(e.line.code)} — ${esc(e.line.name)}`,fmtNum(e.line.quantity),`${fmtNum(e.totalQuantity)} ${esc(e.line.unitCode)}`])))}
                <h3>Сводные нормы по выбранному классу и подклассам</h3>${table(['Ресурс','Суммарный расход','Единица'],totals.map(t=>row([`${esc(t.code)} — ${esc(t.name)}`,fmtNum(t.totalQuantity),esc(t.unitCode)])))}`;
        } catch(err) { results.textContent=err.message; }
    });};
}
