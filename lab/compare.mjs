// Compares the final per-entity state recorded by two stress runs, field by field.
// Fields that already disagree between two runs of the same mode are animation noise.
// usage: node compare.mjs <tagA> <tagB> [noiseTagA noiseTagB]
import { readFileSync } from 'node:fs';

const load = tag => Object.values(JSON.parse(readFileSync(`stress-${tag}-state.json`, 'utf8'))).map(s => JSON.parse(s));

function fields(states) {
    const out = new Map();
    const add = (field, value) => {
        if (!out.has(field)) out.set(field, new Map());
        const bag = out.get(field);
        const key = JSON.stringify(value);
        bag.set(key, (bag.get(key) || 0) + 1);
    };
    for (const s of states) {
        for (const [k, v] of Object.entries(s.meta || {})) add(`meta.${k}`, v);
        for (const k of ['head', 'vel', 'sync', 'move', 'rot', 'equip', 'attrs']) if (s[k] !== undefined) add(k, s[k]);
    }
    return out;
}

function agreement(a, b) {
    const perField = {};
    for (const field of new Set([...a.keys(), ...b.keys()])) {
        const left = a.get(field) || new Map();
        const right = b.get(field) || new Map();
        let total = 0;
        let same = 0;
        for (const [value, n] of left) {
            total += n;
            same += Math.min(n, right.get(value) || 0);
        }
        perField[field] = [same, total];
    }
    return perField;
}

const [a, b, na, nb] = process.argv.slice(2);
const main = agreement(fields(load(a)), fields(load(b)));
const noise = na && nb ? agreement(fields(load(na)), fields(load(nb))) : {};
const show = x => (x ? `${x[0]}/${x[1]}` : '-');

console.log('field'.padEnd(10), `${a} vs ${b}`.padEnd(24), na ? `${na} vs ${nb}` : '');
for (const field of Object.keys(main).sort()) {
    console.log(field.padEnd(10), show(main[field]).padEnd(24), show(noise[field]));
}
