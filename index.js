const canvas = document.getElementById('area-canvas');
const ctx = canvas.getContext('2d');
const form = document.getElementById('point-form');
const yInput = document.getElementById('y-val');
const errorBox = document.getElementById('error-box');
const resultsBody = document.getElementById('results-body');
const btnClear = document.getElementById('btn-clear');
const btnSubmit = document.getElementById('btn-submit');
const rSelect = document.getElementById('r-val');
const connectionErrorPage = document.getElementById('connection-error-page');

const STORAGE_KEY = 'lab2_web';
const SERVER_URL = '/fcgi-bin/server.jar';
let checksHistory = [];

try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
        checksHistory = JSON.parse(stored);
    }
} catch (e) {
    checksHistory = [];
}

function drawCanvas(rVal) {
    const w = canvas.width;
    const h = canvas.height;
    const center = w / 2;
    const scale = 110;

    ctx.clearRect(0, 0, w, h);

    ctx.fillStyle = 'rgba(59, 130, 246, 1)';
    ctx.strokeStyle = '#2563eb';
    ctx.lineWidth = 1.5;

    // 2 четверть: прямоугольник
    ctx.beginPath();
    ctx.rect(center - scale / 2, center - scale, scale / 2, scale);
    ctx.fill();
    ctx.stroke();

    // 3 четверть: четверть круга
    ctx.beginPath();
    ctx.moveTo(center, center);
    ctx.arc(center, center, scale / 2, Math.PI / 2, Math.PI);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();

    // 4 четверть: треугольник
    ctx.beginPath();
    ctx.moveTo(center, center);
    ctx.lineTo(center + scale / 2, center);
    ctx.lineTo(center, center + scale);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();

    ctx.strokeStyle = '#334155';
    ctx.lineWidth = 1.5;
    ctx.fillStyle = '#334155';
    ctx.font = '11px sans-serif';

    ctx.beginPath();
    ctx.moveTo(15, center);
    ctx.lineTo(w - 15, center);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(w - 15, center);
    ctx.lineTo(w - 23, center - 4);
    ctx.lineTo(w - 23, center + 4);
    ctx.closePath();
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(center, h - 15);
    ctx.lineTo(center, 15);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(center, 15);
    ctx.lineTo(center - 4, 23);
    ctx.lineTo(center + 4, 23);
    ctx.closePath();
    ctx.fill();

    const labels = [
        { name: '-R', x: center - scale, y: center, align: 'center', base: 'top', offX: 0, offY: 6 },
        { name: '-R/2', x: center - scale / 2, y: center, align: 'center', base: 'top', offX: 0, offY: 6 },
        { name: 'R/2', x: center + scale / 2, y: center, align: 'center', base: 'top', offX: 0, offY: 6 },
        { name: 'R', x: center + scale, y: center, align: 'center', base: 'top', offX: 0, offY: 6 },
        { name: 'R', x: center, y: center - scale, align: 'left', base: 'middle', offX: 6, offY: 0 },
        { name: 'R/2', x: center, y: center - scale / 2, align: 'left', base: 'middle', offX: 6, offY: 0 },
        { name: '-R/2', x: center, y: center + scale / 2, align: 'left', base: 'middle', offX: 6, offY: 0 },
        { name: '-R', x: center, y: center + scale, align: 'left', base: 'middle', offX: 6, offY: 0 }
    ];

    ctx.strokeStyle = '#64748b';
    ctx.lineWidth = 1;

    labels.forEach(pt => {
        ctx.beginPath();
        if (pt.offX === 0) {
            ctx.moveTo(pt.x, center - 3);
            ctx.lineTo(pt.x, center + 3);
        } else {
            ctx.moveTo(center - 3, pt.y);
            ctx.lineTo(center + 3, pt.y);
        }
        ctx.stroke();

        ctx.textAlign = pt.align;
        ctx.textBaseline = pt.base;
        ctx.fillText(pt.name, pt.x + pt.offX, pt.y + pt.offY);
    });

    ctx.textAlign = 'right';
    ctx.textBaseline = 'bottom';
    ctx.fillText('x', w - 18, center - 6);

    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.fillText('y', center + 8, 16);

    checksHistory.filter(item => item.r === rVal).forEach(item => {
        const px = center + (item.x / rVal) * scale;
        const py = center - (parseFloat(item.y) / rVal) * scale;

        if (px >= 5 && px <= w - 5 && py >= 5 && py <= h - 5) {
            ctx.beginPath();
            ctx.arc(px, py, 4, 0, Math.PI * 2);
            ctx.fillStyle = item.hit ? '#16a34a' : '#dc2626';
            ctx.fill();
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 1;
            ctx.stroke();
        }
    });
}

rSelect.addEventListener('change', () => {
    drawCanvas(Number(rSelect.value));
});

function formatRussianDateTime(isoTimestamp) {
    const dateObj = new Date(isoTimestamp);
    return new Intl.DateTimeFormat('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        timeZoneName: 'short'
    }).format(dateObj);
}

function renderTable() {
    if (checksHistory.length === 0) {
        resultsBody.innerHTML = '<tr id="empty-row"><td colspan="6" class="empty-row">История проверок пуста</td></tr>';
        return;
    }

    resultsBody.innerHTML = '';
    checksHistory.slice().reverse().forEach(item => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${item.x}</td>
            <td>${item.y}</td>
            <td>${item.r}</td>
            <td class="${item.hit ? 'status-hit' : 'status-miss'}">${item.hit ? 'Попадание' : 'Промах'}</td>
            <td>${formatRussianDateTime(item.timestamp)}</td>
            <td>${item.executionTimeMs == null ? '—' : item.executionTimeMs.toFixed(3)}</td>
        `;
        resultsBody.appendChild(row);
    });
}

function showError(msg) {
    errorBox.textContent = msg;
    errorBox.style.display = 'block';
}

function clearError() {
    errorBox.textContent = '';
    errorBox.style.display = 'none';
}

function showConnectionError() {
    connectionErrorPage.hidden = false;
    document.body.style.overflow = 'hidden';
    connectionErrorPage.focus();
}

window.addEventListener('offline', showConnectionError);
window.addEventListener('message', (event) => {
    if (event.source === connectionErrorPage.contentWindow
            && event.origin === window.location.origin
            && event.data === 'close-error-page') {
        connectionErrorPage.hidden = true;
        document.body.style.overflow = '';
        btnSubmit.focus();
    }
});

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (btnSubmit.disabled) return;
    clearError();

    const selectedX = document.querySelector('input[name="x-val"]:checked');
    const xVal = selectedX ? Number(selectedX.value) : NaN;
    if (!Number.isInteger(xVal) || xVal < -3 || xVal > 5) {
        showError('Выберите корректное значение X');
        return;
    }

    const yRaw = yInput.value.trim().replace(',', '.');
    if (yRaw === '') {
        showError('Поле Y не может быть пустым. Введите число строго между −3 и 3');
        return;
    }

    if (!/^-?\d+(\.\d+)?$/.test(yRaw)) {
        showError('В поле Y введены некорректные символы. Разрешены только числа (например: 1.25 или -2,75)');
        return;
    }
    const [integerText] = yRaw.replace('-', '').split('.');
    const integerPart = Number(integerText);

    if (integerPart >= 3) {
        showError('Координата Y должна быть строго между −3 и 3. Вы ввели: ' + yRaw);
        return;
    }

    const rVal = Number(rSelect.value);
    if (![1, 1.5, 2, 2.5, 3].includes(rVal)) {
        showError('Выберите радиус R из списка: 1; 1,5; 2; 2,5; 3');
        return;
    }

    const params = new URLSearchParams({x: xVal, y: yRaw, r: rVal});
    btnSubmit.disabled = true;
    btnClear.disabled = true;
    btnSubmit.textContent = 'Проверка...';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);

    try {
        const response = await fetch(SERVER_URL + '?' + params.toString(), {
            method: 'GET',
            signal: controller.signal
        });
        if (response.status >= 500) {
            showConnectionError();
            return;
        }
        const result = await response.json();

        if (!response.ok) {
            showError(result.error || 'Ошибка сервера');
            return;
        }

        checksHistory.push(result);
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(checksHistory));
        } catch (err) {}

        renderTable();
        drawCanvas(Number(rSelect.value));
    } catch (err) {
        showConnectionError();
    } finally {
        clearTimeout(timeout);
        btnSubmit.disabled = false;
        btnClear.disabled = false;
        btnSubmit.textContent = 'Проверить точку';
    }
});

btnClear.addEventListener('click', () => {
    checksHistory = [];
    try {
        localStorage.removeItem(STORAGE_KEY);
    } catch (err) {}
    renderTable();
    drawCanvas(Number(rSelect.value));
    clearError();
});

drawCanvas(Number(rSelect.value));
renderTable();
