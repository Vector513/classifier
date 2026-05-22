/* ============================================================================
   Раздел «Числовые параметры» — справочник числовых характеристик изделий
   (масса, цена, ёмкость аккумулятора и т. п.) с допустимым диапазоном.
   ========================================================================== */

import { api, ApiError } from '../api.js';
import {
    esc, icon, withIcons, fmtNum, toast, loadingState, errorState, emptyState,
    openForm, confirmDialog,
} from '../ui.js';

let containerEl = null;

export async function render(container, route) {
    containerEl = container;
    container.innerHTML = `<div class="page">${loadingState('Загрузка числовых параметров…')}</div>`;
    await reload();
}

async function reload() {
    let params, units;
    try {
        [params, units] = await Promise.all([api.numericParams.all(), api.units.all()]);
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
                <div class="page-head__icon">${icon('sliders', 20)}</div>
                <div class="page-head__text">
                    <h1>Числовые параметры</h1>
                    <div class="page-head__sub">
                        Справочник числовых характеристик. Для каждого параметра можно задать
                        допустимый диапазон — значения вне него система отклонит при вводе.
                    </div>
                </div>
                <div class="page-head__actions">
                    <button class="btn btn--primary" data-act="add">
                        ${icon('plus', 16)}<span>Добавить параметр</span>
                    </button>
                </div>
            </div>

            <div class="card">
                ${params.length === 0
                    ? emptyState({
                        icon: 'sliders',
                        title: 'Числовые параметры не заданы',
                        text: 'Добавьте первый числовой параметр — например, «Масса» или «Цена».',
                        actionHtml: `<button class="btn btn--primary" data-act="add-empty">${icon('plus', 16)}<span>Добавить параметр</span></button>`,
                    })
                    : `<div class="table-wrap"><table class="table">
                        <thead><tr>
                            <th>Параметр</th>
                            <th style="width:200px">Допустимый диапазон</th>
                            <th style="width:150px">Единица</th>
                            <th class="cell-actions">Действия</th>
                        </tr></thead>
                        <tbody>
                            ${params.map((p) => paramRow(p)).join('')}
                        </tbody>
                    </table></div>`}
            </div>
        </div>
    `;
    withIcons(containerEl);

    const addHandler = () => openParamForm(null, units);
    containerEl.querySelector('[data-act="add"]')?.addEventListener('click', addHandler);
    containerEl.querySelector('[data-act="add-empty"]')?.addEventListener('click', addHandler);

    containerEl.querySelectorAll('[data-edit]').forEach((btn) => {
        const param = params.find((p) => p.id === Number(btn.dataset.edit));
        btn.addEventListener('click', () => openParamForm(param, units));
    });
    containerEl.querySelectorAll('[data-delete]').forEach((btn) => {
        const param = params.find((p) => p.id === Number(btn.dataset.delete));
        btn.addEventListener('click', () => removeParam(param));
    });
}

function paramRow(p) {
    const range = (p.minValue != null || p.maxValue != null)
        ? `<span class="badge badge--blue">${p.minValue != null ? fmtNum(p.minValue) : '−∞'} … ${p.maxValue != null ? fmtNum(p.maxValue) : '+∞'}</span>`
        : '<span class="text-soft">не ограничен</span>';
    return `
        <tr>
            <td>
                <div style="display:flex;align-items:center;gap:8px">
                    <span style="font-weight:650">${esc(p.name)}</span>
                    <span class="badge badge--code">${esc(p.code)}</span>
                </div>
                ${p.description ? `<div class="text-muted" style="font-size:12px;margin-top:3px">${esc(p.description)}</div>` : ''}
            </td>
            <td>${range}</td>
            <td>${p.unitOfMeasureName ? esc(p.unitOfMeasureName) : '<span class="text-soft">—</span>'}</td>
            <td class="cell-actions">
                <div class="row-actions">
                    <button class="icon-btn icon-btn--primary" data-edit="${p.id}" title="Изменить">${icon('edit', 16)}</button>
                    <button class="icon-btn icon-btn--danger" data-delete="${p.id}" title="Удалить">${icon('trash', 16)}</button>
                </div>
            </td>
        </tr>
    `;
}

function openParamForm(param, units) {
    const editing = Boolean(param);
    const unitOpts = [
        { value: '', label: '— Не указана —' },
        ...units.map((u) => ({ value: u.id, label: `${u.name} (${u.code})` })),
    ];

    openForm({
        title: editing ? 'Изменение числового параметра' : 'Новый числовой параметр',
        subtitle: editing ? param.name : 'Добавление в справочник параметров',
        icon: 'sliders',
        wide: true,
        fields: [
            {
                name: 'code', label: 'Код', required: true, autofocus: true,
                value: editing ? param.code : '', maxlength: 100,
                placeholder: 'WEIGHT', hint: 'Уникальный код параметра.',
            },
            {
                name: 'name', label: 'Название', required: true,
                value: editing ? param.name : '', maxlength: 255,
                placeholder: 'Масса',
            },
            {
                name: 'description', label: 'Описание', type: 'textarea', full: true,
                value: editing ? (param.description || '') : '',
                placeholder: 'Краткое пояснение к параметру (необязательно)',
            },
            {
                name: 'minValue', label: 'Минимальное значение', type: 'number',
                value: editing && param.minValue != null ? param.minValue : '',
                hint: 'Оставьте пустым, если ограничения нет.',
            },
            {
                name: 'maxValue', label: 'Максимальное значение', type: 'number',
                value: editing && param.maxValue != null ? param.maxValue : '',
                hint: 'Оставьте пустым, если ограничения нет.',
            },
            {
                name: 'unitOfMeasureId', label: 'Единица измерения', type: 'select', full: true,
                value: editing && param.unitOfMeasureId ? param.unitOfMeasureId : '',
                options: unitOpts,
            },
        ],
        submitLabel: editing ? 'Сохранить' : 'Добавить параметр',
        submitIcon: editing ? 'save' : 'plus',
        onSubmit: async (v) => {
            if (v.minValue != null && v.maxValue != null && v.minValue > v.maxValue) {
                throw { field: 'maxValue', message: 'Максимум не может быть меньше минимума' };
            }
            const payload = {
                code: v.code,
                name: v.name,
                description: v.description || null,
                minValue: v.minValue,
                maxValue: v.maxValue,
                unitOfMeasureId: v.unitOfMeasureId ? Number(v.unitOfMeasureId) : null,
            };
            try {
                if (editing) {
                    await api.numericParams.update(param.id, payload);
                    toast.success(`Параметр «${v.name}» обновлён`);
                } else {
                    await api.numericParams.create(payload);
                    toast.success(`Параметр «${v.name}» добавлен`);
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

async function removeParam(param) {
    const ok = await confirmDialog({
        title: 'Удалить числовой параметр?',
        message: `Параметр «${esc(param.name)}» (${esc(param.code)}) будет удалён из справочника.`,
        detail: 'Удаление невозможно, если параметр назначен хотя бы одному узлу классификатора.',
        confirmLabel: 'Удалить', danger: true,
    });
    if (!ok) return;
    try {
        await api.numericParams.remove(param.id);
        toast.success(`Параметр «${param.name}» удалён`);
        reload();
    } catch (err) {
        toast.error(err.message);
    }
}
