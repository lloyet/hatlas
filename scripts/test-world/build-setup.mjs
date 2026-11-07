#!/usr/bin/env node
// Generates src/test/ingame/_setup/build-baseline.json (a VitaminMCP scenario) from
// src/test/ingame/fixtures.json. The scenario turns the raw test map into the test-world
// baseline: safe zones, the ore zone, the shop, every NPC, the structure gallery and all
// donjons. No dependencies. Usage: node scripts/test-world/build-setup.mjs
//
// Before running the scenario the structure files must be installed in the world
// (scripts/test-server.ps1 install-structures). After it: scripts/test-server.ps1 baseline.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const fx = JSON.parse(readFileSync(join(root, 'src/test/ingame/fixtures.json'), 'utf8'));
const BOT = 'Builder';
const steps = [];
const con = (command) => steps.push({ action: 'console', command });
const as = (command) => con(`execute as ${BOT} run ${command}`);
const tp = (x, y, z, rot = '') => con(`tp ${BOT} ${x + 0.5} ${y} ${z + 0.5}${rot ? ' ' + rot : ''}`);
const tpSurface = (x, z) =>
  con(`execute in minecraft:overworld positioned ${x + 0.5} 0 ${z + 0.5} positioned over motion_blocking_no_leaves run tp ${BOT} ~ ~ ~`);
const chunksOf = ({ from, to }) => {
  const out = [];
  for (let cx = Math.min(from[0], to[0]); cx <= Math.max(from[0], to[0]); cx++)
    for (let cz = Math.min(from[1], to[1]); cz <= Math.max(from[1], to[1]); cz++) out.push([cx, cz]);
  return out;
};
const ticks = (count) => steps.push({ action: 'wait_for', condition: 'ticks', count });

// ── Bot and world rules ─────────────────────────────────────────────────────
steps.push({ action: 'spawn', bot: BOT });
con(`op ${BOT}`);
con(`gamemode creative ${BOT}`);
// Calm, stable test world. Both the pre-1.21.11 and the 1.21.11 rule names are sent;
// the unknown one just fails.
for (const [oldName, newName, value] of [
  ['doMobSpawning', 'spawn_mobs', false], ['doDaylightCycle', 'advance_time', false],
  ['doWeatherCycle', 'advance_weather', false], ['doInsomnia', 'spawn_phantoms', false],
  ['doPatrolSpawning', 'spawn_patrols', false], ['doTraderSpawning', 'spawn_wandering_traders', false],
]) { con(`gamerule ${oldName} ${value}`); con(`gamerule ${newName} ${value}`); }
con('time set day');
con('weather clear');
for (const mob of ['zombie', 'skeleton', 'creeper', 'spider', 'cave_spider', 'enderman', 'witch', 'slime',
  'phantom', 'drowned', 'husk', 'stray', 'bogged', 'pillager', 'zombie_villager', 'wandering_trader'])
  con(`kill @e[type=minecraft:${mob}]`);
con(`setworldspawn ${fx.spawn.x} ${fx.spawn.y} ${fx.spawn.z}`);

// ── Structure gallery (/place template; files installed by install-structures) ──
const gallery = Object.values(fx.gallery);
for (const g of gallery) {
  const [ox, , oz] = g.origin;
  con(`forceload add ${ox} ${oz} ${ox + g.size[0] - 1} ${oz + g.size[2] - 1}`);
}
ticks(100);
for (const g of gallery) {
  const [ox, oy, oz] = g.origin;
  con(`place template ${g.structure} ${ox} ${oy} ${oz}`);
}

// ── Safe zones ──────────────────────────────────────────────────────────────
for (const [name, z] of Object.entries(fx.safezone)) {
  as(`safezone create ${name}`);
  for (const [cx, cz] of chunksOf(z.chunks)) {
    tp(cx * 16 + 8, 150, cz * 16 + 8);
    as(`safezone claim ${name}`);
  }
  tp(z.spawn_point.x, z.spawn_point.y, z.spawn_point.z);
  as(`safezone setspawn ${name}`);
  if (z.description) as(`safezone describe ${name} ${z.description}`);
}

// ── Ore zones ───────────────────────────────────────────────────────────────
for (const [name, o] of Object.entries(fx.orezone)) {
  tp(o.min[0], o.max[1] + 2, o.min[2]);
  as(`orezone pos1 ${o.min.join(' ')}`);
  as(`orezone pos2 ${o.max.join(' ')}`);
  as(`orezone add ${name}`);
  as(`orezone reset ${name}`);
}

// ── Shops ───────────────────────────────────────────────────────────────────
for (const [name, s] of Object.entries(fx.shop)) {
  tp(s.x, s.y, s.z);
  as(`shop create ${name} ${s.job.toLowerCase()}`);
  as(`shop reset ${name}`);
}

// ── NPCs ────────────────────────────────────────────────────────────────────
const npcCommand = (n) => ({
  job: `job npc spawn ${n.job}`,
  smuggler: 'donjon npc summon smuggler',
  explorer: 'safezone npc summon',
  auctioneer: 'auction npc spawn',
}[n.kind]);
for (const [name, n] of Object.entries(fx.npc)) {
  const cmd = npcCommand(n);
  if (!cmd) throw new Error(`npc ${name}: unknown kind ${n.kind}`);
  if (n.surface) tpSurface(n.x, n.z); else tp(n.x, n.y, n.z);
  as(cmd);
}

// ── Donjons (map + gallery) ─────────────────────────────────────────────────
for (const [name, d] of Object.entries(fx.donjon)) {
  const [vx, vy, vz] = d.vault;
  for (const [cx, cz] of chunksOf(d.chunks)) con(`forceload add ${cx * 16} ${cz * 16}`);
  ticks(40);
  tp(vx, vy + 1, vz);
  as(`donjon create ${d.type} ${name}`);     // claims the vault's chunk
  for (const [cx, cz] of chunksOf(d.chunks)) {
    tp(cx * 16 + 8, vy + 1, cz * 16 + 8);
    as(`donjon claim ${name}`);
  }
  as(`donjon set vault ${name} ${vx} ${vy} ${vz}`);
  const [tx, ty, tz] = d.teleport;
  tp(tx, ty, tz);
  as(`donjon set spawn ${name}`);
  for (const [wx, wy, wz] of d.wave_spawns) {
    tp(wx, wy, wz);
    as(`donjon wavespawn add ${name}`);
  }
}

// ── Done: leave the world clean for the baseline save ────────────────────────
tp(fx.spawn.x, fx.spawn.y, fx.spawn.z);
con(`gamemode survival ${BOT}`);
con(`deop ${BOT}`);
con('forceload remove all');
con('save-all flush');
steps.push({ action: 'despawn', bot: BOT });

const out = join(root, 'src/test/ingame/_setup/build-baseline.json');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, '[\n' + steps.map((s) => '  ' + JSON.stringify(s)).join(',\n') + '\n]\n');
console.log(`wrote ${out} (${steps.length} steps)`);
