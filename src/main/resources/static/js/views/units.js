/* ============================================================================
   Раздел «Единицы измерения» — справочник единиц (CRUD).
   ========================================================================== */

import { api, ApiError } from '../api.js';
import {
    esc, icon, withIcons, toast, loadingState, errorState, emptyState,
    openForm, confirmDialog,
} from '../ui.js';

let containerEl = null;

export async function render(container, route) {
    containerEl = container;
    container.innerHTML = `<div class="page">${loadingState('Загрузка единиц измерения…')}</div>`;
    await reload();
}

async function reload() {
    let units;
    try {
        units = await api.units.all();
    } catch (err) {
        const offline = err instanceof ApiError && err.status === 0;
        containerEl.innerHTML = `<div class="page">${errorState({
            title: offline ? 'Нет связи с сервером' : 'Не удалось загрузить данные',
            text: err.message, isOffline: offline,
        })}</div>`;
        return;
    }

    containerEl.innerHTML = `
        <div class="page">
            <div class="page-head">
                <div class="page-head__icon">${icon('ruler', 20)}</div>
                <div class="page-head__text">
                    <h1>Единицы измерения</h1>
                    <div class="page-head__sub">
                        Справочник единиц, в которых учитываются изделия и числовые параметры
                        (штуки, килограммы, метры и т. п.).
                    </div>
                </div>
                <div class="page-head__actions">
                    <button class="btn btn--primary" data-act="add">
                        ${icon('plus', 16)}<span>Добавить единицу</span>
                    </button>
                </div>
            </div>

            <div class="card">
                ${units.length === 0
                    ? emptyState({
                        icon: 'ruler',
                        title: 'Единицы измерения не заданы',
                        text: 'Добавьте первую единицу измерения, чтобы использовать её для изделий и параметров.',
                        actionHtml: `<button class="btn btn--primary" data-act="add-empty">${icon('plus', 16)}<span>Добавить единицу</span></button>`,
                    })
                    : `<div class="table-wrap"><table class="table">
                        <thead><tr>
                            <th style="width:160px">Код</th>
                            <th>Название</th>
                            <th class="cell-actions">Действия</th>
                        </tr></thead>
                        <tbody>
                            ${units.map((u) => `
                                <tr>
                                    <td><span class="badge badge--code">${esc(u.code)}</span></td>
                                    <td style="font-weight:600">${esc(u.name)}</td>
                                    <td class="cell-actions">
                                        <div class="row-actions">
                                            <button class="icon-btn icon-btn--primary" data-edit="${u.id}" title="Изменить">${icon('edit', 16)}</button>
                                            <button class="icon-btn icon-btn--danger" data-delete="${u.id}" title="Удалить">${icon('trash', 16)}</button>
                                        </div>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table></div>`}
            </div>
        </div>
    `;
    withIcons(containerEl);

    const addHandler = () => openUnitForm();
    containerEl.querySelector('[data-act="add"]')?.addEventListener('click', addHandler);
    containerEl.querySelector('[data-act="add-empty"]')?.addEventListener('click', addHandler);

    containerEl.querySelectorAll('[data-edit]').forEach((btn) => {
        const unit = units.find((u) => u.id === Number(btn.dataset.edit));
        btn.addEventListener('click', () => openUnitForm(unit));
    });
    containerEl.querySelectorAll('[data-delete]').forEach((btn) => {
        const unit = units.find((u) => u.id === Number(btn.dataset.delete));
        btn.addEventListener('click', () => removeUnit(unit));
    });
}

function openUnitForm(unit) {
    const editing = Boolean(unit);
    openForm({
        title: editing ? 'Изменение единицы измерения' : 'Новая единица измерения',
        subtitle: editing ? unit.name : 'Добавление в справочник единиц',
        icon: 'ruler',
        fields: [
            {
                name: 'code', label: 'Код', required: true, autofocus: true,
                value: editing ? unit.code : '', maxlength: 50,
                placeholder: 'PCS', hint: 'Краткое обозначение, например PCS, KG, M.',
            },
            {
                name: 'name', label: 'Название', required: true, full: true,
                value: editing ? unit.name : '', maxlength: 255,
                placeholder: 'штуки',
            },
        ],
        submitLabel: editing ? 'Сохранить' : 'Добавить',
        submitIcon: editing ? 'save' : 'plus',
        onSubmit: async (v) => {
            try {
                if (editing) {
                    await api.units.update(unit.id, { code: v.code, name: v.name });
                    toast.success(`Единица «${v.name}» обновлена`);
                } else {
                    await api.units.create({ code: v.code, name: v.name });
                    toast.success(`Единица «${v.name}» добавлена`);
                }
            } catch (err) {
                if (err instanceof ApiError && err.status === 409) {
                    throw { field: 'code', message: err.message };
                }
                throw err;
            }
        },
    }).then((res) => { if (res !== null) reload(); });
}

async function removeUnit(unit) {
    const ok = await confirmDialog({
        title: 'Удалить единицу измерения?',
        message: `Единица «${esc(unit.name)}» (${esc(unit.code)}) будет удалена из справочника.`,
        detail: 'Удаление невозможно, если единица используется узлами или параметрами.',
        confirmLabel: 'Удалить', danger: true,
    });
    if (!ok) return;
    try {
        await api.units.remove(unit.id);
        toast.success(`Единица «${unit.name}» удалена`);
        reload();
    } catch (err) {
        toast.error(err.message);
    }
}
