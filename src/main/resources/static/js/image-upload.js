var ANALYSIS_IMAGE_MAX_SIDE = 1024;
var ANALYSIS_IMAGE_WEBP_THRESHOLD_BYTES = 1024 * 1024;
var ANALYSIS_IMAGE_QUALITY = 0.85;

function getCsrfHeaders() {
    if (window.SeuStockCsrf && typeof window.SeuStockCsrf.headers === 'function') {
        return window.SeuStockCsrf.headers();
    }

    var csrfTokenEl = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderEl = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (csrfTokenEl && csrfHeaderEl) {
        headers[csrfHeaderEl.getAttribute('content')] = csrfTokenEl.getAttribute('content');
    }
    return headers;
}

function canEncodeWebP() {
    try {
        var canvas = document.createElement('canvas');
        canvas.width = 1;
        canvas.height = 1;
        return canvas.toDataURL('image/webp').indexOf('data:image/webp') === 0;
    } catch (e) {
        return false;
    }
}

function createResizedImageFile(file, options) {
    return new Promise(function (resolve) {
        if (!file || file.type === 'image/gif') {
            resolve(file);
            return;
        }

        var maxSide = options.maxSide || ANALYSIS_IMAGE_MAX_SIDE;
        var quality = options.quality || ANALYSIS_IMAGE_QUALITY;
        var targetMimeType = options.targetMimeType || file.type || 'image/jpeg';
        var url = URL.createObjectURL(file);
        var img = new Image();
        img.onload = function () {
            URL.revokeObjectURL(url);
            try {
                var width = img.naturalWidth;
                var height = img.naturalHeight;
                var scale = Math.min(1, maxSide / Math.max(width, height));
                var targetWidth = Math.max(1, Math.round(width * scale));
                var targetHeight = Math.max(1, Math.round(height * scale));
                var canvas = document.createElement('canvas');
                canvas.width  = targetWidth;
                canvas.height = targetHeight;
                canvas.getContext('2d').drawImage(img, 0, 0, targetWidth, targetHeight);
                canvas.toBlob(function (blob) {
                    if (!blob) {
                        resolve(file);
                        return;
                    }
                    var extension = targetMimeType === 'image/webp' ? '.webp' : '.jpg';
                    var filename = (file.name || 'image').replace(/\.[^.]*$/, '') + '-analysis' + extension;
                    resolve(new File([blob], filename, { type: targetMimeType }));
                }, targetMimeType, quality);
            } catch (e) {
                resolve(file);
            }
        };
        img.onerror = function () { URL.revokeObjectURL(url); resolve(file); };
        img.src = url;
    });
}

function prepareAnalysisImage(file) {
    if (!file || !file.type || file.type.indexOf('image/') !== 0) {
        return Promise.resolve(file);
    }

    var shouldUseWebP = file.size > ANALYSIS_IMAGE_WEBP_THRESHOLD_BYTES
        && file.type !== 'image/webp'
        && file.type !== 'image/gif'
        && canEncodeWebP();

    var targetMimeType = shouldUseWebP ? 'image/webp' : 'image/jpeg';
    return createResizedImageFile(file, {
        maxSide: ANALYSIS_IMAGE_MAX_SIDE,
        quality: ANALYSIS_IMAGE_QUALITY,
        targetMimeType: targetMimeType
    });
}

/**
 * Initializes image preview, SHA-256 hash computation, and submit button
 * idempotency guard for a file input element.
 *
 * @param {Object} config
 * @param {string} config.fileInputSelector   - CSS selector for <input type="file">
 * @param {string} config.previewSelector     - CSS selector for the <img> preview element
 * @param {string} config.hashInputSelector   - CSS selector for the hidden hash <input>
 * @param {string} config.submitSelector      - CSS selector for the submit button
 * @param {string} [config.labelTextSelector] - CSS selector for label text span (optional)
 * @param {Element} [root]                    - Scope root element (defaults to document)
 */
function initImageUpload(config, root) {
    var scope       = root || document;
    var fileInput   = scope.querySelector(config.fileInputSelector);
    var previewImg  = scope.querySelector(config.previewSelector);
    var hashInput   = scope.querySelector(config.hashInputSelector);
    var submitBtn   = scope.querySelector(config.submitSelector);
    var existingImg = config.existingImageSelector
                       ? scope.querySelector(config.existingImageSelector)
                       : null;
    var labelText   = config.labelTextSelector
                       ? scope.querySelector(config.labelTextSelector)
                       : null;

    if (!fileInput) return;
    fileInput.imageUploadOnImageReady = typeof config.onImageReady === 'function'
        ? config.onImageReady
        : fileInput.imageUploadOnImageReady;

    if (fileInput.dataset.imageUploadInitialized === 'true') return;
    fileInput.dataset.imageUploadInitialized = 'true';

    fileInput.addEventListener('change', async function () {
        var file = this.files[0];
        if (file && file.size > 10 * 1024 * 1024) { // 10MB limit
            var errorMsg = '업로드 가능한 최대 파일 크기는 10MB입니다. (Max file size limit is 10MB)';
            if (window.SeuStockToast) {
                window.SeuStockToast({ type: 'error', message: errorMsg });
            } else {
                alert(errorMsg);
            }
            this.value = '';
            if (existingImg) existingImg.classList.remove('hidden');
            if (previewImg)  previewImg.classList.add('hidden');
            if (hashInput)   hashInput.value = '';
            if (labelText)   labelText.textContent = '사진을 선택하세요';
            return;
        }
        if (existingImg) existingImg.classList.add('hidden');
        if (previewImg)  previewImg.classList.add('hidden');
        if (hashInput)   hashInput.value = '';
        if (labelText)   labelText.textContent = file ? file.name : '사진을 선택하세요';
        if (!file) return;

        if (previewImg) {
            var objUrl = URL.createObjectURL(file);
            previewImg.onload = function () { URL.revokeObjectURL(objUrl); };
            previewImg.src = objUrl;
            previewImg.classList.remove('hidden');
        }

        if (hashInput && window.crypto && crypto.subtle) {
            try {
                var buf    = await file.arrayBuffer();
                var digest = await crypto.subtle.digest('SHA-256', buf);
                hashInput.value = Array.from(new Uint8Array(digest))
                    .map(function (b) { return b.toString(16).padStart(2, '0'); })
                    .join('');
            } catch (err) {
                console.warn('SHA-256 hash computation failed:', err);
            }
        }

        if (typeof fileInput.imageUploadOnImageReady === 'function') {
            fileInput.imageUploadOnImageReady(file);
        }
    });

    if (submitBtn) {
        submitBtn.addEventListener('click', function () {
            setTimeout(function () { submitBtn.disabled = true; }, 0);
        });
        submitBtn.addEventListener('htmx:afterRequest', function (evt) {
            if (evt.detail && !evt.detail.successful) {
                submitBtn.disabled = false;
            }
        });
    }
}

function createImageAnalysisHandler(config, root) {
    var scope = root || document;
    var currentFile = null;
    var retryAttempt = 0;
    var ctrl = null;

    function find(selector) {
        return selector ? scope.querySelector(selector) : null;
    }

    function setBusy(isBusy) {
        var fileInput = find(config.fileInputSelector);
        var submitBtn = find(config.submitSelector);
        var statusSpan = find(config.statusSelector);
        var fileLabel = find(config.fileLabelSelector);
        var retryBtn = find(config.retryButtonSelector);

        if (fileInput) fileInput.disabled = isBusy;
        if (submitBtn) submitBtn.disabled = isBusy;
        if (retryBtn) {
            retryBtn.disabled = isBusy || !currentFile;
            if (isBusy) retryBtn.classList.add('hidden');
        }
        if (fileLabel) fileLabel.classList.toggle('opacity-50', isBusy);

        if (statusSpan) {
            statusSpan.classList.toggle('hidden', !isBusy);
            if (isBusy) statusSpan.textContent = 'AI 분석 중...';
        }
    }

    function showRetryButton() {
        var retryBtn = find(config.retryButtonSelector);
        if (!retryBtn) return;
        retryBtn.classList.remove('hidden');
        retryBtn.disabled = !currentFile;
    }

    async function analyze(file, attempt) {
        if (!file) return;
        currentFile = file;
        if (ctrl) ctrl.abort();
        var requestCtrl = new AbortController();
        ctrl = requestCtrl;

        var nameInput = find(config.nameInputSelector);
        var descInput = find(config.descriptionInputSelector);
        var statusSpan = find(config.statusSelector);

        setBusy(true);
        var dotTimer = statusSpan ? setInterval(function () {
            var dots = (statusSpan.textContent.match(/\./g) || []).length;
            statusSpan.textContent = 'AI 분석 중' + '.'.repeat(dots % 3 + 1);
        }, 1000) : null;

        try {
            var analysisFile = await prepareAnalysisImage(file);
            var fd = new FormData();
            fd.append('imageFile', analysisFile);
            fd.append('retryAttempt', String(attempt));
            if (attempt > 0) {
                fd.append('previousName', nameInput ? nameInput.value : '');
                fd.append('previousDescription', descInput ? descInput.value : '');
            }

            var res = await fetch('/images/analyze', {
                method: 'POST',
                body: fd,
                headers: getCsrfHeaders(),
                signal: requestCtrl.signal
            });
            if (!res.ok) {
                var errorMessage = '이미지 AI 분석에 실패했습니다.';
                try {
                    var errorData = await res.json();
                    if (errorData && errorData.message) errorMessage = errorData.message;
                } catch (_) {}
                throw new Error(errorMessage);
            }

            var data = await res.json();
            if (nameInput && data.name) nameInput.value = data.name;
            if (descInput && data.description) descInput.value = data.description;
            retryAttempt = attempt;
            showRetryButton();
        } catch (e) {
            if (e.name !== 'AbortError') {
                console.warn('Image analysis failed:', e);
                if (window.SeuStockToast) {
                    window.SeuStockToast({ type: 'error', message: e.message || '이미지 AI 분석에 실패했습니다.' });
                }
                showRetryButton();
            }
        } finally {
            if (dotTimer) clearInterval(dotTimer);
            if (ctrl === requestCtrl) {
                setBusy(false);
            }
        }
    }

    var retryBtn = find(config.retryButtonSelector);
    if (retryBtn && retryBtn.dataset.imageAnalysisRetryInitialized !== 'true') {
        retryBtn.dataset.imageAnalysisRetryInitialized = 'true';
        retryBtn.addEventListener('click', function () {
            analyze(currentFile, retryAttempt + 1);
        });
    }

    return function (file) {
        retryAttempt = 0;
        currentFile = file;
        var retryBtn = find(config.retryButtonSelector);
        if (retryBtn) {
            retryBtn.classList.add('hidden');
            retryBtn.disabled = true;
        }
        analyze(file, 0);
    };
}
