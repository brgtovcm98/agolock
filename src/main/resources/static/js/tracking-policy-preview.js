(function () {
    var MAX_SERIAL_PADDING_LENGTH = 16;

    function parseInteger(value, fallback) {
        var parsed = Number.parseInt(value, 10);
        return Number.isFinite(parsed) ? parsed : fallback;
    }

    function pad(value, paddingLength) {
        var raw = String(value);
        if (paddingLength <= raw.length) {
            return raw;
        }
        return '0'.repeat(paddingLength - raw.length) + raw;
    }

    function formatDate(date, pattern) {
        var yyyy = String(date.getFullYear());
        var mm = String(date.getMonth() + 1).padStart(2, '0');
        var dd = String(date.getDate()).padStart(2, '0');
        return (pattern || 'yyyyMMdd')
            .replace(/yyyy/g, yyyy)
            .replace(/MM/g, mm)
            .replace(/dd/g, dd);
    }

    function asBoolean(value) {
        return value === true || value === 'true' || value === 'on';
    }

    function formatIsoDate(date) {
        var yyyy = String(date.getFullYear());
        var mm = String(date.getMonth() + 1).padStart(2, '0');
        var dd = String(date.getDate()).padStart(2, '0');
        return yyyy + '-' + mm + '-' + dd;
    }

    function updateSerialPreview(root) {
        var mode = root.querySelector('[name="serialMode"]');
        var prefix = root.querySelector('[name="serialPrefix"]');
        var padding = root.querySelector('[name="serialPaddingLength"]');
        var next = root.querySelector('[name="serialNextSequence"]');
        var increment = root.querySelector('[name="serialIncrementUnit"]');
        var preview = root.querySelector('[data-serial-preview]');
        if (!mode || !preview) return;

        var isAuto = mode.value === 'AUTO';
        preview.classList.toggle('hidden', !isAuto);
        if (!isAuto) return;

        var current = parseInteger(next && next.value, 0);
        var step = Math.max(parseInteger(increment && increment.value, 1), 1);
        var paddingLength = Math.min(Math.max(parseInteger(padding && padding.value, 0), 0), MAX_SERIAL_PADDING_LENGTH);
        var effectivePrefix = prefix && prefix.value ? prefix.value : '';
        var examples = [];

        for (var i = 0; i < 3; i++) {
            current += step;
            examples.push(effectivePrefix + pad(current, paddingLength));
        }

        preview.textContent = (preview.dataset.label || '') + ' ' + examples.join(', ');
    }

    function updateLotPreview(root) {
        var mode = root.querySelector('[name="lotMode"]');
        var vendor = root.querySelector('[name="lotVendorCode"]');
        var dateFormat = root.querySelector('[name="lotDateFormat"]');
        var includeSequence = root.querySelector('[name="lotIncludeSequence"]');
        var nextSequence = root.querySelector('[name="lotNextSequence"]');
        var sequenceKey = root.querySelector('[data-lot-sequence-key]');
        var preview = root.querySelector('[data-lot-preview]');
        if (!mode || !preview) return;

        var isAuto = mode.value === 'AUTO';
        preview.classList.toggle('hidden', !isAuto);
        if (!isAuto) return;

        var todayKey = formatDate(new Date(), dateFormat && dateFormat.value);
        var sequence = 1;
        if (sequenceKey && sequenceKey.value === todayKey) {
            sequence = parseInteger(nextSequence && nextSequence.value, 0) + 1;
        }

        var lotNumber = (vendor && vendor.value ? vendor.value.trim() : '') + todayKey;
        if (asBoolean(includeSequence && includeSequence.value)) {
            lotNumber += '-' + pad(sequence, 3);
        }

        preview.textContent = (preview.dataset.label || '') + ' ' + lotNumber;
    }

    function updateExpirationPreview(root) {
        var expirationInput = root.querySelector('[name="expirationPeriodDays"]');
        var expirationPreview = root.querySelector('[data-expiration-preview]');
        if (!expirationPreview) return;

        expirationPreview.classList.add('hidden');

        var expirationDays = parseInteger(expirationInput && expirationInput.value, 0);
        if (expirationDays >= 1) {
            var expirationDate = new Date();
            expirationDate.setDate(expirationDate.getDate() + expirationDays);
            expirationPreview.textContent = (expirationPreview.dataset.label || '') + ' ' + formatIsoDate(expirationDate);
            expirationPreview.classList.remove('hidden');
        }
    }

    window.initTrackingPolicyPreview = function (root) {
        if (!root) return;

        var serialInputs = [
            '[name="serialMode"]',
            '[name="serialPrefix"]',
            '[name="serialPaddingLength"]',
            '[name="serialNextSequence"]',
            '[name="serialIncrementUnit"]'
        ];
        var lotInputs = [
            '[name="lotMode"]',
            '[name="lotVendorCode"]',
            '[name="lotDateFormat"]',
            '[name="lotIncludeSequence"]',
            '[name="lotNextSequence"]'
        ];
        var expirationInput = root.querySelector('[name="expirationPeriodDays"]');

        serialInputs.forEach(function (selector) {
            var input = root.querySelector(selector);
            if (input) input.addEventListener('input', function () { updateSerialPreview(root); });
            if (input) input.addEventListener('change', function () { updateSerialPreview(root); });
        });
        lotInputs.forEach(function (selector) {
            var input = root.querySelector(selector);
            if (input) input.addEventListener('input', function () { updateLotPreview(root); });
            if (input) input.addEventListener('change', function () { updateLotPreview(root); });
        });
        if (expirationInput) {
            expirationInput.addEventListener('input', function () { updateExpirationPreview(root); });
            expirationInput.addEventListener('change', function () { updateExpirationPreview(root); });
        }

        updateSerialPreview(root);
        updateLotPreview(root);
        updateExpirationPreview(root);
    };
})();
