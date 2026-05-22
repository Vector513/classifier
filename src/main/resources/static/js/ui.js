/* ============================================================================
   UI-компоненты и вспомогательные функции.
   • Всплывающие уведомления (toast) — обратная связь на каждое действие.
   • Модальные окна и формы с понятной валидацией полей.
   • Утилиты форматирования и безопасного вывода.
   ========================================================================== */

import { svgIcon, hydrateIcons } from './icons.js';
import { ApiError } from './api.js';

/* ─────────────────────────── Утилиты ───────────────────────────────── */

/** Экранирование текста для безопасной вставки в HTML. */
export function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** Форматирование числа: убирает незначащие нули, разделяет разряды. */
export function fmtNum(value) {
    if (value === null || value === undefined || value === '') return '—';
    const num = Number(value);
    if (Number.isNaN(num)) return esc(value);
    let str = num.toString();
    if (str.includes('.')) str = str.replace(/\.?0+$/, '');
    const [intPart, fracPart] = str.split('.');
    const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    return fracPart ? `${grouped},${fracPart}` : grouped;
}

/** Форматирование даты/времени из ISO-строки (Instant). */
export function fmtDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleString('ru-RU', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });
}

/** Склонение существительного по числу: plural(5, 'узел','узла','узлов'). */
export function plural(n, one, few, many) {
    const abs = Math.abs(n) % 100;
    const last = abs % 10;
    if (abs > 10 && abs < 20) return many;
    if (last > 1 && last < 5) return few;
    if (last === 1) return one;
    return many;
}

/** Иконка как HTML-строка. */
export function icon(name, size = 18, stroke = 2) {
    return svgIcon(name, size, stroke);
}

/** Применяет иконки и возвращает переданный корневой элемент. */
export function withIcons(root) {
    hydrateIcons(root);
    return root;
}

/* ─────────────────────────── Уведомления ───────────────────────────── */

const TOAST_META = {
    success: { icon: 'check-circle',   title: 'Готово' },
    error:   { icon: 'x-circle',       title: 'Ошибка' },
    warning: { icon: 'alert-triangle', title: 'Внимание' },
    info:    { icon: 'info',           title: 'Информация' },
};

function showToast(type, message, title) {
    const stack = document.getElementById('toast-stack');
    if (!stack) return;
    const meta = TOAST_META[type] || TOAST_META.info;

    const node = document.createElement('div');
    node.className = `toast toast--${type}`;
    node.setAttribute('role', type === 'error' ? 'alert' : 'status');
    node.innerHTML = `
        <span class="toast__icon">${svgIcon(meta.icon, 20)}</span>
        <div class="toast__body">
            <div class="toast__title">${esc(title || meta.title)}</div>
            <div class="toast__msg">${esc(message)}</div>
        </div>
        <button class="toast__close" type="button" aria-label="Закрыть">${svgIcon('x', 15)}</button>
    `;
    stack.appendChild(node);

    let timer = null;
    const dismiss = () => {
        if (node.classList.contains('is-leaving')) return;
        clearTimeout(timer);
        node.classList.add('is-leaving');
        node.addEventListener('animationend', () => node.remove(), { once: true });
    };
    node.querySelector('.toast__close').addEventListener('click', dismiss);
    const lifetime = type === 'error' ? 7000 : 4200;
    timer = setTimeout(dismiss, lifetime);
    node.addEventListener('mouseenter', () => clearTimeout(timer));
    node.addEventListener('mouseleave', () => { timer = setTimeout(dismiss, 2500); });
}

export const toast = {
    success: (msg, title) => showToast('success', msg, title),
    error:   (msg, title) => showToast('error', msg, title),
    warning: (msg, title) => showToast('warning', msg, title),
    info:    (msg, title) => showToast('info', msg, title),
};

/* ─────────────────────────── Модальные окна ────────────────────────── */

const modalStack = [];

function closeTopModal() {
    const top = modalStack[modalStack.length - 1];
    if (top) top.dismiss();
}

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modalStack.length) {
        e.stopPropagation();
        closeTopModal();
    }
});

/**
 * Базовое модальное окно.
 * @returns {{overlay, modal, close}} управление окном
 */
function createModal({ title, subtitle, headIcon, headTone, wide, bodyHtml, onClose }) {
    const root = document.getElementById('modal-root');
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';

    const toneClass = headTone ? ` modal__head-icon--${headTone}` : '';
    overlay.innerHTML = `
        <div class="modal${wide ? ' modal--wide' : ''}" role="dialog" aria-modal="true">
            <div class="modal__head">
                ${headIcon ? `<div class="modal__head-icon${toneClass}">${svgIcon(headIcon, 20)}</div>` : ''}
                <div>
                    <div class="modal__title">${esc(title)}</div>
                    ${subtitle ? `<div class="modal__subtitle">${esc(subtitle)}</div>` : ''}
                </div>
                <button class="modal__close" type="button" aria-label="Закрыть">${svgIcon('x', 18)}</button>
            </div>
            <div class="modal__body">${bodyHtml || ''}</div>
        </div>
    `;
    root.appendChild(overlay);

    const modal = overlay.querySelector('.modal');
    const entry = { overlay, dismiss: () => doClose(true) };
    modalStack.push(entry);

    let closed = false;
    function doClose(cancelled) {
        if (closed) return;
        closed = true;
        const idx = modalStack.indexOf(entry);
        if (idx >= 0) modalStack.splice(idx, 1);
        overlay.remove();
        if (onClose) onClose(cancelled);
    }

    overlay.querySelector('.modal__close').addEventListener('click', () => doClose(true));
    overlay.addEventListener('mousedown', (e) => {
        if (e.target === overlay) doClose(true);
    });

    return { overlay, modal, close: doClose };
}

/**
 * Диалог подтверждения действия.
 * @returns {Promise<boolean>}
 */
export function confirmDialog({
    title = 'Подтвердите действие',
    message = '',
    detail = '',
    confirmLabel = 'Подтвердить',
    cancelLabel = 'Отмена',
    danger = false,
    icon: iconName,
} = {}) {
    return new Promise((resolve) => {
        let settled = false;
        const finish = (value) => {
            if (settled) return;
            settled = true;
            ctrl.close(false);
            resolve(value);
        };

        const ctrl = createModal({
            title,
            headIcon: iconName || (danger ? 'alert-triangle' : 'help'),
            headTone: danger ? 'danger' : 'warning',
            bodyHtml: `
                <p>${message}</p>
                ${detail ? `<p class="text-soft" style="font-size:12.5px;margin-top:8px">${detail}</p>` : ''}
            `,
            onClose: () => finish(false),
        });

        const foot = document.createElement('div');
        foot.className = 'modal__foot';
        foot.innerHTML = `
            <button class="btn" type="button" data-act="cancel">${esc(cancelLabel)}</button>
            <button class="btn ${danger ? 'btn--danger' : 'btn--primary'}" type="button" data-act="ok">
                ${danger ? svgIcon('trash', 16) : svgIcon('check', 16)}
                <span>${esc(confirmLabel)}</span>
            </button>
        `;
        ctrl.modal.appendChild(foot);
        foot.querySelector('[data-act="cancel"]').addEventListener('click', () => finish(false));
        foot.querySelector('[data-act="ok"]').addEventListener('click', () => finish(true));
        foot.querySelector('[data-act="ok"]').focus();
    });
}

/* ─────────────────────────── Формы в модальном окне ────────────────── */

function renderField(field) {
    const id = `fld-${field.name}`;
    const req = field.required ? '<span class="field__req" title="Обязательное поле">*</span>' : '';
    const cls = field.full ? 'field field--full' : 'field';
    let control = '';

    const common = `id="${id}" name="${esc(field.name)}" class="%CLS%" ` +
        `${field.placeholder ? `placeholder="${esc(field.placeholder)}"` : ''} ` +
        `${field.autofocus ? 'autofocus' : ''}`;

    if (field.type === 'textarea') {
        control = `<textarea ${common.replace('%CLS%', 'textarea')}>${esc(field.value ?? '')}</textarea>`;
    } else if (field.type === 'select') {
        const opts = (field.options || []).map((o) => {
            const val = o.value ?? '';
            const selected = String(val) === String(field.value ?? '') ? 'selected' : '';
            return `<option value="${esc(val)}" ${selected}>${esc(o.label)}</option>`;
        }).join('');
        control = `<select ${common.replace('%CLS%', 'select')}>${opts}</select>`;
    } else if (field.type === 'number') {
        const attrs = [
            field.min !== undefined ? `min="${field.min}"` : '',
            field.max !== undefined ? `max="${field.max}"` : '',
            `step="${field.step ?? 'any'}"`,
            `value="${field.value ?? ''}"`,
        ].join(' ');
        const input = `<input type="number" ${common.replace('%CLS%', 'input')} ${attrs}>`;
        control = field.suffix
            ? `<div class="input-suffix">${input}<span class="input-suffix__text">${esc(field.suffix)}</span></div>`
            : input;
    } else {
        control = `<input type="text" ${common.replace('%CLS%', 'input')} value="${esc(field.value ?? '')}" ` +
            `${field.maxlength ? `maxlength="${field.maxlength}"` : ''}>`;
    }

    return `
        <div class="${cls}" data-field="${esc(field.name)}">
            <label class="field__label" for="${id}">${esc(field.label)}${req}</label>
            ${control}
            ${field.hint ? `<div class="field__hint">${esc(field.hint)}</div>` : ''}
            <div class="field__error">${svgIcon('alert-circle', 13)}<span></span></div>
        </div>
    `;
}

/**
 * Открывает форму в модальном окне.
 * onSubmit(values) — асинхронная функция. Чтобы показать ошибку поля,
 * выбросьте объект { field: 'имя', message: 'текст' }; для общей ошибки —
 * обычный Error. При успехе окно закрывается, промис резолвится результатом.
 * @returns {Promise<any|null>}
 */
export function openForm({
    title,
    subtitle,
    icon: iconName = 'edit',
    fields,
    submitLabel = 'Сохранить',
    submitIcon = 'save',
    wide = false,
    onSubmit,
    noticeHtml,
}) {
    return new Promise((resolve) => {
        let settled = false;
        const finish = (value) => {
            if (settled) return;
            settled = true;
            ctrl.close(false);
            resolve(value);
        };

        const useGrid = fields.length > 3 && fields.some((f) => !f.full);
        const fieldsHtml = fields.map(renderField).join('');
        const bodyHtml = `
            <form class="modal-form" novalidate>
                ${noticeHtml ? `<div class="mb-16">${noticeHtml}</div>` : ''}
                <div class="${useGrid ? 'form-grid' : ''}">${fieldsHtml}</div>
                <div class="field__error" data-form-error style="margin-top:4px">
                    ${svgIcon('alert-circle', 13)}<span></span>
                </div>
            </form>
        `;

        const ctrl = createModal({
            title, subtitle, headIcon: iconName, wide,
            bodyHtml,
            onClose: () => finish(null),
        });
        hydrateIcons(ctrl.overlay);

        const form = ctrl.overlay.querySelector('form');
        const formError = ctrl.overlay.querySelector('[data-form-error]');

        const foot = document.createElement('div');
        foot.className = 'modal__foot';
        foot.innerHTML = `
            <button class="btn" type="button" data-act="cancel">Отмена</button>
            <button class="btn btn--primary" type="submit" data-act="submit">
                ${svgIcon(submitIcon, 16)}<span>${esc(submitLabel)}</span>
            </button>
        `;
        ctrl.modal.appendChild(foot);
        const submitBtn = foot.querySelector('[data-act="submit"]');
        foot.querySelector('[data-act="cancel"]').addEventListener('click', () => finish(null));
        submitBtn.addEventListener('click', () => form.requestSubmit());

        const clearErrors = () => {
            form.querySelectorAll('.field.has-error').forEach((f) => f.classList.remove('has-error'));
            formError.style.display = 'none';
        };
        const setFieldError = (name, message) => {
            const fieldEl = form.querySelector(`[data-field="${name}"]`);
            if (!fieldEl) { setFormError(message); return; }
            fieldEl.classList.add('has-error');
            fieldEl.querySelector('.field__error span').textContent = message;
        };
        const setFormError = (message) => {
            formError.classList.add('has-error');
            formError.style.display = 'flex';
            formError.querySelector('span').textContent = message;
        };

        // Снимаем подсветку ошибки, как только пользователь правит поле
        form.addEventListener('input', (e) => {
            const fieldEl = e.target.closest('.field');
            if (fieldEl) fieldEl.classList.remove('has-error');
        });

        function collectValues() {
            const values = {};
            for (const field of fields) {
                const input = form.elements[field.name];
                if (!input) continue;
                let raw = input.value;
                if (field.type === 'number') {
                    raw = raw.trim();
                    values[field.name] = raw === '' ? null : Number(raw);
                } else {
                    values[field.name] = typeof raw === 'string' ? raw.trim() : raw;
                }
            }
            return values;
        }

        function validate(values) {
            let firstError = null;
            for (const field of fields) {
                const value = values[field.name];
                if (field.required && (value === null || value === '' || value === undefined)) {
                    setFieldError(field.name, 'Поле обязательно для заполнения');
                    if (!firstError) firstError = field.name;
                    continue;
                }
                if (field.type === 'number' && value !== null) {
                    if (Number.isNaN(value)) {
                        setFieldError(field.name, 'Введите корректное число');
                        if (!firstError) firstError = field.name;
                    } else if (field.min !== undefined && value < field.min) {
                        setFieldError(field.name, `Значение не может быть меньше ${field.min}`);
                        if (!firstError) firstError = field.name;
                    } else if (field.max !== undefined && value > field.max) {
                        setFieldError(field.name, `Значение не может быть больше ${field.max}`);
                        if (!firstError) firstError = field.name;
                    }
                }
            }
            return firstError;
        }

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            clearErrors();
            const values = collectValues();

            const errField = validate(values);
            if (errField) {
                const input = form.elements[errField];
                if (input && input.focus) input.focus();
                return;
            }

            submitBtn.disabled = true;
            const originalHtml = submitBtn.innerHTML;
            submitBtn.innerHTML = `<span class="inline-spinner"></span><span>Сохранение…</span>`;
            try {
                const result = await onSubmit(values);
                finish(result === undefined ? values : result);
            } catch (err) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalHtml;
                if (err && err.field) {
                    setFieldError(err.field, err.message || 'Некорректное значение');
                    const input = form.elements[err.field];
                    if (input && input.focus) input.focus();
                } else {
                    const msg = err instanceof ApiError || err instanceof Error
                        ? err.message
                        : 'Не удалось сохранить изменения';
                    setFormError(msg);
                }
            }
        });

        // Фокус на первое поле
        const firstInput = form.querySelector('input, textarea, select');
        if (firstInput) setTimeout(() => firstInput.focus(), 50);
    });
}

/**
 * Произвольное модальное окно с собственным содержимым и кнопками.
 * builder(api) получает { body, addButton, close, setBusy } и наполняет окно.
 */
export function openDialog({ title, subtitle, icon: iconName, wide, build }) {
    const ctrl = createModal({ title, subtitle, headIcon: iconName, wide, bodyHtml: '' });
    const body = ctrl.overlay.querySelector('.modal__body');
    const foot = document.createElement('div');
    foot.className = 'modal__foot';
    ctrl.modal.appendChild(foot);

    const apiObj = {
        body,
        close: () => ctrl.close(true),
        addButton: ({ label, variant = 'btn', iconName: bi, onClick, id }) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = `btn ${variant}`;
            if (id) btn.dataset.btnId = id;
            btn.innerHTML = `${bi ? svgIcon(bi, 16) : ''}<span>${esc(label)}</span>`;
            btn.addEventListener('click', () => onClick(apiObj, btn));
            foot.appendChild(btn);
            return btn;
        },
    };
    if (build) build(apiObj);
    hydrateIcons(ctrl.overlay);
    return apiObj;
}

/* ─────────────────────────── Готовые блоки разметки ────────────────── */

export function emptyState({ icon: iconName = 'inbox', title, text, actionHtml }) {
    return `
        <div class="empty">
            <div class="empty__icon">${svgIcon(iconName, 30)}</div>
            <div class="empty__title">${esc(title)}</div>
            ${text ? `<div class="empty__text">${esc(text)}</div>` : ''}
            ${actionHtml ? `<div class="empty__action">${actionHtml}</div>` : ''}
        </div>
    `;
}

export function loadingState(text = 'Загрузка данных…') {
    return `<div class="loader"><div class="spinner"></div><span>${esc(text)}</span></div>`;
}

export function errorState({ title = 'Не удалось загрузить данные', text = '', isOffline = false }) {
    return `
        <div class="error-state">
            <div class="error-state__icon">${svgIcon(isOffline ? 'wifi-off' : 'alert-triangle', 30)}</div>
            <div class="error-state__title">${esc(title)}</div>
            ${text ? `<div class="error-state__text">${esc(text)}</div>` : ''}
            <button class="btn btn--primary mt-20" type="button" onclick="location.reload()">
                ${svgIcon('refresh', 16)}<span>Обновить страницу</span>
            </button>
        </div>
    `;
}
