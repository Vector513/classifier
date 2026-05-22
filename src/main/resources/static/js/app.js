/* ============================================================================
   Точка входа клиентского приложения.
   Инициализирует оболочку интерфейса, маршрутизацию и навигацию.
   ========================================================================== */

import { hydrateIcons } from './icons.js';
import { parseHash, onRouteChange, navigate } from './router.js';
import { toast } from './ui.js';

import * as dashboard from './views/dashboard.js';
import * as tree from './views/tree.js';
import * as enumerations from './views/enumerations.js';
import * as numericParameters from './views/numericParameters.js';
import * as units from './views/units.js';
import * as search from './views/search.js';

/* Сопоставление первого сегмента адреса с разделом приложения */
const ROUTES = {
    '':                  { view: dashboard,         nav: '/',                   title: 'Главная' },
    'tree':              { view: tree,              nav: '/tree',               title: 'Классификатор' },
    'enumerations':      { view: enumerations,      nav: '/enumerations',       title: 'Перечисления' },
    'numeric-parameters':{ view: numericParameters, nav: '/numeric-parameters', title: 'Числовые параметры' },
    'units':             { view: units,             nav: '/units',              title: 'Единицы измерения' },
    'search':            { view: search,            nav: '/search',             title: 'Поиск и анализ' },
};

const viewContainer = document.getElementById('view');
let currentView = null;

/* ─────────────────────────── Маршрутизация ─────────────────────────── */

async function handleRoute() {
    const route = parseHash();
    const key = route.segments[0] || '';
    const match = ROUTES[key];

    if (!match) {
        // Неизвестный адрес — возвращаем на главную
        navigate('/');
        return;
    }

    document.title = `${match.title} · Классификатор изделий`;
    setActiveNav(match.nav);
    window.scrollTo(0, 0);
    viewContainer.scrollTop = 0;

    currentView = match.view;
    try {
        await match.view.render(viewContainer, route);
        hydrateIcons(viewContainer);
    } catch (err) {
        console.error('Ошибка при отображении раздела:', err);
        viewContainer.innerHTML = `
            <div class="page">
                <div class="error-state">
                    <div class="error-state__icon">
                        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/>
                            <path d="M12 9v4"/><path d="M12 17h.01"/>
                        </svg>
                    </div>
                    <div class="error-state__title">Не удалось отобразить раздел</div>
                    <div class="error-state__text">${err && err.message ? err.message : 'Внутренняя ошибка интерфейса.'}</div>
                    <button class="btn btn--primary mt-20" type="button" onclick="location.reload()">
                        Обновить страницу
                    </button>
                </div>
            </div>
        `;
        toast.error('Произошла ошибка при загрузке раздела');
    }
}

function setActiveNav(navPath) {
    document.querySelectorAll('.nav__item').forEach((item) => {
        item.classList.toggle('is-active', item.dataset.route === navPath);
    });
}

/* ─────────────────────────── Глобальный поиск ──────────────────────── */

function initGlobalSearch() {
    const form = document.getElementById('global-search');
    const input = document.getElementById('global-search-input');
    const clearBtn = document.getElementById('global-search-clear');
    if (!form || !input) return;

    const toggleClear = () => {
        clearBtn.hidden = input.value.length === 0;
    };

    input.addEventListener('input', toggleClear);

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const q = input.value.trim();
        if (q) navigate('/search', { q });
    });

    clearBtn.addEventListener('click', () => {
        input.value = '';
        toggleClear();
        input.focus();
    });

    // Быстрый доступ к поиску по нажатию «/»
    document.addEventListener('keydown', (e) => {
        if (e.key === '/' && document.activeElement !== input &&
            !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) {
            e.preventDefault();
            input.focus();
        }
    });
}

/* ─────────────────────────── Запуск ────────────────────────────────── */

function init() {
    hydrateIcons(document);
    initGlobalSearch();
    onRouteChange(handleRoute);

    // Первичный рендер
    if (!window.location.hash) {
        window.location.hash = '#/';
    } else {
        handleRoute();
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
