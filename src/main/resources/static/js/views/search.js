/* ============================================================================
   Раздел «Поиск и анализ изделий».
   • Быстрый поиск изделий по коду или названию.
   • Подбор изделий по произвольному набору параметров в выбранном разделе.
   ========================================================================== */

import { api, ApiError } from '../api.js';
import { esc, icon, withIcons, plural, loadingState, emptyState } from '../ui.js';
import { navigate } from '../router.js';
import { filterBuilderHtml, bindFilterBuilder, filterItemCard } from './filterBuilder.js';

let containerEl = null;
// Выбранная область поиска для фильтра сохраняется между переходами
let filterScopeId = null;

/** Плоский список узлов-классов (с потомками) для выпадающего списка. */
function flattenClasses(nodes, depth = 0, acc = []) {
    for (const n of nodes) {
        const hasChildren = n.children && n.children.length > 0;
        if (hasChildren) acc.push({ id: n.id, name: n.name, depth });
        if (hasChildren) flattenClasses(n.children, depth + 1, acc);
    }
    return acc;
}

export async function render(container, route) {
    containerEl = container;
    const query = (route.query.q || '').trim();

    container.innerHTML = `<div class="page">${loadingState('Загрузка раздела…')}</div>`;

    let tree;
    try {
        tree = await api.nodes.tree();
    } catch (err) {
        const offline = err instanceof ApiError && err.status === 0;
        container.innerHTML = `<div class="page">${emptyState({
            icon: offline ? 'wifi-off' : 'alert-triangle',
            title: offline ? 'Нет связи с сервером' : 'Не удалось загрузить раздел',
            text: err.message,
        })}</div>`;
        return;
    }

    const classes = flattenClasses(tree);

    container.innerHTML = `
        <div class="page">
            <div class="page-head">
                <div class="page-head__icon">${icon('search', 20)}</div>
                <div class="page-head__text">
                    <h1>Поиск и анализ изделий</h1>
                    <div class="page-head__sub">
                        Найдите изделие по названию или коду либо подберите изделия
                        по произвольному набору числовых и перечислимых параметров.
                    </div>
                </div>
            </div>

            <div class="card mb-16">
                <div class="card__head">
                    <span class="card__head-icon">${icon('search', 18)}</span>
                    <h2>Поиск по названию или коду</h2>
                </div>
                <div class="card__body">
                    <form id="search-form" class="input-group">
                        <div class="global-search" style="max-width:none;flex:1;margin:0">
                            <span class="global-search__icon">${icon('search', 18)}</span>
                            <input type="text" id="search-input" name="q"
                                   placeholder="Например: iPhone, Galaxy, PHONES-APPLE…"
                                   value="${esc(query)}" autocomplete="off">
                        </div>
                        <button class="btn btn--primary" type="submit">
                            ${icon('search', 16)}<span>Найти</span>
                        </button>
                    </form>
                    <div id="search-results" class="mt-16"></div>
                </div>
            </div>

            <div class="card">
                <div class="card__head">
                    <span class="card__head-icon">${icon('filter', 18)}</span>
                    <h2>Подбор изделий по параметрам</h2>
                </div>
                <div class="card__body">
                    ${classes.length === 0
                        ? emptyState({
                            icon: 'filter',
                            title: 'Нет разделов для отбора',
                            text: 'Создайте классы и изделия в разделе «Классификатор».',
                        })
                        : `<div class="field" style="max-width:480px">
                               <label class="field__label" for="filter-scope">Раздел классификатора для поиска</label>
                               <select class="select" id="filter-scope">
                                   ${classes.map((c) => `<option value="${c.id}">${'    '.repeat(c.depth)}${esc(c.name)}</option>`).join('')}
                               </select>
                               <div class="field__hint">Отбор выполняется среди всех изделий выбранного раздела и его подразделов.</div>
                           </div>
                           <div id="filter-area" class="mt-20"></div>`}
                </div>
            </div>
        </div>
    `;
    withIcons(container);

    /* — Быстрый поиск — */
    const form = container.querySelector('#search-form');
    const input = container.querySelector('#search-input');
    const resultsEl = container.querySelector('#search-results');

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const q = input.value.trim();
        navigate('/search', q ? { q } : {});
    });

    if (query) {
        runTextSearch(query, resultsEl);
    } else {
        resultsEl.innerHTML = `<div class="text-soft" style="font-size:12.5px">${esc('Введите запрос и нажмите «Найти».')}</div>`;
        input.focus();
    }

    /* — Подбор по параметрам — */
    const scopeSel = container.querySelector('#filter-scope');
    if (scopeSel) {
        const filterArea = container.querySelector('#filter-area');
        if (filterScopeId == null || !classes.some((c) => c.id === filterScopeId)) {
            filterScopeId = classes.length ? classes[0].id : null;
        }
        scopeSel.value = String(filterScopeId);
        scopeSel.addEventListener('change', () => {
            filterScopeId = Number(scopeSel.value);
            loadFilter(filterArea);
        });
        loadFilter(filterArea);
    }
}

/* ─────────────────────────── Быстрый поиск ─────────────────────────── */

async function runTextSearch(query, resultsEl) {
    resultsEl.innerHTML = loadingState('Поиск изделий…');
    let results;
    try {
        results = await api.items.search(query);
    } catch (err) {
        resultsEl.innerHTML = `<div class="notice notice--warning">${icon('alert-triangle', 16)}<span>${esc(err.message)}</span></div>`;
        withIcons(resultsEl);
        return;
    }

    if (results.length === 0) {
        resultsEl.innerHTML = emptyState({
            icon: 'inbox',
            title: 'Ничего не найдено',
            text: `По запросу «${query}» изделия не найдены. Проверьте написание запроса.`,
        });
        withIcons(resultsEl);
        return;
    }

    resultsEl.innerHTML =
        `<div class="text-muted mb-16" style="font-size:13px">Найдено: <strong>${results.length}</strong> ` +
        `${plural(results.length, 'изделие', 'изделия', 'изделий')} по запросу «${esc(query)}»</div>` +
        results.map(filterItemCard).join('');
    withIcons(resultsEl);
    resultsEl.querySelectorAll('[data-goto]').forEach((card) => {
        card.addEventListener('click', () => navigate('/tree/' + card.dataset.goto));
    });
}

/* ─────────────────────────── Подбор по параметрам ──────────────────── */

async function loadFilter(filterArea) {
    if (filterScopeId == null) { filterArea.innerHTML = ''; return; }

    filterArea.innerHTML = loadingState('Загрузка параметров раздела…');
    let numericParams, enums;
    try {
        [numericParams, enums] = await Promise.all([
            api.nodeNumeric.effectiveParameters(filterScopeId),
            api.items.effectiveEnumerations(filterScopeId),
        ]);
    } catch (err) {
        filterArea.innerHTML = `<div class="notice notice--warning">${icon('alert-triangle', 16)}<span>${esc(err.message)}</span></div>`;
        withIcons(filterArea);
        return;
    }

    filterArea.innerHTML = filterBuilderHtml(numericParams, enums);
    withIcons(filterArea);
    bindFilterBuilder(filterArea, () => filterScopeId);
}
