# state.edn - Application State Debugging Guide

## What is state.edn?

`state.edn` is a **live snapshot** of the entire application state, automatically updated every time you interact with bd-viewer. It's like Redux DevTools or re-frame tracing - instant visibility into what's happening inside your app.

## Why This is Powerful

Instead of adding `println` statements or stopping at breakpoints, you can:
- **See exactly what's in memory** at any moment
- **Trace how state changes** over time
- **Debug issues** by comparing state before/after actions
- **Verify data loading** from `bd list --json`
- **Understand the flow** of events through the system

## State Structure

```clojure
{:issues [...]           ; Vector of all loaded beads issues (wrapped in ClosedRecord)
 :selected-issue "..."   ; ID of currently selected issue (string or nil)
 :selected-index N       ; Index in filtered list for j/k navigation (-1 = none)
 :filter-text "..."      ; Current search filter text
 :sort-by :priority}     ; Sort criterion (:priority | :created-at | :updated-at | :status)
```

**Note**: `:ui-refs` is removed from the dump because Swing objects can't be serialized.

## Reading the Current State

### Current State Snapshot (from your session)

```clojure
{:issues
 [{:id "bd-viewer-4"
   :title "Implement db.clj - State management with specs"
   :priority 0
   :status "closed"
   :description "Core state management:..."}

  {:id "bd-viewer-3"
   :title "Setup ClosedRecord for type-safe state access"
   :priority 0
   :status "closed"
   :description "Integrate ClosedRecord from slack-retriever:..."}

  ;; ... 10 more issues
  ]

 :selected-issue "bd-viewer-1"
 :selected-index 3
 :filter-text ""
 :sort-by :priority}
```

### What This Tells Us

1. **12 issues loaded** - The app successfully loaded all beads issues
2. **bd-viewer-1 is selected** - User clicked on the planning issue (4th in list, index 3)
3. **No active filter** - Empty filter-text means all issues are visible
4. **Sorted by priority** - Issues appear in priority order (P0, P1, P2, P3)
5. **ClosedRecord working** - All issues have proper structure with typed fields

## Common Debugging Scenarios

### Scenario 1: "Why isn't my issue showing?"

**Check state.edn:**
```clojure
:issues [...]  ; Is the issue in this list?
:filter-text "some text"  ; Is it being filtered out?
```

**Solution**: If issue is in `:issues` but not visible, check if `:filter-text` is filtering it out.

### Scenario 2: "Selection isn't working"

**Check state.edn:**
```clojure
:selected-issue "bd-viewer-5"  ; What's actually selected?
:selected-index 2              ; What index in filtered list?
```

**Solution**: If `:selected-issue` is nil but you clicked something, the event handler isn't firing. If it's set but UI doesn't update, the effects watcher isn't working.

### Scenario 3: "j/k navigation is broken"

**Check state.edn after pressing j:**
```clojure
:selected-index 3  ; Did it increment?
:selected-issue "bd-viewer-8"  ; Did it change to next issue?
```

**Solution**: If index doesn't change, the keyboard handler isn't firing. If index changes but selection doesn't, the index→issue mapping is broken.

### Scenario 4: "Search isn't filtering"

**Type "UI" in search, then check state.edn:**
```clojure
:filter-text "UI"  ; Did the text update?
:issues [...]      ; Are there fewer issues matching "UI"?
```

**Solution**: If `:filter-text` updated but `:issues` didn't shrink, the filter function isn't working. If neither changed, the search event isn't firing.

### Scenario 5: "Delete button does nothing"

**Click delete, then check state.edn:**
```clojure
:issues [...]  ; Did the issue disappear from the list?
:selected-issue nil  ; Was selection cleared?
```

**Solution**: If issue is still in `:issues`, the delete CLI command failed or the event handler didn't remove it. Check terminal output for `bd delete` errors.

## How State Updates Flow

1. **User Action** (click, type, press key)
   ↓
2. **Event Handler** fires (in `events.clj`)
   ↓
3. **State Atom** updated via `swap!`
   ↓
4. **Watcher Fires** (in `core.clj`)
   ↓
5. **state.edn Dumped** (you can see the change!)
   ↓
6. **UI Watchers Fire** (in `effects/swing.clj`)
   ↓
7. **UI Updates** (you see the change on screen)

## Example: Tracing a Click

**You click on "bd-viewer-5" in the list**

1. `state.edn` BEFORE:
```clojure
{:selected-issue "bd-viewer-1"
 :selected-index 3}
```

2. **Event fires**: `::events/issue-selected` with `:issue-id "bd-viewer-5"` `:index 1`

3. `state.edn` AFTER:
```clojure
{:selected-issue "bd-viewer-5"
 :selected-index 1}
```

4. **UI updates**: Detail panel shows bd-viewer-5 info

## Example: Tracing a Search

**You type "keyboard" in search**

1. `state.edn` BEFORE:
```clojure
{:filter-text ""
 :issues [12 items]
 :selected-issue "bd-viewer-1"}
```

2. **Event fires**: `::events/filter-changed` with `:text "keyboard"`

3. `state.edn` AFTER:
```clojure
{:filter-text "keyboard"
 :issues [1 item matching "keyboard"]  ; Filtered!
 :selected-issue nil                   ; Cleared because filter changed
 :selected-index -1}
```

4. **UI updates**: List shows only "bd-viewer-7" (keyboard shortcuts issue)

## Advanced: Comparing State Over Time

### Technique 1: Save snapshots

```bash
# Before action
cp state.edn state-before.edn

# Do action (click, type, etc.)

# After action
cp state.edn state-after.edn

# Compare
diff state-before.edn state-after.edn
```

### Technique 2: Watch live changes

```bash
# In terminal, watch the file update in real-time
watch -n 0.5 'head -50 state.edn'
```

### Technique 3: Extract specific field

```bash
# See just the selected issue
cat state.edn | grep -A1 ":selected-issue"

# See just the filter text
cat state.edn | grep ":filter-text"

# Count how many issues are loaded
cat state.edn | grep ":id " | wc -l
```

## Understanding ClosedRecord in State

Issues in `:issues` are **ClosedRecord-wrapped**, which means:

```clojure
;; This works (valid key)
(:title issue)  ;=> "Implement db.clj..."

;; This THROWS (typo in key name)
(:titel issue)  ;=> ExceptionInfo: INVALID KEY ACCESS
```

**In state.edn**, you'll see the raw data:
```clojure
{:id "bd-viewer-4"
 :title "Implement db.clj - State management with specs"
 :description "..."
 :status "closed"
 :priority 0
 :issue-type "feature"
 :created-at "2025-11-08T14:32:20.176409-08:00"
 :updated-at "2025-11-08T14:32:20.176409-08:00"}
```

If you see unexpected fields or missing fields, ClosedRecord will catch typos at runtime!

## When to Check state.edn

- **After loading**: Verify all issues loaded correctly
- **After clicking**: Confirm selection changed
- **After typing**: Verify filter text updated
- **After delete**: Confirm issue removed from `:issues`
- **After reload**: Check if new issues appeared
- **When debugging**: Any time behavior seems wrong!

## Pro Tips

1. **Keep state.edn open** in your editor while using the app - watch it update in real-time
2. **Check the diff** - If something's wrong, compare before/after to see what changed (or didn't)
3. **Verify events fired** - If state didn't change, the event handler didn't run
4. **Count issues** - Quick sanity check: does `:issues` count match what you expect?
5. **Inspect ClosedRecord fields** - Make sure you're using the exact field names from the schema

## State File Location

- **Path**: `./state.edn` (in project root, next to Makefile)
- **Updated**: Automatically on every state change
- **Gitignored**: Yes (ephemeral debugging data, not committed)

## What's NOT in state.edn

- `:ui-refs` - Removed because Swing objects can't be serialized
  - References to JFrame, JList, JLabels, etc. are in memory but not dumped

To inspect UI widgets, use the REPL:
```clojure
(-> @bd-viewer.db/*app-state :ui-refs :issue-list)
;=> #<JList ...>
```

## Summary

**state.edn is your debugging superpower!**

- ✅ Instant visibility into app state
- ✅ No print statements needed
- ✅ Trace state changes over time
- ✅ Verify events fired correctly
- ✅ Confirm data loaded properly
- ✅ Debug issues faster

**Remember**: Every interaction updates state.edn. If you don't see a change, the event didn't fire!
