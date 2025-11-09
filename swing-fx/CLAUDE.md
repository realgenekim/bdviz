# swing-fx

A lightweight library providing reliable Swing utilities for functional UI development.

## Critical: Seesaw Bug Workarounds

**⚠️  NEVER USE THESE SEESAW FUNCTIONS - THEY ARE BROKEN!**

### `seesaw.core/invoke-later` and `seesaw.invoke/invoke-later`

**Bug**: Lambda functions passed to these functions **do not execute**. The code is silently ignored.

**Impact**:
- UI updates fail silently
- Hot reload breaks
- Event handlers don't run
- Any EDT operations in lambdas are lost

**Fix**: Use `swing-fx.core/invoke-later` instead, which uses `SwingUtilities/invokeLater` directly.

```clojure
;; ❌ BROKEN - Lambda won't execute!
(seesaw.core/invoke-later
  (fn []
    (println "This will never print")))

;; ✅ WORKS - Uses SwingUtilities directly
(swing-fx.core/invoke-later
  (fn []
    (println "This prints!")))
```

### `seesaw.core/selection!`

**Bug**: Returns `nil` without actually setting the selection.

**Impact**:
- List selections don't update visually
- Navigation (j/k keys) breaks
- Selection state becomes inconsistent

**Fix**: Use `swing-fx.core/set-selection!` instead, which uses `.setSelectedIndex` directly.

```clojure
;; ❌ BROKEN - Returns nil, selection unchanged
(seesaw.core/selection! my-listbox 5)

;; ✅ WORKS - Uses .setSelectedIndex directly
(swing-fx.core/set-selection! my-listbox 5)
(swing-fx.core/set-selection! my-listbox 5 :scroll true) ; with auto-scroll
```

## Safe Seesaw Functions

These Seesaw functions work correctly and are safe to use:

- `seesaw.core/select` - Find widgets by ID
- `seesaw.core/config!` - Set widget properties
- `seesaw.core/text` - Get text from widgets
- `seesaw.core/text!` - Set text in widgets
- `seesaw.core/listen` - Add event listeners
- `seesaw.core/frame` - Create frames
- `seesaw.core/button` - Create buttons
- `seesaw.core/label` - Create labels
- `seesaw.core/listbox` - Create listboxes
- `seesaw.core/scrollable` - Add scrollbars
- `seesaw.core/border-panel` - Layout managers
- `seesaw.core/horizontal-panel`
- `seesaw.core/vertical-panel`

## swing-fx Provided Functions

### EDT-Safe Execution

```clojure
(require '[swing-fx.core :as sf])

;; Execute function on EDT
(sf/invoke-later
  (fn []
    (s/config! my-label :text "Updated!")))
```

### JList Selection Management

```clojure
;; Set selection
(sf/set-selection! my-listbox 5)

;; Set selection with auto-scroll
(sf/set-selection! my-listbox 5 :scroll true)

;; Clear selection
(sf/set-selection! my-listbox nil)

;; Get current selection
(sf/get-selection my-listbox)
;; => 5 (or nil if nothing selected)
```

### State Watchers

```clojure
;; Watch atom path and update UI on EDT
(sf/watch! *state [:selected-issue]
  (fn [old-val new-val]
    (update-detail-panel! new-val)))
```

### Toast Notifications

```clojure
;; Show notification in upper-right corner (auto-hides after 3s)
(sf/notify! frame "Issues reloaded!")
```

## Discovery History

We discovered these Seesaw bugs through painful debugging:

1. **First bug found**: Detail panel wouldn't update when selecting issues
   - Debug logs showed handler executing but UI not updating
   - Root cause: `seesaw.invoke/invoke-later` wasn't executing the lambda
   - Fixed by using `SwingUtilities/invokeLater` directly

2. **Second bug found**: j/k navigation didn't move visual selection
   - Debug logs showed `selection!` returning nil
   - Root cause: `seesaw.core/selection!` doesn't actually set selection
   - Fixed by using `.setSelectedIndex` directly

3. **Third bug found**: Hot reload stopped working
   - Same root cause as #1 - `invoke-later` bug struck again in `rebuild-ui!`
   - Reinforced need for swing-fx wrapper library

## Design Philosophy

swing-fx is intentionally minimal:
- Provides only what's needed to work around Seesaw bugs
- Uses Java Swing APIs directly for reliability
- Explicit over magical
- Well-documented with clear warnings

The value isn't in code volume, but in **reliability** and **documentation** of what works and what doesn't.
