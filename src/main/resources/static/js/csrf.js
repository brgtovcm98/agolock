(function () {
    function readCookie(name) {
        var prefix = name + '=';
        var cookies = document.cookie ? document.cookie.split(';') : [];
        for (var i = 0; i < cookies.length; i++) {
            var cookie = cookies[i].trim();
            if (cookie.indexOf(prefix) === 0) {
                return decodeURIComponent(cookie.substring(prefix.length));
            }
        }
        return null;
    }

    function readMeta(name) {
        var element = document.querySelector('meta[name="' + name + '"]');
        return element ? element.getAttribute('content') : null;
    }

    function headers() {
        var token = readCookie('XSRF-TOKEN');
        var header = token ? 'X-XSRF-TOKEN' : readMeta('_csrf_header');
        if (!token) {
            token = readMeta('_csrf');
        }

        var result = {};
        if (token && header) {
            result[header] = token;
        }
        return result;
    }

    window.SeuStockCsrf = {
        headers: headers
    };

    document.addEventListener('htmx:configRequest', function (event) {
        var csrfHeaders = headers();
        Object.keys(csrfHeaders).forEach(function (header) {
            event.detail.headers[header] = csrfHeaders[header];
        });
    });
})();
