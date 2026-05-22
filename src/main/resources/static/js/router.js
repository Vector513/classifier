/* ============================================================================
   Простой клиентский маршрутизатор на основе hash-навигации.
   Адреса вида:  #/tree  ·  #/tree/20  ·  #/search?q=phone
   Hash-маршрутизация выбрана намеренно: приложение отдаётся как статика
   единым Spring Boot-монолитом и не требует серверной обработки путей.
   ========================================================================== */

/** Разбирает текущий hash в объект маршрута. */
export function parseHash() {
    let hash = window.location.hash.replace(/^#/, '');
    if (!hash || hash === '/') return { segments: [], query: {} };

    const [pathPart, queryPart] = hash.split('?');
    const segments = pathPart.split('/').filter(Boolean);

    const query = {};
    if (queryPart) {
        new URLSearchParams(queryPart).forEach((value, key) => {
            query[key] = value;
        });
    }
    return { segments, query };
}

/** Формирует hash-адрес из пути и query-параметров. */
export function buildHash(path, query) {
    let hash = '#' + (path.startsWith('/') ? path : '/' + path);
    if (query && Object.keys(query).length) {
        const usp = new URLSearchParams();
        for (const [k, v] of Object.entries(query)) {
            if (v !== undefined && v !== null && v !== '') usp.append(k, v);
        }
        const qs = usp.toString();
        if (qs) hash += '?' + qs;
    }
    return hash;
}

/** Переход на указанный адрес. */
export function navigate(path, query) {
    const target = buildHash(path, query);
    if (window.location.hash === target) {
        // Принудительно перезапускаем рендер, если адрес не изменился
        window.dispatchEvent(new HashChangeEvent('hashchange'));
    } else {
        window.location.hash = target;
    }
}

/** Подписка на изменение маршрута. */
export function onRouteChange(handler) {
    window.addEventListener('hashchange', handler);
    window.addEventListener('DOMContentLoaded', handler);
}
