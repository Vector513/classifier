/* ============================================================================
   Главная страница — обзор состояния справочника и быстрый переход в разделы.
   ========================================================================== */

import { api, ApiError } from '../api.js';
import { esc, icon, withIcons, loadingState, errorState, plural } from '../ui.js';

function countTree(nodes) {
    let total = 0;
    let terminals = 0;
    const walk = (list) => {
        for (const n of list) {
            total += 1;
            if (n.isTerminal) terminals += 1;
            if (n.children && n.children.length) walk(n.children);
        }
    };
    walk(nodes || []);
    return { total, terminals };
}

function statCard({ tone, iconName, value, label, hint, href }) {
    return `
        <a class="stat-card" href="${href}">
            <div class="stat-card__top">
                <div class="stat-card__icon tone-${tone}">${icon(iconName, 22)}</div>
                <span class="stat-card__arrow">${icon('arrow-right', 18)}</span>
            </div>
            <div>
                <div class="stat-card__value">${value}</div>
                <div class="stat-card__label">${esc(label)}</div>
            </div>
            <div class="text-soft" style="font-size:12px">${esc(hint)}</div>
        </a>
    `;
}

export async function render(container, route) {
    container.innerHTML = `<div class="page">${loadingState('Загрузка обзора справочника…')}</div>`;

    let tree, units, enumClasses, numericParams;
    try {
        [tree, units, enumClasses, numericParams] = await Promise.all([
            api.nodes.tree(),
            api.units.all(),
            api.enumClasses.all(),
            api.numericParams.all(),
        ]);
    } catch (err) {
        const offline = err instanceof ApiError && err.status === 0;
        container.innerHTML = `<div class="page">${errorState({
            title: offline ? 'Нет связи с сервером' : 'Не удалось загрузить данные',
            text: err.message,
            isOffline: offline,
        })}</div>`;
        return;
    }

    const { total: nodeCount, terminals } = countTree(tree);
    const classCount = enumClasses.length;
    const enumCount = enumClasses.reduce((sum, c) => sum + (c.enumerationCount || 0), 0);

    container.innerHTML = `
        <div class="page">
            <div class="page-head">
                <div class="page-head__text">
                    <h1>Обзор справочника</h1>
                    <div class="page-head__sub">
                        Информационная система для ведения иерархического справочника изделий:
                        классификация, числовые и перечислимые параметры, наследование и анализ данных.
                    </div>
                </div>
                <div class="page-head__actions">
                    <a class="btn btn--primary" href="#/tree">
                        ${icon('tree', 16)}<span>Открыть классификатор</span>
                    </a>
                </div>
            </div>

            <div class="grid grid--stats">
                ${statCard({
                    tone: 'blue', iconName: 'tree', value: nodeCount,
                    label: 'Узлов классификатора',
                    hint: `в том числе ${terminals} ${plural(terminals, 'изделие', 'изделия', 'изделий')}`,
                    href: '#/tree',
                })}
                ${statCard({
                    tone: 'violet', iconName: 'tags', value: classCount,
                    label: plural(classCount, 'Класс перечислений', 'Класса перечислений', 'Классов перечислений'),
                    hint: `${enumCount} ${plural(enumCount, 'перечисление', 'перечисления', 'перечислений')}`,
                    href: '#/enumerations',
                })}
                ${statCard({
                    tone: 'green', iconName: 'sliders', value: numericParams.length,
                    label: 'Числовых параметров',
                    hint: 'масса, цена, ёмкость и др.',
                    href: '#/numeric-parameters',
                })}
                ${statCard({
                    tone: 'amber', iconName: 'ruler', value: units.length,
                    label: 'Единиц измерения',
                    hint: 'справочник единиц',
                    href: '#/units',
                })}
            </div>

            <div class="grid grid--2 mt-24">
                <div class="card">
                    <div class="card__head">
                        <span class="card__head-icon">${icon('sparkles', 18)}</span>
                        <h2>Возможности системы</h2>
                    </div>
                    <div class="card__body">
                        <ul class="feature-list">
                            ${feature('tree', 'Иерархический классификатор',
                                'Древовидная структура изделий с созданием, перемещением и упорядочиванием узлов.')}
                            ${feature('git-branch', 'Наследование параметров',
                                'Параметры, заданные на классе, автоматически действуют для всех вложенных изделий.')}
                            ${feature('sliders', 'Числовые и перечислимые параметры',
                                'Контроль допустимых диапазонов значений и выбор из заранее заданных вариантов.')}
                            ${feature('bar-chart', 'Анализ и фильтрация',
                                'Агрегаты (мин./макс./среднее), распределения и отбор изделий по значениям.')}
                        </ul>
                    </div>
                </div>

                <div class="card">
                    <div class="card__head">
                        <span class="card__head-icon">${icon('rocket', 18)}</span>
                        <h2>С чего начать</h2>
                    </div>
                    <div class="card__body">
                        <ol class="step-list">
                            ${step('Откройте раздел «Классификатор» и выберите узел дерева.')}
                            ${step('Назначьте классу числовые параметры и перечисления.')}
                            ${step('Заполните значения параметров для конкретных изделий.')}
                            ${step('Используйте раздел «Поиск и анализ» для отбора и статистики.')}
                        </ol>
                        <a class="btn btn--subtle btn--block mt-16" href="#/tree">
                            ${icon('arrow-right', 16)}<span>Перейти к классификатору</span>
                        </a>
                    </div>
                </div>
            </div>
        </div>
    `;

    // Небольшие стили списков подключаем единожды
    injectDashboardStyles();
    withIcons(container);
}

function feature(iconName, title, text) {
    return `
        <li class="feature-list__item">
            <span class="feature-list__icon">${icon(iconName, 18)}</span>
            <div>
                <div class="feature-list__title">${esc(title)}</div>
                <div class="feature-list__text">${esc(text)}</div>
            </div>
        </li>
    `;
}

function step(text) {
    return `<li class="step-list__item">${esc(text)}</li>`;
}

let stylesInjected = false;
function injectDashboardStyles() {
    if (stylesInjected) return;
    stylesInjected = true;
    const css = `
        .feature-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 16px; }
        .feature-list__item { display: flex; gap: 12px; }
        .feature-list__icon {
            display: flex; align-items: center; justify-content: center;
            width: 36px; height: 36px; flex: none;
            color: var(--primary); background: var(--primary-soft);
            border-radius: var(--r-sm);
        }
        .feature-list__title { font-size: 13.5px; font-weight: 650; }
        .feature-list__text { font-size: 12.5px; color: var(--text-muted); margin-top: 2px; line-height: 1.45; }
        .step-list { margin: 0; padding: 0; list-style: none; counter-reset: step; display: flex; flex-direction: column; gap: 11px; }
        .step-list__item {
            position: relative; padding-left: 38px; font-size: 13px;
            color: var(--text-muted); line-height: 1.5; min-height: 26px;
            display: flex; align-items: center;
        }
        .step-list__item::before {
            counter-increment: step; content: counter(step);
            position: absolute; left: 0; top: 0;
            width: 26px; height: 26px;
            display: flex; align-items: center; justify-content: center;
            font-size: 12px; font-weight: 700;
            color: var(--primary); background: var(--primary-soft);
            border: 1px solid var(--primary-border); border-radius: 50%;
        }
    `;
    const style = document.createElement('style');
    style.textContent = css;
    document.head.appendChild(style);
}
