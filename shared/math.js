(function (document, window) {
  "use strict";
  var pending = [];
  var loading = false;
  var vendor = "assets/vendor/katex/";
  var options = {
    delimiters: [
      {left: "$$", right: "$$", display: true},
      {left: "\\[", right: "\\]", display: true},
      {left: "\\(", right: "\\)", display: false},
      {left: "$", right: "$", display: false}
    ],
    ignoredTags: ["script", "noscript", "style", "textarea", "pre", "code", "option", "input"],
    throwOnError: false, trust: false, strict: "ignore", maxExpand: 200
  };
  function render(node) {
    if (document.documentElement.contains(node)) window.renderMathInElement(node, options);
  }
  function load(files) {
    if (!files.length) {
      loading = false;
      if (window.renderMathInElement) pending.splice(0).forEach(render);
      return;
    }
    var entry = document.createElement("script");
    entry.src = vendor + files.shift();
    entry.onload = function () { load(files); };
    entry.onerror = function () { loading = false; pending.length = 0; };
    document.head.appendChild(entry);
  }
  window.SOTV_MATH = function (node) {
    if (!node || !/\$|\\[\[(]/.test(node.textContent || "")) return;
    if (window.renderMathInElement) return render(node);
    if (pending.indexOf(node) === -1) pending.push(node);
    if (loading) return;
    loading = true;
    if (!document.getElementById("tvquiz-formula-style")) {
      var style = document.createElement("link");
      style.id = "tvquiz-formula-style";
      style.rel = "stylesheet";
      style.href = vendor + "katex.min.css";
      document.head.appendChild(style);
    }
    load(window.katex ? ["contrib/auto-render.min.js"] : ["katex.min.js", "contrib/auto-render.min.js"]);
  };
})(document, window);
