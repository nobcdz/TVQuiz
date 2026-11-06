(function () {
  'use strict';
  function invoke(action, values) {
    var api = window.SOTVNative;
    if (!api || typeof api[action] !== 'function') return null;
    try { return api[action].apply(api, values || []); } catch (error) { return null; }
  }
  function json(route, payload, receive) {
    var wire = new XMLHttpRequest();
    var completed = false;
    function finish(error, data) {
      if (completed) return;
      completed = true;
      receive(error, data);
    }
    wire.open(payload === null ? 'GET' : 'POST', route, true);
    wire.timeout = 8000;
    wire.setRequestHeader('Content-Type', 'application/json');
    wire.setRequestHeader('X-Device-Token', invoke('apiToken') || '');
    wire.onreadystatechange = function () {
      if (wire.readyState !== 4 || wire.status === 0) return;
      var packet;
      try { packet = JSON.parse(wire.responseText); } catch (error) {
        finish('电视返回的数据无法读取');
        return;
      }
      if (wire.status < 200 || wire.status >= 300 || packet.error) {
        finish(packet.message || '请求失败，请重试');
      } else finish(null, packet);
    };
    wire.onerror = function () { finish('连接设备失败'); };
    wire.ontimeout = function () { finish('连接超时，请重试'); };
    wire.send(payload === null ? null : JSON.stringify(payload));
  }
  window.QuizConnection = { invoke: invoke, json: json };
})();
