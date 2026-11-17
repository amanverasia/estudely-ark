// Theme button: system -> light -> dark -> system, remembered in this browser only.
(function () {
  var btn = document.getElementById('theme');
  if (!btn) return;
  var order = ['system', 'light', 'dark'];
  function current() { return document.documentElement.dataset.theme || 'system'; }
  function show() {
    var c = current(), next = order[(order.indexOf(c) + 1) % order.length];
    btn.innerHTML = '<span aria-hidden="true">◐ </span>theme: ' + c;
    btn.setAttribute('aria-label', 'Colour theme: ' + c + ' (click for ' + next + ')');
  }
  btn.addEventListener('click', function () {
    var next = order[(order.indexOf(current()) + 1) % order.length];
    if (next === 'system') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = next;
    try { if (next === 'system') localStorage.removeItem('theme'); else localStorage.setItem('theme', next); } catch (e) {}
    show();
  });
  show();
})();
