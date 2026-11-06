const crypto = require('node:crypto');

module.exports = function balanceNumericOptions(questions) {
    for (const question of questions) {
        if (question.subject !== '数学' || !question.a.every(value => /^-?\d+(?:\.\d+)?$/.test(value))) continue;
        const values = question.a.map(Number);
        const answer = values[question.k];
        const old = values.filter((_, index) => index !== question.k);

        // Reuse the original distances so the choices stay close to the intended skill.
        const digest = crypto.createHash('sha256').update(question.id).digest();
        const lower = digest[0] % 4;
        const precision = Math.max(...question.a.map(value => (value.split('.')[1] || '').length));
        const distances = old.map(value => Math.abs(value - answer));
        const min = Math.min(0, ...values);
        const max = /填百分号前的数/.test(question.q) ? 100 : Infinity;
        const variants = [];
        for (let mask = 0; mask < 8; mask++) {
            const signs = distances.map((_, index) => mask & (1 << index) ? -1 : 1);
            const options = distances.map((distance, index) => Number((answer + signs[index] * distance).toFixed(precision)));
            if (options.some(value => value < min || value > max || value === answer)) continue;
            if (new Set(options).size !== options.length) continue;
            variants.push({options, lower: signs.filter(sign => sign < 0).length});
        }
        if (!variants.length) continue;
        const nearest = Math.min(...variants.map(candidate => Math.abs(candidate.lower - lower)));
        const candidates = variants.filter(candidate => Math.abs(candidate.lower - lower) === nearest);
        const choices = candidates[digest[1] % candidates.length].options.map(String);
        const replacement = question.a.map((value, index) => index === question.k ? value : choices.shift());
        if (replacement.every((value, index) => value === question.a[index])) continue;
        question.previousOptions = question.a;
        question.a = replacement;
    }
};
