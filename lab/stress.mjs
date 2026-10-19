// Joins N mineflayer bots, opens a UltimateUI page for each and counts what the server sends.
// usage: node stress.mjs <bots> <seconds> <page> <tag>
import mineflayer from 'mineflayer';
import { appendFileSync, writeFileSync } from 'node:fs';

const COUNT = parseInt(process.argv[2] || '15');
const DURATION = parseInt(process.argv[3] || '40');
const PAGE = process.argv[4] || 'hub';
const TAG = process.argv[5] || 'test';
const FAKE_MIN = 2_000_000_000;
const WATCHED_BOTS = 3;

const bots = [];
const counts = {};
const states = new Map();
let totalPackets = 0;
let totalBytes = 0;

function track(bot, type, data) {
    const eid = data.entityId;
    if (typeof eid !== 'number' || eid < FAKE_MIN) return;
    const key = `${bot}:${eid}`;
    const s = states.get(key) || { meta: {}, move: [0, 0, 0] };
    switch (type) {
        case 'entity_metadata':
            for (const m of data.metadata) s.meta[m.key] = m.value;
            break;
        case 'entity_head_rotation': s.head = data.headYaw; break;
        case 'entity_velocity': s.vel = [data.velocityX, data.velocityY, data.velocityZ]; break;
        case 'sync_entity_position':
            s.sync = [data.x, data.y, data.z, data.yaw, data.pitch];
            s.move = [0, 0, 0];
            break;
        case 'rel_entity_move':
        case 'entity_move_look':
            s.move = [s.move[0] + data.dX, s.move[1] + data.dY, s.move[2] + data.dZ];
            if (data.yaw !== undefined) s.rot = [data.yaw, data.pitch];
            break;
        case 'entity_look': s.rot = [data.yaw, data.pitch]; break;
        case 'entity_equipment': s.equip = data.equipments; break;
        case 'entity_update_attributes': s.attrs = data.properties; break;
        case 'spawn_entity': states.delete(key); return;
        default: return;
    }
    states.set(key, s);
}

function stateDigest() {
    const replacer = (_, v) => (typeof v === 'bigint' ? v.toString() : v);
    const out = {};
    for (const [k, v] of [...states.entries()].sort()) out[k] = JSON.stringify(v, replacer);
    return out;
}

function join(i) {
    return new Promise(resolve => {
        const name = `Stress${String(i).padStart(2, '0')}`;
        const bot = mineflayer.createBot({ host: '127.0.0.1', port: 22222, username: name, version: '1.21.4' });
        const watched = bots.length < WATCHED_BOTS;

        bot._client.on('packet', (data, meta) => {
            counts[meta.name] = (counts[meta.name] || 0) + 1;
            totalPackets++;
            if (data) totalBytes += JSON.stringify(data).length;
            if (watched && data) track(name, meta.name, data);
        });
        bot.on('error', () => {});
        bot.once('spawn', () => {
            bots.push(bot);
            console.log(`[${bots.length}/${COUNT}] ${name} joined`);
            resolve(bot);
        });
        setTimeout(() => resolve(null), 12000);
    });
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function run() {
    console.log(`stress: ${COUNT} bots, ${DURATION}s, page=${PAGE}, tag=${TAG}`);
    for (let i = 1; i <= COUNT; i++) {
        await join(i);
        await sleep(600);
    }
    await sleep(2000);

    for (const b of bots) {
        appendFileSync('commands.txt', `uui open ${PAGE} ${b.username}\n`);
        await sleep(150);
    }

    console.log(`${bots.length} bots connected, measuring ${DURATION}s`);
    totalPackets = 0;
    totalBytes = 0;
    for (const k of Object.keys(counts)) counts[k] = 0;
    states.clear();

    await sleep(DURATION * 1000);

    const rate = n => (n / DURATION).toFixed(1);
    const perBot = n => (bots.length ? (n / bots.length).toFixed(0) : '?');
    const lines = [
        `result [${TAG}]: ${bots.length} bots, ${DURATION}s`,
        `packets: ${totalPackets} (${rate(totalPackets)}/s, ${perBot(totalPackets)}/bot)`,
        `decoded bytes: ${totalBytes} (${rate(totalBytes)}/s)`,
        '',
        'packet                       count    /s     /bot',
        '-'.repeat(52),
    ];
    for (const [name, n] of Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 15)) {
        lines.push(`${name.padEnd(26)} ${String(n).padStart(7)} ${rate(n).padStart(6)} ${perBot(n).padStart(7)}`);
    }
    const report = lines.join('\n');
    console.log(report);
    writeFileSync(`stress-${TAG}.txt`, report);
    writeFileSync(`stress-${TAG}-state.json`, JSON.stringify(stateDigest(), null, 1));
    console.log(`tracked fake entities: ${states.size}`);

    for (const b of bots) b.quit();
    setTimeout(() => process.exit(0), 2000);
}

run().catch(e => {
    console.error(e);
    process.exit(1);
});
