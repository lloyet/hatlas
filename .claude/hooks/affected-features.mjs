// Resolves which in-game test suites a change touches, from src/test/ingame/features.yml.
//
//   node .claude/hooks/affected-features.mjs [--base <branch>]     (default: main)
//
// Prints JSON: { base, head, changed, features, all, uncovered, missingSuites }
//   features      affected feature names (every feature when a shared file changed)
//   uncovered     changed src/main files that match no feature and no shared glob
//   missingSuites affected features that have no src/test/ingame/<feature>/*.json yet
// Changed files = committed diff origin/<base>...HEAD + uncommitted changes.
import { execSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const MAP = "src/test/ingame/features.yml";

/** Parses the restricted YAML of features.yml: `shared:` list and `features:` map of lists. */
export function parseFeatureMap(text) {
  const shared = [];
  const features = {};
  let section = null;
  let current = null;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.replace(/\s+#.*$/, "");
    if (!line.trim() || line.trim().startsWith("#")) continue;
    if (/^shared:\s*$/.test(line)) { section = "shared"; current = null; continue; }
    if (/^features:\s*$/.test(line)) { section = "features"; current = null; continue; }
    const feat = line.match(/^ {2}([\w-]+):\s*$/);
    if (section === "features" && feat) { current = feat[1]; features[current] = []; continue; }
    const item = line.match(/^\s+-\s+(.+?)\s*$/);
    if (!item) continue;
    if (section === "shared") shared.push(item[1]);
    else if (section === "features" && current) features[current].push(item[1]);
  }
  return { shared, features };
}

/** Glob → RegExp: `**` any dirs, `*` no slash, `?` one char. */
export function globToRegExp(glob) {
  let re = "";
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === "*" && glob[i + 1] === "*") {
      if (glob[i + 2] === "/") { re += "(?:.*/)?"; i += 2; } else { re += ".*"; i += 1; }
    } else if (c === "*") re += "[^/]*";
    else if (c === "?") re += "[^/]";
    else re += c.replace(/[.+^${}()|[\]\\]/g, "\\$&");
  }
  return new RegExp("^" + re + "$");
}

const sh = (cmd, cwd) => execSync(cmd, { cwd, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
const lines = (s) => s.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);

export function affectedFeatures(base = "main", root = process.cwd()) {
  const map = parseFeatureMap(readFileSync(join(root, MAP), "utf8"));
  let head = "";
  try { head = sh("git rev-parse HEAD", root).trim(); } catch {}
  const changed = new Set();
  try { lines(sh(`git diff --name-only origin/${base}...HEAD`, root)).forEach((f) => changed.add(f)); } catch {}
  try { lines(sh("git diff --name-only HEAD", root)).forEach((f) => changed.add(f)); } catch {}
  try { lines(sh("git ls-files --others --exclude-standard", root)).forEach((f) => changed.add(f)); } catch {}

  const files = [...changed].sort();
  const match = (globs, f) => globs.some((g) => globToRegExp(g).test(f));
  const all = files.some((f) => match(map.shared, f));
  const features = new Set();
  const uncovered = [];
  for (const f of files) {
    let hit = false;
    for (const [name, globs] of Object.entries(map.features)) {
      if (match(globs, f)) { features.add(name); hit = true; }
    }
    if (!hit && !match(map.shared, f) && f.startsWith("src/main/")) uncovered.push(f);
  }
  const list = all ? Object.keys(map.features) : [...features].sort();
  const missingSuites = list.filter((name) => {
    const dir = join(root, "src/test/ingame", name);
    return !existsSync(dir) || !readdirSync(dir).some((f) => f.endsWith(".json"));
  });
  return { base, head, changed: files, features: list, all, uncovered, missingSuites };
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  const i = process.argv.indexOf("--base");
  const base = i > 0 ? process.argv[i + 1] : "main";
  process.stdout.write(JSON.stringify(affectedFeatures(base), null, 2) + "\n");
}
