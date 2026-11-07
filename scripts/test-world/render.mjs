#!/usr/bin/env node
// Prints a scenario with its {{path}} placeholders replaced from src/test/ingame/fixtures.json.
//   node scripts/test-world/render.mjs src/test/ingame/shop/vault-payment.json
// {{npc.job_miner.x}} -> a number; {{donjon.dark_forest.vault}} (array) -> "x y z";
// {{spawn}} (object with x/y/z) -> "x y z". An unknown path is an error (exit 1).
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const fx = JSON.parse(readFileSync(join(root, 'src/test/ingame/fixtures.json'), 'utf8'));
const file = process.argv[2];
if (!file) { console.error('usage: render.mjs <scenario.json>'); process.exit(2); }

const text = readFileSync(file, 'utf8').replace(/\{\{\s*([\w.]+)\s*\}\}/g, (_, path) => {
  let v = fx;
  for (const part of path.split('.')) {
    if (v == null || !(part in v)) { console.error(`unknown fixture: ${path} (in ${file})`); process.exit(1); }
    v = v[part];
  }
  if (Array.isArray(v)) return v.join(' ');
  if (v && typeof v === 'object' && 'x' in v && 'z' in v) return [v.x, v.y, v.z].filter((n) => n !== undefined).join(' ');
  if (typeof v === 'object') { console.error(`fixture ${path} is not a value (in ${file})`); process.exit(1); }
  return String(v);
});
JSON.parse(text); // must still be valid JSON
process.stdout.write(text);
