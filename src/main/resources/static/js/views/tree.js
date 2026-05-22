/* ============================================================================
   Раздел «Классификатор» — древовидный справочник изделий.
   Слева: дерево с возможностью разворачивать/сворачивать узлы и управлять ими.
   Справа: подробная карточка выбранного узла (модуль nodeDetail).
   ========================================================================== */

import { api, ApiError } from '../api.js';
import {
    esc, icon, withIcons, toast, loadingState, errorState, emptyState,
} from '../ui.js';
import { navigate } from '../router.js';
import { renderNodeDetail } from './nodeDetail.js';
import { createNodeDialog, editNodeDialog, deleteNodeDialog } from './nodeForms.js';

// Состояние раздела сохраняется между переходами по узлам
const state = {
    tree: null,
    units: [],
    loaded: false,
    expanded: new Set(),
};

let treePanelEl = null;
let detailSlotEl = null;
let selectedId = null;

/* ─────────────────────────── Загрузка данных ───────────────────────── */

async function loadData() {
    const [tree, units] = await Promise.all([api.nodes.tree(), api.units.all()]);
    state.tree = tree;
    state.units = units;
    state.loaded = true;
}

/** Возвращает массив id-предков узла (для авто-разворачивания дерева). */
function findAncestorPath(nodes, targetId, trail = []) {
    for (const n of nodes) {
        if (n.id === targetId) return trail;
        if (n.children && n.children.length) {
            const res = findAncestorPath(n.children, targetId, [...trail, n.id]);
            if (res) return res;
        }
    }
    return null;
}

function allClassIds(nodes, acc = []) {
    for (const n of nodes) {
        if (n.children && n.children.length) {
            acc.push(n.id);
            allClassIds(n.children, acc);
        }
    }
    return acc;
}

/* ─────────────────────────── Точка входа ───────────────────────────── */

export async function render(container, route) {
    selectedId = route.segments[1] ? Number(route.segments[1]) : null;

    if (!state.loaded) {
        container.innerHTML = `<div class="page">${loadingState('Загрузка классификатора…')}</div>`;
        try {
            await loadData();
        } catch (err) {
            const offline = err instanceof ApiError && err.status === 0;
            container.innerHTML = `<div class="page">${errorState({
                title: offline ? 'Нет связи с сервером' : 'Не удалось загрузить классификатор',
                text: err.message, isOffline: offline,
            })}</div>`;
            return;
        }
    }

    // При первом открытии разворачиваем корневые классы
    if (state.expanded.size === 0 && state.tree.length) {
        state.tree.forEach((n) => { if (n.children && n.children.length) state.expanded.add(n.id); });
    }
    // Разворачиваем путь до выбранного узла
    if (selectedId != null) {
        const path = findAncestorPath(state.tree, selectedId);
        if (path) path.forEach((id) => state.expanded.add(id));
    }

    container.innerHTML = `
        <div class="page">
            <div class="page-head">
                <div class="page-head__icon">${icon('tree', 20)}</div>
                <div class="page-head__text">
                    <h1>Классификатор изделий</h1>
                    <div class="page-head__sub">
                        Иерархический справочник: классы группируют изделия, листовые узлы —
                        конкретные изделия. Выберите узел, чтобы просмотреть и изменить его данные.
                    </div>
                </div>
            </div>

            <div class="tree-layout">
                <div class="card tree-panel" id="tree-panel"></div>
                <div id="detail-slot"></div>
            </div>
        </div>
    `;
    withIcons(container);

    treePanelEl = container.querySelector('#tree-panel');
    detailSlotEl = container.querySelector('#detail-slot');

    renderTreePanel();
    renderDetail();
}

/* ─────────────────────────── Панель дерева ─────────────────────────── */

function detailContext() {
    return {
        units: state.units,
        getTree: () => state.tree,
        onTreeChanged: async () => {
            await loadData();
            renderTreePanel();
        },
    };
}

function renderDetail() {
    if (!detailSlotEl) return;
    if (selectedId == null) {
        detailSlotEl.innerHTML = `
            <div class="card">
                ${emptyState({
                    icon: 'tree',
                    title: 'Узел не выбран',
                    text: 'Выберите узел в дереве слева, чтобы просмотреть его параметры, ' +
                          'значения и выполнить анализ. Или создайте новый узел.',
                })}
            </div>
        `;
        withIcons(detailSlotEl);
        return;
    }
    renderNodeDetail(detailSlotEl, selectedId, detailContext());
}

function renderTreePanel() {
    const hasNodes = state.tree && state.tree.length > 0;

    treePanelEl.innerHTML = `
        <div class="tree-toolbar">
            <button class="btn btn--primary btn--sm" data-act="add-root">
                ${icon('plus', 15)}<span>Корневой узел</span>
            </button>
            <button class="icon-btn" data-act="expand-all" title="Развернуть все узлы">
                ${icon('expand', 17)}
            </button>
            <button class="icon-btn" data-act="collapse-all" title="Свернуть все узлы">
                ${icon('collapse', 17)}
            </button>
            <button class="icon-btn" data-act="validate" title="Проверить дерево на циклы">
                ${icon('check-circle', 17)}
            </button>
            <span class="tree-toolbar__spacer"></span>
            <button class="icon-btn" data-act="refresh" title="Обновить дерево">
                ${icon('refresh', 17)}
            </button>
        </div>
        <div class="tree-scroll">
            ${hasNodes
                ? `<div class="tree-root">${renderNodes(state.tree, 0)}</div>`
                : emptyState({
                    icon: 'tree',
                    title: 'Классификатор пуст',
                    text: 'Создайте первый корневой узел, чтобы начать наполнять справочник.',
                })}
        </div>
    `;
    withIcons(treePanelEl);
    bindToolbar();
    bindTreeEvents();
}

function renderNodes(nodes, depth) {
    return nodes.map((node) => {
        const hasChildren = node.children && node.children.length > 0;
        const isOpen = state.expanded.has(node.id);
        const isSelected = node.id === selectedId;
        const pad = depth * 17 + 6;

        const toggle = hasChildren
            ? `<button class="tree-row__toggle ${isOpen ? 'is-open' : ''}" data-toggle="${node.id}"
                       aria-label="${isOpen ? 'Свернуть' : 'Развернуть'}">${icon('chevron-right', 15)}</button>`
            : `<span class="tree-row__toggle tree-row__toggle--leaf"></span>`;

        const iconName = hasChildren ? (isOpen ? 'folder-open' : 'folder') : 'box';
        const iconCls = hasChildren ? 'tree-row__icon--folder' : 'tree-row__icon--item';

        return `
            <div class="tree-node ${isOpen ? 'is-open' : ''}" data-node="${node.id}">
                <div class="tree-row ${isSelected ? 'is-selected' : ''}" data-row="${node.id}"
                     style="padding-left:${pad}px">
                    ${toggle}
                    <span class="tree-row__icon ${iconCls}">${icon(iconName, 17)}</span>
                    <span class="tree-row__name" title="${esc(node.name)}">${esc(node.name)}</span>
                    ${hasChildren ? `<span class="tree-row__count">${node.children.length}</span>` : ''}
                    <span class="tree-row__actions">
                        <button class="icon-btn icon-btn--primary" data-act="row-add" data-id="${node.id}"
                                title="Добавить дочерний узел">${icon('add-child', 15)}</button>
                        <button class="icon-btn" data-act="row-edit" data-id="${node.id}"
                                title="Изменить узел">${icon('edit', 15)}</button>
                        <button class="icon-btn icon-btn--danger" data-act="row-delete" data-id="${node.id}"
                                title="Удалить узел">${icon('trash', 15)}</button>
                    </span>
                </div>
                ${hasChildren
                    ? `<div class="tree-children">${renderNodes(node.children, depth + 1)}</div>`
                    : ''}
            </div>
        `;
    }).join('');
}

/* ─────────────────────────── Обработчики ───────────────────────────── */

function findNode(nodes, id) {
    for (const n of nodes) {
        if (n.id === id) return n;
        if (n.children) {
            const res = findNode(n.children, id);
            if (res) return res;
        }
    }
    return null;
}

function bindToolbar() {
    treePanelEl.querySelector('[data-act="add-root"]').addEventListener('click', async () => {
        const created = await createNodeDialog({ units: state.units });
        if (created) {
            await loadData();
            renderTreePanel();
            navigate('/tree/' + created.id);
        }
    });
    treePanelEl.querySelector('[data-act="expand-all"]').addEventListener('click', () => {
        allClassIds(state.tree).forEach((id) => state.expanded.add(id));
        renderTreePanel();
    });
    treePanelEl.querySelector('[data-act="collapse-all"]').addEventListener('click', () => {
        state.expanded.clear();
        renderTreePanel();
    });
    treePanelEl.querySelector('[data-act="refresh"]').addEventListener('click', async () => {
        try {
            await loadData();
            renderTreePanel();
            toast.success('Дерево обновлено');
        } catch (err) { toast.error(err.message); }
    });
    treePanelEl.querySelector('[data-act="validate"]').addEventListener('click', async () => {
        try {
            const res = await api.nodes.validateCycles();
            if (res.valid) {
                toast.success('Дерево корректно: циклов не обнаружено', 'Проверка целостности');
            } else {
                toast.warning(`Обнаружены циклы: ${res.cycles.length}`, 'Проверка целостности');
            }
        } catch (err) { toast.error(err.message); }
    });
}

function bindTreeEvents() {
    const root = treePanelEl.querySelector('.tree-root');
    if (!root) return;

    root.addEventListener('click', async (e) => {
        // Разворачивание/сворачивание
        const toggle = e.target.closest('[data-toggle]');
        if (toggle) {
            e.stopPropagation();
            const id = Number(toggle.dataset.toggle);
            if (state.expanded.has(id)) state.expanded.delete(id);
            else state.expanded.add(id);
            renderTreePanel();
            return;
        }

        // Действия в строке
        const actionBtn = e.target.closest('[data-act]');
        if (actionBtn) {
            e.stopPropagation();
            const id = Number(actionBtn.dataset.id);
            const node = findNode(state.tree, id);
            if (!node) return;
            const act = actionBtn.dataset.act;

            if (act === 'row-add') {
                const created = await createNodeDialog({
                    parentId: id, parentName: node.name, units: state.units,
                });
                if (created) {
                    state.expanded.add(id);
                    await loadData();
                    renderTreePanel();
                    navigate('/tree/' + created.id);
                }
            } else if (act === 'row-edit') {
                const updated = await editNodeDialog({ node, units: state.units });
                if (updated) {
                    await loadData();
                    renderTreePanel();
                    if (selectedId === id) renderDetail();
                }
            } else if (act === 'row-delete') {
                const childCount = node.children ? node.children.length : 0;
                const ok = await deleteNodeDialog({ node: { ...node, childrenCount: childCount } });
                if (ok) {
                    await loadData();
                    if (selectedId === id) {
                        navigate('/tree');
                    } else {
                        renderTreePanel();
                    }
                }
            }
            return;
        }

        // Выбор узла
        const row = e.target.closest('[data-row]');
        if (row) {
            const id = Number(row.dataset.row);
            if (id !== selectedId) navigate('/tree/' + id);
        }
    });
}
