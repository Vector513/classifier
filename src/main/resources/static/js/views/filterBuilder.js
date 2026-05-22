/* ============================================================================
   Конструктор фильтра изделий по нескольким параметрам.
   Переиспользуется в карточке узла (вкладка «Анализ») и в разделе
   «Поиск и анализ». Позволяет задать произвольное число условий по числовым
   и перечислимым параметрам; результат — изделия, удовлетворяющие всем сразу.
   ========================================================================== */

import { api } from '../api.js';
import { esc, icon, withIcons, fmtNum, toast, loadingState } from '../ui.js';
import { navigate } from '../router.js';

/**
 * Возвращает HTML конструктора фильтра (без обработчиков).
 * Если параметров нет — возвращает предупреждение.
 */
export function filterBuilderHtml(numericParams, enums) {
    if (numericParams.length === 0 && enums.length === 0) {
        return `<div class="notice notice--warning">${icon('alert-triangle', 16)}` +
            `<span>Для выбранного раздела не задано параметров, по которым можно выполнить отбор.</span></div>`;
    }

    const numericRows = numericParams.map((p) => `
        <div class="filter-crit" data-num-param="${p.parameterId}">
            <div class="filter-crit__name">
                ${esc(p.parameterName)}
                ${p.unitOfMeasureName ? `<span class="text-soft">· ${esc(p.unitOfMeasureName)}</span>` : ''}
            </div>
            <div class="filter-crit__controls">
                <input class="input filter-crit__num" data-min type="number" step="any" placeholder="от">
                <span class="filter-crit__dash">—</span>
                <input class="input filter-crit__num" data-max type="number" step="any" placeholder="до">
            </div>
        </div>
    `).join('');

    const enumRows = enums.map((e) => `
        <div class="filter-crit" data-enum-crit="${e.enumerationId}">
            <div class="filter-crit__name">${esc(e.enumerationName)}</div>
            <div class="filter-crit__controls">
                <select class="select filter-crit__select" data-enum-value>
                    <option value="">— не учитывать —</option>
                    ${e.values.map((v) => `<option value="${v.id}">${esc(v.name)}</option>`).join('')}
                </select>
            </div>
        </div>
    `).join('');

    return `
        <div class="notice notice--info mb-16">
            ${icon('info', 16)}
            <span>Заполните условия по нужным параметрам — будут найдены изделия,
            удовлетворяющие <strong>всем</strong> условиям сразу. Незаполненные параметры
            не учитываются. Без условий показываются все изделия раздела.</span>
        </div>
        <div class="filter-builder">
            ${numericParams.length ? `
                <div class="filter-builder__group">${icon('sliders', 13)}<span>Числовые параметры</span></div>
                ${numericRows}
            ` : ''}
            ${enums.length ? `
                <div class="filter-builder__group">${icon('tags', 13)}<span>Перечислимые параметры</span></div>
                ${enumRows}
            ` : ''}
        </div>
        <div class="flex-gap flex-wrap mt-16">
            <button class="btn btn--primary" data-act="mf-run">
                ${icon('search', 15)}<span>Найти изделия</span>
            </button>
            <button class="btn" data-act="mf-reset">
                ${icon('refresh', 15)}<span>Сбросить условия</span>
            </button>
        </div>
        <div data-mf-result class="mt-16"></div>
    `;
}

/**
 * Привязывает обработчики к отрисованному конструктору фильтра.
 * @param {HTMLElement} scopeEl — элемент, содержащий разметку filterBuilderHtml
 * @param {() => (number|null)} getNodeId — функция, возвращающая ID узла-области поиска
 */
export function bindFilterBuilder(scopeEl, getNodeId) {
    const runBtn = scopeEl.querySelector('[data-act="mf-run"]');
    if (!runBtn) return; // конструктор не отрисован (нет параметров)

    const resultBox = scopeEl.querySelector('[data-mf-result]');

    runBtn.addEventListener('click', async () => {
        const nodeId = getNodeId();
        if (nodeId == null) {
            toast.warning('Сначала выберите раздел классификатора для поиска');
            return;
        }

        // Числовые условия — учитываются только заполненные строки
        const numericCriteria = [];
        scopeEl.querySelectorAll('[data-num-param]').forEach((row) => {
            const minRaw = row.querySelector('[data-min]').value.trim();
            const maxRaw = row.querySelector('[data-max]').value.trim();
            if (minRaw === '' && maxRaw === '') return;
            numericCriteria.push({
                parameterId: Number(row.dataset.numParam),
                minValue: minRaw === '' ? null : Number(minRaw),
                maxValue: maxRaw === '' ? null : Number(maxRaw),
            });
        });

        // Перечислимые условия
        const enumCriteria = [];
        scopeEl.querySelectorAll('[data-enum-crit]').forEach((row) => {
            const val = row.querySelector('[data-enum-value]').value;
            if (!val) return;
            enumCriteria.push({
                enumerationId: Number(row.dataset.enumCrit),
                valueId: Number(val),
            });
        });

        for (const c of numericCriteria) {
            if (c.minValue != null && c.maxValue != null && c.minValue > c.maxValue) {
                toast.error('Нижняя граница диапазона не может быть больше верхней');
                return;
            }
        }
        const condCount = numericCriteria.length + enumCriteria.length;

        resultBox.innerHTML = loadingState('Подбор изделий…');
        try {
            const items = await api.items.multiFilter(nodeId, { numericCriteria, enumCriteria });
            if (items.length === 0) {
                resultBox.innerHTML = `<div class="notice notice--warning">${icon('inbox', 16)}<span>${
                    condCount > 0
                        ? `Изделий, удовлетворяющих всем условиям (${condCount}), не найдено.`
                        : 'В выбранном разделе нет изделий.'
                }</span></div>`;
                withIcons(resultBox);
                return;
            }
            resultBox.innerHTML =
                `<div class="text-muted mb-16" style="font-size:12.5px">Найдено изделий: ` +
                `<strong>${items.length}</strong>` +
                (condCount > 0 ? ` · задано условий: ${condCount}` : ' · показаны все изделия раздела') +
                `</div>` +
                items.map(filterItemCard).join('');
            withIcons(resultBox);
            resultBox.querySelectorAll('[data-goto]').forEach((c) =>
                c.addEventListener('click', () => navigate('/tree/' + c.dataset.goto)));
        } catch (err) {
            resultBox.innerHTML = `<div class="notice notice--warning">${icon('alert-triangle', 16)}` +
                `<span>${esc(err.message)}</span></div>`;
            withIcons(resultBox);
        }
    });

    const resetBtn = scopeEl.querySelector('[data-act="mf-reset"]');
    if (resetBtn) resetBtn.addEventListener('click', () => {
        scopeEl.querySelectorAll('[data-min], [data-max]').forEach((i) => { i.value = ''; });
        scopeEl.querySelectorAll('[data-enum-value]').forEach((s) => { s.value = ''; });
        resultBox.innerHTML = '';
    });
}

/** Карточка найденного изделия с перечнем его параметров. */
export function filterItemCard(item) {
    const numericTags = (item.numericValues || []).map((v) => `
        <span class="param-tag">
            <span class="param-tag__key">${esc(v.parameterName)}:</span>
            <span class="param-tag__val">${fmtNum(v.value)}${v.unitOfMeasureName ? ' ' + esc(v.unitOfMeasureName) : ''}</span>
        </span>`).join('');
    const enumTags = (item.enumerationAttributes || []).map((a) => `
        <span class="param-tag">
            <span class="param-tag__key">${esc(a.enumerationName)}:</span>
            <span class="param-tag__val">${esc(a.selectedValueName)}</span>
        </span>`).join('');
    const hasParams = numericTags || enumTags;

    return `
        <div class="result-card" data-goto="${item.id}" role="button" tabindex="0"
             title="Открыть изделие в классификаторе">
            <div class="result-card__head">
                <div class="result-card__icon">${icon('box', 20)}</div>
                <div style="flex:1;min-width:0">
                    <div class="result-card__title">${esc(item.name)}</div>
                    <div class="result-card__path">
                        <span class="badge badge--code">${esc(item.code)}</span>
                        ${item.parentName ? `&nbsp; в составе «${esc(item.parentName)}»` : ''}
                    </div>
                </div>
                <span style="color:var(--text-soft);display:flex">${icon('arrow-right', 18)}</span>
            </div>
            ${hasParams ? `<div class="result-card__params">${numericTags}${enumTags}</div>` : ''}
        </div>
    `;
}
