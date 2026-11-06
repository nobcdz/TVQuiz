const grades = '一二三四五六七八九';

const math = [
    n => {
        const a = n % 9 + 1, b = Math.floor(n / 9) + 1;
        return ['20以内加减法', `盒里有 ${a} 支红笔和 ${b} 支蓝笔。把红笔和蓝笔合在一起，共有几支？`,
            a + b, '总数等于红笔和蓝笔各自数量的和。'];
    },
    n => {
        const a = n % 9 + 1, b = Math.floor(n / 9) + 1;
        return ['表内乘法', `每个书签要穿 ${a} 颗珠子。做 ${b} 个同样的书签，需要多少颗珠子？`,
            a * b, '同样的数量重复多次相加，可以用乘法。'];
    },
    n => ['路程计算', `从学校到公园的第一段路长 ${n + 23} 米，第二段长 ${n + 36} 米，两段共多少米？`,
        2 * n + 59, '两段路的长度用加法合并。'],
    n => ['乘法分配律', `算出 ${n + 12} × 99 的值。可以先算乘 100，再减去原来的数。`,
        (n + 12) * 99, '99 等于 100 减 1，用分配律把乘法拆成两部分。'],
    n => ['简易方程', `方程 4x + 9 = ${4 * n + 29} 中，x 等于多少？`,
        n + 5, '先在等号两边减去 9，再同时除以 4。'],
    n => ['分数乘法应用', `图书馆把 ${(n + 4) * 8} 本书中的八分之三借出，借出了多少本？`,
        (n + 4) * 3, '把总数平均分成 8 份，借出的数量是其中 3 份。'],
    n => ['有理数', `早晨的气温是 −${n + 3} ℃，中午回升 6 ℃，中午的气温是多少？`,
        3 - n, '在原来的负温度上加 6。'],
    n => ['完全平方公式', `展开 (2x − ${n + 4})²，x 项的系数是多少？`,
        -4 * (n + 4), '中间项为 2 × 2x × (−常数)。'],
    n => ['一元二次方程', `如果 (x − ${n + 2})(x + 5) = 0，两个根相加是多少？`,
        n - 3, '分别令两个因式等于零，得到正根与 −5，再求和。']
];

const language = [
    '伞 sǎn|鸟 niǎo|虫 chóng|火 huǒ|云 yún|鱼 yú|雨 yǔ|花 huā',
    '厚 薄|黑 白|苦 甜|满 空|硬 软|深 浅|远 近|早 晚',
    '不约而同 没有事先商量却做法一致|异想天开 想法非常离奇|大吃一惊 感到非常惊讶|目瞪口呆 吃惊得说不出话|无边无际 广阔得望不到边|欢天喜地 非常高兴|不慌不忙 镇定而不急躁|层层叠叠 一层又一层',
    '悠然 从容闲适的样子|斑驳 色彩杂乱或痕迹深浅不一|寂寥 空旷而冷清|踌躇 拿不定主意|眷恋 对人或地方舍不得离开|振奋 精神受到鼓舞|澄澈 清亮透明|急促 短暂而又快',
    '环顾 向四周看|远眺 向远处看|审视 仔细观察和打量|注视 目光集中在某处|稀罕 觉得少见而珍贵|罕见 很少见到|珍贵 价值高而值得珍视|贵重 价值高或分量重',
    '白描 用简练笔墨勾勒特征|铺陈 把事物的不同方面展开叙写|衬托 借旁的事物突出主要对象|反衬 用相反特点突出主要对象|设问 先发问再作答|反问 用问句强调确定的意思|排比 并列三个或更多结构相近的分句|对偶 用一对结构相称的短语或句子表达',
    '自省 反思自己的言行|释然 疑虑消解后心情平静|怅然 因失意而不愉快|悯恤 同情并体恤困苦者|慎独 独处时也谨慎自律|俭朴 节俭朴素|允诺 答应对方的请求|诘问 追问或责问',
    '对举 把相关的两项并排提出|照应 前后内容相互呼应|省略 有意略去可由上下文补足的部分|设悬 留下疑问引起读者期待|象征 用具体形象寄托抽象意义|过渡 连接前后两个内容层次|插叙 主线中插入有关的往事或说明|倒叙 从后来发生的情形说起再回述先前经过',
    '实证 用可核对的材料说明判断|反例 用来否定一般命题的具体例子|类比 从部分相似推知其他可能相似之处|反证 从否定结论出发推导矛盾|假设 暂且认定某种情况以便讨论|限定 缩小词语适用的范围|递进 表意逐层加深|让步 先承认一种情况再提出另一层意思'
];

module.exports = function replaceDuplicateStems(questions, add) {
    const key = question => [question.level, question.subject, question.q.trim().replace(/\s+/g, ' ')].join('|');
    const occupied = new Set(questions.map(key));
    const seen = new Set(), counters = new Map();
    const length = questions.length;
    for (let index = 0; index < length; index++) {
        const original = questions[index];
        const stemKey = key(original);
        if (!seen.has(stemKey)) {
            seen.add(stemKey);
            continue;
        }
        const grade = grades.indexOf(original.level[0]) + 1;
        const group = original.level + '|' + original.subject;
        const number = counters.get(group) || 0;
        counters.set(group, number + 1);
        if (original.subject === '数学') {
            const [topic, stem, answer, tip] = math[grade - 1](number);
            add(grade, original.term, '数学', topic, stem, answer, [answer + 1, answer + 2, answer - 1], tip);
        } else if (original.subject === '语文') {
            const pairs = language[grade - 1].split('|').map(pair => pair.split(' '));
            const [word, meaning] = pairs[number % pairs.length];
            const other = [1, 3, 5].map(offset => pairs[(number + offset) % pairs.length]);
            const topic = grade === 1 ? '拼音' : grade === 2 ? '反义词' : '词义与表达';
            const forward = number < pairs.length;
            const stem = grade === 1 ? (forward ? `给「${word}」选一个正确读音。` : `哪个字读作 ${meaning}？`)
                : grade === 2 ? (forward ? `选择与「${word}」意思相反的词。` : `「${meaning}」和哪个词意思相反？`)
                : (forward ? `词语「${word}」与哪项解释相符？` : `哪项可以表达「${meaning}」？`);
            const tip = grade === 1 ? `「${word}」读作 ${meaning}。` : grade === 2
                ? `「${word}」与「${meaning}」意思相反。` : `「${word}」表示${meaning}。`;
            add(grade, original.term, '语文', topic, stem, forward ? meaning : word,
                other.map(pair => pair[forward ? 1 : 0]), tip);
        } else {
            throw new Error('No replacement for duplicate: ' + stemKey);
        }
        const replacement = questions.pop();
        if (occupied.has(key(replacement))) throw new Error('Replacement stem repeats: ' + replacement.q);
        occupied.add(key(replacement));
        replacement.retiredQuestion = {
            id: original.id, q: original.q, tip: original.tip, a: original.a, k: original.k,
            term: original.term, previousTerm: original.previousTerm || 0
        };
        questions[index] = replacement;
    }
};
