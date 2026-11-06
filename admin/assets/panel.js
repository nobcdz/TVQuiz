(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const grades = ['幼儿园', ...Array.from('一二三四五六七八九', n => n + '年级')];
  const subjects = ['语文','数学','英语','常识'];
  $('tvAddress').value = location.host;
  const configFields = [
    ['curriculumGrade','出题年级',grades],
    ['dailyMinutes','每日观看上限（分钟）',1,480], ['sessionMinutes','每轮观看时间（分钟）',1,120],
    ['questionsPerRound','每轮题数',1,100], ['roundsPerDay','每日轮次上限',1,100],
    ['playMode','任务模式',[['streak','连续答对'],['round','按组答题'],['practice','自由练习']]],
    ['passPercent','通过所需正确率（%）',1,100,.01], ['allDay','全天可用','checkbox'],
    ['windowStart','开始时间','time'], ['windowEnd','结束时间','time'],
    ['extraEnabled','启用错题加练','checkbox'], ['wrongThreshold','触发加练的错题数',1,100],
    ['extraQuestions','每次增加题数',1,100], ['spacedRepetitionEnabled','启用间隔复习','checkbox']
  ];
  const questionFields = [
    ['id','题目 ID（新增时留空）','text'], ['subject','学科',subjects],
    ['level','年级',grades], ['difficulty','难度',1,5],
    ['type','题型',[['single','单选'],['multiple','多选'],['boolean','判断'],['text','填空'],['subjective','主观']]],
    ['q','题干','textarea'], ['a','选项，每行一项','textarea'],
    ['k','正确选项序号（从 1 开始）',1,8], ['correctIndices','多选序号，用逗号分隔','text'],
    ['answers','填空可接受答案，每行一项','textarea'], ['modelAnswer','参考答案','textarea'],
    ['rubric','评分标准','textarea'], ['tip','解析','textarea']
  ];
  const gradingFields = [
    ['mode','批改方式',[['manual','家长批改'],['ai','AI 批改']]],
    ['passScore','及格分',1,100,.1], ['endpoint','HTTPS 接口地址','url'],
    ['model','模型名称','text'], ['apiKey','API Key（留空保留已保存密钥）','password'], ['vision','支持图片批改','checkbox']
  ];
  const names = {settings: 'settings', questions: 'questions', reviews: 'reviews', records: 'records'};
  let token = sessionStorage.getItem('tvquiz-parent') || '';
  let child = 1, tab = 'settings', edited = null, busy = false, generation = 0;

  function make(tag, value) {
    const node = document.createElement(tag);
    if (value !== undefined) node.textContent = value;
    return node;
  }
  function field(spec, value) {
    const [name, label, kind, max, step] = spec;
    const row = make('label', label);
    const input = make(Array.isArray(kind) ? 'select' : kind === 'textarea' ? 'textarea' : 'input');
    input.name = name;
    if (Array.isArray(kind)) {
      for (const option of kind) {
        const [key, text] = Array.isArray(option) ? option : [option, option];
        const node = make('option', text);
        node.value = key;
        input.append(node);
      }
      if (max === 'number') input.dataset.numeric = '1';
    } else if (typeof kind === 'number') {
      input.type = 'number'; input.min = kind; input.max = max; input.step = step || 1;
    } else if (kind !== 'textarea') input.type = kind;
    if (kind === 'checkbox') input.checked = Boolean(value);
    else if (value !== undefined && value !== null) input.value = value;
    row.append(input);
    return row;
  }
  function fields(id, schema, values = {}) {
    $(id).replaceChildren(...schema.map(spec => field(spec, values[spec[0]])));
  }
  function values(form) {
    const result = {};
    for (const input of form.elements) {
      if (!input.name || input.disabled) continue;
      if (input.type === 'checkbox') result[input.name] = input.checked;
      else if (input.type === 'number' || input.dataset.numeric) {
        if (input.value !== '') result[input.name] = Number(input.value);
      } else result[input.name] = input.value;
    }
    return result;
  }
  function notice(message, error = false) {
    const banner = $('notice');
    banner.textContent = message;
    banner.hidden = !message;
    banner.className = error ? 'error' : '';
  }
  async function api(path, method = 'GET', payload, profile = child) {
    const headers = {'X-Child-Profile': String(profile)};
    const session = token;
    if (session) headers.Authorization = 'Bearer ' + session;
    if (payload !== undefined) headers['Content-Type'] = 'application/json';
    const reply = await fetch('/api/' + path, {
      method, headers, body: payload === undefined ? undefined : JSON.stringify(payload), cache: 'no-store'
    });
    const data = await reply.json();
    if (reply.status === 401 && path !== 'auth') {
      if (session === token) showLogin();
      throw Error('登录已失效，请重新输入密码。');
    }
    if (!reply.ok || data.error) throw Error(data.message || (path === 'auth' ? data.error : '操作失败，请重试。'));
    return data;
  }
  function showLogin() {
    token = ''; sessionStorage.removeItem('tvquiz-parent'); generation++;
    $('login').hidden = false; $('panel').hidden = true;
  }
  function showPanel() { $('login').hidden = true; $('panel').hidden = false; }
  function submit(id, operation) {
    $(id).addEventListener('submit', async event => {
      event.preventDefault();
      if (busy) return;
      busy = true;
      const buttons = Array.from($(id).querySelectorAll('button'));
      buttons.forEach(button => { button.disabled = true; });
      try { notice(''); await operation($(id)); }
      catch (error) { notice(error.message, true); }
      finally { busy = false; buttons.forEach(button => { button.disabled = false; }); }
    });
  }
  async function refreshFamily() {
    const data = await api('household');
    const children = data.children || [];
    const current = children.some(item => Number(item.id) === child);
    if (!current && children.length) child = Number(children[0].id);
    $('profile').replaceChildren(...children.map(item => {
      const entry = make('option', item.name); entry.value = item.id; return entry;
    }));
    $('profile').value = child;
    const schema = [['childCount','启用档案数',[[1,'一个孩子'],[2,'两个孩子']],'number']];
    const defaults = { childCount: data.childCount };
    for (const item of children) {
      schema.push(['name' + item.id, '孩子 ' + item.id + ' 的名字','text']);
      defaults['name' + item.id] = item.name;
    }
    fields('familyFields', schema, defaults);
  }
  async function refreshConfig() {
    const snapshot = ++generation, profile = child;
    $('config').hidden = true;
    const data = await api('state', 'GET', undefined, profile);
    if (snapshot !== generation || profile !== child) return;
    const rules = data.config;
    rules.passPercent = Number((rules.passRatio * 100).toFixed(2));
    fields('configFields', configFields, rules);
    const inputs = $('config').elements;
    const updateSchedule = () => {
      inputs.windowStart.disabled = inputs.allDay.checked;
      inputs.windowEnd.disabled = inputs.allDay.checked;
    };
    inputs.allDay.onchange = updateSchedule;
    updateSchedule();
    $('config').hidden = false;
  }
  function action(label, callback) {
    const button = make('button', label);
    button.type = 'button';
    button.onclick = async () => {
      if (button.disabled) return;
      button.disabled = true;
      try { notice(''); await callback(); } catch (error) { notice(error.message, true); }
      finally { button.disabled = false; }
    };
    return button;
  }
  function questionForm(item = {}) {
    edited = item.id ? item : null;
    fields('questionFields', questionFields, {
      subject:'语文', level:'七年级', term:1, unit:1, difficulty:2, type:'single',
      ...item, a:(item.a || []).join('\n'), k:(item.k || 0) + 1,
      answers:(item.answers || []).join('\n'), correctIndices:(item.correctIndices || []).map(i => i + 1).join(',')
    });
  }
  async function refreshQuestions() {
    const filter = values($('filter'));
    const query = new URLSearchParams({limit:'500'});
    if (filter.subject) query.set('subject', filter.subject);
    if (filter.level) query.set('level', filter.level);
    const result = await api('questions?' + query);
    if (JSON.stringify(filter) !== JSON.stringify(values($('filter')))) return;
    $('questionList').replaceChildren(...result.items.map(item => {
      const card = make('article');
      card.className = 'card';
      card.append(make('strong', [item.subject,item.level || '未标注年级', '难度 ' + item.difficulty].join(' · ')),
        make('p', item.q), action('编辑', () => { questionForm(item); $('question').scrollIntoView(); }),
        action('删除', async () => {
          if (!confirm('删除这道题？')) return;
          await api('questions/' + encodeURIComponent(item.id), 'DELETE');
          await refreshQuestions(); notice('题目已删除。');
        }));
      return card;
    }));
    if (window.SOTV_MATH) window.SOTV_MATH($('questionList'));
    if (!result.items.length) $('questionList').append(make('p', '当前筛选下暂无题目。'));
  }
  async function refreshReviews() {
    const profile = child, snapshot = generation;
    $('grading').hidden = true;
    $('reviewList').replaceChildren();
    const [settings, reviews] = await Promise.all([api('grading'), api('reviews?state=all')]);
    if (snapshot !== generation || profile !== child) return;
    fields('gradingFields', gradingFields, settings);
    $('grading').hidden = false;
    $('reviewList').replaceChildren(...reviews.items.map(item => {
      const card = make('article');
      card.className = 'card';
      card.append(make('h2', item.state === 'graded' ? '已批改 · ' + item.score + ' 分' : '待批改'),
        make('p', item.question.q), make('p', item.input.text || ''), make('p', item.feedback || ''));
      if (item.question.modelAnswer) card.append(make('p', '参考答案：' + item.question.modelAnswer));
      if (item.question.rubric) card.append(make('p', '评分标准：' + item.question.rubric));
      const drawing = item.input.drawing || '';
      if (drawing.startsWith('data:image/png;base64,')) {
        const image = make('img'); image.src = drawing; image.alt = '孩子的手写答案'; card.append(image);
      }
      if (item.state !== 'graded') {
        const grade = field(['score','评分',0,100,.1]);
        const feedback = field(['feedback','批改反馈','textarea']);
        card.append(grade, feedback, action('保存评分', async () => {
          const score = grade.querySelector('input');
          if (!score.value || !score.reportValidity()) throw Error('请填写 0–100 的评分。');
          await api('reviews/' + encodeURIComponent(item.id), 'POST',
            {score:Number(score.value),feedback:feedback.querySelector('textarea').value}, profile);
          if (profile === child) await refreshReviews();
        }), action('重试 AI 批改', async () => {
          await api('reviews/' + encodeURIComponent(item.id) + '/retry', 'POST', {}, profile);
          notice('已重新提交批改。');
        }));
      }
      return card;
    }));
    if (!reviews.items.length) $('reviewList').append(make('p','暂无批改记录。'));
    if (window.SOTV_MATH) window.SOTV_MATH($('reviewList'));
  }
  async function refreshRecords() {
    const profile = child, snapshot = generation;
    const [history, events] = await Promise.all([api('history?days=7'), api('events?limit=50')]);
    if (snapshot !== generation || profile !== child) return;
    const limit = Math.max(1, ...history.items.map(row => row.usedMinutes));
    $('history').replaceChildren(...history.items.map(row => {
      const block = make('div');
      block.className = 'record';
      const meter = make('meter');
      meter.max = limit; meter.value = row.usedMinutes;
      block.append(make('div', row.label + ' · 观看 ' + row.usedMinutes.toFixed(1) + ' 分钟 · 答对 ' + row.correct + '/' + row.answered), meter);
      return block;
    }));
    const types = {answer:'作答', quiz:'完成答题', quiz_pass:'获得时长', extra_added:'增加加练', review_submitted:'提交批改', review_graded:'完成批改', question_added:'添加题目', study:'学习活动'};
    $('events').replaceChildren(...events.items.map(event => {
      const row = make('div', new Date(event.ts).toLocaleString() + ' · ' + (types[event.type] || event.type));
      row.className = 'record'; return row;
    }));
  }
  async function refresh() {
    if (tab === 'settings') await refreshConfig();
    if (tab === 'questions') await refreshQuestions();
    if (tab === 'reviews') await refreshReviews();
    if (tab === 'records') await refreshRecords();
  }

  submit('login', async form => {
    const formData = values(form);
    const reply = await api('auth','POST',{passphrase:formData.passphrase});
    token = reply.token;
    sessionStorage.setItem('tvquiz-parent', token);
    form.reset(); await refreshFamily(); await refresh(); showPanel();
  });
  submit('family', async form => {
    const settings = values(form);
    const names = {};
    for (let id = 1; id <= settings.childCount; id++) {
      if (settings['name' + id] !== undefined) names[id] = settings['name' + id];
    }
    await api('household','PUT',{childCount:settings.childCount,names});
    await refreshFamily(); await refreshConfig(); notice('孩子档案已保存。');
  });
  submit('config', async form => {
    const rules = values(form);
    if (rules.passPercent !== undefined) rules.passRatio = rules.passPercent / 100;
    delete rules.passPercent;
    await api('config','PUT',rules); await refreshConfig(); notice('观看规则已保存。');
  });
  submit('filter', refreshQuestions);
  submit('question', async form => {
    const question = {...edited, ...values(form)};
    for (const key of ['a','answers']) question[key] = question[key].split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    question.k = (question.k || 1) - 1;
    question.correctIndices = question.correctIndices.split(/[,，\s]+/).filter(Boolean).map(value => Number(value) - 1);
    await api('questions','POST',question); questionForm(); await refreshQuestions(); notice('题目已保存。');
  });
  submit('import', async form => {
    const file = form.elements.file.files[0];
    if (file && file.size > 4_000_000) throw Error('JSON 文件需小于 4 MB。');
    const items = JSON.parse(file ? await file.text() : form.elements.json.value);
    if (!Array.isArray(items)) throw Error('请输入题目 JSON 数组。');
    const reply = await api('questions','POST',{items});
    form.reset(); await refreshQuestions(); notice('导入 ' + reply.added + ' 道，跳过 ' + reply.skipped + ' 道。');
  });
  submit('grading', async form => {
    await api('grading','PUT',values(form)); await refreshReviews(); notice('批改设置已保存。');
  });

  $('question').addEventListener('reset', event => { event.preventDefault(); questionForm(); });
  $('exportBank').onclick = async () => {
    try {
      const filter = values($('filter'));
      const query = new URLSearchParams({limit:'5000'});
      if (filter.subject) query.set('subject', filter.subject);
      if (filter.level) query.set('level', filter.level);
      const result = await api('questions?' + query);
      const url = URL.createObjectURL(new Blob([JSON.stringify(result.items, null, 2)], {type:'application/json'}));
      const link = make('a');
      link.href = url; link.download = 'tvquiz-questions.json';
      document.body.append(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      notice('已导出 ' + result.items.length + ' 道题。');
    } catch (error) { notice(error.message, true); }
  };
  $('useGrade').onclick = async () => {
    try {
      const grade = values($('filter')).level;
      if (!grade) throw Error('请先选择年级。');
      await api('config','PUT',{curriculumGrade:grade});
      notice('已设为 ' + grade + '。');
    } catch (error) { notice(error.message, true); }
  };
  $('tabs').onclick = event => {
    const selected = event.target.dataset.tab;
    if (!names[selected] || busy) return;
    tab = selected; generation++;
    for (const name of Object.keys(names)) $(name).hidden = name !== tab;
    for (const button of $('tabs').querySelectorAll('button')) button.setAttribute('aria-pressed', String(button.dataset.tab === tab));
    refresh().catch(error => notice(error.message, true));
  };
  $('profile').onchange = () => {
    if (busy) { $('profile').value = child; return; }
    child = Number($('profile').value); generation++;
    refresh().catch(error => notice(error.message, true));
  };
  $('logout').onclick = async () => {
    if (busy) return;
    busy = true;
    try { await api('logout','POST'); showLogin(); notice('已退出登录。'); }
    catch (error) { notice(error.message, true); }
    finally { busy = false; }
  };
  fields('filters', [['subject','学科',[['','全部学科'], ...subjects]], ['level','年级',[['','全部年级'], ...grades]]]);
  questionForm();
  api('hello').then(info => { $('device').textContent = (info.name || info.app) + ' · ' + info.questions + ' 道题'; }).catch(() => {});
  if (token) refreshFamily().then(refresh).then(showPanel).catch(error => notice(error.message, true));
})();
