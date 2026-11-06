const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const grades = ['一', '二', '三', '四', '五', '六', '七', '八', '九'];
const questions = [];
function add(grade, term, subject, topic, stem, correct, distractors, tip, difficulty = grade <= 3 ? 1 : 2) {
    const answers = [...new Set([String(correct), ...distractors.map(String)])].slice(0, 4);
    if (answers.length !== 4) throw new Error('Four distinct options required: ' + stem);
    const id = 'rjb-' + crypto.createHash('sha256').update([grade, term, subject, stem].join('/')).digest('hex').slice(0, 20);
    // A stable rotation keeps the correct answer distributed across all positions.
    const position = questions.length % 4;
    const options = answers.slice(4 - position).concat(answers.slice(0, 4 - position));
    questions.push({id, subject, level: grade === 0 ? '幼儿园' : grades[grade - 1] + '年级', term, unit: 1,
        topic, type: 'single', difficulty, q: stem,
        a: options, k: options.indexOf(String(correct)), tip, source: '自编人教版知识点练习'});
}
function number(grade, term, topic, stem, correct, tip, difficulty) {
    add(grade, term, '数学', topic, stem, correct, [correct + 1, correct - 1, correct + 2], tip, difficulty);
}
for (let grade = 1; grade <= 9; grade++) {
    for (let term = 1; term <= 2; term++) {
        for (let i = 1; i <= 40; i++) {
            const a = i + term, b = i % 8 + 2;
            if (grade === 1) {
                const left = i % 11 + 1, right = i % 7 + 1;
                number(grade, term, '20以内加减法', left + ' + ' + right + ' = ？', left + right, '把两部分合起来，计算它们的和。');
            } else if (grade === 2) {
                const left = i % 9 + 1;
                number(grade, term, '表内乘法', left + ' × ' + b + ' = ？', left * b, '乘法表示几个相同加数的和。');
            } else if (grade === 3) {
                number(grade, term, '乘除法', (a * b) + ' ÷ ' + b + ' = ？', a, '用商乘除数，可以验算是否等于被除数。');
            } else if (grade === 4) {
                number(grade, term, '运算律', a + ' × 25 × 4 = ？', a * 100, '先算 25 × 4 = 100，再乘另一个因数。');
            } else if (grade === 5) {
                const answer = a * b;
                number(grade, term, '简易方程', '方程 x ÷ ' + b + ' = ' + a + ' 的解是？', answer, '等式两边同时乘 ' + b + '。');
            } else if (grade === 6) {
                number(grade, term, '百分数', '某班有 ' + (a * 10) + ' 人，其中 20% 参加合唱队，参加合唱队的有多少人？', a * 2, '求一个数的百分之几，用这个数乘百分率。');
            } else if (grade === 7) {
                number(grade, term, '一元一次方程', '解方程 ' + b + 'x − ' + a + ' = ' + (a * b - a) + '，x = ？', a, '先两边加 ' + a + '，再除以 ' + b + '。');
            } else if (grade === 8) {
                number(grade, term, '平方差公式', '(' + a + ' + ' + b + ')(' + a + ' − ' + b + ') = ？', a * a - b * b, '利用平方差公式 (a+b)(a−b)=a²−b²。');
            } else {
                const r = i % 12 + 1;
                number(grade, term, '二次函数', '抛物线 y = (x − ' + a + ')² + ' + r + ' 的最小值是多少？', r, '平方项不小于 0，x = ' + a + ' 时取得最小值。');
            }
        }
    }
}
const vocabulary = [
    'book=书,pen=钢笔,apple=苹果,cat=猫,dog=狗,sun=太阳,red=红色,blue=蓝色',
    'eye=眼睛,ear=耳朵,hand=手,nose=鼻子,water=水,milk=牛奶,bird=鸟,fish=鱼',
    'school=学校,teacher=教师,student=学生,friend=朋友,family=家庭,father=父亲,mother=母亲,brother=兄弟',
    'library=图书馆,breakfast=早餐,weather=天气,cloudy=多云的,coat=外套,gloves=手套,horse=马,potato=土豆',
    'polite=有礼貌的,clever=聪明的,helpful=乐于助人的,village=村庄,forest=森林,bridge=桥,healthy=健康的,delicious=美味的',
    'museum=博物馆,scientist=科学家,pilot=飞行员,cinema=电影院,subway=地铁,crossing=十字路口,yesterday=昨天,tomorrow=明天',
    'subject=学科,geography=地理,history=历史,important=重要的,practice=练习,usually=通常,different=不同的,between=在两者之间',
    'environment=环境,experience=经历,traditional=传统的,necessary=必要的,competition=比赛,although=虽然,perhaps=也许,improve=改善',
    'knowledge=知识,pronunciation=发音,conversation=交谈,attention=注意,patient=有耐心的,achievement=成就,discovery=发现,responsible=负责任的'
];
vocabulary.forEach((line, index) => {
    const words = line.split(',').map(value => value.split('='));
    for (let term = 1; term <= 2; term++) {
        words.forEach(([word, meaning], i) => {
            const others = words.filter(item => item[0] !== word);
            if (term === 1) {
                add(index + 1, term, '英语', index < 2 ? '英语启蒙词汇' : '基础词汇',
                    '单词 ' + word + ' 的中文意思是？', meaning, others.slice(0, 3).map(item => item[1]), word + ' 表示「' + meaning + '」。');
            } else {
                add(index + 1, term, '英语', index < 2 ? '英语启蒙词汇' : '基础词汇',
                    '「' + meaning + '」对应的英语单词是？', word, others.slice(0, 3).map(item => item[0]), '「' + meaning + '」用英语说是 ' + word + '。');
            }
        });
    }
});
const chinese = [
    ['拼音','「山」的拼音|shān|sān|shǎn|sàn','「水」的拼音|shuǐ|suǐ|shuí|shuì','「日」的拼音|rì|lì|yì|rí','「月」的拼音|yuè|yue|yuě|yù','「火」的拼音|huǒ|huò|hǔo|húo','「木」的拼音|mù|mú|mǔ|mū','「人」的拼音|rén|rěn|rèn|rēn','「田」的拼音|tián|tiǎn|tiān|tiàn'],
    ['反义词','「高」的反义词|矮|长|大|远','「快」的反义词|慢|急|跑|早','「近」的反义词|远|旁|低|短','「明」的反义词|暗|亮|日|净','「热」的反义词|冷|暖|温|火','「新」的反义词|旧|小|鲜|多','「轻」的反义词|重|薄|小|少','「深」的反义词|浅|近|清|远'],
    ['成语','形容非常专心的成语|全神贯注|三心二意|东张西望|心不在焉','比喻多做了多余的事的成语|画蛇添足|雪中送炭|锦上添花|助人为乐','比喻眼光狭小的成语|坐井观天|高瞻远瞩|博古通今|见多识广','形容团结合作的成语|齐心协力|各自为政|孤立无援|一意孤行','形容走路很快的成语|健步如飞|慢条斯理|寸步难行|步履蹒跚','形容事先充分考虑的成语|深思熟虑|轻举妄动|不假思索|心血来潮','形容东西完好无损的成语|完好无缺|支离破碎|残缺不全|四分五裂','比喻说话行动与实际情况不符的成语|自相矛盾|言行一致|实事求是|表里如一'],
    ['词义','「鸦雀无声」形容的场景|非常安静|鸟鸣不断|热闹非凡|人声鼎沸','「人山人海」形容什么|人非常多|山非常高|海非常深|路非常长','「豁然开朗」中的「豁然」意为|一下子开阔|缓慢变暗|逐渐狭窄|一直模糊','「精益求精」表达的意思|好了还求更好|满足已有成绩|做事粗心大意|只求速度','「目不转睛」表现了什么|看得专注|看得模糊|十分疲惫|心情焦躁','「从容不迫」形容的态度|镇定沉着|慌乱急躁|犹豫害怕|盲目冲动','「恍然大悟」表现了什么|一下子明白|始终不解|忘记事情|心生疑惑','「持之以恒」强调什么|坚持不懈|随时放弃|偶尔尝试|轻易改变'],
    ['古诗作者','《静夜思》的作者|李白|杜甫|白居易|王维','《春晓》的作者|孟浩然|李白|杜甫|王安石','《咏柳》的作者|贺知章|苏轼|陆游|杜牧','《游子吟》的作者|孟郊|李白|王维|陶渊明','《山行》的作者|杜牧|杜甫|李白|白居易','《题西林壁》的作者|苏轼|王安石|陆游|杜甫','《泊船瓜洲》的作者|王安石|苏轼|杜牧|李白','《示儿》的作者|陆游|苏轼|杜甫|王维'],
    ['修辞','「花儿对我笑」采用的修辞|拟人|比喻|反问|排比','「小河像一条银色的带子」采用的修辞|比喻|拟人|反问|设问','「这道理难道不清楚吗」采用的修辞|反问|设问|比喻|拟人','「是谁创造了历史？是人民」采用的修辞|设问|反问|比喻|拟人','「飞流直下三千尺」主要采用的修辞|夸张|设问|反问|对偶','「红的像火，粉的像霞，白的像雪」整体采用的修辞|排比|设问|反问|对偶','「危楼高百尺，手可摘星辰」主要采用的修辞|夸张|设问|反问|借代','「春天像刚落地的娃娃」采用的修辞|比喻|反问|设问|对偶'],
    ['文言词义','「学而时习之」中的「时」意为|按时|时间|当时|时常的时刻','「人不知而不愠」中的「愠」意为|生气恼怒|高兴|知道|悲伤','「吾日三省吾身」中的「日」意为|每天|太阳|白天|日子','「温故而知新」中的「故」意为|学过的知识|事故|故意|所以','「三人行，必有我师焉」中的「师」意为|老师|军队|同学|朋友','「博学而笃志」中的「笃」意为|坚定|怀疑|忘记|扩大','「饭疏食，饮水」中的「水」意为|冷水|热水|酒水|米汤','「不义而富且贵」中的「义」意为|道义|意思|意义|朋友情谊'],
    ['文言词义','「阡陌交通」中的「交通」意为|交错相通|交通运输|通讯联系|相互交谈','「芳草鲜美」中的「鲜美」意为|新鲜美好|味道鲜美|色彩单一|十分罕见','「率妻子邑人」中的「妻子」意为|妻子儿女|成年女性|妻子的儿子|全村人','「诣太守」中的「诣」意为|拜访|命令|离开|追赶','「寻病终」中的「寻」意为|不久|寻找|探求|长度单位','「虽乘奔御风」中的「奔」意为|飞奔的马|奔跑的人|急流|追赶','「素湍绿潭」中的「素」意为|白色|平常|朴素|原来','「属引凄异」中的「属」意为|连接|属于|种类|亲属'],
    ['文言词义','「越明年」中的「越」意为|到|超过|越过|越国','「政通人和」中的「和」意为|和乐|连同|唱和|调配','「百废具兴」中的「具」通什么字|俱|惧|巨|据','「朝晖夕阴」中的「晖」意为|日光|月光|星光|灯光','「先天下之忧而忧」中的「先」意为|在……之前|先前|祖先|最早','「一鼓作气」中的「鼓」意为|击鼓|鼓励|凸起|鼓的形状','「小大之狱」中的「狱」意为|诉讼事件|监牢|罪犯|判决书','「牺牲玉帛」中的「牺牲」意为|祭祀用的牲畜|为正义献身|舍弃利益|受到损失']
];
chinese.forEach(([topic, ...items], index) => {
    items.forEach((item, i) => {
        const [stem, correct, ...wrong] = item.split('|');
        for (let term = 1; term <= 2; term++) {
            // Different term pools retain shared prerequisite knowledge intentionally.
            add(index + 1, term, '语文', topic, stem + (stem.endsWith('意为') ? '？' : '是？'), correct, wrong, '本题考查' + topic + '。正确答案是「' + correct + '」。');
        }
    });
});
// Additional original practice uses the same IDs for an unchanged stem across releases.
const moreEnglish = [
    'tree=树,moon=月亮,star=星星,ball=球,desk=书桌,bag=包,egg=鸡蛋,green=绿色',
    'foot=脚,face=脸,hair=头发,leg=腿,rice=米饭,juice=果汁,duck=鸭子,cake=蛋糕',
    'sister=姐妹,grandpa=爷爷,grandma=奶奶,classroom=教室,chair=椅子,window=窗户,door=门,pencil=铅笔',
    'rainy=下雨的,windy=有风的,jacket=夹克衫,sweater=毛衣,carrot=胡萝卜,tomato=西红柿,lunch=午餐,dinner=晚餐',
    'quiet=安静的,strict=严格的,mountain=山,river=河流,weekend=周末,sandwich=三明治,fresh=新鲜的,sweet=甜的',
    'hospital=医院,restaurant=餐馆,worker=工人,fisherman=渔民,traffic=交通,helmet=头盔,dictionary=词典,postcard=明信片',
    'minute=分钟,exercise=锻炼,neighbor=邻居,market=市场,across=横过,during=在……期间,together=一起,remember=记得',
    'decision=决定,situation=情况,pressure=压力,communicate=交流,prepare=准备,explain=解释,provide=提供,protect=保护',
    'compare=比较,connect=连接,review=复习,pronounce=发音,express=表达,avoid=避免,manage=设法完成,support=支持'
];
const moreChinese = [
    ['拼音','口|kǒu','耳|ěr','目|mù','手|shǒu','风|fēng','云|yún','雨|yǔ','天|tiān'],
    ['反义词','进|退','开|关','远|近','多|少','难|易','直|弯','笑|哭','升|降'],
    ['量词','一__小船|艘','一__大象|头','一__铅笔|支','一__扇子|把','一__树叶|片','一__信|封','一__小诗|首','一__墙|面'],
    ['成语含义','形容做事有条理|井井有条','形容一点也不马虎|一丝不苟','形容十分着急|心急如焚','形容遭遇困难时互相帮助|同舟共济','比喻遇事只凭经验，等待侥幸|守株待兔','比喻已经掌握技巧，做起来很顺手|得心应手','比喻在坏事发生后及时补救|亡羊补牢','形容说话或写文章简洁明了|言简意赅'],
    ['说明方法','列出数据说明图书馆规模|列数字','把地球比作一颗蓝色水晶球|打比方','拿两座塔的高度进行对照|作比较','举图书馆的实例说明借阅规则|举例子','按用途分别介绍三类桥梁|分类别','用简明准确的语言界定什么是蒸发|下定义','用示意图说明地铁线路走向|列图表','说明某项技术时直接引用专家的话|引用'],
    ['修辞辨析','一排路灯像夜色里的珍珠|比喻','小溪唱着歌跑向远方|拟人','这一声巨响仿佛震塌了半边天|夸张','难道我们可以把垃圾扔进河里吗|反问','谁把屋子打扫干净了？是值日生|设问','书可以开阔眼界，可以增长知识，可以启发思考|排比','海内存知己，天涯若比邻|对偶','读万卷书，行万里路|对偶'],
    ['词语运用','写完作业后，他再次检查每一道题，做事很__|仔细','虽然两人的看法不同，但讨论一直很__|平和','太阳下山后，天色渐渐__下来|暗','她把杂乱的资料逐一__，便于查找|整理','两条小路在山脚下__在一起|交汇','为免打扰同学，他__地关上门|轻轻','这段描述生动地__了雨后的街道|描绘','这次失败促使他__了原来的计划|调整'],
    ['论证方法','用几个真实事例支持观点|举例论证','引用公认的道理来说明观点|道理论证','将做法及后果的两种情形放在一起论述|对比论证','用船的航向比喻人生的目标来阐述观点|比喻论证','列举学校节水的具体案例来论证|举例论证','把耐心比作种子发芽所需的时间|比喻论证','引用有关守信的格言支持观点|道理论证','对照勤学和懒散造成的不同结果|对比论证'],
    ['句间关系','因为天降大雨，所以比赛推迟|因果','虽然道路崎岖，但是队员们没有退缩|转折','只要反复练习，就能更加熟练|条件','如果明天放晴，我们就去远足|假设','他不但完成了实验，而且记录了数据|递进','这本书既有文字，也有插图|并列','与其临时抱佛脚，不如平时用功|选择','先把资料分类，然后逐项核对|承接']
];
moreEnglish.forEach((line, index) => {
    const words = line.split(',').map(value => value.split('='));
    words.forEach(([word, meaning], i) => {
        const others = [1, 3, 5].map(offset => words[(i + offset) % words.length]);
        add(index + 1, 1, '英语', '词汇辨义', '选择 ' + word + ' 的中文释义。', meaning, others.map(item => item[1]), word + ' 表示「' + meaning + '」。');
        add(index + 1, 2, '英语', '词汇辨义', '选择与「' + meaning + '」相符的单词。', word, others.map(item => item[0]), '「' + meaning + '」对应 ' + word + '。');
    });
});
moreChinese.forEach(([topic, ...entries], index) => {
    const items = entries.map(entry => entry.split('|'));
    items.forEach(([stem, correct], i) => {
        const wrong = [...new Set(items.map(item => item[1]).filter(value => value !== correct))];
        for (let term = 1; term <= 2; term++) {
            const start = (i + term) % wrong.length;
            add(index + 1, term, '语文', topic, topic + ' · ' + stem + '（请选择）', correct,
                wrong.slice(start).concat(wrong.slice(0, start)), '本题考查' + topic + '，正确答案是「' + correct + '」。');
        }
    });
});
for (let grade = 1; grade <= 9; grade++) {
    for (let term = 1; term <= 2; term++) {
        for (let i = 2; i <= 9; i++) {
            const n = i + term, m = i + 2, cm = term === 1 ? '厘米' : '分米';
            const cases = [
                [['减法', (n+7)+' − 7 = ？', n], ['应用题','有 '+n+' 支铅笔，又买了 3 支，一共有多少支？',n+3], ['数的顺序',n+' 后面紧挨着的数是？',n+1], ['加法','小华有 '+n+' 张贴纸，小明有 2 张，两人共有几张？',n+2]],
                [['表内乘法','每盒有 '+i+' 支笔，'+term+' 盒共有多少支？',i*term], ['加法',(n*10)+' + 25 = ？',n*10+25], ['减法',(n*10+23)+' − 14 = ？',n*10+9], ['长度比较','两根彩带分别长 '+(n+7)+' 和 7 '+cm+'，相差多少'+cm+'？',n]],
                [['长方形周长','长 '+n+'、宽 3 '+cm+' 的长方形，周长是多少'+cm+'？',2*(n+3)], ['乘法',n+' × 12 = ？',n*12], ['时间换算',i+' 小时 '+term+' 分钟共多少分钟？',i*60+term], ['长度换算',n+' 米等于多少厘米？',n*100]],
                [['乘法分配律',n+' × (100 + 2) = ？',n*102], ['平均数',n+'、'+(n+2)+' 和 '+(n+4)+' 的平均数是？',n+2], ['小数运算',(n+0.5)+' + 1.5 = ？',n+2], ['长方形面积','长 '+n+'、宽 4 '+cm+'，长方形面积是多少平方'+cm+'？',4*n]],
                [['因数与倍数',n+' 的第 3 个正倍数是？',3*n], ['长方体体积','长 '+n+'、宽 3、高 4 '+cm+'，长方体体积是多少立方'+cm+'？',12*n], ['方程','若 3x + 2 = '+(3*n+2)+'，则 x 等于多少？',n], ['分数','一盒 '+(n*4)+' 块饼干，吃掉四分之一，还剩多少块？',3*n]],
                [['比','按 2:3 分配 '+(n*5)+' 张卡片，较少的一份有多少张？',2*n], ['百分数',n*4+' 元的商品打七五折，现价多少元？',n*3], ['圆周长','若圆的周长是 '+(2*n)+'π 厘米，半径是多少厘米？',n], ['比例尺','比例尺 1:100 的图上，'+n+' 厘米表示实际多少米？',n]],
                [['有理数','(−'+n+') + '+(n+4+term)+' = ？',4+term], ['一元一次方程','解方程 2(x + '+term+') = '+(2*(n+term))+'。x 等于多少？',n], ['平面直角坐标系','点 ('+n+', '+m+') 向左移动 '+term+' 格，横坐标是？',n-term], ['二元一次方程组','若 x+y='+ (n+m) +' 且 y='+m+'，则 x 等于多少？',n]],
                [['勾股定理','直角三角形的两条直角边长为 '+3*n+' 和 '+4*n+'，斜边长是多少？',5*n], ['整式运算','当 x='+n+' 时，(x+'+term+')² − x² 的值是？',2*n*term+term*term], ['一次函数','y=2x+'+term+'，当 x='+n+' 时，y 等于多少？',2*n+term], ['二次根式','√'+(n*n)+' = ？',n]],
                [['二次函数','y=(x−'+n+')²+'+term+' 的对称轴为 x=？',n], ['相似三角形','两相似三角形的相似比为 1:'+n+'，若较小图形的面积是 '+term+'，较大的面积是？',term*n*n], ['一元二次方程','x²−'+(n*n)+'=0 的正根为多少？',n], ['反比例函数','y='+n*m+'/x，当 x='+m+' 时，y 等于多少？',n]]
            ];
            cases[grade - 1].forEach(([topic, stem, answer]) => {
                if (grade === 7 && topic === '有理数') stem = '数轴上从 −'+n+' 向右移动 '+(n+4+term)+' 格，到达哪个数？';
                number(grade, term, topic, stem, answer, '本题考查' + topic + '，计算结果为 ' + answer + '。');
            });
        }
    }
}
const mathExtensions = [
    (n, t) => [
        ['看算式补数', n+' + □ = '+(n+t+4)+'，□ 里应填多少？', t+4, '用总数减去已知的加数。'],
        ['两步加减','有 '+(n+4)+' 个橘子，吃了 '+t+' 个，又拿来 3 个，现在有多少个？', n+7-t, '先减去吃掉的数量，再加上后来拿的数量。']
    ],
    (n, t) => [
        ['平均分','把 '+(n*(t+2))+' 张卡片平均分给 '+(t+2)+' 人，每人几张？', n, '用卡片总数除以人数。'],
        ['乘加应用','有 '+n+' 袋糖，每袋 '+(t+2)+' 颗，另有 4 颗，一共有多少颗？', n*(t+2)+4, '先算袋里的糖，再加上单独的 4 颗。']
    ],
    (n, t) => [
        ['经过时间','从上午 '+n+' 点 '+(t*5)+' 分到 '+(n+1)+' 点整，经过多少分钟？', 60-t*5, '从当前分钟数补到 60 分钟。'],
        ['周长推宽','长方形周长 '+(2*(n+t+8))+' 厘米，长 '+(n+4)+' 厘米，宽多少厘米？', t+4, '周长除以 2 得到长和宽的和。']
    ],
    (n, t) => [
        ['运算顺序', (n+5)+' × ('+(t+7)+' − 3) = ？', (n+5)*(t+4), '先算括号内的减法。'],
        ['平均数推算','4 次测量的平均值是 '+(n+10)+' 厘米。前三次共 '+(3*n+29+t)+' 厘米，第四次是多少厘米？', n+11-t, '用平均值乘次数求总和，再减去前三次之和。']
    ],
    (n, t) => [
        ['分数应用','一条 '+(n*6)+' 米的绳子，用了全长的 '+t+'/6，用了多少米？', n*t, '先求全长的六分之一，再乘分子。'],
        ['组合面积','长 '+(n+4)+' 米、宽 '+(t+4)+' 米的长方形场地，去掉边长 3 米的正方形后，剩多少平方米？', (n+4)*(t+4)-9, '用长方形的面积减去正方形的面积。']
    ],
    (n, t) => [
        ['百分数还原','参加活动的 '+(n*t*5)+' 人占总人数的 '+(t*10)+'%，总人数是多少？', n*50, '已知部分和百分率，用部分除以百分率。'],
        ['比例分配','甲乙两人按 '+t+':'+(t+2)+' 分 '+(n*(2*t+2))+' 元，乙比甲多分多少元？', 2*n, '先求每份金额，再乘相差的份数。']
    ],
    (n, t) => [
        ['绝对值', '|−'+(n+4)+'| − |'+(n-t)+'| = ？', t+4, '先计算两个绝对值，再相减。'],
        ['方程应用','有 3 个等重的盒子，每盒加入 '+t+' 克后，总重为 '+(3*(n+t+8))+' 克。原来每盒重多少克？', n+8, '先算加重后的单盒重量，再减每盒增加的重量。']
    ],
    (n, t) => [
        ['一次函数','直线经过 (0,'+t+') 和 (2,'+(2*n+t)+')，斜率是多少？', n, '纵坐标的变化量除以横坐标的变化量。'],
        ['勾股定理应用','长方形对角线长 '+(13*n)+' 厘米，一条边长 '+(5*n)+' 厘米，另一条边长多少厘米？', 12*n, '两条边是直角边，用对角线平方减去已知边平方。']
    ],
    (n, t) => [
        ['二次方程根的关系','方程 x² − '+(n+t)+'x + '+n*t+' = 0，两根之和是多少？', n+t, '首项系数为 1 时，两根和等于一次项系数的相反数。'],
        ['二次函数应用','某抛物线的顶点为 ('+n+','+t+')，已知点 ('+(n+2)+','+(t+12)+') 在抛物线上，那么横坐标为 '+(n-2)+' 的点纵坐标是多少？', t+12, '两个横坐标到对称轴的距离相等，纵坐标也相等。']
    ]
];
for (let grade = 1; grade <= 9; grade++) {
    for (let term = 1; term <= 2; term++) {
        for (let i = 2; i <= 9; i++) {
            mathExtensions[grade - 1](i+term, term).forEach(([topic, stem, answer, tip], index) =>
                number(grade, term, topic, stem, answer, tip, index === 0 ? 3 : 5));
        }
    }
}
const englishExtensions = [
    ['a __ can swim|duck|desk|pen|book','the __ is in the sky|moon|bed|chair|bag','I write with a __|pen|fish|cat|cup','an __ is a fruit|apple|egg|box|desk','I can read a __|book|ball|dog|cup','a __ has four legs|cat|tree|pen|bag','we sit on a __|chair|book|door|hat','the __ gives us light in the daytime|sun|bag|milk|fish'],
    ['I hear with my __|ears|eyes|hands|legs','I see with my __|eyes|ears|feet|hands','I wash my __ before lunch|hands|shoes|desk|bag','a fish lives in __|water|sand|a tree|a box','my feet are in my __|shoes|gloves|hat|bag','I smell with my __|nose|feet|eyes|ears','I wear a __ on my head|hat|shoe|sock|glove','we can drink __|milk|bread|rice|cake'],
    ['this is __ pencil|my|I|me|mine','__ are my friends|They|He|She|It','it __ a red ball|is|am|are|be','we __ students|are|is|am|be','I __ a new book|have|has|is|am','__ is my brother|He|They|We|It','those are two __|apples|apple|an apple|a apple','there __ one window|is|are|am|be'],
    ['the sky is dark. It may __|rain|read|draw|eat','we borrow books from a __|library|kitchen|farm|bedroom','it is cold. Put on your __|coat|plate|pen|cup','we eat breakfast in the __|morning|evening|night|afternoon','I am thirsty. I need some __|water|shoes|books|paper','the sun is shining. It is __|sunny|snowy|rainy|foggy','we have __ at about noon|lunch|breakfast|supper|midnight','carrots are __|vegetables|drinks|animals|clothes'],
    ['there __ two rivers near our village|are|is|am|be','the pencil is __ the box; open it to find the pencil|in|above|beside|behind','she __ often help at home. She helps only once a month|does not|do not|is not|has not','would you like __ rice?|some|many|a few|an','there is __ milk in the glass|some|many|a|a few','he can __ very well|swim|swims|swimming|swam','what __ you do on Sundays?|do|does|is|has','the bridge is __ the river|over|inside|underwater|through'],
    ['we __ the museum yesterday|visited|visit|visits|visiting','turn left __ the crossing|at|in|on|of','my father goes to work __ subway|by|with|on a|at','she will __ a postcard tomorrow|send|sent|sends|sending','they __ football last Saturday|played|play|plays|playing','I am going __ a dictionary|to buy|buy|bought|buys','he __ at home yesterday|was|is|are|were','please __ a helmet when you ride|wear|wears|wore|wearing'],
    ['my sister usually __ up at six|gets|get|getting|got','listen! Someone __ at the door|is knocking|knock|has knock|are knocking','this road goes __ the park from one end to the other|through|among|above|between','there are __ people here today than yesterday|more|most|much|many','he __ his homework now, so please be quiet|is doing|do|did|are doing','I often go jogging __ the morning|in|at|on|for','the library is __ the bank and the park|between|among|with|from','she enjoys __ English stories|reading|read|reads|to reads'],
    ['if it rains tomorrow, we __ at home|will stay|stayed|have stayed|are stay','she speaks __ than before|more clearly|clearly|most clearly|clear','I was reading when the phone __|rang|rings|ring|has ring','he has lived here __ 2020|since|for|at|from since','the box is too heavy for me __|to carry|carry|carrying|carried','they kept __ even when it was difficult|practising|practise|practises|practised','it was late, __ we went home|so|because|although|unless','the film is __ than the book|shorter|shortest|short|more short'],
    ['the classroom __ every morning|is cleaned|cleans|has cleaning|cleaned it','I do not know __ the train leaves|when|what|whose|which one','the book __ you lent me was interesting|that|who|what|whose','neither of the answers __ correct|is|am|be|were been','if I __ you, I would ask for help|were|am|will be|have been','he asked me where I __ the key|had found|do find|have find|will found','this is the person __ helped me yesterday|who|which|whose|what','you will be late __ you leave now|unless|although|because|so that']
];
const chineseExtensions = [
    ['白的反义词|黑','大的反义词|小','上的反义词|下','左的反义词|右','一__书|本','一__马|匹','一__花|朵','一__鱼|条'],
    ['一__黑板|块','一__队伍|支','一__雨伞|把','一__椅子|把','“早__”表示清晨，横线处应填哪个字|晨','哪个字能与“火”组成表示热的词？|热','“进来”的反义词|出去','“打开”的反义词|关上'],
    ['“快得像闪电”主要运用的修辞|比喻','“星星在眨眼”主要运用的修辞|拟人','“难道这不是好消息吗”主要运用的修辞|反问','“谁在值日？我在值日。”主要运用的修辞|设问','一__邮票|枚','一__轮船|艘','一__布|匹','一__树林|片'],
    ['形容慌得不知怎么办|手足无措','形容彼此相处和睦|和睦相处','形容道路弯弯曲曲|蜿蜒曲折','形容接连不断|络绎不绝','形容平安健康|安然无恙','形容精神振奋|精神抖擞','形容没有任何根据地担心|杞人忧天','形容懂得感恩、记得来源|饮水思源'],
    ['“所以迟到了”常承接哪类原因|因果','“虽然天冷，但大家仍在训练”属于哪类关系|转折','“先洗手，再吃饭”属于哪类关系|承接','“既会唱歌，也会跳舞”属于哪类关系|并列','“只要按时复习，就能减少遗忘”属于哪类关系|条件','“如果路滑，我们就慢走”属于哪类关系|假设','“不但整洁，而且宽敞”属于哪类关系|递进','“与其等待，不如主动询问”属于哪类关系|选择'],
    ['“钟声响彻云霄”主要运用的修辞|夸张','“树影在墙上跳舞”主要运用的修辞|拟人','“书页像一片片扬起的帆”主要运用的修辞|比喻','“这难道是借口吗”主要运用的修辞|反问','说明新桥长 150 米，主要使用什么方法|列数字','把新旧两条路线的用时放在一起比较|作比较','用具体例子解释抽象现象|举例子','按不同成因分组说明|分类别'],
    ['“敏而好学”中的“敏”意为|聪敏','“己所不欲”中的“欲”意为|想要','“学而不思则罔”中的“罔”意为|迷惑','“逝者如斯夫”中的“斯”指什么|这','“择其善者而从之”中的“从”意为|学习','“知之者不如好之者”中的“好”意为|喜爱','“学而不厌”中的“厌”意为|满足','“诲人不倦”中的“诲”意为|教导'],
    ['提出自己的主张并用事例说明，使用了|举例论证','通过对照两种做法的后果来说明观点，使用了|对比论证','引用公认的道理来说明观点，使用了|道理论证','用筑屋打地基来说明学习基础的重要，使用了|比喻论证','说明文中解释一个术语的确切含义，通常用|下定义','把研究数据绘成图形，使用了|列图表','引用原文来说明一种观点，使用了|引用','把对象按特征分成若干类，使用了|分类别'],
    ['“得道多助”中的“道”指|道义','“寡助之至”中的“寡”意为|少','“故天将降大任于是人也”中的“故”意为|所以','“曾益其所不能”中的“曾”同哪个字|增','“行拂乱其所为”中的“拂”意为|违背','“入则无法家拂士”中的“入”指|在国内','“困于心”中的“困”意为|忧困','“衡于虑”中的“衡”同哪个字|横']
];
function addLanguageExtensions(subject, groups) {
    groups.forEach((entries, gradeIndex) => entries.forEach((row, index) => {
        const [stem, correct, ...wrong] = row.split('|');
        const distractors = wrong.length ? wrong : [...new Set(entries.map(entry => entry.split('|')[1]))].filter(value => value !== correct);
        const offset = index % distractors.length;
        add(gradeIndex+1, index < 4 ? 1 : 2, subject, subject === '英语' ? '语境填词' : '基础辨析',
            stem + '（请选择）', correct, distractors.slice(offset).concat(distractors.slice(0, offset)),
            '正确答案为「' + correct + '」。', index % 4 < 2 ? 1 : 3);
    }));
}
addLanguageExtensions('英语', englishExtensions);
addLanguageExtensions('语文', chineseExtensions);

const preschool = [
    ['常识', '颜色', '成熟香蕉的外皮通常是什么颜色？', '黄色', '蓝色', '紫色', '黑色'],
    ['常识', '颜色', '晴天时，抬头常看到什么颜色的天空？', '蓝色', '红色', '绿色', '橙色'],
    ['常识', '动物', '哪种动物会喵喵叫？', '猫', '狗', '鸭', '牛'],
    ['常识', '动物', '哪种动物有很长的鼻子？', '大象', '兔子', '小鸡', '金鱼'],
    ['常识', '动物', '哪种动物通常生活在水里，用鳃呼吸？', '金鱼', '小猫', '小狗', '山羊'],
    ['常识', '生活', '刷牙时要用什么？', '牙刷', '尺子', '梳子', '勺子'],
    ['常识', '生活', '下雨出门，可以用什么挡雨？', '雨伞', '扇子', '水杯', '铅笔'],
    ['常识', '生活', '睡觉前，哪种做法有助于保护牙齿？', '刷牙', '吃糖', '喝甜汽水', '咬玩具'],
    ['常识', '安全', '过马路等信号灯时，红灯亮了该怎么办？', '停下来等', '跑过去', '闭眼走', '在马路上玩'],
    ['常识', '安全', '不小心和家长走散了，可以向谁求助？', '现场的警察', '电话里的陌生人', '路边的小猫', '商店里的玩偶'],
    ['数学', '形状', '足球的外形最接近什么？', '球体', '长方体', '圆柱体', '圆锥体'],
    ['数学', '形状', '一个三角形有几条边？', '3', '2', '4', '5'],
    ['数学', '规律', '红、蓝、红、蓝、红，接下来是什么颜色？', '蓝色', '红色', '黄色', '白色'],
    ['数学', '规律', '小、大、大、小、大、大，接下来是哪一个？', '小', '大', '两个大', '两个小'],
    ['语文', '听说', '收到别人送的礼物，可以说什么？', '谢谢', '对不起', '晚安', '请进'],
    ['语文', '听说', '早上见到老师，可以怎么打招呼？', '老师早', '老师晚安', '老师再见', '老师请坐'],
    ['语文', '词语', '“关门”的相反动作是什么？', '开门', '洗手', '起床', '拍手'],
    ['语文', '词语', '哪一个词语表示声音很小？', '轻声', '大喊', '高唱', '尖叫'],
    ['英语', '颜色', '英语 red 表示什么颜色？', '红色', '蓝色', '黄色', '绿色'],
    ['英语', '颜色', '英语 blue 表示什么颜色？', '蓝色', '红色', '紫色', '白色'],
    ['英语', '动物', '英语 cat 指哪种动物？', '猫', '狗', '鸭', '鱼'],
    ['英语', '动物', '英语 dog 指哪种动物？', '狗', '猫', '羊', '马'],
    ['英语', '问候', '见面打招呼时可以说哪个英语词？', 'Hello', 'Bye', 'Thanks', 'Sorry'],
    ['英语', '数字', '英语 three 表示数字几？', '3', '1', '2', '4']
];
for (const [subject, topic, stem, correct, ...wrong] of preschool) {
    add(0, 0, subject, topic, stem, correct, wrong, '正确答案是「' + correct + '」。');
}
for (let count = 2; count <= 10; count++) {
    const dots = '● '.repeat(count).trim();
    number(0, 0, '数数', '数一数，有几个圆点？\n' + dots, count, '从左到右，每个圆点只数一次。');
    number(0, 0, '数序', count + ' 前面紧挨着的是几？', count - 1, '按顺序数一数，' + (count - 1) + ' 后面就是 ' + count + '。');
}

const mathPractice = [
    ['认识钟表', n => ['时针指着 ' + n + '，分针指着 12，是几点？（选整点数字）', n, '分针指着 12 时是整点，读时针指的数字。']],
    ['长度换算', n => [n + ' 米等于多少厘米？', n * 100, '1 米等于 100 厘米。']],
    ['长方形周长', n => ['长方形的长是 ' + (n + 4) + ' 厘米，宽是 ' + n + ' 厘米，周长是多少厘米？', 4 * n + 8, '长方形周长等于（长＋宽）× 2。']],
    ['平均数', n => [n + '、' + (n + 3) + '、' + (n + 6) + ' 的平均数是多少？', n + 3, '把三个数相加，再除以 3。']],
    ['小数乘法', n => ['每支笔 2.5 元，买 ' + (n * 2) + ' 支需要多少元？', n * 5, '每两支共 5 元，再乘以组数。']],
    ['比例尺', n => ['比例尺为 1∶1000，图上距离 ' + n + ' 厘米，实际距离是多少米？', n * 10, '图上 1 厘米对应实际 1000 厘米，也就是 10 米。']],
    ['有理数', n => ['气温从 −' + n + ' ℃ 升高了 8 ℃，现在是多少摄氏度？', 8 - n, '把初始温度与升高的度数相加。']],
    ['勾股定理', n => ['直角三角形两条直角边分别为 ' + (3 * n) + ' 和 ' + (4 * n) + '，斜边长是多少？', 5 * n, '斜边的平方等于两条直角边的平方和。']],
    ['圆的周长', n => ['一个圆的半径为 ' + n + ' 厘米，它的周长是几倍 π 厘米？', 2 * n, '圆的周长等于 2πr。']]
];
mathPractice.forEach(([topic, exercise], index) => {
    for (let n = 2; n <= 12; n++) {
        const [stem, answer, tip] = exercise(n);
        number(index + 1, 0, topic, stem, answer, tip);
    }
});

// Each row supplies two different skills per grade, with 16 exercises per skill.
const moreMath = [
    [
        ['20以内减法', n => [`${n + 2} − 2 = ？`, n, '从总数中去掉指定的数量。']],
        ['比较大小', n => [`${n + 1} 比 3 大多少？`, n - 2, '用较大的数减去较小的数。']]
    ],
    [
        ['有余数的除法', n => [`${n * 4 + n % 3 + 1} ÷ 4 的余数是多少？`, n % 3 + 1, '先找出不超过被除数的最大 4 的倍数，再相减。']],
        ['两位数加法', n => [`有 ${n + 20} 本故事书和 17 本图画书，一共有多少本？`, n + 37, '求合计，用加法。']]
    ],
    [
        ['面积', n => [`长方形长 ${n + 3} 米、宽 4 米，面积是多少平方米？`, (n + 3) * 4, '长方形面积等于长乘宽。']],
        ['时间换算', n => [`一场活动持续 1 小时 ${n} 分钟，一共多少分钟？`, 60 + n, '1 小时等于 60 分钟。']]
    ],
    [
        ['小数加法', n => [`${n}.4 + 2.6 = ？`, n + 3, '小数点对齐，十分位相加满十进一。']],
        ['角度', n => [`直角三角形的一个锐角是 ${n + 20}°，另一个锐角是多少度？`, 70 - n, '直角三角形的两个锐角之和为 90°。']]
    ],
    [
        ['最大公因数', n => [`${n * 2} 和 ${n * 3} 的最大公因数是多少？`, n, '去掉两数共有的倍数后，2 和 3 互质。']],
        ['体积', n => [`长方体长 ${n} 厘米，宽 4 厘米，高 3 厘米，体积是多少立方厘米？`, n * 12, '长方体体积等于长、宽、高的乘积。']]
    ],
    [
        ['分数应用', n => [`一盒有 ${n * 4} 支笔，送出其中的四分之一，还剩多少支？`, n * 3, '先求四分之一，再用总数减去送出的数量。']],
        ['速度路程', n => [`骑车每小时行 ${n + 8} 千米，按同样速度骑 3 小时能行多少千米？`, (n + 8) * 3, '路程等于速度乘时间。']]
    ],
    [
        ['有理数乘法', n => [`(−${n}) × (−4) = ？`, n * 4, '两个负数相乘得正数。']],
        ['整式求值', n => [`当 x = ${n} 时，2x + 7 的值是多少？`, n * 2 + 7, '把 x 的值代入整式。']]
    ],
    [
        ['一次函数', n => [`一次函数 y = 3x − 5，当 x = ${n} 时，y 是多少？`, n * 3 - 5, '把 x 的值代入函数式。']],
        ['平方根', n => [`${(n + 7) ** 2} 的算术平方根是多少？`, n + 7, '算术平方根是平方后等于原数的非负数。']]
    ],
    [
        ['相似三角形', n => [`两个相似三角形的对应边长之比为 2∶3。较小三角形的一条边长 ${n * 2}，较大三角形对应边长是多少？`, n * 3, '对应边长按同一个比例变化。']],
        ['二次函数', n => [`抛物线 y = (x + ${n})² − 6 的对称轴是 x 等于多少？`, -n, '令平方项括号内的式子等于 0。']]
    ]
];
moreMath.forEach((topics, gradeIndex) => {
    for (const [topic, exercise] of topics) {
        for (let n = 3; n <= 18; n++) {
            const [stem, answer, tip] = exercise(n);
            number(gradeIndex + 1, 0, topic, stem, answer, tip);
        }
    }
});

const extraVocabulary = [
    'apple 苹果|banana 香蕉|pear 梨|orange 橙子|cat 猫|dog 狗|duck 鸭子|fish 鱼|red 红色|blue 蓝色|green 绿色|yellow 黄色',
    'book 书|pen 钢笔|pencil 铅笔|ruler 尺子|hand 手|foot 脚|nose 鼻子|ear 耳朵|bread 面包|rice 米饭|milk 牛奶|egg 鸡蛋',
    'window 窗户|door 门|desk 书桌|chair 椅子|morning 早晨|afternoon 下午|evening 傍晚|night 夜晚|sunny 晴朗的|rainy 下雨的|snowy 下雪的|cloudy 多云的',
    'garden 花园|library 图书馆|hospital 医院|museum 博物馆|river 河流|forest 森林|mountain 山|island 岛|read 阅读|write 写|draw 画|sing 唱歌',
    'ticket 票|station 车站|airport 机场|bridge 桥|breakfast 早餐|lunch 午餐|dinner 晚餐|snack 零食|quiet 安静的|busy 忙碌的|hungry 饥饿的|thirsty 口渴的',
    'borrow 借入|lend 借出|return 归还|collect 收集|journey 旅程|map 地图|route 路线|passport 护照|careful 小心的|patient 有耐心的|honest 诚实的|polite 礼貌的',
    'compare 比较|describe 描述|explain 解释|imagine 想象|habit 习惯|choice 选择|reason 原因|result 结果|ancient 古老的|modern 现代的|common 常见的|rare 罕见的',
    'achieve 实现|consider 考虑|discover 发现|improve 改进|environment 环境|population 人口|temperature 温度|resource 资源|available 可获得的|familiar 熟悉的|similar 相似的|necessary 必要的',
    'contribute 贡献|establish 建立|maintain 维持|recommend 推荐|evidence 证据|method 方法|purpose 目的|conclusion 结论|accurate 准确的|efficient 高效的|independent 独立的|responsible 负责任的'
];
extraVocabulary.forEach((words, gradeIndex) => {
    const pairs = words.split('|').map(pair => pair.split(' '));
    pairs.forEach(([english, chinese], index) => {
        const other = [1, 2, 3].map(offset => pairs[Math.floor(index / 4) * 4 + (index + offset) % 4]);
        const tip = english + ' 表示「' + chinese + '」。';
        add(gradeIndex + 1, 0, '英语', '词义辨认', '哪个选项是 ' + english + ' 的常见含义？', chinese, other.map(pair => pair[1]), tip);
        add(gradeIndex + 1, 0, '英语', '单词辨认', '哪个英语单词可以表示「' + chinese + '」？', english, other.map(pair => pair[0]), tip);
    });
});

const antonyms = [
    '前 后|多 少|开 关|进 退|远 近|长 短|高 低|快 慢',
    '干净 肮脏|明亮 昏暗|安静 吵闹|温暖 寒冷|热闹 冷清|容易 困难|整齐 凌乱|坚硬 柔软',
    '诚实 虚伪|勤劳 懒惰|勇敢 胆怯|粗心 细心|宽阔 狭窄|稀疏 茂密|清澈 浑浊|潮湿 干燥',
    '具体 抽象|熟悉 陌生|清晰 模糊|严肃 随便|节约 浪费|赞成 反对|肯定 否定|增加 减少',
    '谦虚 骄傲|镇定 慌乱|敏捷 迟钝|热情 冷淡|明智 愚蠢|严谨 草率|轻视 重视|扩大 缩小',
    '繁荣 萧条|稳定 动荡|迅速 缓慢|顺利 曲折|公开 保密|真实 虚假|暂时 永久|主动 被动',
    '慷慨 吝啬|坦率 含蓄|朴素 奢华|乐观 悲观|冷静 冲动|妥善 失当|周密 疏漏|刚强 软弱',
    '凝聚 涣散|严密 松散|孤立 联合|统一 分裂|理性 感性|保守 激进|宽容 苛刻|简洁 冗长',
    '推崇 鄙弃|果断 犹豫|充裕 匮乏|严苛 宽松|精确 粗略|隐晦 明白|深奥 浅显|繁复 简单'
];
antonyms.forEach((words, gradeIndex) => {
    const pairs = words.split('|').map(pair => pair.split(' '));
    pairs.forEach(([word, opposite], index) => {
        const wrong = [1, 2, 3].map(offset => pairs[(index + offset) % pairs.length][0]);
        add(gradeIndex + 1, 0, '语文', '反义词', '「' + word + '」的反义词是哪一个？', opposite, wrong,
            '「' + word + '」和「' + opposite + '」意思相对。');
    });
});

require('./expand-builtin-bank.cjs')(add);
require('./extend-builtin-bank.cjs')(add);

const seen = new Set();
questions.forEach(question => {
    const key = question.level + '/' + question.term + '/' + question.q;
    if (seen.has(key)) throw new Error('Duplicate question: ' + key);
    seen.add(key);
    if (!question.topic.trim() || !question.tip.trim() || question.a[question.k] == null ||
        new Set(question.a).size !== question.a.length) throw new Error('Incomplete question: ' + key);
});
fs.writeFileSync(path.join(root, 'data/seed.json'), JSON.stringify(questions, null, 2) + '\n');
console.log('Built ' + questions.length + ' questions across 10 grade levels.');
