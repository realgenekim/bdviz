# bd-viewer Project Plan

## Project Overview

Create a Swing-based UI for viewing and managing beads issues, following the functional programming patterns from the mailmerge project.

## Goals

1. **View Issues**: Display all beads issues in a searchable list
2. **Issue Details**: Show complete issue information in a detail panel
3. **CRUD Operations**: Create, Read, Update, Delete issues
4. **Keyboard Navigation**: Efficient keyboard shortcuts for all operations
5. **Hot Reload**: Support code and config reloading without losing state
6. **Functional Architecture**: Clean separation of state, events, and effects

## UI Design

```
┌────────────────────────────────────────────────────────────────┐
│  Search: [_________________]  [New (⌘N)] [Delete (⌘D)] [⌘R]    │
├─────────────────┬──────────────────────────────────────────────┤
│ bd-viewer-1  P0 │ Title: Create UI architecture plan...        │
│ bd-viewer-2  P1 │                                              │
│ bd-viewer-3  P2 │ Description:                                 │
│                 │ Design the overall architecture for          │
│                 │ bd-viewer following mailmerge patterns...    │
│                 │                                              │
│                 │ Status: ○ open     Priority: P0              │
│                 │ Type: feature      Labels: planning, arch    │
│                 │                                              │
│                 │ Created:  2025-11-08 14:26:51               │
│                 │ Updated:  2025-11-08 14:26:51               │
│                 │                                              │
│                 │ [Edit] [Close] [Reopen]                     │
└─────────────────┴──────────────────────────────────────────────┘
```

## Architecture Components

### 1. State Management (db.clj)
- Single atom holding all application state
- Load issues via `bd list --json`
- Store UI widget references
- Persist across hot reloads with `defonce`

### 2. Event System (events.clj)
- Multimethod dispatch based on `:event/type`
- Pure state transformations
- No Swing imports
- Integration with bd CLI for persistence

### 3. Effects System (effects/swing.clj)
- All Swing mutations isolated here
- Watchers trigger UI updates
- EDT-safe using `SwingUtilities/invokeLater`
- Diff old/new state to minimize updates

### 4. UI Components (ui.clj)
- Widget creation functions
- Layout management
- Event wiring
- Store widget references in state

### 5. Keyboard Shortcuts (keyboard.clj)
- InputMap/ActionMap pattern
- Global shortcuts (Cmd+N, Cmd+D, Delete, Cmd+R)
- Dispatch to event system

### 6. Hot Reload (reload.clj)
- Reload code: `require :reload` + rebuild UI
- Reload config: re-run `bd list --json`
- Use `resolve` for reloaded functions

## Implementation Phases

### Phase 1: Project Setup ✓
- [x] Initialize beads database (`bd init`)
- [x] Create .claude.md with usage instructions
- [x] Create planning issue in beads
- [x] Document functional Swing architecture

### Phase 2: Infrastructure
- [ ] Copy Makefile from mailmerge
- [ ] Copy and adapt deps.edn structure
- [ ] Setup project structure (src/bd_viewer/...)
- [ ] Configure MCP integration
- [ ] Create initial namespace files

### Phase 3: Core State & Events
- [ ] Implement db.clj
  - [ ] State atom structure
  - [ ] `load-issues-from-bd` function
  - [ ] `init-state!` function
- [ ] Implement events.clj
  - [ ] Event multimethod
  - [ ] `::issue-selected`
  - [ ] `::filter-changed`
  - [ ] `::reload-issues`
  - [ ] `::delete-issue`
  - [ ] `::new-issue`
  - [ ] `::update-issue`

### Phase 4: UI Components
- [ ] Implement ui.clj
  - [ ] `create-issue-list` - left panel with JList
  - [ ] `create-detail-panel` - right panel with issue details
  - [ ] `create-search-bar` - top filter field
  - [ ] `create-toolbar` - buttons (New, Delete, Reload)
  - [ ] `create-main-frame` - assemble layout
- [ ] Implement list rendering
  - [ ] Custom ListCellRenderer for issue display
  - [ ] Show issue ID and priority
  - [ ] Visual status indicators
- [ ] Implement detail panel
  - [ ] Title display
  - [ ] Description text area
  - [ ] Metadata fields (status, priority, type, labels)
  - [ ] Timestamps
  - [ ] Action buttons

### Phase 5: Effects System
- [ ] Implement effects/swing.clj
  - [ ] `setup-watchers!` - register state watchers
  - [ ] `update-issue-list!` - sync JList with state
  - [ ] `update-selected-issue!` - sync detail panel
  - [ ] `update-filter!` - apply search filter
  - [ ] EDT-safe wrappers

### Phase 6: Keyboard Shortcuts
- [ ] Implement keyboard.clj
  - [ ] Cmd+N - New issue dialog
  - [ ] Cmd+D / Delete - Delete selected issue
  - [ ] Cmd+R - Reload issues from disk
  - [ ] Cmd+Shift+R - Reload code
  - [ ] Arrow keys - Navigate issue list
  - [ ] Cmd+F - Focus search field

### Phase 7: Dialogs
- [ ] New issue dialog
  - [ ] Title field
  - [ ] Description text area
  - [ ] Priority dropdown
  - [ ] Type dropdown
  - [ ] Labels field
  - [ ] OK/Cancel buttons
- [ ] Edit issue dialog
  - [ ] Pre-populate with current values
  - [ ] Same fields as new issue
- [ ] Delete confirmation dialog

### Phase 8: Advanced Features
- [ ] Filtering
  - [ ] Text search across title/description
  - [ ] Filter by status
  - [ ] Filter by priority
  - [ ] Filter by labels
  - [ ] Combined filters
- [ ] Sorting
  - [ ] By priority
  - [ ] By created date
  - [ ] By updated date
  - [ ] By status
- [ ] File watching
  - [ ] Monitor .beads/issues.jsonl
  - [ ] Auto-reload on external changes
  - [ ] Show notification on reload

### Phase 9: Hot Reload
- [ ] Implement reload.clj
  - [ ] `reload-code!` - require :reload and rebuild UI
  - [ ] `reload-config!` - re-run bd list
  - [ ] Add "Reload Code" button
  - [ ] Add "Reload Config" button
  - [ ] Test hot reload preserves state

### Phase 10: Polish
- [ ] Error handling
  - [ ] Handle bd CLI errors gracefully
  - [ ] Show error dialogs
  - [ ] Validate user input
- [ ] Visual improvements
  - [ ] Better fonts and spacing
  - [ ] Status color coding
  - [ ] Priority badges
  - [ ] Icons for buttons
- [ ] Performance
  - [ ] Lazy loading for large issue lists
  - [ ] Virtual scrolling if needed
  - [ ] Debounce search filter

## File Structure

```
bd-viewer/
├── .beads/
│   ├── bd-viewer.db          # SQLite cache
│   └── issues.jsonl          # Source of truth (git)
├── .claude/
│   └── settings.local.json
├── .claude.md
├── docs/
│   ├── FUNCTIONAL_SWING_ARCHITECTURE.md
│   └── PROJECT_PLAN.md
├── src/
│   └── bd_viewer/
│       ├── core.clj          # Entry point (-main)
│       ├── db.clj            # State management
│       ├── events.clj        # Event handlers
│       ├── ui.clj            # UI components
│       ├── keyboard.clj      # Keyboard shortcuts
│       ├── reload.clj        # Hot reload support
│       └── effects/
│           └── swing.clj     # Swing effects
├── deps.edn
├── Makefile
└── README.md
```

## State Structure

```clojure
{:issues
 [{:id "bd-viewer-1"
   :title "Create UI architecture plan for bd-viewer"
   :description "Design the overall architecture..."
   :status "open"
   :priority 0
   :issue_type "feature"
   :created_at "2025-11-08T14:26:51.10051-08:00"
   :updated_at "2025-11-08T14:26:51.10051-08:00"
   :labels ["architecture" "planning"]}]

 :selected-issue "bd-viewer-1"

 :filter {:text ""
          :status nil      ; nil | "open" | "closed" | "in-progress"
          :priority nil    ; nil | 0 | 1 | 2 | 3 | 4
          :labels []}

 :sort-by :priority         ; :priority | :created | :updated | :status

 :ui-refs {:frame #<JFrame>
           :issue-list #<JList>
           :detail-panel #<JPanel>
           :search-field #<JTextField>
           :title-label #<JLabel>
           :description-area #<JTextArea>}}
```

## Event Types

| Event | Description | State Change |
|-------|-------------|--------------|
| `::issue-selected` | User clicks issue in list | Updates `:selected-issue` |
| `::filter-changed` | User types in search | Updates `:filter/text` |
| `::filter-status` | Filter by status | Updates `:filter/status` |
| `::filter-priority` | Filter by priority | Updates `:filter/priority` |
| `::sort-changed` | Change sort order | Updates `:sort-by` |
| `::reload-issues` | Reload from bd CLI | Replaces `:issues` |
| `::delete-issue` | Delete selected issue | Removes from `:issues`, calls bd CLI |
| `::new-issue` | Create new issue | Adds to `:issues`, calls bd CLI |
| `::update-issue` | Edit existing issue | Updates in `:issues`, calls bd CLI |
| `::reload-code` | Hot reload code | Rebuilds UI |
| `::show-new-issue-dialog` | Show create dialog | No state change |
| `::show-edit-dialog` | Show edit dialog | No state change |

## Integration with bd CLI

### Read Operations
```bash
bd list --json                    # Load all issues
bd show <id> --json               # Get single issue details
```

### Write Operations
```bash
bd create "title" \
  --description "desc" \
  --priority 0 \
  --type feature \
  --labels planning,architecture

bd update <id> \
  --title "new title" \
  --description "new desc" \
  --status in-progress

bd delete <id>
```

### Watching for Changes
Monitor `.beads/issues.jsonl` modification time and auto-reload when external tools modify it.

## Keyboard Shortcuts Summary

| Shortcut | Action |
|----------|--------|
| Cmd+N | New issue |
| Cmd+D | Delete selected issue |
| Delete | Delete selected issue |
| Cmd+R | Reload issues from disk |
| Cmd+Shift+R | Reload code (hot reload) |
| Cmd+F | Focus search field |
| Cmd+E | Edit selected issue |
| ↑/↓ | Navigate issue list |
| Enter | Open selected issue details |
| Escape | Clear search / deselect |

## Success Criteria

- [x] Can view list of all beads issues
- [x] Can select and view issue details
- [x] Can filter issues by text search
- [x] Can create new issues via dialog
- [x] Can delete issues with confirmation
- [x] Can edit existing issues
- [x] Keyboard shortcuts work for all operations
- [x] Hot reload preserves UI state
- [x] Changes persist to .beads/issues.jsonl
- [x] External changes (from bd CLI) are detected
- [x] Clean functional architecture maintained

## Development Workflow

1. Start with `make repl`
2. In REPL: `(require 'bd-viewer.core) (bd-viewer.core/-main)`
3. Make code changes
4. In UI: Click "Reload Code" or press Cmd+Shift+R
5. Test changes immediately
6. Iterate quickly

## Testing Strategy

1. **Manual Testing**: Use the UI to test all features
2. **REPL Testing**: Test event handlers directly in REPL
3. **State Inspection**: Watch state changes with `(add-tap println)`
4. **CLI Integration**: Verify bd CLI commands work correctly
5. **Hot Reload**: Test that reload preserves state

## Next Steps

1. **Immediate**: Copy Makefile and deps.edn from mailmerge
2. **Today**: Implement core state management (db.clj, events.clj)
3. **This Week**: Build basic UI (issue list + detail panel)
4. **Next Week**: Add keyboard shortcuts, dialogs, and polish
