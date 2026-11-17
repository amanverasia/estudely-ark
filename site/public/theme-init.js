// Applies the saved theme before first paint. A plain script file, because the CSP forbids inline scripts.
try { var t = localStorage.getItem('theme'); if (t === 'light' || t === 'dark') document.documentElement.dataset.theme = t } catch (e) {}
