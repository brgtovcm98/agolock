(function () {
    function revealActiveSortTabs() {
        document.querySelectorAll('.sort-row').forEach(function (row) {
            var active = row.querySelector('.sort-tab-active');
            if (!active) return;
            var rowRect = row.getBoundingClientRect();
            var tabRect = active.getBoundingClientRect();
            if (tabRect.right > rowRect.right) {
                row.scrollLeft += tabRect.right - rowRect.right;   // overflow on the right
            } else if (tabRect.left < rowRect.left) {
                row.scrollLeft -= rowRect.left - tabRect.left;     // overflow on the left
            }
        });
    }

    document.addEventListener('DOMContentLoaded', revealActiveSortTabs);
    document.addEventListener('htmx:afterSwap', revealActiveSortTabs);
})();
