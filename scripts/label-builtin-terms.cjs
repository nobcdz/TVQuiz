// Topic placement for the PEP mathematics sequence. Cross-term review stays unlabelled.
const topics = [
    ['认识钟表|数的组成|凑十|20以内加法', '认识数位|整十数加法|凑整十'],
    ['表内乘法|乘法应用|乘加应用|成组计数|长度比较|长度换算|长度单位|时刻计算|两位数加法|两位数减法|百以内减法', '平均分|简单除法|有余数的除法|余数'],
    ['两位数乘一位数|长方形周长|周长推宽|周长|千克和克|质量比较|质量换算|分数初步|分数的意义', '面积|正方形面积|面积应用|天数换算'],
    ['平角与角度|角度比较', '运算律|乘法分配律|乘法结合律|简便计算|小数加法|小数减法|小数进位|小数比较|小数乘十|三角形内角和|角度|平均数'],
    ['小数乘法|小数除法|简易方程|等式的性质|平行四边形面积|梯形面积|三角形面积|组合面积', '因数与倍数|最大公因数|公倍数|长方体体积|长方体表面积|体积|体积与容积|体积应用|分数加法|分数减法|分数比较'],
    ['比|比的应用|按比分配|按比例分配|分数乘法应用|圆周长|圆的半径|百分数应用|百分率', '比例尺|实际与图上距离|圆柱体积|圆柱侧面积|圆柱高|圆锥体积|百分数折扣'],
    ['有理数|有理数乘法|有理数减法|有理数加法|绝对值|相反数|数轴|整式求值|合并同类项|去括号|整式运算|整式化简|一元一次方程|一元一次方程应用|余角|角的平分线', '平面直角坐标系|坐标与对称|方位与对称|二元一次方程组|一元一次不等式|不等式|不等式变形'],
    ['平方差公式|平方差|整式乘法|完全平方公式|完全平方|幂的运算|全等三角形|三角形外角', '二次根式|勾股定理|勾股定理应用|勾股定理逆用|一次函数|一次函数截距|一次函数交点|函数变化量|平行四边形的性质|数据的中位数|数据极差|统计平均数'],
    ['一元二次方程|一元二次方程的根|二次方程根的关系|二次函数|二次函数应用|二次函数顶点|二次函数平移|二次函数求值|圆周角|圆心角|概率|等可能概率|简单概率', '反比例函数|反比例函数求系数|相似三角形|相似三角形面积|相似图形面积|相似图形周长|锐角三角函数']
];
const grades = '一二三四五六七八九';
const terms = topics.map(pair => new Map(pair.flatMap((group, index) =>
    group.split('|').filter(Boolean).map(topic => [topic, index + 1]))));

function firstGradeTerm(question) {
    const numbers = (question.q.match(/\d+/g) || []).map(Number);
    const answer = Number(question.a[question.k]);
    if (['数的顺序', '排队顺序', '逆向数数'].includes(question.topic)) {
        return numbers.length && Math.max(...numbers, answer) <= 10 ? 1 : 0;
    }
    if (numbers.length !== 2) return 0;
    const [first, second] = numbers;
    if (['简单加法应用', '加减法应用'].includes(question.topic)) {
        return first + second === answer && answer <= 20 ? 1 : 0;
    }
    let total, removed;
    switch (question.topic) {
        case '20以内减法':
        case '简单减法应用':
        case '补足数量':
            total = first;
            removed = second;
            break;
        case '比较多少':
            total = Math.max(first, second);
            removed = Math.min(first, second);
            break;
        default:
            return 0;
    }
    if (total - removed !== answer) return 0;
    if (total <= 10) return 1;
    if (total < 20 && removed < 10 && answer < 10) return 2;
    return 0;
}

module.exports = function labelTerms(questions) {
    for (const question of questions) {
        if (question.level === '七年级' && question.subject === '语文' && question.term === 2 &&
            /^“(?:择其善者而从之|知之者不如好之者)”/.test(question.q)) {
            question.previousTerm = 2;
            question.term = 1;
        }
        if (question.term || question.subject !== '数学' || question.level === '幼儿园') continue;
        question.term = terms[grades.indexOf(question.level[0])]?.get(question.topic) ||
            (question.level === '一年级' ? firstGradeTerm(question) : 0);
    }
};
