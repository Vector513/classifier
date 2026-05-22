/* ============================================================================
   Раздел «Перечисления» — управление классами перечислений, перечислениями
   и их значениями. Перечисление привязывается к узлу классификатора и
   действует для него и всех вложенных изделий.
   ========================================================================== */

import { api, ApiError } from '../api.js';
import {
    esc, icon, withIcons, fmtDate, plural, toast,
    loadingState, errorState, emptyState, openForm, confirmDialog,
} from '../ui.js';
import { navigate } from '../router.js';

const state = {
    classes: [],
    enumsByClass: new Map(),
    treeFlat: [],
    expanded: new Set(),
    selectedEnumId: null,
    loaded: false,
};

let containerEl = null;
let listEl = null;
let detailEl = null;

/* ─────────────────────────── Загрузка данных ───────────────────────── */

function flattenTree(nodes, depth = 0, acc = []) {
    for (const n of nodes) {
        acc.push({ id: n.id, name: n.name, depth });
        if (n.children && n.children.length) flattenTree(n.children, depth + 1, acc);
    }
    return acc;
}

async function loadAll() {
    const [classes, tree] = await Promise.all([api.enumClasses.all(), api.nodes.tree()]);
    state.classes = classes;
    state.treeFlat = flattenTree(tree);
    state.enumsByClass = new Map();
    await Promise.all(classes.map(async (c) => {
        const enums = await api.enumClasses.enumerations(c.id);
        state.enumsByClass.set(c.id, enums);
    }));
    state.loaded = true;
}

function nodeOptions(includeEmpty = true) {
    const opts = includeEmpty ? [{ value: '', label: '— Не привязано —' }] : [];
    for (const n of state.treeFlat) {
        opts.push({ value: n.id, label: '    '.repeat(n.depth) + n.name });
    }
    return opts;
}

function findEnum(id) {
    for (const list of state.enumsByClass.values()) {
        const found = list.find((e) => e.id === id);
        if (found) return found;
    }
    return null;
}

/* ─────────────────────────── Точка входа ───────────────────────────── */

export async function render(container, route) {
    containerEl = container;
    container.innerHTML = `<div class="page">${loadingState('Загрузка перечислений…')}</div>`;

    try {
        await loadAll();
    } catch (err) {
        const offline = err instanceof ApiError && err.status === 0;
        container.innerHTML = `<div class="page">${errorState({
            title: offline ? 'Нет связи с сервером' : 'Не удалось загрузить перечисления',
            text: err.message, isOffline: offline,
        })}</div>`;
        return;
    }

    // По умолчанию разворачиваем все классы
    if (state.expanded.size === 0) {
        state.classes.forEach((c) => state.expanded.add(c.id));
    }

    container.innerHTML = `
        <div class="page">
            <div class="page-head">
                <div class="page-head__icon">${icon('tags', 20)}</div>
                <div class="page-head__text">
                    <h1>Перечисления</h1>
                    <div class="page-head__sub">
                        Классы перечислений группируют однотипные характеристики (цвет, память, ОС).
                        Перечисление задаёт набор допустимых значений для выбора у изделий.
                    </div>
                </div>
            </div>

            <div class="enum-layout">
                <div class="card enum-panel" id="enum-list-card">
                    <div class="card__head">
                        <span class="card__head-icon">${icon('layers', 18)}</span>
                        <h2>Классы перечислений</h2>
                        <div class="card__head-actions">
                            <button class="btn btn--subtle btn--sm" data-act="add-class">
                                ${icon('plus', 15)}<span>Класс</span>
                            </button>
                        </div>
                    </div>
                    <div class="enum-list" id="enum-list"></div>
                </div>
                <div id="enum-detail"></div>
            </div>
        </div>
    `;
    withIcons(container);

    listEl = container.querySelector('#enum-list');
    detailEl = container.querySelector('#enum-detail');

    container.querySelector('[data-act="add-class"]').addEventListener('click', openCreateClass);

    renderList();
    renderDetail();
}

/* ─────────────────────────── Левая панель ──────────────────────────── */

function renderList() {
    if (state.classes.length === 0) {
        listEl.innerHTML = emptyState({
            icon: 'layers',
            title: 'Нет классов перечислений',
            text: 'Создайте первый класс — например, «Цвет» или «Объём памяти».',
        });
        withIcons(listEl);
        return;
    }

    listEl.innerHTML = state.classes.map((cls) => {
        const enums = state.enumsByClass.get(cls.id) || [];
        const isOpen = state.expanded.has(cls.id);
        return `
            <div class="enum-class ${isOpen ? 'is-open' : ''}" data-class="${cls.id}">
                <div class="enum-class__head" data-class-toggle="${cls.id}">
                    <span class="enum-class__toggle">${icon('chevron-right', 15)}</span>
                    <span class="card__head-icon">${icon('layers', 16)}</span>
                    <span class="enum-class__name">${esc(cls.name)}</span>
                    <span class="badge badge--count badge--neutral">${enums.length}</span>
                    <span class="row-actions">
                        <button class="icon-btn icon-btn--primary" data-add-enum="${cls.id}" title="Добавить перечисление">${icon('plus', 15)}</button>
                        <button class="icon-btn" data-edit-class="${cls.id}" title="Изменить класс">${icon('edit', 15)}</button>
                        <button class="icon-btn icon-btn--danger" data-delete-class="${cls.id}" title="Удалить класс">${icon('trash', 15)}</button>
                    </span>
                </div>
                <div class="enum-class__items">
                    ${enums.length === 0
                        ? '<div class="text-soft" style="font-size:12px;padding:6px 10px 6px 30px">Нет перечислений</div>'
                        : enums.map((e) => `
                            <div class="enum-item ${e.id === state.selectedEnumId ? 'is-selected' : ''}" data-enum="${e.id}">
                                ${icon('tags', 15)}
                                <span class="enum-item__name">${esc(e.name)}</span>
                                <span class="badge badge--count ${e.id === state.selectedEnumId ? 'badge--blue' : 'badge--neutral'}">${e.valueCount}</span>
                            </div>
                        `).join('')}
                </div>
            </div>
        `;
    }).join('');
    withIcons(listEl);

    // Разворачивание классов
    listEl.querySelectorAll('[data-class-toggle]').forEach((head) => {
        head.addEventListener('click', (e) => {
            if (e.target.closest('[data-add-enum],[data-edit-class],[data-delete-class]')) return;
            const id = Number(head.dataset.classToggle);
            if (state.expanded.has(id)) state.expanded.delete(id);
            else state.expanded.add(id);
            renderList();
        });
    });
    // Действия с классом
    listEl.querySelectorAll('[data-add-enum]').forEach((b) => {
        b.addEventListener('click', () => openCreateEnum(Number(b.dataset.addEnum)));
    });
    listEl.querySelectorAll('[data-edit-class]').forEach((b) => {
        const cls = state.classes.find((c) => c.id === Number(b.dataset.editClass));
        b.addEventListener('click', () => openEditClass(cls));
    });
    listEl.querySelectorAll('[data-delete-class]').forEach((b) => {
        const cls = state.classes.find((c) => c.id === Number(b.dataset.deleteClass));
        b.addEventListener('click', () => removeClass(cls));
    });
    // Выбор перечисления
    listEl.querySelectorAll('[data-enum]').forEach((row) => {
        row.addEventListener('click', () => {
            state.selectedEnumId = Number(row.dataset.enum);
            renderList();
            renderDetail();
        });
    });
}

/* ─────────────────────────── Правая панель ─────────────────────────── */

async function renderDetail() {
    if (state.selectedEnumId == null) {
        detailEl.innerHTML = `<div class="card">${emptyState({
            icon: 'tags',
            title: 'Перечисление не выбрано',
            text: 'Выберите перечисление в списке слева, чтобы просмотреть и изменить его значения.',
        })}</div>`;
        withIcons(detailEl);
        return;
    }

    detailEl.innerHTML = `<div class="card">${loadingState('Загрузка перечисления…')}</div>`;

    let enumeration;
    try {
        enumeration = await api.enumerations.byId(state.selectedEnumId);
    } catch (err) {
        detailEl.innerHTML = `<div class="card"><div class="card__body">${emptyState({
            icon: 'alert-triangle', title: 'Не удалось загрузить', text: err.message,
        })}</div></div>`;
        withIcons(detailEl);
        return;
    }

    const values = enumeration.values || [];
    detailEl.innerHTML = `
        <div class="card">
            <div class="card__body">
                <div class="node-detail__head">
                    <div class="node-detail__icon" style="color:var(--accent);background:var(--accent-soft);border-color:var(--accent-border)">
                        ${icon('tags', 24)}
                    </div>
                    <div style="flex:1;min-width:0">
                        <h2 class="node-detail__title">${esc(enumeration.name)}</h2>
                        <div class="node-detail__meta">
                            <span class="badge badge--code">${esc(enumeration.code)}</span>
                            <span class="badge badge--violet">${esc(enumeration.enumerationClassName)}</span>
                            ${enumeration.classifierNodeName
                                ? `<span class="badge badge--blue" title="Перечисление действует для этого узла и его потомков">
                                     ${icon('tree', 11)} ${esc(enumeration.classifierNodeName)}</span>`
                                : `<span class="badge badge--amber">${icon('alert-triangle', 11)} не привязано к узлу</span>`}
                        </div>
                    </div>
                </div>

                ${enumeration.classifierNodeName ? '' : `
                    <div class="notice notice--warning mt-16">
                        ${icon('alert-triangle', 16)}
                        <span>Перечисление не привязано к узлу классификатора, поэтому недоступно
                        для выбора у изделий. Укажите узел через «Изменить».</span>
                    </div>`}

                <div class="flex-gap flex-wrap mt-16">
                    <button class="btn btn--sm" data-act="edit-enum">${icon('edit', 15)}<span>Изменить</span></button>
                    ${enumeration.classifierNodeId
                        ? `<button class="btn btn--sm" data-act="open-node">${icon('tree', 15)}<span>Открыть узел</span></button>`
                        : ''}
                    <button class="btn btn--sm btn--ghost" data-act="delete-enum" style="color:var(--danger)">
                        ${icon('trash', 15)}<span>Удалить перечисление</span>
                    </button>
                </div>
            </div>

            <div class="tab-panel" style="border-top:1px solid var(--border)">
                <div class="section-title">
                    ${icon('list', 16)}<span>Значения перечисления</span>
                    <span class="section-title__count">${values.length}</span>
                    <span class="section-title__actions">
                        <button class="btn btn--subtle btn--sm" data-act="add-value">
                            ${icon('plus', 15)}<span>Добавить значение</span>
                        </button>
                    </span>
                </div>

                ${values.length === 0
                    ? emptyState({
                        icon: 'list',
                        title: 'Нет значений',
                        text: 'Добавьте варианты, которые можно будет выбирать у изделий.',
                    })
                    : `<div class="notice notice--info mb-16">
                        ${icon('info', 16)}
                        <span>Порядок значений задаёт их отображение при выборе у изделий.
                        Управляйте порядком стрелками.</span>
                       </div>
                       <div data-values>${values.map((v, idx) => valueRow(v, idx, values.length)).join('')}</div>`}

                <div class="text-soft mt-20" style="font-size:11.5px">
                    Создано: ${fmtDate(enumeration.createdAt)} · Изменено: ${fmtDate(enumeration.updatedAt)}
                </div>
            </div>
        </div>
    `;
    withIcons(detailEl);

    detailEl.querySelector('[data-act="edit-enum"]').addEventListener('click', () => openEditEnum(enumeration));
    detailEl.querySelector('[data-act="delete-enum"]').addEventListener('click', () => removeEnum(enumeration));
    detailEl.querySelector('[data-act="add-value"]').addEventListener('click', () => openAddValue(enumeration));
    const openNodeBtn = detailEl.querySelector('[data-act="open-node"]');
    if (openNodeBtn) {
        openNodeBtn.addEventListener('click', () => navigate('/tree/' + enumeration.classifierNodeId));
    }

    detailEl.querySelectorAll('[data-value-row]').forEach((row) => {
        const valueId = Number(row.dataset.valueRow);
        const value = values.find((v) => v.id === valueId);
        const idx = values.indexOf(value);

        row.querySelector('[data-act="edit-value"]').addEventListener('click', () => openEditValue(enumeration, value));
        row.querySelector('[data-act="delete-value"]').addEventListener('click', () => removeValue(enumeration, value));
        const up = row.querySelector('[data-act="move-up"]');
        const down = row.querySelector('[data-act="move-down"]');
        if (up) up.addEventListener('click', () => reorderValue(enumeration, value, idx - 1));
        if (down) down.addEventListener('click', () => reorderValue(enumeration, value, idx + 1));
    });
}

function valueRow(value, index, total) {
    return `
        <div class="value-row" data-value-row="${value.id}">
            <span class="value-row__order">${index + 1}</span>
            <div class="value-row__reorder">
                <button class="icon-btn" data-act="move-up" ${index === 0 ? 'disabled' : ''}
                        title="Выше">${icon('chevron-up-small', 14)}</button>
                <button class="icon-btn" data-act="move-down" ${index === total - 1 ? 'disabled' : ''}
                        title="Ниже">${icon('chevron-down-small', 14)}</button>
            </div>
            <div class="value-row__main">
                <div class="value-row__name">${esc(value.name)}</div>
                <div class="text-soft" style="font-size:11.5px">
                    <span class="text-mono">${esc(value.code)}</span>
                </div>
            </div>
            <div class="value-row__actions">
                <button class="icon-btn icon-btn--primary" data-act="edit-value" title="Изменить">${icon('edit', 16)}</button>
                <button class="icon-btn icon-btn--danger" data-act="delete-value" title="Удалить">${icon('trash', 16)}</button>
            </div>
        </div>
    `;
}

/* ─────────────────────────── Классы перечислений ───────────────────── */

function openCreateClass() {
    openForm({
        title: 'Новый класс перечислений',
        icon: 'layers',
        fields: [
            { name: 'code', label: 'Код', required: true, autofocus: true, maxlength: 100, placeholder: 'COLOR' },
            { name: 'name', label: 'Название', required: true, full: true, maxlength: 255, placeholder: 'Цвет' },
            { name: 'description', label: 'Описание', type: 'textarea', full: true,
              placeholder: 'Назначение класса (необязательно)' },
        ],
        submitLabel: 'Создать класс', submitIcon: 'plus',
        onSubmit: async (v) => {
            try {
                await api.enumClasses.create({ code: v.code, name: v.name, description: v.description || null });
                toast.success(`Класс «${v.name}» создан`);
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) throw { field: 'code', message: err.message };
                throw err;
            }
        },
    }).then((res) => { if (res !== null) refresh(); });
}

function openEditClass(cls) {
    openForm({
        title: 'Изменение класса перечислений',
        subtitle: cls.name,
        icon: 'edit',
        fields: [
            { name: 'code', label: 'Код', required: true, autofocus: true, value: cls.code, maxlength: 100 },
            { name: 'name', label: 'Название', required: true, full: true, value: cls.name, maxlength: 255 },
            { name: 'description', label: 'Описание', type: 'textarea', full: true, value: cls.description || '' },
        ],
        submitLabel: 'Сохранить',
        onSubmit: async (v) => {
            try {
                await api.enumClasses.update(cls.id, { code: v.code, name: v.name, description: v.description || null });
                toast.success(`Класс «${v.name}» обновлён`);
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) throw { field: 'code', message: err.message };
                throw err;
            }
        },
    }).then((res) => { if (res !== null) refresh(); });
}

async function removeClass(cls) {
    const enums = state.enumsByClass.get(cls.id) || [];
    if (enums.length > 0) {
        await confirmDialog({
            title: 'Удаление недоступно',
            message: `Класс «${esc(cls.name)}» содержит перечисления (${enums.length}).`,
            detail: 'Сначала удалите все перечисления этого класса.',
            confirmLabel: 'Понятно', cancelLabel: 'Закрыть', icon: 'alert-triangle',
        });
        return;
    }
    const ok = await confirmDialog({
        title: 'Удалить класс перечислений?',
        message: `Класс «${esc(cls.name)}» будет удалён.`,
        confirmLabel: 'Удалить', danger: true,
    });
    if (!ok) return;
    try {
        await api.enumClasses.remove(cls.id);
        toast.success(`Класс «${cls.name}» удалён`);
        refresh();
    } catch (err) { toast.error(err.message); }
}

/* ─────────────────────────── Перечисления ──────────────────────────── */

function openCreateEnum(classId) {
    const cls = state.classes.find((c) => c.id === classId);
    openForm({
        title: 'Новое перечисление',
        subtitle: `Класс: ${cls ? cls.name : ''}`,
        icon: 'tags',
        noticeHtml: `<div class="notice notice--info">${icon('info', 16)}<span>Привяжите перечисление к узлу классификатора — тогда его можно будет выбирать у этого узла и всех вложенных изделий.</span></div>`,
        fields: [
            { name: 'code', label: 'Код', required: true, autofocus: true, maxlength: 100, placeholder: 'PHONE-COLORS' },
            { name: 'name', label: 'Название', required: true, full: true, maxlength: 255, placeholder: 'Цвета смартфонов' },
            { name: 'classifierNodeId', label: 'Привязка к узлу классификатора', type: 'select', full: true,
              options: nodeOptions(), hint: 'Узел, для которого действует перечисление.' },
        ],
        submitLabel: 'Создать перечисление', submitIcon: 'plus',
        onSubmit: async (v) => {
            try {
                const created = await api.enumerations.create({
                    code: v.code, name: v.name, enumerationClassId: classId,
                    classifierNodeId: v.classifierNodeId ? Number(v.classifierNodeId) : null,
                });
                toast.success(`Перечисление «${v.name}» создано`);
                return created;
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) throw { field: 'code', message: err.message };
                throw err;
            }
        },
    }).then((created) => {
        if (created) {
            state.expanded.add(classId);
            refresh(created.id);
        }
    });
}

function openEditEnum(enumeration) {
    openForm({
        title: 'Изменение перечисления',
        subtitle: enumeration.name,
        icon: 'edit',
        fields: [
            { name: 'code', label: 'Код', required: true, autofocus: true, value: enumeration.code, maxlength: 100 },
            { name: 'name', label: 'Название', required: true, full: true, value: enumeration.name, maxlength: 255 },
            { name: 'classifierNodeId', label: 'Привязка к узлу классификатора', type: 'select', full: true,
              value: enumeration.classifierNodeId ?? '', options: nodeOptions() },
        ],
        submitLabel: 'Сохранить',
        onSubmit: async (v) => {
            try {
                await api.enumerations.update(enumeration.id, {
                    code: v.code, name: v.name,
                    classifierNodeId: v.classifierNodeId ? Number(v.classifierNodeId) : null,
                });
                toast.success('Перечисление обновлено');
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) throw { field: 'code', message: err.message };
                throw err;
            }
        },
    }).then((res) => { if (res !== null) refresh(enumeration.id); });
}

async function removeEnum(enumeration) {
    const valueCount = (enumeration.values || []).length;
    if (valueCount > 0) {
        await confirmDialog({
            title: 'Удаление недоступно',
            message: `Перечисление «${esc(enumeration.name)}» содержит значения (${valueCount}).`,
            detail: 'Сначала удалите все значения перечисления.',
            confirmLabel: 'Понятно', cancelLabel: 'Закрыть', icon: 'alert-triangle',
        });
        return;
    }
    const ok = await confirmDialog({
        title: 'Удалить перечисление?',
        message: `Перечисление «${esc(enumeration.name)}» будет удалено.`,
        confirmLabel: 'Удалить', danger: true,
    });
    if (!ok) return;
    try {
        await api.enumerations.remove(enumeration.id);
        toast.success('Перечисление удалено');
        state.selectedEnumId = null;
        refresh();
    } catch (err) { toast.error(err.message); }
}

/* ─────────────────────────── Значения перечисления ─────────────────── */

function openAddValue(enumeration) {
    openForm({
        title: 'Новое значение перечисления',
        subtitle: enumeration.name,
        icon: 'list',
        fields: [
            { name: 'code', label: 'Код значения', required: true, autofocus: true, maxlength: 100, placeholder: 'BLACK' },
            { name: 'name', label: 'Отображаемое название', required: true, full: true, maxlength: 255, placeholder: 'Чёрный' },
        ],
        submitLabel: 'Добавить значение', submitIcon: 'plus',
        onSubmit: async (v) => {
            try {
                await api.enumerations.addValue(enumeration.id, { code: v.code, name: v.name });
                toast.success(`Значение «${v.name}» добавлено`);
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) throw { field: 'code', message: err.message };
                throw err;
            }
        },
    }).then((res) => { if (res !== null) refresh(enumeration.id); });
}

function openEditValue(enumeration, value) {
    openForm({
        title: 'Изменение значения',
        subtitle: enumeration.name,
        icon: 'edit',
        fields: [
            { name: 'code', label: 'Код значения', required: true, autofocus: true, value: value.code, maxlength: 100 },
            { name: 'name', label: 'Отображаемое название', required: true, full: true, value: value.name, maxlength: 255 },
        ],
        submitLabel: 'Сохранить',
        onSubmit: async (v) => {
            try {
                await api.enumerations.updateValue(enumeration.id, value.id, { code: v.code, name: v.name });
                toast.success('Значение обновлено');
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) throw { field: 'code', message: err.message };
                throw err;
            }
        },
    }).then((res) => { if (res !== null) refresh(enumeration.id); });
}

async function removeValue(enumeration, value) {
    const ok = await confirmDialog({
        title: 'Удалить значение?',
        message: `Значение «${esc(value.name)}» будет удалено из перечисления.`,
        detail: 'Если это значение выбрано у каких-либо изделий, выбор будет снят.',
        confirmLabel: 'Удалить', danger: true,
    });
    if (!ok) return;
    try {
        await api.enumerations.removeValue(enumeration.id, value.id);
        toast.success('Значение удалено');
        refresh(enumeration.id);
    } catch (err) { toast.error(err.message); }
}

async function reorderValue(enumeration, value, newIndex) {
    try {
        await api.enumerations.reorderValue(enumeration.id, value.id, newIndex);
        refresh(enumeration.id);
    } catch (err) { toast.error(err.message); }
}

/* ─────────────────────────── Обновление ────────────────────────────── */

async function refresh(selectEnumId) {
    try {
        await loadAll();
        if (selectEnumId !== undefined) state.selectedEnumId = selectEnumId;
        // Если выбранное перечисление исчезло — снимаем выбор
        if (state.selectedEnumId != null && !findEnum(state.selectedEnumId)) {
            state.selectedEnumId = null;
        }
        renderList();
        renderDetail();
    } catch (err) {
        toast.error(err.message);
    }
}
