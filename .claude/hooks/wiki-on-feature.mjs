// PostToolUse hook (Bash, filtered on `gh pr create`): once a feature PR is opened,
// asks the main session to run the wiki-writer agent in the background.
// Prints nothing (no-op) when the command was not a successful `gh pr create`.
import { execSync } from "node:child_process";

let raw = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (c) => (raw += c));
process.stdin.on("end", () => {
  let input;
  try { input = JSON.parse(raw); } catch { return; }

  // Only a real `gh pr create` invocation counts: some segment of the command line
  // (split on && || ; |) must start with it. Text like `echo "gh pr create"` does not.
  const cmd = input?.tool_input?.command ?? "";
  const segments = cmd.split(/&&|\|\||;|\||\n/).map((s) => s.trim());
  if (!segments.some((s) => /^gh\s+pr\s+create\b/.test(s))) return;

  // gh prints the new PR URL alone on a line of stdout.
  const resp = input?.tool_response;
  const stdout = typeof resp === "string" ? resp : (resp?.stdout ?? "");
  const pr = stdout.match(/^https:\/\/github\.com\/[^\s/]+\/[^\s/]+\/pull\/(\d+)\s*$/m);
  if (!pr) return; // PR creation failed or was not printed

  let branch = "";
  try { branch = execSync("git branch --show-current", { encoding: "utf8" }).trim(); } catch {}

  // Nothing player-facing to document if the PR touches no plugin code or shipped config.
  const base = (cmd.match(/--base[=\s]+["']?([\w./-]+)/) ?? [])[1] ?? "main";
  try {
    const changed = execSync(`git diff --name-only origin/${base}...HEAD`, { encoding: "utf8" });
    if (!changed.split("\n").some((f) => f.startsWith("src/main/"))) return;
  } catch {} // unknown base: let the agent decide

  const context =
    `Feature finished: PR #${pr[1]} (${pr[0]}) was opened from branch '${branch}'. ` +
    `Per CLAUDE.md (Open Question 12), now launch the 'wiki-writer' agent in the background ` +
    `(Agent tool, subagent_type "wiki-writer") with the prompt: "Update WIKI.md and WIKI-FR.md ` +
    `for PR #${pr[1]} (branch ${branch}, diff: origin/${base}...HEAD)." ` +
    `When it reports back, review its edits, commit them on '${branch}' as ` +
    `"docs(wiki): <feature>", push, and tell the user what changed.`;

  process.stdout.write(JSON.stringify({
    hookSpecificOutput: { hookEventName: "PostToolUse", additionalContext: context },
  }));
});
