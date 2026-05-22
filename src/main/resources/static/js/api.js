/* ============================================================================
   Клиент REST API классификатора изделий.
   Интерфейс обслуживается тем же приложением Spring Boot (единый монолит),
   поэтому базовый адрес — относительный, без указания хоста.
   ========================================================================== */

const BASE = '/api/v1';

/** Ошибка обращения к API с HTTP-статусом и понятным сообщением. */
export class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
    }
}

function buildQuery(params) {
    if (!params) return '';
    const usp = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== null && value !== '') {
            usp.append(key, value);
        }
    }
    const str = usp.toString();
    return str ? `?${str}` : '';
}

async function request(method, path, body) {
    const options = { method, headers: {} };
    if (body !== undefined) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(BASE + path, options);
    } catch (networkError) {
        throw new ApiError(
            'Не удалось связаться с сервером. Убедитесь, что приложение и база данных запущены.',
            0
        );
    }

    if (response.status === 204) return null;

    const text = await response.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); }
        catch { data = text; }
    }

    if (!response.ok) {
        const message = (data && typeof data === 'object' && data.message)
            ? data.message
            : `Ошибка сервера (${response.status})`;
        throw new ApiError(message, response.status);
    }
    return data;
}

const get   = (path, params) => request('GET', path + buildQuery(params));
const post  = (path, body)   => request('POST', path, body);
const patch = (path, body)   => request('PATCH', path, body);
const put   = (path, body)   => request('PUT', path, body);
const del   = (path)         => request('DELETE', path);

/* ──────────────────────────── Публичный API ─────────────────────────── */

export const api = {

    /* — Узлы классификатора — */
    nodes: {
        roots:        ()              => get('/nodes/roots'),
        byId:         (id)            => get(`/nodes/${id}`),
        children:     (id)            => get(`/nodes/${id}/children`),
        tree:         ()              => get('/nodes/tree'),
        descendants:  (id)            => get(`/nodes/${id}/descendants`),
        ancestors:    (id)            => get(`/nodes/${id}/ancestors`),
        terminals:    (id)            => get(`/nodes/${id}/terminals`),
        search:       (query)         => get('/nodes/search', { query }),
        create:       (payload)       => post('/nodes', payload),
        update:       (id, payload)   => patch(`/nodes/${id}`, payload),
        remove:       (id)            => del(`/nodes/${id}`),
        move:         (id, parentId)  => patch(`/nodes/${id}/move`, { newParentId: parentId }),
        reorder:      (id, order)     => patch(`/nodes/${id}/reorder`, { newSortOrder: order }),
        validateCycles: ()            => post('/nodes/validate-cycles'),
    },

    /* — Единицы измерения — */
    units: {
        all:    ()            => get('/units'),
        byId:   (id)          => get(`/units/${id}`),
        create: (payload)     => post('/units', payload),
        update: (id, payload) => put(`/units/${id}`, payload),
        remove: (id)          => del(`/units/${id}`),
    },

    /* — Классы перечислений — */
    enumClasses: {
        all:          ()            => get('/enumeration-classes'),
        byId:         (id)          => get(`/enumeration-classes/${id}`),
        enumerations: (id)          => get(`/enumeration-classes/${id}/enumerations`),
        create:       (payload)     => post('/enumeration-classes', payload),
        update:       (id, payload) => patch(`/enumeration-classes/${id}`, payload),
        remove:       (id)          => del(`/enumeration-classes/${id}`),
    },

    /* — Перечисления и их значения — */
    enumerations: {
        byId:        (id)              => get(`/enumerations/${id}`),
        create:      (payload)         => post('/enumerations', payload),
        update:      (id, payload)     => patch(`/enumerations/${id}`, payload),
        remove:      (id)              => del(`/enumerations/${id}`),
        values:      (id)              => get(`/enumerations/${id}/values`),
        addValue:    (id, payload)     => post(`/enumerations/${id}/values`, payload),
        updateValue: (id, vId, body)   => patch(`/enumerations/${id}/values/${vId}`, body),
        removeValue: (id, vId)         => del(`/enumerations/${id}/values/${vId}`),
        reorderValue:(id, vId, order)  => patch(`/enumerations/${id}/values/${vId}/reorder`, { newSortOrder: order }),
    },

    /* — Атрибуты узла (выбранные значения перечислений) — */
    attributes: {
        all:    (nodeId)             => get(`/nodes/${nodeId}/attributes`),
        one:    (nodeId, enumId)     => get(`/nodes/${nodeId}/attributes/${enumId}`),
        select: (nodeId, payload)    => put(`/nodes/${nodeId}/attributes`, payload),
        clear:  (nodeId, enumId)     => del(`/nodes/${nodeId}/attributes/${enumId}`),
    },

    /* — Числовые параметры — */
    numericParams: {
        all:    ()            => get('/numeric-parameters'),
        byId:   (id)          => get(`/numeric-parameters/${id}`),
        create: (payload)     => post('/numeric-parameters', payload),
        update: (id, payload) => patch(`/numeric-parameters/${id}`, payload),
        remove: (id)          => del(`/numeric-parameters/${id}`),
    },

    /* — Числовые параметры и значения в контексте узла — */
    nodeNumeric: {
        ownParameters:       (nodeId)            => get(`/nodes/${nodeId}/numeric/parameters`),
        effectiveParameters: (nodeId)            => get(`/nodes/${nodeId}/numeric/parameters/effective`),
        assignParameter:     (nodeId, paramId)   => post(`/nodes/${nodeId}/numeric/parameters/${paramId}`),
        removeParameter:     (nodeId, paramId)   => del(`/nodes/${nodeId}/numeric/parameters/${paramId}`),
        values:              (nodeId)            => get(`/nodes/${nodeId}/numeric/values`),
        setValue:            (nodeId, payload)   => put(`/nodes/${nodeId}/numeric/values`, payload),
        clearValue:          (nodeId, paramId)   => del(`/nodes/${nodeId}/numeric/values/${paramId}`),
        aggregates:          (nodeId, paramId)   => get(`/nodes/${nodeId}/numeric/aggregates/${paramId}`),
        filter:              (nodeId, paramId, range) =>
                                 get(`/nodes/${nodeId}/numeric/filter/${paramId}`, range),
    },

    /* — Поиск и анализ изделий — */
    items: {
        search:               (query)          => get('/items/search', { query }),
        withParameters:       (nodeId)         => get(`/items/${nodeId}/parameters`),
        effectiveEnumerations:(nodeId)         => get(`/nodes/${nodeId}/enumerations/effective`),
        filterByEnum:         (nodeId, params) => get(`/nodes/${nodeId}/filter/enum`, params),
        enumAggregates:       (nodeId, enumId) => get(`/nodes/${nodeId}/aggregates/enum/${enumId}`),
        /** Отбор изделий по произвольному числу условий (числовых и перечислимых). */
        multiFilter:          (nodeId, body)   => post(`/nodes/${nodeId}/filter`, body),
    },
};
