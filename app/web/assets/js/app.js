(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var all = function (selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); };
  var state = { screen: '', round: null, config: {}, learning: {}, busy: false, selected: [], version: 0, draft: null };
  var routeEpoch = 0, polling = false;
  var toastTimer, nextTimer;
  var poems = [
    ['王维', '山居秋暝', '明月松间照，清泉石上流。'],
    ['孟浩然', '春晓', '春眠不觉晓，处处闻啼鸟。'],
    ['李白', '望庐山瀑布', '日照香炉生紫烟，遥看瀑布挂前川。'],
    ['杜甫', '绝句', '两个黄鹂鸣翠柳，一行白鹭上青天。'],
    ['白居易', '钱塘湖春行', '乱花渐欲迷人眼，浅草才能没马蹄。'],
    ['王之涣', '登鹳雀楼', '欲穷千里目，更上一层楼。'],
    ['王安石', '泊船瓜洲', '春风又绿江南岸，明月何时照我还。'],
    ['苏轼', '题西林壁', '横看成岭侧成峰，远近高低各不同。'],
    ['杨万里', '小池', '小荷才露尖尖角，早有蜻蜓立上头。'],
    ['朱熹', '春日', '等闲识得东风面，万紫千红总是春。'],
    ['杜牧', '山行', '停车坐爱枫林晚，霜叶红于二月花。'],
    ['叶绍翁', '游园不值', '春色满园关不住，一枝红杏出墙来。']
  ];
  var nativeCall = window.QuizConnection.invoke;
  var request = window.QuizConnection.json;
  var focusDefault = window.QuizRemote.reset;
  var move = window.QuizRemote.move;
  function toast(message) {
    var banner = $('toast');
    if (!banner) {
      banner = document.createElement('output');
      banner.id = 'toast';
      banner.className = 'toast';
      document.body.appendChild(banner);
    }
    banner.textContent = message;
    banner.classList.add('show');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { banner.classList.remove('show'); }, 3000);
  }

  function show(screen) {
    var oldPanel = $('screen-' + state.screen);
    if (oldPanel) oldPanel.classList.remove('active');
    var panel = $('screen-' + screen);
    panel.classList.add('active');
    state.screen = screen;
    document.body.className = ['on', screen].join('-');
    $('dailyPoem').hidden = true;
  }

  function pill() {
    var usage = state.learning, seconds = usage.dailyRemainingSeconds;
    var time = '--:--';
    if (typeof seconds === 'number') {
      var minutes = Math.floor(seconds / 60);
      var parts = [('0' + (minutes % 60)).slice(-2), ('0' + (seconds % 60)).slice(-2)];
      if (minutes >= 60) parts.unshift(Math.floor(minutes / 60));
      time = parts.join(':');
    }
    $('dailyTime').textContent = time;
    var labels = [];
    var who = usage.viewingTogether ? '一起观看' : usage.childName;
    if (who) labels.push(who);
    if (usage.remainingSeconds > 0) labels.push('可用 ' + Math.ceil(usage.remainingSeconds / 60) + ' 分钟');
    labels.push('还剩 ' + Math.max(0, (state.config.roundsPerDay || 3) - (usage.roundsDoneToday || 0)) + ' 组');
    if (usage.extraPool > 0) labels.push('加练池 ' + usage.extraPool + ' 题');
    $('topPill').textContent = labels.join(' · ');
  }

  function loadState(done) {
    var stamp = routeEpoch;
    request('/api/state', null, function (failure, snapshot) {
      if (stamp !== routeEpoch) { done('stale'); return; }
      if (!failure) {
        state.learning = snapshot.learning;
        state.config = snapshot.config;
        pill();
      }
      done(failure);
    });
  }

  function retryScreen(message) {
    state.round = null;
    show('quiz');
    $('qText').textContent = message;
    var area = $('qOptions');
    area.textContent = '';
    area.appendChild(command('重试', 'retry', 'btn btn-primary'));
    focusDefault();
  }

  function start() {
    if (state.busy) return;
    resetMark();
    var stamp = routeEpoch;
    state.busy = true;
    request('/api/quiz/start', {}, function (failure, response) {
      if (routeEpoch !== stamp) return;
      state.busy = false;
      if (failure) { retryScreen(failure); return; }
      if (response.status) { state.learning = response.status; pill(); }
      if (response.blocked) { lock(response.blocked); return; }
      if (response.watching) {
        state.lockReason = '';
        var roundMinutes = Math.ceil(state.learning.remainingSeconds / 60);
        var dailyMinutes = Math.ceil(state.learning.dailyRemainingSeconds / 60);
        lockCard('已解锁使用时间', '今日总剩余 ' + dailyMinutes + ' 分钟\n本轮剩余 ' + roundMinutes + ' 分钟', '✓', '', '返回桌面', 'home');
      } else {
        state.round = response;
        show('quiz');
        renderQuestion();
      }
    });
  }

  function command(label, action, style) {
    var button = document.createElement('button');
    button.type = 'button';
    button.className = style + ' focusable';
    button.textContent = label;
    if (action) button.setAttribute('data-action', action);
    return button;
  }

  function resetMark() {
    state.mark = null;
    $('markNext').hidden = true;
    $('markNext').classList.remove('focusable');
    $('qOptions').hidden = false;
    $('quizHint').textContent = '方向键选择 · OK 确认';
    var banner = $('answerFeedback');
    banner.textContent = '';
    banner.classList.remove('visible');
    banner.classList.remove('wrong');
  }

  function renderQuestion() {
    var task = state.round, question = task.question;
    if (!state.draft || state.draft.nonce !== task.nonce) {
      state.draft = { nonce: task.nonce, text: '' };
    }
    state.selected = [];
    resetMark();
    $('qText').textContent = question.q;
    var mode = {streak:'累计答对', practice:'自由练习', round:'本组任务'};
    var context = [task.childName ? '请 ' + task.childName + ' 作答' : '', question.level, question.subject];
    if (question.topic) context.push('知识点：' + question.topic);
    context.push(task.extra ? '加练任务' : mode[task.mode] || mode.round);
    context.push('难度 ' + (question.difficulty >= 4 ? '难' : question.difficulty === 3 ? '中' : '易'));
    $('qMode').textContent = context.filter(Boolean).join(' · ');
    var position = task.answered + 1;
    if (task.mode === 'streak') position = task.correct;
    if (task.mode === 'round') position = Math.min(position, task.target);
    $('qIndex').textContent = position;
    $('qTotal').textContent = task.mode === 'practice' ? '题' : '/ ' + task.target;
    $('qPool').textContent = task.extraPool > 0 ? '加练池还剩 ' + task.extraPool + ' 题' : '';
    $('qDots').textContent = task.mode === 'streak' ? '累计答对 ' + task.correct + ' 题' : '';

    var area = $('qOptions');
    area.textContent = '';
    if (question.type === 'text') {
      writtenControls(area);
    } else {
      var options = document.createDocumentFragment();
      (question.a || []).forEach(function (text, index) {
        var button = command('', null, 'option');
        button.setAttribute('data-opt', index);
        appendText(button, 'span', String.fromCharCode(65 + index), 'letter');
        appendText(button, 'span', text, 'opt-text');
        options.appendChild(button);
      });
      if (question.type === 'multiple') options.appendChild(command('确认所选答案', 'submit', 'btn btn-primary answer-submit'));
      area.appendChild(options);
    }
    if (window.SOTV_MATH) {
      window.SOTV_MATH($('qText'));
      window.SOTV_MATH(area);
    }
    focusDefault();
  }

  function writtenControls(area) {
    var field = document.createElement('textarea');
    var draft = state.draft;
    field.id = 'writtenAnswer';
    field.className = 'written-answer';
    field.placeholder = '请输入答案';
    field.setAttribute('aria-label', field.placeholder);
    field.maxLength = 10000;
    field.rows = 4;
    field.value = draft.text;
    field.oninput = function () { draft.text = field.value; };
    area.appendChild(field);
    if (!document.documentElement.classList.contains('tv-device')) {
      var tools = document.createElement('div');
      tools.className = 'input-tools';
      tools.appendChild(command('语音输入', 'voice', 'btn btn-ghost'));
      area.appendChild(tools);
    }
    area.appendChild(command('提交答案', 'submit', 'btn btn-primary answer-submit'));
  }

  function choose(index) {
    var round = state.round;
    if (!round || state.busy) return;
    if (round.question.type !== 'multiple') {
      state.selected = [index];
      submit();
      return;
    }
    var selected = state.selected;
    var previous = selected.indexOf(index);
    if (previous === -1) selected.push(index);
    else selected.splice(previous, 1);
    var control = $('qOptions').querySelector('[data-opt="' + index + '"]');
    if (control) {
      var pressed = previous === -1;
      control.classList.toggle('selected', pressed);
      control.setAttribute('aria-pressed', String(pressed));
    }
  }

  function submit() {
    var task = state.round;
    if (!task || state.busy) return;
    var input = $('writtenAnswer');
    var text = input ? input.value : '';
    var choices = state.selected.slice();
    if (input ? !text.trim() : choices.length === 0) {
      toast('请先填写或选择答案');
      return;
    }
    state.busy = true;
    var submission = {
      childId: task.childId, roundId: task.id, nonce: task.nonce,
      selections: choices, text: text
    };
    request('/api/quiz/answer', submission, function (error, reply) {
      if (state.round !== task || state.screen !== 'quiz') return;
      if (error || reply.blocked) {
        state.busy = false;
        if (error) toast(error);
        else lock(reply.blocked);
        return;
      }
      showMark(reply, choices, task.question);
      if (!reply.correct) {
        state.mark = reply;
        $('markNext').hidden = false;
        $('markNext').classList.add('focusable');
        $('quizHint').textContent = '↑↓ 阅读解析 · OK 继续';
        focusDefault();
        return;
      }
      nextTimer = window.setTimeout(function () {
        if (state.round !== task || state.screen !== 'quiz') return;
        state.busy = false;
        continueAfterMark(reply);
      }, 600);
    });
  }

  function showMark(reply, choices, question) {
    state.learning = reply.status;
    pill();
    var correct = reply.correctIndices || [];
    all('#qOptions [data-opt]').forEach(function (option) {
      var index = Number(option.getAttribute('data-opt'));
      if (correct.indexOf(index) !== -1) {
        option.classList.add('right');
        appendText(option, 'span', '✓ 正确', 'option-result');
      } else if (choices.indexOf(index) !== -1) {
        option.classList.add('wrong');
        appendText(option, 'span', '× 选错', 'option-result');
      }
    });
    var banner = $('answerFeedback');
    banner.textContent = reply.correct ? '✓ 回答正确' : '× 回答错误';
    if (reply.correct && reply.tip) toast(reply.tip);
    if (!reply.correct) {
      var answer = reply.answerText || correct.map(function (index) { return question.a[index]; }).join('、');
      if (answer) appendText(banner, 'div', '正确答案：' + answer);
      appendText(banner, 'div', reply.tip ? '解析：' + reply.tip : '这道题还没有解析。');
      if (window.SOTV_MATH) window.SOTV_MATH(banner);
      var options = $('qOptions');
      options.hidden = !options.querySelector('[data-opt]');
      all('#qOptions .focusable').forEach(function (control) {
        control.classList.remove('focusable', 'focused');
      });
      all('#qOptions [data-action="submit"]').forEach(function (control) { control.hidden = true; });
      $('quizBody').scrollTop = 0;
    }
    banner.classList.add('visible');
    banner.classList.toggle('wrong', !reply.correct);
  }

  function continueAfterMark(reply) {
    if (!reply.finished) {
      state.round = reply.next;
      renderQuestion();
      return;
    }
    state.round = null;
    if (!reply.passed) {
      toast('本组未通过，再试一组');
      start();
      return;
    }
    var availability = reply.status.reason;
    if (availability === 'timeup' && reply.status.viewingTogether) { start(); return; }
    if (availability) {
      lock(availability);
      toast('奖励已保存，达到可用时段后使用');
      return;
    }
    lockCard('任务完成', '已解锁 ' + reply.rewardMinutes + ' 分钟', '✓', '', '返回桌面', 'home');
    window.setTimeout(function () {
      if (state.screen === 'lock' && $('lockTitle').textContent === '任务完成') nativeCall('exitToLauncher');
    }, 1300);
  }
  function lockCard(title, message, icon, note, label, action) {
    show('lock');
    $('lockTitle').textContent = title;
    $('lockDesc').textContent = message;
    $('lockIcon').textContent = icon;
    $('lockNote').textContent = note || '';
    var controls = $('lockActions');
    controls.textContent = '';
    if (action) controls.appendChild(command(label, action, 'btn btn-primary'));
    focusDefault();
  }

  function lock(reason) {
    var titles = {schedule:'还没到可用时间', rounds:'今天的任务已全部完成',
      quota:'今天的使用时间已用完', timeup:'使用时间到了'};
    state.lockReason = reason;
    state.round = null;
    var nextAt = state.learning.nextAvailableAt;
    var message = reason === 'timeup' ? '完成学习任务后才能使用设备' : '现在暂不可用';
    if (nextAt) {
      var date = new Date(nextAt);
      message = '下次可用：' + (date.getMonth() + 1) + ' 月 ' + date.getDate() + ' 日 ' + date.toTimeString().slice(0, 5);
    }
    var config = state.config;
    var note = config.allDay || !config.windowStart || !config.windowEnd ? '' :
      '可用时段 ' + config.windowStart + ' — ' + config.windowEnd;
    lockCard(titles[reason] || titles.timeup, message, reason === 'schedule' || reason === 'quota' ? '◷' : '⌛',
      note, '开始答题', reason === 'timeup' ? 'retry' : null);
    if (reason === 'quota') {
      document.body.classList.add('on-quota');
      showDailyPoem();
    }
  }

  function showDailyPoem() {
    var date = new Date();
    var day = Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);
    if (state.poemDay !== day) {
      var poem = poems[((day % poems.length) + poems.length) % poems.length];
      $('poemVerse').textContent = poem[2].replace('，', '，\n');
      $('poemCredit').textContent = poem[0] + '《' + poem[1] + '》节选';
      state.poemDay = day;
    }
    $('dailyPoem').hidden = false;
  }
  function pair() {
    show('pair');
    var address = managementAddress('lanInfo');
    var qr = $('pairQr');
    $('pairExit').textContent = nativeCall('viewingMode') === 'parent' ? '返回电视桌面' : '返回答题';
    $('pairUrl').textContent = address || '请先将电视连接到局域网';
    if (address) drawCode(qr, address, 8, 32, '家长管理连接二维码');
    else qr.textContent = '暂无连接地址';
    focusDefault();
  }
  function activate(control) {
    control = control || window.QuizRemote.current();
    if (!control || window.QuizRemote.belowFold(control)) return;
    var option = control.getAttribute('data-opt');
    if (option !== null) { choose(Number(option)); return; }
    var action = control.getAttribute('data-action') || '';
    if (action === 'mark-next' && state.mark) {
      var result = state.mark;
      resetMark();
      state.busy = false;
      continueAfterMark(result);
      return;
    }
    if (action.slice(0, 5) === 'pair-' && state.screen === 'pair') {
      nativeCall('parentPanelAction', [action.slice(5)]);
      return;
    }
    switch (action) {
      case 'teen':
        var child = Number(control.getAttribute('data-child-id'));
        if (nativeCall('chooseTeen', [child])) {
          if (location.hash !== '#/quiz') location.hash = '#/quiz';
          else route();
        } else if (nativeCall('viewingMode') === 'choose') renderMode();
        break;
      case 'parent': nativeCall('chooseParent'); break;
      case 'voice': nativeCall('answerInput', [true]); break;      case 'submit': submit(); break;
      case 'retry': start(); break;
      case 'home': nativeCall('exitToLauncher'); break;
      case 'quiz':
        if (state.screen === 'pair') nativeCall('backToQuiz');
        else start();
        break;
    }
  }

  function renderMode(raw) {
    var profileJson = raw || nativeCall('childProfiles') || '[]';
    var children = decode(profileJson, []);
    if (!Array.isArray(children)) children = [];
    state.modeProfiles = profileJson;
    var board = $('modeCards'), parent = $('parentModeCard');
    var previous = board.querySelectorAll('[data-child-id]');
    for (var index = previous.length - 1; index >= 0; index--) board.removeChild(previous[index]);
    board.classList.toggle('three', children.length === 2);
    var entries = document.createDocumentFragment();
    children.forEach(function (child, position) {
      var entry = document.createElement('button');
      entry.type = 'button';
      entry.className = 'mode-card focusable';
      entry.setAttribute('data-action', 'teen');
      entry.setAttribute('data-child-id', child.id);
      appendText(entry, 'span', '档案 0' + (position + 1) + ' / 青少年', 'mode-number');
      appendText(entry, 'strong', child.name);
      appendText(entry, 'span', child.grade);
      appendText(entry, 'i', '开始观看 →');
      entries.appendChild(entry);
    });
    board.insertBefore(entries, parent);
    show('mode');
    $('topPill').textContent = '请选择观看档案';
    refreshBootPair();
    focusDefault();
  }

  function decode(value, fallback) {
    try { return value ? JSON.parse(value) : fallback; } catch (error) { return fallback; }
  }

  function appendText(parent, tag, value, css) {
    var node = document.createElement(tag);
    if (css) node.className = css;
    node.textContent = value;
    parent.appendChild(node);
  }

  function managementAddress(bridgeMethod) {
    var address = decode(nativeCall(bridgeMethod), null);
    if (!address || !address.ip || !address.port) return '';
    return ['http://', address.ip, ':', address.port, '/admin/'].join('');
  }

  function drawCode(container, address, size, margin, label) {
    var code = window.qrcode(0, 'M');
    code.addData(address);
    code.make();
    container.innerHTML = code.createImgTag(size, margin, label);
  }

  function refreshBootPair() {
    var address = managementAddress('pairingAddress');
    if (state.bootPairUrl === address) return;
    state.bootPairUrl = address;
    $('bootPairHint').textContent = address ? '同一网络 · 扫码后输入密码' : '电视尚未连接局域网';
    if (address) drawCode($('bootQr'), address, 5, 20, '家长管理登录页二维码');
    else $('bootQr').textContent = '';
  }
  function route() {
    var epoch = ++routeEpoch;
    window.clearTimeout(nextTimer);
    resetMark();
    state.busy = false;
    var path = location.hash.slice(2).split('?')[0];
    if (path === 'pair') { pair(); return; }
    if (nativeCall('viewingMode') === 'choose') { renderMode(); return; }
    loadState(function (error) {
      if (epoch !== routeEpoch) return;
      if (error) {
        show('quiz');
        $('qText').textContent = error;
        $('qOptions').textContent = '';
        $('qOptions').appendChild(command('重试', 'retry', 'btn btn-primary'));
        focusDefault();
        return;
      }
      var reason = state.learning.reason;
      if (reason === 'quota' || path === 'lock') lock(reason || 'timeup');
      else start();
    });
  }

  function refreshScreen() {
    $('clock').textContent = new Date().toTimeString().slice(0, 5);
    if (state.busy || state.screen === 'pair' || polling) return;
    if (nativeCall('viewingMode') === 'choose') {
      var currentProfiles = nativeCall('childProfiles') || '[]';
      if (state.screen === 'mode' && currentProfiles === state.modeProfiles) refreshBootPair();
      else renderMode(currentProfiles);
      return;
    }
    if (state.screen === 'mode') return;
    var version = state.config.version, viewers = state.learning.viewerSelection;
    polling = true;
    loadState(function (error) {
      polling = false;
      if (error || state.busy || state.screen === 'pair' || state.screen === 'mode') return;
      var reason = state.learning.reason;
      if (reason === 'quota') {
        if (state.screen !== 'lock' || state.lockReason !== 'quota') lock(reason);
        else showDailyPoem();
        return;
      }
      if (viewers && viewers !== state.learning.viewerSelection) { start(); return; }
      if (state.screen === 'lock' && state.lockReason !== reason) {
        if (reason) lock(reason); else start();
      } else if (state.screen === 'quiz') {
        if ((reason === 'rounds' || reason === 'schedule') && state.config.playMode !== 'practice') lock(reason);
        else if (version !== state.config.version) start();
      }
    });
  }

  function shortcut() {
    var count = 0, last = 0;
    return function (reset) {
      if (reset) { count = 0; return false; }
      var now = Date.now();
      count = now - last < 900 ? count + 1 : 1;
      last = now;
      if (count !== 5) return false;
      count = 0;
      nativeCall('openParent');
      return true;
    };
  }

  function boot() {
    var brandShortcut = shortcut(), remoteShortcut = shortcut();
    document.querySelector('.brand').onclick = function () { brandShortcut(false); };
    document.addEventListener('click', function (event) {
      for (var target = event.target; target && target !== document.body; target = target.parentNode) {
        if (!target.classList || !target.classList.contains('focusable')) continue;
        activate(target);
        break;
      }
    });
    var arrows = {37:'left', 38:'up', 39:'right', 40:'down'};
    document.addEventListener('keydown', function (event) {
      var key = event.keyCode;
      if (!event.repeat && remoteShortcut(key !== 38)) {
        event.preventDefault();
        return;
      }
      if (/^(INPUT|TEXTAREA)$/.test(event.target.tagName)) return;
      if (arrows[key]) move(arrows[key]);
      else if (key === 13) activate();
      else return;
      event.preventDefault();
    });
    var update = null, banner = $('updateTip');
    $('updateGo').onclick = function () {
      if (update) nativeCall('installUpdate', [update.url, update.latest, update.notes || '']);
    };
    $('updateClose').onclick = function () { banner.classList.remove('show'); };
    window.SOTV_UPDATE = function (release) {
      if (!release || !release.latest) return;
      update = release;
      $('updateText').textContent = '发现新版本 v' + release.latest;
      banner.classList.add('show');
    };
    var platform = decode(nativeCall('deviceInfo'), {}).platform;
    document.documentElement.classList.toggle('tv-device', platform === 'android-tv');
    show('quiz');
    route();
    window.setInterval(refreshScreen, 2000);
  }

  window.SOTV_INPUT = function (result) {
    if (!result || !state.round) return;
    state.draft = { nonce: state.round.nonce, text: String(result.text || '') };
    renderQuestion();
  };
  window.TV = { move: move, select: function () { activate(); }, back: function () {
    if (state.screen === 'pair') nativeCall('backToQuiz');
    else if (!state.learning.reason && state.learning.remainingSeconds > 0) nativeCall('exitToLauncher');
    else toast('现在暂不可用');
  }, state: state };
  window.SOTV_RESUME = function () { if (document.readyState === 'complete' && !state.busy && state.screen !== 'pair') route(); };
  window.addEventListener('hashchange', route);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();

