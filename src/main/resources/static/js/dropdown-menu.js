(function () {
    var dropdownSelector = 'details[data-dropdown-menu]';

    function closeDropdown(dropdown) {
        if (!dropdown || !dropdown.open) return;
        dropdown.open = false;
        syncSummary(dropdown);
    }

    function closeAllExcept(activeDropdown) {
        document.querySelectorAll(dropdownSelector + '[open]').forEach(function (dropdown) {
            if (dropdown !== activeDropdown) {
                closeDropdown(dropdown);
            }
        });
    }

    function syncSummary(dropdown) {
        var summary = dropdown ? dropdown.querySelector('summary') : null;
        if (summary) {
            summary.setAttribute('aria-expanded', dropdown.open ? 'true' : 'false');
        }
    }

    function syncAllSummaries() {
        document.querySelectorAll(dropdownSelector).forEach(syncSummary);
    }

    document.addEventListener('click', function (event) {
        var summary = event.target.closest(dropdownSelector + ' > summary');
        if (summary) {
            closeAllExcept(summary.parentElement);
            return;
        }

        var dropdown = event.target.closest(dropdownSelector);
        if (!dropdown) {
            closeAllExcept(null);
            return;
        }

        if (!event.target.closest('summary') && event.target.closest('a, button, [role="menuitem"]')) {
            closeDropdown(dropdown);
        }
    }, true);

    document.addEventListener('toggle', function (event) {
        if (!event.target.matches || !event.target.matches(dropdownSelector)) return;
        syncSummary(event.target);
        if (event.target.open) {
            closeAllExcept(event.target);
        }
    }, true);

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeAllExcept(null);
        }
    });

    document.addEventListener('htmx:beforeRequest', function () {
        closeAllExcept(null);
    });

    document.addEventListener('htmx:beforeSwap', function () {
        closeAllExcept(null);
    });

    document.addEventListener('htmx:afterSwap', syncAllSummaries);
    document.addEventListener('DOMContentLoaded', syncAllSummaries);
})();
