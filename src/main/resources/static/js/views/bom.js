import { api } from '../api.js';
import { esc, fmtNum, fmtDate, openForm, toast, confirmDialog, loadingState } from '../ui.js';
import { navigate } from '../router.js';

const kinds = { ASSEMBLY: 'Сборочная единица', PART: 'Деталь', MATERIAL: 'Материал', PURCHASED: 'Покупное изделие' };
const kindHints = {
    ASSEMBLY: 'Собираем из нескольких компонентов. Например, корпус с креплениями.',
    PART: 'Изготавливаем отдельную деталь из материала. Например, пластину из стали.',
    MATERIAL: 'Расходуем при изготовлении: сталь, краска, проволока.',
    PURCHASED: 'Получаем готовым от поставщика. Например, болт М6. Физически он тоже деталь, но здесь важен способ получения.',
};
const button = (label, attrs = '', primary = false) => `<button type="button" class="btn ${primary ? 'btn--primary' : 'btn--secondary'}" ${attrs}>${label}</button>`;
const table = (heads, rows, empty = 'Пока нет записей.') => `<div class="table-wrap"><table class="table"><thead><tr>${heads.map(h=>`<th scope="col">${h}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.map(cells=>`<tr>${cells.map((cell,i)=>`<td data-label="${esc(heads[i])}"><div class="bom-cell-value">${cell}</div></td>`).join('')}</tr>`).join('') : `<tr><td colspan="${heads.length}" class="bom-empty">${empty}</td></tr>`}</tbody></table></div>`;
const row = cells => cells;
const label = s => `${s.productName} · версия ${s.revision} (${s.productCode})`;
const flatten = (nodes, depth = 0) => nodes.flatMap(n => [{...n, depth}, ...flatten(n.children || [], depth + 1)]);
const status = s => `<span class="bom-status ${s.status === 'DRAFT' ? 'bom-status--draft' : ''}">${s.status === 'DRAFT' ? 'Черновик' : 'Утверждён'}</span>`;
// Результат старого запроса не должен заменять новую карточку при быстром переходе.
let renderVersion = 0;

export async function render(container, route) {
    const version = ++renderVersion;
    const active = () => version === renderVersion && window.location.hash === initialHash;
    const initialHash = window.location.hash;
    container.innerHTML = `<div class="page">${loadingState('Загрузка составов изделий…')}</div>`;
    const [products, specs, roots, units] = await Promise.all([
        api.bom.products(), api.bom.specifications(), api.nodes.tree(), api.units.all(),
    ]);
    if (!active()) return;
    const nodes = flatten(roots);
    const selected = Number(route.segments[1]);
    const spec = specs.find(s => s.id === selected);
    const refresh = () => active() ? render(container, route) : Promise.resolve();
    const act = async fn => { try { await fn(); } catch (err) { toast.error(err.message); } };
    const form = async options => {
        if (await openForm(options) !== null) { toast.success('Изменения сохранены'); await refresh(); }
    };
    const unitLabel = code => ({'PCS':'шт.', 'KG':'кг', 'M':'м', 'BOM-PCS':'шт.', 'BOM-KG':'кг'}[code] || units.find(u=>u.code===code)?.name || code);
    const productOptions = products.map(p => ({value:p.id,label:`${p.name} — ${p.code} (${unitLabel(p.unitCode)})`}));
    const released = specs.filter(s => s.status === 'RELEASED');
    const specLink = id => {
        const target = specs.find(s=>s.id===id);
        return target ? `<a href="#/bom/${id}">${esc(label(target))}</a>` : 'Исходная версия недоступна';
    };
    const demo = specs.find(s=>s.productCode==='BOM-HOUSING' && s.revision===1);
    container.innerHTML = `<div class="page bom-page">
        <div class="page-head"><div class="page-head__text"><h1>Составы изделий</h1>
        <div class="page-head__sub">Из чего состоит изделие и сколько материалов понадобится для изготовления. Такой список называется спецификацией.</div></div>
        <div class="page-head__actions">${button('Добавить изделие или материал','data-register')}${button('Создать состав','data-create',true)}</div></div>
        <details class="card bom-guide" ${!selected ? 'open' : ''}><summary>Первый раз здесь? Начните с трёх шагов</summary>
            <ol class="bom-steps">
                <li><strong>Выберите изделие</strong><span>Откройте готовый состав ниже или создайте свой черновик.</span></li>
                <li><strong>Укажите, из чего оно состоит</strong><span>Добавьте компоненты и количество каждого на одну единицу изделия.</span></li>
                <li><strong>Рассчитайте потребность</strong><span>Укажите объём изготовления. Система учтёт и компоненты вложенных сборок.</span></li>
            </ol>
            ${demo ? `<a class="btn btn--secondary" href="#/bom/${demo.id}">Разобраться на примере корпуса</a>` : '<p>Для нового изделия сначала создайте запись в <a href="#/tree">справочнике изделий</a>, затем нажмите «Добавить изделие или материал».</p>'}
            <p>Черновик можно менять. Утверждённый состав зафиксирован: для следующих изменений создайте новую версию.</p>
        </details>
        ${selected ? '<a class="bom-back" href="#/bom">← Все составы и версии</a>' : ''}
        <div id="bom-detail"></div>
        <${selected ? 'details' : 'section'} class="card" id="bom-list">
            ${selected ? '<summary>Другие составы и история версий</summary>' : '<h2>Выберите состав изделия</h2>'}
            <div class="bom-filters"><label class="field"><span>Название или код изделия</span><input class="input" type="search" id="bom-search" placeholder="Например, корпус или BOM-HOUSING"></label>
            <label class="field"><span>Какие версии показать</span><select class="select" id="bom-status"><option value="">Все версии</option><option value="DRAFT">Черновики — можно менять</option><option value="RELEASED">Утверждённые — зафиксированы</option></select></label></div>
            <div id="bom-specs" aria-live="polite"></div>
        </${selected ? 'details' : 'section'}>
        <details class="card"><summary>Изделия и материалы, доступные для состава (${products.length})</summary>
            <p>Выбирайте тип по тому, как используется или получается изделие. Единица измерения нужна для расчёта количества.</p>
            ${table(['Название и код','Тип','Единица измерения'],products.map(p=>row([`<strong>${esc(p.name)}</strong><div class="text-muted">${esc(p.code)}</div>`,`${kinds[p.kind]}<div class="bom-cell-hint">${kindHints[p.kind]}</div>`,esc(unitLabel(p.unitCode))])), 'Список пуст. Создайте запись в справочнике и добавьте её кнопкой вверху страницы.')}
            <p>Не нашли нужное название? <a href="#/tree">Откройте справочник изделий</a>, создайте запись внутри подходящей группы и вернитесь сюда.</p>
        </details>
        <details class="card"><summary>Словарь простыми словами</summary><dl class="bom-glossary">
            <dt>Спецификация (BOM)</dt><dd>Список компонентов и их количества для изготовления одного изделия.</dd>
            <dt>Компонент и позиция</dt><dd>Компонент — то, что входит в состав. Позиция — номер его строки. Один компонент может встречаться несколько раз.</dd>
            <dt>Норма расхода</dt><dd>Количество компонента на одну единицу изделия. Например, 0,5 кг стали на один кронштейн.</dd>
            <dt>Версия и модификация</dt><dd>Новая версия описывает изменение того же изделия. Модификация — состав другого изделия на основе существующего, например усиленного корпуса.</dd>
            <dt>Исходный состав (база)</dt><dd>Утверждённая версия, из которой взяты компоненты. В новой версии вы задаёте только отличия; исходная версия сохраняется.</dd>
            <dt>Группа ресурсов (класс)</dt><dd>Раздел справочника, например «Материалы». Расчёт по группе включает и все её вложенные группы.</dd>
        </dl></details>
    </div>`;
    const drawList = () => {
        const search = container.querySelector('#bom-search').value.trim().toLocaleLowerCase('ru');
        const filter = container.querySelector('#bom-status').value;
        const visible = specs.filter(s=>(!filter || s.status===filter) && `${s.productName} ${s.productCode}`.toLocaleLowerCase('ru').includes(search));
        container.querySelector('#bom-specs').innerHTML = table(['Изделие и версия','Состояние','На основе','Что изменилось'],visible.map(s=>row([
            specLink(s.id),status(s),s.baseId?specLink(s.baseId):'Самостоятельный состав',esc(s.description),
        ])), specs.length ? 'По этим условиям ничего не найдено. Измените название или выберите все версии.' : 'Составов пока нет. Нажмите «Создать состав» и добавьте первый компонент.');
    };
    drawList();
    container.querySelector('#bom-search').oninput = drawList;
    container.querySelector('#bom-status').onchange = drawList;
    container.querySelector('[data-register]').onclick = () => {
        const available = nodes.filter(n=>n.nodeKind!=='GROUP' && !products.some(p=>p.id===n.id));
        if (!available.length || !units.length) {
            toast.info('Сначала добавьте изделие в справочник и единицу измерения в соответствующем разделе.'); return;
        }
        act(()=>form({title:'Добавить изделие или материал',subtitle:'Выберите существующую запись справочника, чтобы использовать её в составах.',fields:[
            {name:'nodeId',label:'Запись справочника',type:'select',required:true,full:true,options:available.map(n=>({value:n.id,label:`${n.name} — ${n.code}`}))},
            {name:'kind',label:'Как получаем или используем',type:'select',required:true,options:Object.entries(kinds).map(([value,label])=>({value,label})),hint:kindHints.ASSEMBLY},
            {name:'unitId',label:'В чём измеряем количество',type:'select',required:true,options:units.map(u=>({value:u.id,label:`${u.name} — ${u.code}`})),hint:'Для болтов — штуки, для стали — килограммы. Эта единица будет использоваться во всех составах.'},
        ], onMount:f=>{
            f.elements.kind.onchange=()=>{ f.querySelector('[data-field="kind"] .field__hint').textContent=kindHints[f.elements.kind.value]; };
        },onSubmit:v=>api.bom.register({nodeId:Number(v.nodeId),kind:v.kind,unitId:Number(v.unitId)})}));
    };
    const create = base => {
        if (!products.length) { toast.info('Сначала добавьте изделие или материал кнопкой рядом.'); return; }
        act(()=>form({title:base?'Создать состав на основе существующего':'Создать состав изделия',submitLabel:'Создать черновик',subtitle:base?'Для новой версии оставьте то же изделие. Для модификации выберите другое.':'Сначала создадим черновик, затем добавим его компоненты.',fields:[
            {name:'productId',label:'Для какого изделия',type:'select',required:true,options:productOptions,value:base?.productId},
            {name:'baseId',label:'Взять компоненты из готового состава',type:'select',value:base?.id,options:[{value:'',label:'Начать с пустого состава'},...released.map(s=>({value:s.id,label:label(s)}))],hint:'Исходный состав останется прежним. В новом черновике можно заменить, убрать и добавить компоненты.'},
            {name:'description',label:'Что создаём или меняем',required:true,full:true,maxlength:1000,placeholder:'Например: увеличиваем толщину корпуса и добавляем краску'},
        ],onSubmit:async v=>{
            const result=await api.bom.create({productId:Number(v.productId),baseId:v.baseId?Number(v.baseId):null,description:v.description});
            navigate(`/bom/${result.id}`);
        }}));
    };
    container.querySelector('[data-create]').onclick=()=>create(null);
    if (!spec) {
        if (selected) container.querySelector('#bom-detail').innerHTML='<div class="card"><h2>Этот состав не найден</h2><p>Возможно, черновик был удалён. Выберите другой состав из списка ниже.</p></div>';
        return;
    }
    const [lines, local, baseLines] = await Promise.all([
        api.bom.lines(spec.id),api.bom.localPositions(spec.id),spec.baseId ? api.bom.lines(spec.baseId) : Promise.resolve([]),
    ]);
    if (!active()) return;
    const editable = spec.status==='DRAFT';
    const product = products.find(p=>p.id===spec.productId);
    const amount = l => `${fmtNum(l.quantity)} ${esc(unitLabel(l.unitCode))}`;
    const component = l => `<strong>${esc(l.name)}</strong><div class="text-muted">${esc(l.code)}</div>`;
    const changeKind = l => !local.includes(l.position) ? (spec.baseId ? 'Из исходного состава' : 'В этой версии') : baseLines.some(b=>b.position===l.position) ? 'Заменён' : 'Добавлен';
    const changes = local.map(position=>({position,before:baseLines.find(l=>l.position===position),after:lines.find(l=>l.position===position)})).filter(c=>c.before || c.after);
    const detail=container.querySelector('#bom-detail');
    detail.innerHTML=`<section class="card"><div class="page-head"><div class="page-head__text">
        <div class="bom-meta">${status(spec)}<span>Версия ${spec.revision}</span><span>${esc(spec.productCode)}</span></div>
        <h2>${esc(spec.productName)}: состав на 1 ${esc(unitLabel(product?.unitCode || 'ед.'))}</h2>
        <p>${esc(spec.description)}</p></div><div class="page-head__actions">${editable?button('Добавить компонент','data-add',true)+button('Утвердить состав',`data-release ${!lines.length?'disabled':''}`):button('Создать новую версию / модификацию','data-revise',true)}</div></div>
        <div class="bom-notice">${editable?'Вы работаете с черновиком. Изменения сохраняются сразу. Проверьте состав и расчёт, затем утвердите версию.':'Этот состав утверждён и зафиксирован. Его можно использовать для расчётов. Для изменения создайте новую версию.'}</div>
        ${spec.baseId?`<p>Исходный состав: ${specLink(spec.baseId)}. Компоненты без изменений взяты из него.</p>`:''}
        <h3>Из чего состоит изделие</h3><p>Количество в каждой строке указано на одну единицу изделия. Для вложенной сборки можно открыть её собственный состав.</p>
        ${table(['№','Компонент','На одну единицу изделия','Вложенный состав','Откуда взят',...(editable?['Действия']:[])],lines.map(l=>row([
            l.position,component(l),amount(l),l.componentSpecificationId?specLink(l.componentSpecificationId):'Учитываем целиком',changeKind(l),
            ...(editable?[button('Изменить',`data-edit="${l.position}"`)+button('Убрать',`data-exclude="${l.position}"`)]:[]),
        ])), 'Состав пуст. Добавьте первый компонент: например, сталь и её количество на одно изделие.')}
        ${changes.length ? `<details class="bom-changes" ${spec.baseId?'open':''}><summary>Изменения этой версии (${changes.length})</summary>
        <p>Здесь видны и убранные компоненты. ${editable?'Возврат к исходному составу действует только на выбранную строку.':''}</p>
        ${table(['№','Было в исходном составе','Стало в этой версии',...(editable?['Действие']:[])],changes.map(c=>row([
            c.position,c.before?`${component(c.before)}${amount(c.before)}${c.before.componentSpecificationId?`<div>${specLink(c.before.componentSpecificationId)}</div>`:''}`:'Не было',
            c.after?`${component(c.after)}${amount(c.after)}${c.after.componentSpecificationId?`<div>${specLink(c.after.componentSpecificationId)}</div>`:''}`:'Убран из состава',
            ...(editable?[button(c.before?'Вернуть как было':'Отменить добавление',`data-reset="${c.position}"`)]:[]),
        ])))}</details>`:''}
        <div class="bom-meta bom-detail-footer"><span>Создан: ${fmtDate(spec.createdAt)}${spec.releasedAt?` · Утверждён: ${fmtDate(spec.releasedAt)}`:''}</span>${editable?button('Удалить черновик','data-remove'):''}</div>
        </section>
        <section class="card"><h2>Сколько понадобится для изготовления?</h2>
        <p>Количество умножается на расход каждого компонента, включая вложенные составы. Например, на 3 корпуса с 2 кронштейнами в каждом нужно 6 кронштейнов.</p>
        <form id="bom-calc" class="bom-filters"><label class="field"><span>Сколько изготовить (${esc(unitLabel(product?.unitCode || 'ед.'))})</span><input class="input" name="quantity" inputmode="decimal" value="1" required aria-describedby="bom-quantity-hint"><span class="field__hint" id="bom-quantity-hint">Число больше нуля. Дробь можно вводить через запятую.</span></label>
        <label class="field"><span>Что посчитать</span><select class="select" name="classId"><option value="">Все компоненты</option>${nodes.filter(n=>n.nodeKind==='GROUP').map(n=>`<option value="${n.id}">${'— '.repeat(n.depth)}${esc(n.name)} (${esc(n.code)})</option>`).join('')}</select><span class="field__hint">Группа из справочника и все её подгруппы.</span></label>
        <button class="btn btn--primary" type="submit">Рассчитать потребность</button></form><div id="bom-results" aria-live="polite"></div></section>`;
    const lineForm = line => act(()=>form({title:line?'Изменить компонент':'Добавить компонент',submitLabel:line?'Сохранить изменение':'Добавить в состав',subtitle:`Количество на 1 ${unitLabel(product?.unitCode || 'ед.')} изделия «${spec.productName}».`,fields:[
        {name:'position',label:'Номер строки',type:'number',min:1,step:1,required:true,value:line?.position??Math.max(0,...lines.map(l=>l.position),...local)+1,hint:'Номер предложен автоматически. Разные строки могут содержать один и тот же компонент.'},
        {name:'componentId',label:'Что входит в состав',type:'select',required:true,options:productOptions.filter(o=>o.value!==spec.productId),value:line?.componentId},
        {name:'quantity',label:'Количество на одну единицу изделия',required:true,value:line?.quantity??'1',hint:'Положительное число, до 6 знаков после запятой.'},
        {name:'componentSpecificationId',label:'Как учитывать этот компонент',type:'select',value:line?.componentSpecificationId,options:[],hint:'Если компонент изготавливается из других компонентов, выберите его утверждённый состав. Иначе он учитывается целиком, без вложенных материалов.'},
    ],onMount:f=>{
        f.elements.position.readOnly=Boolean(line);
        const updateComponent=()=>{
            const id=Number(f.elements.componentId.value);
            const p=products.find(p=>p.id===id);
            const versionSelect=f.elements.componentSpecificationId;
            const previous=versionSelect.value || (line?.componentId===id ? String(line.componentSpecificationId || '') : '');
            const available=released.filter(s=>s.productId===id);
            versionSelect.innerHTML=`<option value="">Учитывать целиком — без вложенного состава</option>${available.map(s=>`<option value="${s.id}">${esc(label(s))}</option>`).join('')}`;
            versionSelect.value=available.some(s=>String(s.id)===previous)?previous:'';
            f.querySelector('[data-field="quantity"] .field__hint').textContent=p?`Единица: ${unitLabel(p.unitCode)}. Например, 0,5. До 6 знаков после запятой.`:'Выберите компонент.';
        };
        f.elements.componentId.onchange=updateComponent;
        updateComponent();
    },onSubmit:async v=>{
        const position=Number(v.position);
        if (!Number.isInteger(position) || position<1 || position>2147483647) throw {field:'position',message:'Укажите целый номер строки от 1 до 2147483647.'};
        if (!line && (lines.some(l=>l.position===position) || local.includes(position))) throw {field:'position',message:'Этот номер уже занят. Выберите свободный номер или измените существующую строку.'};
        const quantity=String(v.quantity).replace(',','.');
        if (!/^(?:\d{1,13})(?:\.\d{1,6})?$/.test(quantity) || Number(quantity)<=0) throw {field:'quantity',message:'Введите число больше нуля: до 13 цифр перед запятой и 6 после.'};
        await api.bom.putLine(spec.id,position,{componentId:Number(v.componentId),quantity,componentSpecificationId:v.componentSpecificationId?Number(v.componentSpecificationId):null});
    }}));
    detail.querySelector('[data-add]')?.addEventListener('click',()=>{
        if (!productOptions.some(o=>o.value!==spec.productId)) { toast.info('Добавьте ещё одно изделие или материал, чтобы включить его в состав.'); return; }
        lineForm(null);
    });
    detail.querySelector('[data-revise]')?.addEventListener('click',()=>create(spec));
    detail.querySelector('[data-release]')?.addEventListener('click',()=>act(async()=>{
        if(await confirmDialog({title:'Зафиксировать этот состав?',message:'После утверждения редактирование этой версии закроется. Для следующих изменений можно будет создать новую версию.',confirmLabel:'Утвердить состав'})){
            await api.bom.release(spec.id); toast.success('Состав утверждён. Для изменений создайте новую версию.'); await refresh();
        }
    }));
    detail.querySelector('[data-remove]')?.addEventListener('click',()=>act(async()=>{
        if(await confirmDialog({title:'Удалить черновик?',message:'Этот черновик и его изменения будут удалены. Исходный утверждённый состав сохранится.',danger:true,confirmLabel:'Удалить черновик'})){
            await api.bom.remove(spec.id); toast.success('Черновик удалён'); if(active()) navigate('/bom');
        }
    }));
    detail.querySelectorAll('[data-edit]').forEach(b=>b.onclick=()=>lineForm(lines.find(l=>l.position===Number(b.dataset.edit))));
    detail.querySelectorAll('[data-exclude]').forEach(b=>b.onclick=()=>act(async()=>{
        const position=Number(b.dataset.exclude);
        const inherited=baseLines.some(l=>l.position===position);
        if (!inherited && !await confirmDialog({title:'Убрать добавленный компонент?',message:'Эта строка отсутствует в исходном составе. Чтобы вернуть её после удаления, нужно будет добавить компонент заново.',confirmLabel:'Убрать компонент'})) return;
        b.disabled=true;
        try { await api.bom.exclude(spec.id,position); toast.success(inherited?'Компонент убран. Его можно вернуть в блоке изменений.':'Компонент убран из состава.'); await refresh(); }
        finally { b.disabled=false; }
    }));
    detail.querySelectorAll('[data-reset]').forEach(b=>b.onclick=()=>act(async()=>{
        const change=changes.find(c=>c.position===Number(b.dataset.reset));
        const message=change.before?`В строке ${change.position} снова будет «${esc(change.before.name)}»: ${amount(change.before)}. Текущее изменение этой строки отменится.`:'Добавленная строка будет удалена из этой версии.';
        if(await confirmDialog({title:change.before?'Вернуть исходный компонент?':'Отменить добавление компонента?',message,confirmLabel:change.before?'Вернуть как было':'Отменить добавление'})){
            await api.bom.reset(spec.id,change.position); toast.success('Изменение строки отменено'); await refresh();
        }
    }));
    let calculation = 0;
    const calcForm=detail.querySelector('#bom-calc');
    calcForm.oninput=()=>{
        calculation++;
        detail.querySelector('#bom-results').textContent='Условия изменились. Нажмите «Рассчитать потребность», чтобы обновить результат.';
        calcForm.querySelector('button').disabled=false;
    };
    calcForm.onsubmit=e=>{e.preventDefault();act(async()=>{
        const request=++calculation;
        const data=new FormData(e.target), quantity=String(data.get('quantity')).trim().replace(',','.'), classId=Number(data.get('classId'));
        const results=detail.querySelector('#bom-results'), submit=calcForm.querySelector('button');
        if (!/^(?:\d{1,13})(?:\.\d{1,6})?$/.test(quantity) || Number(quantity)<=0) {
            results.textContent='Укажите количество больше нуля: до 13 цифр перед запятой и 6 после.'; calcForm.elements.quantity.focus(); return;
        }
        results.innerHTML=loadingState('Рассчитываем количество компонентов…'); submit.disabled=true;
        try {
            const [explosion,groups]=await Promise.all([
                api.bom.explode(spec.id,quantity),
                classId ? api.bom.totals(spec.id,quantity,classId) : Promise.all(roots.map(n=>api.bom.totals(spec.id,quantity,n.id))),
            ]);
            if(!active() || request!==calculation) return;
            // Корневые группы не пересекаются; суммирование выполняется сервером с decimal-точностью.
            const totals=classId?groups:groups.flat();
            const groupName=nodes.find(n=>n.id===classId)?.name || 'Все компоненты';
            results.innerHTML=`<h3>Потребность на ${fmtNum(quantity)} ${esc(unitLabel(product?.unitCode || 'ед.'))}: ${esc(spec.productName)}</h3>
                <p>${esc(groupName)}${editable?' · Расчёт по текущему черновику':''}. Одинаковые компоненты из разных ветвей сложены.</p>
                <div class="bom-notice">Сборки и материалы внутри них показаны отдельно. Не складывайте их между собой: это разные уровни состава, а не общий список закупки.</div>
                ${table(['Что понадобится','Сколько всего','Единица измерения'],totals.map(t=>row([`${esc(t.name)}<div class="text-muted">${esc(t.code)}</div>`,fmtNum(t.totalQuantity),esc(unitLabel(t.unitCode))])), 'В выбранной группе нет компонентов этого состава. Выберите другую группу или «Все компоненты».')}
                <details class="bom-changes"><summary>Как получился результат: все уровни состава (${explosion.length} строк)</summary>
                <p>Путь 1 → 2 означает: компонент из строки 2 внутри сборки из строки 1. Количество на партию учитывает весь путь от исходного изделия.</p>
                ${table(['Путь строк','Компонент','На единицу родительского изделия','На всю партию'],explosion.map(e=>row([
                    e.path.join(' → '),`${'↳ '.repeat(Math.min(e.depth-1,6))}${esc(e.line.name)}<div class="text-muted">${esc(e.line.code)} · уровень ${e.depth}</div>`,amount(e.line),`${fmtNum(e.totalQuantity)} ${esc(unitLabel(e.line.unitCode))}`,
                ])), 'В этом составе пока нет компонентов.')}</details>`;
        } catch(err) { if(active() && request===calculation) results.textContent=err.message; }
        finally { if(request===calculation) submit.disabled=false; }
    });};
}
