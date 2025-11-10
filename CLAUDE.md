# bd-viewer Project

A Swing-based UI for viewing and managing beads issues, inspired by the functional Swing patterns from the mailmerge project.

## Using Beads in This Project

### Basic Commands

```bash
# List all issues
bd list

# List with JSON output
bd list --json

# Create a new issue
bd create --title "Issue title" --description "Description"

# Show issue details
bd show <issue-id>

# Update an issue
bd update <issue-id> --status in-progress

# Delete an issue
bd delete <issue-id>
```

### Filtering Issues

```bash
# By status
bd list --status open
bd list --status closed
bd list --status in-progress

# By priority (1-5, where 1 is highest)
bd list --priority 1

# By assignee
bd list --assignee username

# By type
bd list --type bug
bd list --type feature

# Limit results
bd list --limit 10
```

### Common Workflows

1. **Planning a feature**: Create issue → break into subtasks → track progress
2. **Bug tracking**: Create bug issue → assign → update status as you fix
3. **Code review**: Create issues for each review item → close as addressed

### Database Location

- Project database: `.beads/bd-viewer.db`
- Source of truth: `.beads/issues.jsonl` (committed to git)
- Issues are named: `bd-viewer-1`, `bd-viewer-2`, etc.

### Integration with Development

- Use `bd list --json` to load issues into the UI
- The UI will watch for changes to `.beads/issues.jsonl`
- Changes made in the UI will sync back through the beads CLI

## Development Philosophy

### FAST FEEDBACK IS CRITICAL!

**Principle**: Get the app running as soon as possible, then iterate.

- Run `make run` at the earliest opportunity to see it work
- Don't wait for all features - minimal working version first!
- Test frequently with actual UI, not just compilation checks
- Use hot reload to iterate without restarting

### Development Workflow

1. **Make changes** to source files
2. **Test immediately**: `make run` or use reload buttons in UI
3. **Iterate fast**: See results, make adjustments, repeat
4. Use `make runtests-once` for compilation checks
5. Use clojure-mcp for all .clj and .edn file edits

### CRITICAL: Seesaw Function Bugs

**⚠️  DO NOT USE `seesaw.core/invoke-later` or `seesaw.invoke/invoke-later`!**

Seesaw's `invoke-later` has a critical bug where **lambda functions don't execute**. This will silently break your code:
- Detail panels won't update
- Hot reload won't work
- Any EDT operations in lambdas will be silently ignored

**✅ ALWAYS use `swing-fx.core/invoke-later` instead!**

This applies to ALL code in this project. See `swing-fx/src/swing_fx/core.clj` for the full list of broken Seesaw functions and their working replacements.

**Why this matters:**
- We discovered this bug twice: first when detail panels stopped updating, then when hot reload broke
- The bug is silent - no errors, just code that doesn't run
- swing-fx provides fixed wrappers that use Java Swing APIs directly

### Debugging

- **Logs**: All output from `make run` is saved to `./00LOGS.txt` (automatically via `tee`)
- **State dump**: Current app state is saved to `./state.edn` on every state change
- **Check logs**: `cat 00LOGS.txt` or `tail -f 00LOGS.txt` to watch logs in real-time
- **Check state**: `cat state.edn` to see current app state (issues, selection, filters, etc.)

### Reload Features

- **Cmd+R (Reload Config)**: Refresh issue list from `bd list --json` + rebuild graph tabs
- **Cmd+Shift+R (Full UI Fresh)**: Complete refresh - reload ALL code + data + rebuild entire UI
  - Reloads all namespaces with fresh code
  - Rebuilds entire UI from scratch with new view functions
  - Reloads all data from `bd list --json`
  - Rebuilds all graph tabs with fresh diagrams
  - Triggers all watchers to repopulate UI with fresh data
  - Validates selected issue still exists (clears if deleted externally)
  - **Use this when you make code changes and want to see them immediately!**
- State persists across hot reloads (using `defonce`)

### Keyboard Shortcuts

**Navigation:**
- `j` - Next issue (context-aware: tree order in Tree View, numeric in other tabs)
- `k` - Previous issue (context-aware: tree order in Tree View, numeric in other tabs)
- `o` - Toggle open/all issues filter
- `Escape` - Clear search filter

**Actions:**
- `c` - Close current issue (sets status to "closed")
- `Cmd+D` - Delete current issue

**Reload:**
- `Cmd+R` - Reload data from `bd list --json`
- `Cmd+Shift+R` - Full UI fresh (code + data + complete UI rebuild)
