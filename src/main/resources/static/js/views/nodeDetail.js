/* ============================================================================
   Карточка узла классификатора — детальная информация и операции:
   обзор, числовые параметры, перечислимые атрибуты, анализ поддерева.
   ========================================================================== */

import { api, ApiError } from '../api.js';
import {
    esc, icon, withIcons, fmtNum, fmtDate, plural,
    openForm, openDialog, confirmDialog, toast, emptyState, loadingState,
} from '../ui.js';
import { navigate } from '../router.js';
import { createNodeDialog, editNodeDialog, moveNodeDialog, deleteNodeDialog, nodeKindLabels } from './nodeForms.js';
import { filterBuilderHtml, bindFilterBuilder, loadScopeParameters } from './filterBuilder.js';

// Активная вкладка сохраняется между переходами по узлам
let activeTab = 'overview';

const TABS = [
    { id: 'overview',   label: 'Обзор',          icon: 'info' },
    { id: 'numeric',    label: 'Числовые',       icon: 'sliders' },
    { id: 'enum',       label: 'Варианты и свойства',   icon: 'tags' },
    { id: 'analysis',   label: 'Анализ',         icon: 'bar-chart' },
];

/**
 * Рисует карточку узла в указанный контейнер.
 * @param {HTMLElement} container
 * @param {number} nodeId
 * @param {{onTreeChanged: Function, getTree: Function, units: Array}} ctx
 */
export async function renderNodeDetail(container, nodeId, ctx) {
    container.innerHTML = `<div class="card">${loadingState('Загрузка карточки узла…')}</div>`;

    let node, ancestors;
    try {
        [node, ancestors] = await Promise.all([
            api.nodes.byId(nodeId),
            api.nodes.ancestors(nodeId),
        ]);
    } catch (err) {
        container.innerHTML = `<div class="card"><div class="card__body">${emptyState({
            icon: 'alert-triangle',
            title: 'Узел не найден',
            text: err.message,
        })}</div></div>`;
        return;
    }

    // Бэкенд возвращает предков в порядке от корня к узлу
    const path = ancestors;
    const isTerminal = node.isTerminal;

    container.innerHTML = `
        <div class="card">
            <div class="card__body">
                <div class="breadcrumbs">
                    <a data-nav-root>Классификатор</a>
                    ${path.map((p) => `
                        <span class="breadcrumbs__sep">${icon('chevron-right', 13)}</span>
                        <a data-nav="${p.id}">${esc(p.name)}</a>
                    `).join('')}
                    <span class="breadcrumbs__sep">${icon('chevron-right', 13)}</span>
                    <span class="breadcrumbs__current">${esc(node.name)}</span>
                </div>

                <div class="node-detail__head">
                    <div class="node-detail__icon">
                        ${icon(isTerminal ? 'box' : 'folder', 24)}
                    </div>
                    <div style="flex:1;min-width:0">
                        <h2 class="node-detail__title">${esc(node.name)}</h2>
                        <div class="node-detail__meta">
                            <span class="badge badge--code">${esc(node.code)}</span>
                            <span class="badge badge--blue">${nodeKindLabels[node.nodeKind]}</span>
                            ${node.unitOfMeasure
                                ? `<span class="badge badge--neutral">${icon('ruler', 12)} ${esc(node.unitOfMeasure.name)}</span>`
                                : ''}
                        </div>
                    </div>
                </div>

                <div class="flex-gap flex-wrap mt-16">
                    <button class="btn btn--primary btn--sm" data-act="add-child" ${node.nodeKind==='PRODUCT'?'disabled':''}>
                        ${icon('add-child', 15)}<span>Добавить потомка</span>
                    </button>
                    <button class="btn btn--sm" data-act="edit">
                        ${icon('edit', 15)}<span>Изменить</span>
                    </button>
                    <button class="btn btn--sm" data-act="move">
                        ${icon('move', 15)}<span>Переместить</span>
                    </button>
                    <button class="btn btn--sm btn--ghost" data-act="delete" style="color:var(--danger)">
                        ${icon('trash', 15)}<span>Удалить</span>
                    </button>
                </div>
            </div>

            <div class="tabs" role="tablist">
                ${TABS.map((t) => `
                    <button class="tab ${t.id === activeTab ? 'is-active' : ''}" data-tab="${t.id}" role="tab">
                        ${icon(t.icon, 15)}<span>${esc(t.label)}</span>
                    </button>
                `).join('')}
            </div>
            <div class="tab-panel" id="node-tab-panel"></div>
        </div>
    `;
    withIcons(container);

    // — Навигация по хлебным крошкам —
    container.querySelector('[data-nav-root]').addEventListener('click', () => navigate('/tree'));
    container.querySelectorAll('[data-nav]').forEach((a) => {
        a.addEventListener('click', () => navigate('/tree/' + a.dataset.nav));
    });

    // — Действия с узлом —
    container.querySelector('[data-act="add-child"]').addEventListener('click', async () => {
        const created = await createNodeDialog({ parentId: node.id, parentName: node.name, parentKind:node.nodeKind, units: ctx.units });
        if (created) {
            await ctx.onTreeChanged();
            navigate('/tree/' + created.id);
        }
    });
    container.querySelector('[data-act="edit"]').addEventListener('click', async () => {
        const updated = await editNodeDialog({ node, units: ctx.units });
        if (updated) {
            await ctx.onTreeChanged();
            renderNodeDetail(container, nodeId, ctx);
        }
    });
    container.querySelector('[data-act="move"]').addEventListener('click', async () => {
        const moved = await moveNodeDialog({ node, tree: ctx.getTree() });
        if (moved) {
            await ctx.onTreeChanged();
            renderNodeDetail(container, nodeId, ctx);
        }
    });
    container.querySelector('[data-act="delete"]').addEventListener('click', async () => {
        const ok = await deleteNodeDialog({ node });
        if (ok) {
            await ctx.onTreeChanged();
            navigate(node.parentId ? '/tree/' + node.parentId : '/tree');
        }
    });

    // — Вкладки —
    const panel = container.querySelector('#node-tab-panel');
    const tabButtons = container.querySelectorAll('.tab');
    const openTab = (tabId) => {
        activeTab = tabId;
        tabButtons.forEach((b) => b.classList.toggle('is-active', b.dataset.tab === tabId));
        renderTab(tabId, panel, node, ctx);
    };
    tabButtons.forEach((b) => b.addEventListener('click', () => openTab(b.dataset.tab)));
    openTab(node.code === 'BOM-EL-KITS' ? 'overview' : activeTab);
}

function renderTab(tabId, panel, node, ctx) {
    panel.innerHTML = loadingState('Загрузка…');
    if (tabId === 'overview') return renderOverviewTab(panel, node, ctx);
    if (tabId === 'numeric')  return renderNumericTab(panel, node, ctx);
    if (tabId === 'enum')     return renderEnumTab(panel, node, ctx);
    if (tabId === 'analysis') return renderAnalysisTab(panel, node, ctx);
}

/* ═══════════════════════════ Вкладка «Обзор» ════════════════════════════ */

async function renderOverviewTab(panel, node, ctx) {
    let siblings = [];
    let children = [];
    try {
        [siblings, children] = await Promise.all([
            node.parentId ? api.nodes.children(node.parentId) : api.nodes.roots(),
            api.nodes.children(node.id),
        ]);
    } catch (err) {
        panel.innerHTML = emptyState({ icon: 'alert-triangle', title: 'Ошибка загрузки', text: err.message });
        return;
    }

    const position = node.sortOrder;
    const siblingCount = siblings.length;

    panel.innerHTML = `
        ${node.code === 'BOM-EL-KITS' ? '<div class="section-block" data-specifications></div>' : ''}
        <div class="prop-list">
            <div class="prop-list__key">Код узла</div>
            <div class="prop-list__val"><span class="badge badge--code">${esc(node.code)}</span></div>

            <div class="prop-list__key">Название</div>
            <div class="prop-list__val">${esc(node.name)}</div>

            <div class="prop-list__key">Тип</div>
            <div class="prop-list__val">
                <span class="badge badge--blue">${nodeKindLabels[node.nodeKind]}</span>

            </div>

            <div class="prop-list__key">Единица измерения</div>
            <div class="prop-list__val">
                ${node.unitOfMeasure
                    ? `${esc(node.unitOfMeasure.name)} <span class="badge badge--code">${esc(node.unitOfMeasure.code)}</span>`
                    : '<span class="text-soft">не указана</span>'}
            </div>

            <div class="prop-list__key">Положение среди соседей</div>
            <div class="prop-list__val">
                <span>${position + 1} из ${siblingCount}</span>
                <span class="row-actions" style="margin-left:6px">
                    <button class="icon-btn" data-act="up" ${position <= 0 ? 'disabled' : ''}
                            title="Переместить выше">${icon('arrow-up', 16)}</button>
                    <button class="icon-btn" data-act="down" ${position >= siblingCount - 1 ? 'disabled' : ''}
                            title="Переместить ниже">${icon('arrow-down', 16)}</button>
                </span>
            </div>

            <div class="prop-list__key">Создан</div>
            <div class="prop-list__val text-muted">${fmtDate(node.createdAt)}</div>

            <div class="prop-list__key">Изменён</div>
            <div class="prop-list__val text-muted">${fmtDate(node.updatedAt)}</div>
        </div>

        <div class="section-block">
            <div class="section-title">
                ${icon('tree', 16)}<span>Дочерние узлы</span>
                <span class="section-title__count">${children.length}</span>
            </div>
            ${children.length === 0
                ? `<div class="notice notice--info">${icon('info', 16)}<span>Потомков пока нет. Тип записи задан явно: пустая группа остаётся группой, модель — моделью.</span></div>`
                : `<div class="child-list">${children.map((c) => `
                    <button class="child-list__item" data-child="${c.id}">
                        <span class="child-list__icon">${icon(c.isTerminal ? 'box' : 'folder', 17)}</span>
                        <span class="child-list__name">${esc(c.name)}</span>
                        <span class="badge badge--code">${esc(c.code)}</span>
                        ${c.isTerminal ? '' : `<span class="tree-row__count">${c.childrenCount}</span>`}
                        <span class="child-list__arrow">${icon('chevron-right', 16)}</span>
                    </button>
                `).join('')}</div>`}
        </div>
    `;
    withIcons(panel);
    injectOverviewStyles();
    const specifications = panel.querySelector('[data-specifications]');
    if (specifications) renderSpecifications(specifications);

    panel.querySelectorAll('[data-child]').forEach((btn) => {
        btn.addEventListener('click', () => navigate('/tree/' + btn.dataset.child));
    });

    const reorder = async (newOrder) => {
        try {
            await api.nodes.reorder(node.id, newOrder);
            toast.success('Порядок узла изменён');
            await ctx.onTreeChanged();
            renderOverviewTab(panel, { ...node, sortOrder: newOrder }, ctx);
        } catch (err) {
            toast.error(err.message);
        }
    };
    const upBtn = panel.querySelector('[data-act="up"]');
    const downBtn = panel.querySelector('[data-act="down"]');
    if (upBtn) upBtn.addEventListener('click', () => reorder(position - 1));
    if (downBtn) downBtn.addEventListener('click', () => reorder(position + 1));
}

async function renderSpecifications(section) {
    section.innerHTML = loadingState('Загрузка составов изделий…');
    try {
        const specs = await api.bom.specifications();
        section.innerHTML = `
            <div class="section-title">
                <span>Все составы и версии</span><span class="section-title__count">${specs.length}</span>
            </div>
            <p><a href="#/bom">Открыть раздел «Составы изделий»</a></p>
            ${specs.length ? `<div class="child-list">${specs.map(spec => `
                <a class="child-list__item" href="#/bom/${spec.id}">
                    <span class="child-list__name">${esc(spec.productName)}</span>
                    <span class="badge badge--code">версия ${spec.revision}</span>
                    <span class="badge badge--neutral">${spec.status === 'DRAFT' ? 'Черновик' : 'Утверждён'}</span>
                </a>`).join('')}</div>` : '<p class="text-muted">Составов пока нет.</p>'}
        `;
    } catch (err) {
        section.innerHTML = emptyState({ icon: 'alert-triangle', title: 'Не удалось загрузить составы', text: err.message });
    }
}

/* ═══════════════════════ Вкладка «Числовые параметры» ═══════════════════ */

async function renderNumericTab(panel, node, ctx) {
    let effective, values, allParams, own;
    try {
        [effective, values, allParams, own] = await Promise.all([
            api.nodeNumeric.effectiveParameters(node.id),
            api.nodeNumeric.values(node.id),
            api.numericParams.all(),
            api.nodeNumeric.ownParameters(node.id),
        ]);
    } catch (err) {
        panel.innerHTML = emptyState({ icon: 'alert-triangle', title: 'Ошибка загрузки', text: err.message });
        return;
    }

    const valueByParam = new Map(values.map((v) => [v.parameterId, v]));
    const effectiveIds = new Set(effective.map((p) => p.parameterId));
    const assignable = allParams.filter((p) => !effectiveIds.has(p.id));

    const reload = () => renderNumericTab(panel, node, ctx);

    panel.innerHTML = `
        <div class="section-block">
            <div class="section-title">
                ${icon('sliders', 16)}<span>Числовые параметры</span>
                <span class="section-title__count">${effective.length}</span>
                <span class="section-title__actions">
                    <button class="btn btn--subtle btn--sm" data-act="assign"
                            ${assignable.length === 0 ? 'disabled title="Все параметры уже назначены"' : ''}>
                        ${icon('plus', 15)}<span>Назначить параметр</span>
                    </button>
                </span>
            </div>

            <div class="notice notice--info mb-16">
                ${icon('git-branch', 16)}
                <span>Параметры со значком наследования действуют для узла, потому что назначены
                одному из его родительских классов. Можно исключить параметр для этого подкласса и потомков. Чтобы вернуть его, нажмите «Назначить параметр». Сначала очистите сохранённые значения исключаемого параметра.</span>
            </div>

            ${effective.length === 0
                ? emptyState({
                    icon: 'sliders',
                    title: 'Числовые параметры не назначены',
                    text: 'Назначьте параметр этому узлу или одному из его родительских классов.',
                })
                : effective.map((p) => numericParamRow(p, valueByParam.get(p.parameterId), node)).join('')}
            ${own.some(p=>p.excluded)?`<p class="text-muted">Исключены здесь: ${own.filter(p=>p.excluded).map(p=>esc(p.parameterName)).join(', ')}. Назначьте параметр повторно, чтобы вернуть его.</p>`:''}
        </div>
    `;
    withIcons(panel);

    // Назначить параметр
    const assignBtn = panel.querySelector('[data-act="assign"]');
    if (assignBtn && assignable.length) {
        assignBtn.addEventListener('click', async () => {
            const res = await openForm({
                title: 'Назначение числового параметра',
                subtitle: node.name,
                icon: 'sliders',
                noticeHtml: `<div class="notice notice--info">${icon('info', 16)}<span>Назначенный параметр будет унаследован всеми дочерними узлами этого класса.</span></div>`,
                fields: [{
                    name: 'paramId', label: 'Числовой параметр', type: 'select', required: true, full: true,
                    options: assignable.map((p) => ({
                        value: p.id,
                        label: `${p.name} (${p.code})` + (p.unitOfMeasureName ? ` · ${p.unitOfMeasureName}` : ''),
                    })),
                }],
                submitLabel: 'Назначить', submitIcon: 'plus',
                onSubmit: async (v) => {
                    await api.nodeNumeric.assignParameter(node.id, Number(v.paramId));
                    toast.success('Параметр назначен узлу');
                },
            });
            if (res !== null) reload();
        });
    }

    // Действия по строкам параметров
    panel.querySelectorAll('[data-param-row]').forEach((row) => {
        const paramId = Number(row.dataset.paramRow);
        const param = effective.find((p) => p.parameterId === paramId);
        const value = valueByParam.get(paramId);

        const setBtn = row.querySelector('[data-act="set-value"]');
        if (setBtn) setBtn.addEventListener('click', () => openSetValueDialog(node, param, value, reload));

        const clearBtn = row.querySelector('[data-act="clear-value"]');
        if (clearBtn) clearBtn.addEventListener('click', async () => {
            const ok = await confirmDialog({
                title: 'Удалить значение?',
                message: `Значение параметра «${esc(param.parameterName)}» для изделия «${esc(node.name)}» будет удалено.`,
                confirmLabel: 'Удалить значение', danger: true,
            });
            if (!ok) return;
            try {
                await api.nodeNumeric.clearValue(node.id, paramId);
                toast.success('Значение удалено');
                reload();
            } catch (err) { toast.error(err.message); }
        });

        const excludeBtn = row.querySelector('[data-act="exclude-param"]');
        if (excludeBtn) excludeBtn.addEventListener('click', async()=>{
            if (!await confirmDialog({title:'Исключить параметр?',message:'Параметр перестанет действовать здесь и у потомков. Если для него сохранены значения, сначала очистите их.',confirmLabel:'Исключить'})) return;
            try { await api.nodeNumeric.excludeParameter(node.id,paramId); await reload(); } catch(err) { toast.error(err.message); }
        });
        const removeBtn = row.querySelector('[data-act="remove-param"]');
        if (removeBtn) removeBtn.addEventListener('click', async () => {
            const ok = await confirmDialog({
                title: 'Снять параметр с узла?',
                message: `Параметр «${esc(param.parameterName)}» перестанет действовать для узла «${esc(node.name)}» и его потомков.`,
                detail: 'Если параметр назначен выше, он продолжит наследоваться. Для полного отключения используйте «Исключить». Сохранённые значения должны оставаться допустимыми.',
                confirmLabel: 'Снять параметр', danger: true,
            });
            if (!ok) return;
            try {
                await api.nodeNumeric.removeParameter(node.id, paramId);
                toast.success('Параметр снят с узла');
                reload();
            } catch (err) { toast.error(err.message); }
        });
    });
}

function numericParamRow(param, value, node) {
    const range = (param.minValue != null || param.maxValue != null)
        ? `Диапазон: ${param.minValue != null ? fmtNum(param.minValue) : '−∞'} … ${param.maxValue != null ? fmtNum(param.maxValue) : '+∞'}`
        : 'Диапазон не ограничен';
    const unit = param.unitOfMeasureName ? ` · ${esc(param.unitOfMeasureName)}` : '';
    const isOwn = !param.isInherited;

    return `
        <div class="param-row" data-param-row="${param.parameterId}">
            <div class="param-row__icon">${icon('hash', 18)}</div>
            <div class="param-row__main">
                <div class="param-row__name">
                    ${esc(param.parameterName)}
                    <span class="badge badge--code">${esc(param.parameterCode)}</span>
                    ${param.isInherited
                        ? `<span class="badge badge--violet" title="Параметр назначен родительскому классу">
                             ${icon('git-branch', 11)} от «${esc(param.definedAtNodeName)}»</span>`
                        : '<span class="badge badge--blue">собственный</span>'}
                </div>
                <div class="param-row__sub">${esc(range)}${unit}</div>
            </div>
            <div class="param-row__value">
                ${value
                    ? `<div class="param-row__value-num">${fmtNum(value.value)}</div>
                       ${param.unitOfMeasureName ? `<div class="param-row__value-unit">${esc(param.unitOfMeasureName)}</div>` : ''}`
                    : '<div class="param-row__value-empty">не задано</div>'}
            </div>
            <div class="param-row__actions">
                ${value
                    ? `<button class="icon-btn icon-btn--primary" data-act="set-value" title="Изменить значение">${icon('edit', 16)}</button>
                       <button class="icon-btn icon-btn--danger" data-act="clear-value" title="Удалить значение">${icon('trash', 16)}</button>`
                    : `<button class="btn btn--subtle btn--sm" data-act="set-value">${icon('plus', 14)}<span>Указать значение</span></button>`}
                <button class="btn btn--subtle btn--sm" data-act="exclude-param" title="Исключить здесь и у потомков">Исключить</button>
                ${isOwn
                    ? `<button class="icon-btn icon-btn--danger" data-act="remove-param" title="Снять параметр с узла">${icon('unlink', 16)}</button>`
                    : ''}
            </div>
        </div>
    `;
}

function openSetValueDialog(node, param, existing, onDone) {
    const bounds = [];
    if (param.minValue != null) bounds.push(`не менее ${fmtNum(param.minValue)}`);
    if (param.maxValue != null) bounds.push(`не более ${fmtNum(param.maxValue)}`);
    const hint = bounds.length
        ? `Допустимое значение: ${bounds.join(', ')}${param.unitOfMeasureName ? ' ' + param.unitOfMeasureName : ''}.`
        : 'Ограничения на значение не заданы.';

    openForm({
        title: existing ? 'Изменение значения' : 'Ввод значения параметра',
        subtitle: `${param.parameterName} · ${node.name}`,
        icon: 'sliders',
        fields: [{
            name: 'value', label: 'Значение параметра', type: 'number', required: true, autofocus: true,
            value: existing ? existing.value : '',
            suffix: param.unitOfMeasureName || '',
            min: param.minValue != null ? Number(param.minValue) : undefined,
            max: param.maxValue != null ? Number(param.maxValue) : undefined,
            hint,
        }],
        submitLabel: 'Сохранить значение',
        onSubmit: async (v) => {
            try {
                await api.nodeNumeric.setValue(node.id, { parameterId: param.parameterId, value: v.value });
                toast.success('Значение сохранено');
            } catch (err) {
                if (err instanceof ApiError && err.status === 422) {
                    throw { field: 'value', message: err.message };
                }
                throw err;
            }
        },
    }).then((res) => { if (res !== null) onDone(); });
}

/* ═══════════════════════ Вкладка «Перечисления» ═════════════════════════ */

async function renderEnumTab(panel, node, ctx) {
    let enums, attributes, bindings;
    try {
        [enums, attributes, bindings] = await Promise.all([
            api.items.effectiveEnumerations(node.id),api.attributes.all(node.id),api.catalog.bindings(node.id),
        ]);
    } catch(err) { panel.innerHTML=emptyState({icon:'alert-triangle',title:'Не удалось загрузить характеристики',text:err.message});return; }
    const concrete=node.nodeKind==='PRODUCT';
    const selectedByEnum=new Map(attributes.map(a=>[a.enumerationId,a]));
    const own=new Map(bindings.map(b=>[b.enumerationId,b]));
    const reload=()=>renderEnumTab(panel,node,ctx);
    panel.innerHTML=`<div class="section-block">
        <div class="section-title"><span>${concrete?'Свойства конкретного изделия':'Доступные варианты для потомков'}</span><span class="section-title__actions"><button class="btn btn--subtle btn--sm" data-bind-list>Назначить характеристику</button></span></div>
        <div class="notice notice--info mb-16"><span>${concrete?'Выберите одно значение каждой характеристики. Список уже учитывает ограничения модели и групп.':'У модели может быть несколько допустимых цветов и объёмов памяти. Это варианты выпуска, а не один выбранный цвет. Конкретные значения выбираются в карточках её исполнений.'}</span></div>
        ${!enums.length?'<p>Характеристики ещё не назначены. Используйте кнопку выше или назначьте их родительской группе.</p>':enums.map(e=>{
            const selected=selectedByEnum.get(e.enumerationId);
            return `<div class="param-row" style="display:block" data-enum-block="${e.enumerationId}"><h3>${esc(e.enumerationName)}</h3>
                <p class="text-muted">${e.isInherited?'Унаследовано от «'+esc(e.definedAtNodeName)+'»':'Назначено здесь'}</p>
                <div class="option-grid">${e.values.map(v=>concrete?`<button class="option-pill ${selected?.selectedValueId===v.id?'is-selected':''}" data-value="${v.id}" aria-pressed="${selected?.selectedValueId===v.id}">${esc(v.name)}</button>`:`<span class="badge badge--neutral">${esc(v.name)}</span>`).join('')||'<span>Нет допустимых вариантов</span>'}</div>
                ${concrete?`<p class="mt-16">${selected?'Выбрано: '+esc(selected.selectedValueName):'Значение ещё не выбрано'}</p>`:`${selected?`<p>Ранее сохранённое постоянное свойство: ${esc(selected.selectedValueName)}. Доступные варианты задаются отдельно.</p>`:''}<button class="btn btn--sm mt-16" data-options="${e.enumerationId}">Настроить доступные варианты</button>`}
                <button class="btn btn--sm mt-16" data-exclude-enum="${e.enumerationId}">Исключить характеристику</button>
                ${own.has(e.enumerationId)?`<button class="btn btn--sm mt-16" data-unbind="${e.enumerationId}">Снять назначение / ограничение</button>`:''}
            </div>`;
        }).join('')}
        ${bindings.some(b=>b.excluded)?`<p class="text-muted">Исключены здесь: ${bindings.filter(b=>b.excluded).map(b=>esc(b.enumerationName)).join(', ')}. Кнопка «Назначить характеристику» вернёт её.</p>`:''}
    </div>`;
    panel.querySelector('[data-bind-list]').onclick=async()=>{
        try {
            const classes=await api.enumClasses.all();
            const lists=(await Promise.all(classes.map(c=>api.enumClasses.enumerations(c.id)))).flat();
            const available=lists.filter(e=>!own.has(e.id)||own.get(e.id).excluded);
            if(!available.length){toast.info('Нет новых списков. Создайте характеристику в разделе «Перечисления».');return;}
            const result=await openForm({title:'Назначить характеристику',subtitle:node.name,fields:[{name:'enumId',label:'Список вариантов',type:'select',required:true,full:true,options:available.map(e=>({value:e.id,label:e.name}))}],onSubmit:async v=>{await api.catalog.assign(node.id,Number(v.enumId));}});
            if(result!==null)reload();
        }catch(err){toast.error(err.message);}
    };
    panel.querySelectorAll('[data-value]').forEach(btn=>btn.onclick=async()=>{
        const enumId=Number(btn.closest('[data-enum-block]').dataset.enumBlock), valueId=Number(btn.dataset.value);
        try {
            if(selectedByEnum.get(enumId)?.selectedValueId===valueId)await api.attributes.clear(node.id,enumId);
            else await api.attributes.select(node.id,{enumerationId:enumId,valueId});
            await reload();
        }catch(err){toast.error(err.message);}
    });
    panel.querySelectorAll('[data-exclude-enum]').forEach(btn=>btn.onclick=async()=>{
        if(!await confirmDialog({title:'Исключить характеристику?',message:'Она перестанет действовать здесь и у потомков. Сначала очистите сохранённые значения, если они есть.',confirmLabel:'Исключить'}))return;
        try{await api.catalog.exclude(node.id,Number(btn.dataset.excludeEnum));await reload();}catch(err){toast.error(err.message);}
    });
    panel.querySelectorAll('[data-unbind]').forEach(btn=>btn.onclick=async()=>{
        if(!await confirmDialog({title:'Снять собственное назначение?',message:'Если характеристика назначена выше, снова будут действовать её родительские ограничения. Сохранённые значения должны оставаться допустимыми.',confirmLabel:'Снять назначение'}))return;
        try{await api.catalog.unassign(node.id,Number(btn.dataset.unbind));await reload();}catch(err){toast.error(err.message);}
    });
    panel.querySelectorAll('[data-options]').forEach(btn=>btn.onclick=async()=>{
        const id=Number(btn.dataset.options), current=enums.find(e=>e.enumerationId===id);
        try {
            // Можно вернуть ранее исключённые варианты, но нельзя выйти за ограничения родителя.
            const parentEnums=node.parentId?await api.items.effectiveEnumerations(node.parentId):[];
            const parent=parentEnums.find(e=>e.enumerationId===id);
            const values=parent?parent.values:await api.enumerations.values(id);
            const binding=own.get(id);
            openDialog({title:'Какие варианты доступны?',subtitle:`${node.name} · ${current.enumerationName}`,build:dialog=>{
                dialog.body.innerHTML=`<p>Отметьте все варианты, в которых выпускается модель. Сохранённые исполнения должны им соответствовать.</p><label class="flex-gap mt-16"><input type="checkbox" data-all ${!binding?.restricted?'checked':''}>Все варианты родителя / общего списка</label><div class="catalog-options mt-16">${values.map(v=>`<label><input type="checkbox" data-option="${v.id}" ${current.values.some(x=>x.id===v.id)?'checked':''}>${esc(v.name)}</label>`).join('')}</div><p data-options-error role="alert"></p>`;
                const toggle=()=>{dialog.body.querySelectorAll('[data-option]').forEach(c=>c.disabled=dialog.body.querySelector('[data-all]').checked);};
                dialog.body.querySelector('[data-all]').onchange=toggle;toggle();
                dialog.addButton({label:'Отмена',onClick:()=>dialog.close()});
                dialog.addButton({label:'Сохранить варианты',variant:'btn--primary',onClick:async(_,save)=>{
                    save.disabled=true;
                    try{
                        const ids=dialog.body.querySelector('[data-all]').checked?null:[...dialog.body.querySelectorAll('[data-option]:checked')].map(c=>Number(c.dataset.option));
                        if(ids?.length===0)throw new Error('Выберите хотя бы один вариант.');
                        await api.catalog.assign(node.id,id,ids);dialog.close();await reload();
                    }catch(err){dialog.body.querySelector('[data-options-error]').textContent=err.message;save.disabled=false;}
                }});
            }});
        }catch(err){toast.error(err.message);}
    });
}

/* ═══════════════════════════ Вкладка «Анализ» ═══════════════════════════ */

async function renderAnalysisTab(panel, node, ctx) {
    let numericParams, enums;
    try {
        [numericParams, enums] = await loadScopeParameters(node.id);
    } catch (err) {
        panel.innerHTML = emptyState({ icon: 'alert-triangle', title: 'Ошибка загрузки', text: err.message });
        return;
    }

    panel.innerHTML = `
        <div class="notice notice--info mb-16">
            ${icon('bar-chart', 16)}
            <span>Анализ выполняется по всему поддереву узла «${esc(node.name)}» —
            учитываются модели и конкретные изделия, включая выбранную запись. Группы не участвуют. Считаются только заполненные значения; число записей не является складским остатком.</span>
        </div>

        <div class="section-block">
            <div class="section-title">${icon('sigma', 16)}<span>Сводка по числовому параметру</span></div>
            ${numericParams.length === 0
                ? `<div class="notice notice--warning">${icon('alert-triangle', 16)}<span>Для узла нет числовых параметров.</span></div>`
                : `<div class="flex-gap flex-wrap">
                    <select class="select" id="agg-param" style="max-width:320px">
                        ${numericParams.map((p) => `<option value="${p.parameterId}">${esc(p.parameterName)} (${esc(p.parameterCode)})</option>`).join('')}
                    </select>
                    <button class="btn btn--primary" id="agg-run">${icon('bar-chart', 15)}<span>Рассчитать</span></button>
                   </div>
                   <div id="agg-result" class="mt-16"></div>`}
        </div>

        <div class="section-block">
            <div class="section-title">${icon('filter', 16)}<span>Подбор изделий по параметрам</span></div>
            ${filterBuilderHtml(numericParams, enums)}
        </div>

        <div class="section-block">
            <div class="section-title">${icon('tags', 16)}<span>Распределение по перечислению</span></div>
            ${enums.length === 0
                ? `<div class="notice notice--warning">${icon('alert-triangle', 16)}<span>Для узла нет перечислений.</span></div>`
                : `<div class="flex-gap flex-wrap">
                    <select class="select" id="dist-enum" style="max-width:320px">
                        ${enums.map((e) => `<option value="${e.enumerationId}">${esc(e.enumerationName)}</option>`).join('')}
                    </select>
                    <button class="btn btn--primary" id="dist-run">${icon('bar-chart', 15)}<span>Построить</span></button>
                   </div>
                   <div id="dist-result" class="mt-16"></div>`}
        </div>
    `;
    withIcons(panel);

    // — Агрегаты по числовому параметру —
    const aggRun = panel.querySelector('#agg-run');
    if (aggRun) aggRun.addEventListener('click', async () => {
        const paramId = Number(panel.querySelector('#agg-param').value);
        const box = panel.querySelector('#agg-result');
        box.innerHTML = loadingState('Расчёт…');
        try {
            const a = await api.nodeNumeric.aggregates(node.id, paramId);
            box.innerHTML = a.count === 0
                ? `<div class="notice notice--warning">${icon('inbox', 16)}<span>В поддереве нет изделий со значением этого параметра.</span></div>`
                : `<div class="metric-grid">
                    ${metric('Количество', a.count)}
                    ${metric('Минимум', fmtNum(a.minValue))}
                    ${metric('Максимум', fmtNum(a.maxValue))}
                    ${metric('Среднее', fmtNum(a.avgValue))}
                    ${metric('Сумма', fmtNum(a.sumValue))}
                   </div>`;
            withIcons(box);
        } catch (err) { box.innerHTML = errBox(err.message); }
    });

    // — Подбор изделий по нескольким параметрам (общий модуль) —
    bindFilterBuilder(panel, () => node.id);

    // — Распределение по перечислению —
    const distRun = panel.querySelector('#dist-run');
    if (distRun) distRun.addEventListener('click', async () => {
        const enumId = Number(panel.querySelector('#dist-enum').value);
        const box = panel.querySelector('#dist-result');
        box.innerHTML = loadingState('Построение…');
        try {
            const d = await api.items.enumAggregates(node.id, enumId);
            if (!d.distribution || d.distribution.length === 0 || d.totalCount === 0) {
                box.innerHTML = `<div class="notice notice--warning">${icon('inbox', 16)}<span>В поддереве нет изделий с выбранным значением этого перечисления.</span></div>`;
                withIcons(box);
                return;
            }
            const max = Math.max(...d.distribution.map((x) => x.count), 1);
            box.innerHTML = `
                <div class="text-muted mb-16" style="font-size:12.5px">Всего изделий с заполненным параметром: <strong>${d.totalCount}</strong></div>
                <div class="distribution">
                    ${d.distribution.map((x) => `
                        <div class="dist-row">
                            <div class="dist-row__label">${esc(x.valueName)}</div>
                            <div class="dist-row__track">
                                <div class="dist-row__bar" style="width:${Math.round((x.count / max) * 100)}%"></div>
                            </div>
                            <div class="dist-row__count">${x.count}</div>
                        </div>
                    `).join('')}
                </div>
            `;
        } catch (err) { box.innerHTML = errBox(err.message); }
    });
}

function metric(label, value, unit) {
    return `
        <div class="metric">
            <div class="metric__label">${esc(label)}</div>
            <div class="metric__value">${esc(value)}${unit ? ` <span class="metric__unit">${esc(unit)}</span>` : ''}</div>
        </div>
    `;
}

function errBox(message) {
    return `<div class="notice notice--warning">${icon('alert-triangle', 16)}<span>${esc(message)}</span></div>`;
}

/* ─────────────────────────── Стили вкладки «Обзор» ─────────────────── */

let overviewStylesInjected = false;
function injectOverviewStyles() {
    if (overviewStylesInjected) return;
    overviewStylesInjected = true;
    const style = document.createElement('style');
    style.textContent = `
        .child-list { display: flex; flex-direction: column; gap: 6px; }
        .child-list__item {
            display: flex; align-items: center; gap: 10px;
            padding: 10px 12px; text-align: left;
            background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--r-md); cursor: pointer; font-family: inherit;
            transition: var(--transition);
        }
        .child-list__item:hover { border-color: var(--primary-border); background: var(--primary-soft); }
        .child-list__icon { display: flex; color: var(--text-soft); flex: none; }
        .child-list__item:hover .child-list__icon { color: var(--primary); }
        .child-list__name { flex: 1; min-width: 0; font-size: 13.5px; font-weight: 600;
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .child-list__arrow { display: flex; color: var(--text-soft); flex: none; }
        .child-list__item:hover .child-list__arrow { color: var(--primary); }
    `;
    document.head.appendChild(style);
}
