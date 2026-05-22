/* ============================================================================
   Диалоги управления узлами классификатора: создание, изменение,
   перемещение и удаление. Используются и в дереве, и в карточке узла.
   ========================================================================== */

import { api, ApiError } from '../api.js';
import { openForm, confirmDialog, toast, esc, icon } from '../ui.js';

const CODE_HINT = 'Уникальный код. Рекомендуется латиница, цифры и дефис.';

function unitOptions(units) {
    return [
        { value: '', label: '— Не указана —' },
        ...units.map((u) => ({ value: u.id, label: `${u.name} (${u.code})` })),
    ];
}

/** Превращает ошибку дубликата кода в ошибку поля «code». */
function rethrow(err) {
    if (err instanceof ApiError && err.status === 409) {
        throw { field: 'code', message: err.message };
    }
    throw err;
}

function noticeHtml(text, tone = 'info') {
    const iconName = tone === 'warning' ? 'alert-triangle' : 'info';
    return `<div class="notice notice--${tone}">${icon(iconName, 16)}<span>${esc(text)}</span></div>`;
}

/* ─────────────────────────── Создание узла ─────────────────────────── */

export function createNodeDialog({ parentId = null, parentName = null, units = [] }) {
    return openForm({
        title: parentId ? 'Новый дочерний узел' : 'Новый корневой узел',
        subtitle: parentId
            ? `Узел будет добавлен в «${parentName}»`
            : 'Узел верхнего уровня классификатора',
        icon: 'add-child',
        noticeHtml: parentId
            ? noticeHtml(`Новый узел станет дочерним для класса «${parentName}» и унаследует его параметры.`)
            : noticeHtml('Корневой узел создаётся на верхнем уровне дерева классификатора.'),
        fields: [
            {
                name: 'code', label: 'Код узла', required: true, autofocus: true,
                placeholder: 'PHONES-APPLE', maxlength: 100, hint: CODE_HINT,
            },
            {
                name: 'name', label: 'Название', required: true, full: true,
                placeholder: 'Например: Apple', maxlength: 255,
            },
            {
                name: 'unitOfMeasureId', label: 'Единица измерения', type: 'select', full: true,
                options: unitOptions(units),
                hint: 'Единица, в которой учитывается изделие (необязательно).',
            },
        ],
        submitLabel: 'Создать узел', submitIcon: 'plus',
        onSubmit: async (v) => {
            try {
                const node = await api.nodes.create({
                    code: v.code,
                    name: v.name,
                    parentId: parentId,
                    unitOfMeasureId: v.unitOfMeasureId ? Number(v.unitOfMeasureId) : null,
                });
                toast.success(`Узел «${node.name}» создан`);
                return node;
            } catch (err) { rethrow(err); }
        },
    });
}

/* ─────────────────────────── Изменение узла ────────────────────────── */

export function editNodeDialog({ node, units = [] }) {
    return openForm({
        title: 'Изменение узла',
        subtitle: node.name,
        icon: 'edit',
        fields: [
            {
                name: 'code', label: 'Код узла', required: true, autofocus: true,
                value: node.code, maxlength: 100, hint: CODE_HINT,
            },
            {
                name: 'name', label: 'Название', required: true, full: true,
                value: node.name, maxlength: 255,
            },
            {
                name: 'unitOfMeasureId', label: 'Единица измерения', type: 'select', full: true,
                value: node.unitOfMeasure ? node.unitOfMeasure.id : '',
                options: unitOptions(units),
            },
        ],
        submitLabel: 'Сохранить изменения',
        onSubmit: async (v) => {
            try {
                const updated = await api.nodes.update(node.id, {
                    code: v.code,
                    name: v.name,
                    unitOfMeasureId: v.unitOfMeasureId ? Number(v.unitOfMeasureId) : null,
                });
                toast.success(`Изменения узла «${updated.name}» сохранены`);
                return updated;
            } catch (err) { rethrow(err); }
        },
    });
}

/* ─────────────────────────── Перемещение узла ──────────────────────── */

/** Собирает плоский список узлов дерева с отступами по уровню вложенности. */
function flattenTree(tree, depth = 0, acc = []) {
    for (const n of tree) {
        acc.push({ id: n.id, name: n.name, code: n.code, depth });
        if (n.children && n.children.length) flattenTree(n.children, depth + 1, acc);
    }
    return acc;
}

/** Идентификаторы узла и всех его потомков (запрещены как новый родитель). */
function collectSubtreeIds(tree, targetId, found = { hit: false }, acc = new Set()) {
    for (const n of tree) {
        if (n.id === targetId) {
            const mark = (node) => {
                acc.add(node.id);
                (node.children || []).forEach(mark);
            };
            mark(n);
        } else if (n.children) {
            collectSubtreeIds(n.children, targetId, found, acc);
        }
    }
    return acc;
}

export function moveNodeDialog({ node, tree }) {
    const forbidden = collectSubtreeIds(tree, node.id);
    const flat = flattenTree(tree).filter((n) => !forbidden.has(n.id));
    const indent = (d) => '    '.repeat(d);

    const options = [
        { value: 'ROOT', label: '⌂ Сделать корневым узлом' },
        ...flat.map((n) => ({ value: n.id, label: `${indent(n.depth)}${n.name}` })),
    ];

    return openForm({
        title: 'Перемещение узла',
        subtitle: node.name,
        icon: 'move',
        noticeHtml: noticeHtml(
            'При перемещении узел вместе со всеми потомками станет дочерним для выбранного класса. ' +
            'Перемещение внутрь собственного поддерева невозможно.'
        ),
        fields: [
            {
                name: 'target', label: 'Новый родительский узел', type: 'select', required: true, full: true,
                value: node.parentId ?? 'ROOT',
                options,
            },
        ],
        submitLabel: 'Переместить', submitIcon: 'move',
        onSubmit: async (v) => {
            const newParentId = v.target === 'ROOT' ? null : Number(v.target);
            if (newParentId === (node.parentId ?? null)) {
                throw { field: 'target', message: 'Узел уже находится в этом расположении' };
            }
            const updated = await api.nodes.move(node.id, newParentId);
            toast.success(`Узел «${updated.name}» перемещён`);
            return updated;
        },
    });
}

/* ─────────────────────────── Удаление узла ─────────────────────────── */

export async function deleteNodeDialog({ node }) {
    const childCount = node.childrenCount ?? 0;
    if (childCount > 0) {
        // Узел с потомками удалить нельзя — предупреждаем заранее
        await confirmDialog({
            title: 'Удаление недоступно',
            message: `Узел «${esc(node.name)}» содержит вложенные узлы (${childCount}).`,
            detail: 'Сначала удалите или переместите все дочерние узлы, затем повторите попытку.',
            confirmLabel: 'Понятно',
            cancelLabel: 'Закрыть',
            icon: 'alert-triangle',
        });
        return false;
    }

    const confirmed = await confirmDialog({
        title: 'Удалить узел?',
        message: `Узел «${esc(node.name)}» будет удалён без возможности восстановления.`,
        detail: 'Связанные значения параметров и выбранные атрибуты также будут удалены.',
        confirmLabel: 'Удалить узел',
        danger: true,
    });
    if (!confirmed) return false;

    try {
        await api.nodes.remove(node.id);
        toast.success(`Узел «${node.name}» удалён`);
        return true;
    } catch (err) {
        toast.error(err.message);
        return false;
    }
}
