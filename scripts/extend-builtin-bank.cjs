module.exports = function extendBank(add) {
    // Each grade gets a mix of arithmetic, geometry, and applied reasoning.
    const math = [
        [
            ['整十数加法', n => [`30 加 ${n * 10} 等于多少？`, 30 + n * 10, '把两个数分别看成几个十，再把十的个数相加。'], 6],
            ['认识数位', n => [`${n * 5 + 20} 的十位上是几？`, Math.floor((n * 5 + 20) / 10), '从右往左，第二位是十位。'], 12],
            ['凑十', n => [`9 颗珠子再添 ${n} 颗。先从添来的珠子里拿 1 颗凑成 10，还剩几颗没有放进去？`, n - 1, '从新添的珠子中拿走 1 颗。'], 9],
            ['数的顺序', n => [`在 ${n + 20}、__、${n + 22} 这三个连续的数中，横线填几？`, n + 21, '连续的数每次增加 1。'], 12],
            ['加减法应用', n => [`有 ${n + 7} 人排队，前面 3 人离开了，后来又来了 2 人。现在有几人？`, n + 6, '先减去离开的人数，再加上新来的人数。'], 12]
        ],
        [
            ['乘加应用', n => [`5 个袋子各有 ${n} 颗糖，桌上另有 3 颗。共有几颗糖？`, 5 * n + 3, '先算袋子里的糖，再加上桌上的 3 颗。'], 9],
            ['简单除法', n => [`把 ${n * 8} 张贴纸平均分给 8 人，每人几张？`, n, '把贴纸总数除以人数。'], 9],
            ['长度单位', n => [`一条彩带长 ${n} 米 20 厘米，共多少厘米？`, n * 100 + 20, '先把米换成厘米，再加上 20 厘米。'], 12],
            ['百以内减法', n => [`用 80 元买一个 ${n + 35} 元的书包，应找回多少元？`, 45 - n, '用付出的钱减去书包价格。'], 12],
            ['时刻计算', n => [`现在是上午 ${n} 点，2 小时后是几点？（用 24 小时制）`, n + 2, '在原来的整点数上加 2。'], 11]
        ],
        [
            ['两位数乘一位数', n => [`每盒 ${n + 13} 块拼图，4 盒共有几块？`, (n + 13) * 4, '每盒数量相同，总数等于每盒数量乘盒数。']],
            ['周长', n => [`正方形边长 ${n + 3} 厘米，周长是多少厘米？`, (n + 3) * 4, '正方形四边等长，周长是边长的 4 倍。']],
            ['分数初步', n => [`把 ${n + 3} 块饼干平均分给 ${n + 3} 人。每人得到总数的几分之一？（填分母）`, n + 3, '平均分成几份，每份就是几分之一。']],
            ['质量比较', n => [`一袋米重 ${n + 2} 千克，另一袋重 1 千克 500 克，两袋相差多少克？`, (n + 2) * 1000 - 1500, '统一换成克，再用较重的质量减去较轻的质量。']],
            ['面积应用', n => [`长方形地面上每排铺 ${n + 4} 块方砖，共铺 6 排。铺满一共要几块？`, (n + 4) * 6, '每排块数乘排数就是方砖总数。']]
        ],
        [
            ['小数乘十', n => [`${n}.7 × 10 等于多少？`, n * 10 + 7, '小数乘 10，小数点向右移动一位。']],
            ['小数比较', n => [`8.${n + 35} 与 8.25 相差多少？`, (n + 10) / 100, '整数部分相同，用较大的小数部分减去较小的小数部分。']],
            ['平均数', n => [`三次投篮分别进了 ${n}、${n + 2} 和 ${n + 7} 球，平均每次进几球？`, n + 3, '把三次进球数相加，再除以 3。']],
            ['平角与角度', n => [`平角被一条射线分成两个角，其中一个是 ${n + 50}°，另一个是多少度？`, 130 - n, '平角是 180°，减去已知角度。']],
            ['简便计算', n => [`计算 125 × ${n + 2} × 8，结果是多少？`, (n + 2) * 1000, '先把 125 和 8 相乘得到 1000。']]
        ],
        [
            ['分数减法', n => [`同分母分数 ${n + 9}/25 − 4/25，结果写成分母为 25 的分数，分子是多少？`, n + 5, '分母不变，分子相减。']],
            ['梯形面积', n => [`梯形上底 ${n + 2} 米，下底 ${n + 6} 米，高 4 米，面积是多少平方米？`, (2 * n + 8) * 2, '梯形面积是上底与下底的和乘高再除以 2。']],
            ['小数乘法', n => [`1.25 元一支的铅笔买 ${n * 4} 支，共多少元？`, n * 5, '每 4 支花 5 元，再乘以组数。']],
            ['长方体表面积', n => [`长方体长 ${n} 厘米，宽 3 厘米，高 2 厘米，表面积是多少平方厘米？`, 10 * n + 12, '分别求三组相邻面的面积，再把它们的和乘 2。']],
            ['公倍数', n => [`${n * 3} 和 ${n * 4} 的最小公倍数是多少？`, n * 12, '3 和 4 互质，最小公倍数是 12，再乘共同因数。']]
        ],
        [
            ['按比例分配', n => [`两组书的本数之比是 3∶4，总共有 ${n * 7} 本。较多的一组有几本？`, n * 4, '总量对应 7 份，较多的一组占 4 份。']],
            ['百分数折扣', n => [`一件物品原价 ${n * 20} 元，优惠 15%，少付多少元？`, n * 3, '少付的钱等于原价乘 15%。']],
            ['圆柱侧面积', n => [`圆柱底面周长是 ${n + 10} 厘米，高 6 厘米，侧面积是多少平方厘米？`, (n + 10) * 6, '侧面展开是长方形，长等于底面周长，宽等于高。']],
            ['圆锥体积', n => [`圆锥底面积是 ${n * 3} 平方厘米，高 5 厘米，体积是多少立方厘米？`, n * 5, '底面积乘高，再除以 3。']],
            ['单位换算', n => [`${n}.5 升水等于多少毫升？`, n * 1000 + 500, '每升等于 1000 毫升，0.5 升等于 500 毫升。']]
        ],
        [
            ['相反数', n => [`${n + 10} 的相反数是多少？`, -n - 10, '相反数与原数到 0 的距离相同，符号相反。']],
            ['有理数减法', n => [`从 −${n} 中减去 −15，结果是多少？`, 15 - n, '减去负数等于加上它的相反数。']],
            ['去括号', n => [`化简 8x − (${n}x − 5)，其中 x 的系数是多少？`, 8 - n, '括号前是减号，去括号后括号内各项变号。']],
            ['一元一次方程应用', n => [`三个连续整数的和是 ${3 * n + 6}，中间的整数是多少？`, n + 2, '三个连续整数的和等于中间整数的 3 倍。']],
            ['余角', n => [`一个角是 ${n + 35}°，它的余角是多少度？`, 55 - n, '两个互余的角度数之和为 90°。']]
        ],
        [
            ['勾股定理逆用', n => [`直角三角形斜边是 ${13 * n}，一条直角边是 ${5 * n}，另一条直角边是多少？`, 12 * n, '另一条边的平方等于斜边平方减已知直角边平方。']],
            ['完全平方公式', n => [`展开 (x + ${n})²，其中 x 项的系数是多少？`, n * 2, '平方展开后中间项等于两项乘积的 2 倍。']],
            ['函数变化量', n => [`对于 y = 4x + 1，x 增加 ${n} 时，y 增加多少？`, n * 4, '一次函数的变化量等于斜率乘自变量变化量。']],
            ['全等三角形', n => [`△ABC 与 △DEF 全等，A、B、C 分别对应 D、E、F。若 BC = ${n + 8}，EF 等于多少？`, n + 8, '全等三角形的对应边相等，BC 对应 EF。']],
            ['数据的中位数', n => [`把 ${n + 6}、${n}、${n + 3}、${n + 1}、${n + 5} 从小到大排列，中位数是多少？`, n + 3, '排序后，五个数中的第三个数是中位数。']]
        ],
        [
            ['二次函数平移', n => [`将 y = x² 的图像向右平移 ${n} 个单位，顶点的横坐标是多少？`, n, '原顶点在原点，向右平移后横坐标增加。']],
            ['一元二次方程的根', n => [`方程 (x − ${n})(x + 4) = 0 的正根是多少？`, n, '乘积为零时至少有一个因式为零，两个根中取正数。']],
            ['反比例函数', n => [`在反比例函数 y = ${n * 12}/x 中，当 x = 6 时，y 等于多少？`, n * 2, '代入横坐标，用比例系数除以 x。']],
            ['相似图形面积', n => [`两个相似三角形的对应边长之比为 1∶3。较小的面积为 ${n}，较大的面积是多少？`, n * 9, '相似图形的面积比是对应边长比的平方。']],
            ['圆周角', n => [`同一段弧所对的圆心角为 ${2 * (n + 20)}°，它所对的圆周角是多少度？`, n + 20, '同弧所对的圆周角等于圆心角的一半。']]
        ]
    ];
    math.forEach((topics, index) => {
        for (const [topic, exercise, count = 12] of topics) {
            for (let n = 1; n <= count; n++) {
                const [stem, answer, explanation] = exercise(n);
                const offsets = topic === '认识数位' ? [1, 2, 3].map(offset => (answer + offset) % 10)
                    : [1, 2, 3].map(offset => Number((answer + offset).toFixed(2)));
                add(index + 1, 0, '数学', topic, stem, answer, offsets, explanation);
            }
        }
    });

    const chinese = [
        '清脆 声音清亮悦耳|柔和 温和而不刺耳或刺眼|平坦 平整而没有明显坡度|弯曲 由直变弯|摇晃 来回摆动不稳|悄悄 动作轻不让人察觉|仿佛 好像|赶紧 抓紧时间去做|认真 用心而不马虎',
        '飘扬 随风摆动|倒映 物体的影像反映在水面上|绽放 花朵开放|凋谢 花叶枯萎脱落|珍惜 重视并爱护|观察 仔细察看事物|赞赏 认为好并表示称赞|鼓励 给人信心和勇气|陪伴 在身边相随',
        '一丝不苟 做事认真连小地方也不马虎|相提并论 把不同的人或事放在一起谈论或看待|四面八方 各个地方或方向|五光十色 色彩鲜艳而繁多|轻盈 动作或体态轻巧|敏锐 感觉或观察很灵敏|凝神 集中精神|端详 仔细地看|分辨 区分不同的事物',
        '井底之蛙 比喻见识狭小的人|守株待兔 比喻不主动努力只想碰运气|亡羊补牢 比喻出了问题后设法补救|刻舟求剑 比喻拘泥成规而不知情况已变|含蓄 意思不完全说出而留有余味|质朴 朴实而不加过多修饰|婉转 表达委婉或声音曲折动听|鲜明 特点明确而突出|生动 有活力并能让人产生鲜明印象',
        '胸有成竹 做事之前已有成熟的打算|手不释卷 形容勤奋读书|自相矛盾 自己的说法或做法互相抵触|画蛇添足 做了多余的事反而把事情弄糟|循序渐进 按一定次序逐步推进|分门别类 按类别分别整理|融会贯通 把各方面知识合起来理解|持之以恒 长久坚持|锲而不舍 坚持做下去而不放弃',
        '神态描写 描写人物的表情和神色|语言描写 通过人物说的话表现人物|心理描写 写人物内心的想法或感受|外貌描写 写人物的长相和衣着等外在特征|顺叙 按事件发展先后叙述|概括叙述 用简明语言交代较多事情|细节描写 把小而有表现力的地方写具体|叙述人称 叙述者讲故事时所用的人称|详略安排 根据表达重点分配笔墨',
        '并列复句 分句分别陈述相关情况而地位相等|因果复句 分句之间有原因和结果的关系|选择复句 分句列出不同可能供选择|承接复句 分句按时间或行动先后相继|同义词 意义相同或相近的词|反义词 意义相反或相对的词|多义词 一个词具有多个相互关联的义项|词义范围 一个词能指称的对象范围|感情色彩 词语带有的褒贬态度',
        '议论文 以提出并证明观点为主要任务的文章|说明文 以介绍事物或解释事理为主要任务的文章|记叙文 以叙写人物和事件为主要任务的文章|书信 用来传达消息或情感的信件|实写 直接写实际所见所闻或发生的事|虚写 写想象回忆或未直接出现的情景|正面描写 直接描写对象本身的特征|对比 把差别明显的事物放在一起比较|点题 直接揭示题目或主题的含义',
        '中心论点 全文所要论证的主要观点|分论点 从不同方面支撑中心论点的观点|事实论据 用来证明观点的事实和数据|道理论据 用来证明观点的道理或原理|总分结构 先总述再从不同方面展开|递进结构 按由浅入深的层次逐步推进|并列结构 把地位相当的内容分项说明|正反对照 把正面与反面的情况放在一起比较|归纳 从多件具体事例概括出一般结论'
    ];
    chinese.forEach((row, gradeIndex) => {
        const entries = row.split('|').map(value => value.split(' '));
        entries.forEach(([word, meaning], position) => {
            const others = [1, 3, 5].map(offset => entries[(position + offset) % entries.length]);
            const topic = gradeIndex < 4 ? '词义辨析' : gradeIndex === 4 ? '成语理解' : '阅读与表达';
            const explanation = `「${word}」指${meaning}。比较选项时，要看哪个意思最准确。`;
            add(gradeIndex + 1, 0, '语文', topic, `阅读时遇到「${word}」，应该怎样理解？`, meaning, others.map(entry => entry[1]), explanation);
            add(gradeIndex + 1, 0, '语文', topic, `如果要表达「${meaning}」的意思，哪个选项最合适？`, word, others.map(entry => entry[0]), explanation);
        });
    });

    const english = [
        'cow 奶牛|pig 猪|goat 山羊|sheep 绵羊|purple 紫色|grey 灰色|blue 蓝色|green 绿色',
        'desk 书桌|chair 椅子|table 桌子|sofa 沙发|tea 茶|juice 果汁|soup 汤|water 水',
        'bus 公共汽车|truck 卡车|van 厢式货车|taxi 出租车|square 正方形|circle 圆形|triangle 三角形|rectangle 长方形',
        'spring 春季|summer 夏季|autumn 秋季|winter 冬季|wind 风|cloud 云|rainbow 彩虹|thunder 雷声',
        'art 美术|science 科学|history 历史|geography 地理|piano 钢琴|guitar 吉他|violin 小提琴|drum 鼓',
        'east 东|west 西|north 北|south 南|turn 转弯|cross 穿过|follow 跟随|reach 到达',
        'practice 练习|prepare 准备|review 复习|repeat 重复|health 健康|energy 能量|sleep 睡眠|exercise 锻炼',
        'reduce 减少|reuse 再利用|protect 保护|prevent 预防|riverbank 河岸|wetland 湿地|coast 海岸|valley 山谷',
        'analyze 分析|predict 预测|verify 核实|summarize 总结|objective 目标|obstacle 障碍|priority 优先事项|outcome 结果'
    ];
    english.forEach((row, index) => {
        const entries = row.split('|').map(value => value.split(' '));
        entries.forEach(([word, meaning], position) => {
            const group = Math.floor(position / 4) * 4;
            const others = [1, 2, 3].map(offset => entries[group + (position + offset) % 4]);
            const explanation = `${word} 在这里的含义是「${meaning}」。`;
            add(index + 1, 0, '英语', '主题词汇', `读到英文词「${word}」，哪个选项和它意思一致？`, meaning, others.map(entry => entry[1]), explanation);
            add(index + 1, 0, '英语', '主题词汇', `在英文单词卡上，应该给「${meaning}」配哪个词？`, word, others.map(entry => entry[0]), explanation);
        });
    });

    const grammar = [
        ['I __ a student.|am|is|are|be|I 后用 am。', 'You __ my friend.|are|am|is|be|you 后用 are。', 'It __ a cat.|is|am|are|be|it 是第三人称单数，用 is。', 'One book, two __.|books|book|a book|book is|two 后面用复数 books。'],
        ['These __ my shoes.|are|is|am|be|these 指多个事物，用 are。', 'That __ my hat.|is|are|am|be|that 指单个事物，用 is。', 'I have three __.|pens|pen|a pen|one pen|three 后接可数名词复数。', 'This is __ apple.|an|a|two|many|apple 的发音以元音音素开头，用 an。'],
        ['There __ one door.|is|are|am|be|one door 是单数，用 is。', 'There __ four windows.|are|is|am|be|four windows 是复数，用 are。', 'The ball is __ the box. Open the box to get it.|in|under|behind|above|要打开盒子才能取到球，球在里面。', 'She can __ very fast.|run|runs|ran|running|can 后接动词原形。'],
        ['He __ a new bag.|has|have|is|are|he 是第三人称单数，此处用 has。', 'They __ two rulers.|have|has|is|am|主语 they 后使用 have。', 'Do you __ English books?|like|likes|liked|liking|助动词 do 后使用动词原形。', 'Does she __ to school by bus?|go|goes|going|went|does 已标明第三人称单数，go 保持原形。'],
        ['Look! The boys __ in the park.|are running|runs|run yesterday|is running|Look 提示正在发生，boys 是复数，用 are running。', 'Listen! The baby __.|is crying|are crying|cried yesterday|cry|Listen 提示此刻正在发生，baby 是单数。', 'Tom is __ than his brother.|taller|tallest|tall|the tall|than 前使用比较级 taller。', 'My pencil is shorter __ yours.|than|then|that|when|比较级用 than 引出比较对象。'],
        ['We __ a movie last night.|watched|watch|are watching|will watch|last night 表示过去，watch 用过去式。', 'She __ her keys yesterday.|lost|loses|will lose|is losing|yesterday 指过去，lose 的过去式是 lost。', 'They will __ tomorrow.|arrive|arrives|arrived|arriving|will 后用动词原形。', 'I __ going to visit my aunt.|am|is|are|be|I am going to 表示打算。'],
        ['She __ breakfast at seven every day.|eats|eat|eating|ate yesterday|every day 表示习惯，第三人称单数用 eats。', 'We __ football when it began to rain.|were playing|are playing|play|will play|过去某一时刻正在进行，用过去进行时。', 'If you hurry, you __ the bus.|will catch|caught|have caught|were catching|条件从句用现在时，主句可用 will 表示将来。', 'It was raining, __ we stayed inside.|so|although|unless|until|后半句是下雨造成的结果，用 so。'],
        ['She has worked here __ three years.|for|since|at|on|three years 是一段时间，搭配 for。', 'We have known each other __ 2020.|since|for|at|during|2020 是开始时间点，搭配 since。', 'The room __ every morning.|is cleaned|cleans|is cleaning|cleaned it|房间是被打扫的，用 is cleaned。', 'These emails __ yesterday.|were sent|are sending|will send|sends|邮件是被发送的，yesterday 表示过去，用 were sent。'],
        ['The girl __ won the race is my classmate.|who|which|where|when|先行词是人，关系词作主语，用 who。', 'This is the shop __ we bought the lamp.|where|who|what|whose|先行词是地点，关系词在从句中作地点状语。', 'I wonder __ the meeting will begin.|when|whose|which person|how many|问会议何时开始，使用 when。', 'The box is too heavy for me __.|to lift|lifting|lifted|lift|too ... for somebody to do 表示太……以至于某人不能做。']
    ];
    grammar.forEach((items, index) => {
        for (const item of items) {
            const [stem, answer, first, second, third, tip] = item.split('|');
            add(index + 1, 0, '英语', '句型与语法', `补全句子：${stem}`, answer, [first, second, third], tip);
        }
    });

    const additionalWords = [
        'car 汽车|boat 小船|plane 飞机|shoe 鞋|cap 帽子|coat 外套',
        'finger 手指|tooth 牙齿|mouth 嘴|banana 香蕉|melon 瓜|tomato 番茄',
        'Monday 星期一|Tuesday 星期二|Wednesday 星期三|Thursday 星期四|Friday 星期五|Sunday 星期日',
        'teacher 老师|doctor 医生|farmer 农民|driver 司机|baker 面包师|pilot 飞行员',
        'minute 分钟|hour 小时|week 星期|month 月份|season 季节|year 年',
        'straight 笔直的|narrow 狭窄的|wide 宽阔的|empty 空的|full 满的|crowded 拥挤的',
        'promise 承诺|refuse 拒绝|accept 接受|offer 提供|avoid 避免|suggest 建议',
        'biodiversity 生物多样性|drought 干旱|flood 洪水|erosion 侵蚀|emission 排放|conservation 保护',
        'reliable 可靠的|relevant 相关的|consistent 一致的|precise 精确的|flexible 灵活的|practical 实用的'
    ];
    additionalWords.forEach((row, gradeIndex) => {
        const entries = row.split('|').map(value => value.split(' '));
        entries.forEach(([word, meaning], position) => {
            const others = [1, 2, 3].map(offset => entries[(position + offset) % entries.length]);
            const tip = `「${meaning}」可以用 ${word} 表示。`;
            add(gradeIndex + 1, 0, '英语', '词义配对', `词卡上的 ${word} 应与哪项配对？`, meaning, others.map(entry => entry[1]), tip);
            add(gradeIndex + 1, 0, '英语', '词义配对', `哪张英文词卡和「${meaning}」是一组？`, word, others.map(entry => entry[0]), tip);
        });
    });

    const preschool = [
        ['常识', '四季', '很多树叶变黄落下，常见于哪个季节？', '秋天', '春天', '夏天', '一年四季都一样', '秋天气温下降，不少落叶树的叶子会变色、掉落。'],
        ['常识', '生活安全', '遇到自己打不开的药瓶，应该怎么做？', '交给大人', '用牙咬开', '随便吃里面的东西', '拿去给朋友吃', '药物应由大人按说明保管和使用。'],
        ['常识', '动物观察', '哪种动物会用翅膀飞，也会在花间采蜜？', '蜜蜂', '蜗牛', '蚯蚓', '金鱼', '蜜蜂会飞，常在花间采集花蜜和花粉。'],
        ['常识', '卫生习惯', '玩具掉进泥地，拿起来后应该怎么做？', '清洁玩具并洗手', '直接放进嘴里', '擦在脸上', '放进饭碗里', '泥土容易弄脏玩具和手，清洗后再玩。'],
        ['常识', '饮食习惯', '口渴了，平时可以先喝什么？', '白开水', '油', '盐水', '酱油', '白开水可以补充水分。'],
        ['常识', '天气观察', '下雪天路面变滑，走路应该怎样？', '放慢脚步', '闭眼跑', '跳着冲下坡', '用力推别人', '放慢脚步能更容易保持身体平衡。'],
        ['数学', '数的比较', '5 颗珠子和 3 颗珠子相比，多几颗？', '2', '1', '3', '4', '一一配对后，5 颗的那组会多出 2 颗。'],
        ['数学', '图形辨认', '哪种图形没有角？', '圆形', '三角形', '正方形', '长方形', '圆形的边界弯曲，没有拐角。'],
        ['数学', '前后顺序', '排队时，小明排第一，小红排第二。谁在前面？', '小明', '小红', '两人一样靠前', '两人都在最后', '第一位比第二位更靠前。'],
        ['数学', '规律', '大、小、大、小、大，下一项是什么？', '小', '大', '方', '圆', '「大、小」这两个大小交替出现。'],
        ['数学', '简单减法', '盘里有 5 个橙子，拿走 1 个，还剩几个？', '4', '2', '3', '5', '从 5 个橙子里拿掉 1 个，剩 4 个。'],
        ['数学', '数序', '数字 7 的后面紧挨着哪个数？', '8', '6', '9', '5', '按从小到大的顺序，7 后面是 8。'],
        ['语文', '礼貌用语', '向别人借画笔，可以怎么开口？', '请问可以借我一支画笔吗', '快把笔给我', '我直接拿走了', '我不要还给你', '用「请问」提出请求，再等对方同意。'],
        ['语文', '词语理解', '「轻轻」通常说的是怎样的动作？', '用力很小', '用力很大', '动作很快', '叫得很响', '轻轻表示动作柔和，用力小。'],
        ['语文', '词语理解', '「湿」和哪一个词的意思相对？', '干', '红', '高', '甜', '东西有水叫湿，没有水分或水分很少叫干。'],
        ['英语', '日常问候', '英语 Thanks 表示什么？', '谢谢', '再见', '早安', '请进', 'Thanks 是道谢时常用的表达。'],
        ['英语', '数字辨认', '英语 five 表示数字几？', '5', '3', '4', '6', 'four 是四，five 是五，six 是六。'],
        ['英语', '身体词汇', '英语 hand 指身体的哪个部位？', '手', '头', '脚', '眼睛', 'hand 表示手。'],
        ['英语', '动物词汇', '英语 bird 指哪一类动物？', '鸟', '鱼', '猫', '狗', 'bird 表示鸟。']
    ];
    for (const [subject, topic, stem, answer, first, second, third, tip] of preschool) {
        add(0, 0, subject, topic, stem, answer, [first, second, third], tip);
    }
};
