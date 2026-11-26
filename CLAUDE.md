<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

The graph MCP server's own instructions say when its tools beat search: relationship questions
(callers, importers, tests, impact, diffs). Use Grep for plain text.

### Verify in the source

- Do not change code from graph output alone: for any non-trivial change,
  read the implementation and the relevant tests before concluding.
- Verify the exact source when touching behavior, database logic, migrations, retries, fallbacks,
  recovery, or compatibility code.
- When the graph and the source disagree, the source wins. The graph may be stale.
- An empty graph result can mean "not indexed" or "not statically visible", not "does not exist".
<!-- /code-review-graph MCP tools -->

## Agent skills

### Issue tracker

GitHub Issues on AppThere/photothere (`gh -R AppThere/photothere`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
