(function () {
  'use strict';
  var keys = { left: [-1, 0], right: [1, 0], up: [0, -1], down: [0, 1] };
  function panel() { return document.querySelector('.screen.active'); }
  function controls() {
    var active = panel();
    return active ? Array.prototype.slice.call(active.querySelectorAll('.focusable')) : [];
  }
  function questionArea() {
    var active = panel();
    return active && active.id === 'screen-quiz' ? document.getElementById('quizBody') : null;
  }
  function current() {
    var active = panel();
    return active && active.querySelector('.focused');
  }
  function select(control, reset) {
    var previous = current();
    if (previous) previous.classList.remove('focused');
    if (!control) return;
    control.classList.add('focused');
    var area = questionArea();
    if (!area) return;
    if (reset) { area.scrollTop = 0; return; }
    var frame = area.getBoundingClientRect(), box = control.getBoundingClientRect();
    if (box.top < frame.top) area.scrollTop += box.top - frame.top - 8;
    else if (box.bottom > frame.bottom) area.scrollTop += box.bottom - frame.bottom + 8;
  }
  function belowFold(control) {
    var area = questionArea();
    if (!area || control.getBoundingClientRect().top < area.getBoundingClientRect().bottom) return false;
    area.scrollTop += Math.round(area.clientHeight * .75);
    return true;
  }
  function center(control) {
    var box = control.getBoundingClientRect();
    return [(box.left + box.right) / 2, (box.top + box.bottom) / 2];
  }
  function move(direction) {
    var axis = keys[direction], active = current();
    if (!axis) return;
    var candidates = controls();
    if (!active) { select(candidates[0], false); return; }
    if (direction === 'down' && belowFold(active)) return;
    var origin = center(active), nearest, best = Infinity;
    candidates.forEach(function (candidate) {
      if (candidate === active) return;
      var point = center(candidate), x = point[0] - origin[0], y = point[1] - origin[1];
      var along = x * axis[0] + y * axis[1];
      if (along <= 8) return;
      var distance = along + 1.8 * Math.abs(x * axis[1] - y * axis[0]);
      if (distance < best) { nearest = candidate; best = distance; }
    });
    if (nearest) select(nearest, false);
    else if (direction === 'up' && questionArea()) questionArea().scrollTop = 0;
  }
  window.QuizRemote = {
    move: move,
    current: current,
    belowFold: belowFold,
    reset: function () {
      if (document.documentElement.classList.contains('tv-device')) select(controls()[0], true);
    }
  };
})();
