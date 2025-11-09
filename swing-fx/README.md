# swing-fx

A tiny library and pattern for building functional Swing applications with Clojure.

## Philosophy

**Remove ceremony, not control.**

Traditional imperative Swing code mixes state mutations, UI updates, and business logic together. swing-fx provides just enough helpers to make Swing pleasant, while keeping your code explicit and debuggable.

**What swing-fx provides:**
- **Seesaw** - Declarative widget creation and layout
- **ClosedRecord** - Type-safe map access (catches typos immediately!)
- **Explicit state** - Single atom, no magic
- **Clear events** - Multimethod dispatch, testable
- **Visible watchers** - Automatic UI updates without hidden bindings

**What you get:**
- ✅ Hot-reloadable code (state persists across reloads)
- ✅ Testable event handlers (pure-ish functions, no GUI imports)
- ✅ Debuggable state (inspect atom in REPL anytime)
- ✅ Maintainable code (clear separation of concerns)
- ✅ 60% less boilerplate vs raw Swing

## The Architecture

```
User Interaction
    ↓
Events (events.clj) - Pure event handlers
    ↓
State (db.clj) - Single atom, immutable data
    ↓
Watchers (effects.clj) - Detect changes
    ↓
UI Updates (Seesaw on EDT)
```

### The Four Layers

**1. State (db.clj)** - Single source of truth, no GUI imports, ClosedRecord for safety
```clojure
(require '[swing-fx.closed-record :refer [closed-record]])

;; Define your data schema with clojure.spec
(s/def ::id string?)
(s/def ::title string?)
(s/def ::item (s/keys :req-un [::id ::title]))

;; Wrap data in ClosedRecord - catches typos immediately!
(defn make-item [data]
  (closed-record data {:spec ::item}))

(defonce *app-state (atom {:items [] :selected nil}))
```

**2. Events (events.clj)** - Pure event handlers, no GUI imports
```clojure
(defmulti handle-event :event/type)
(defmethod handle-event ::item-selected [event]
  (swap! *app-state assoc :selected (:item-id event)))
```

**3. UI (ui.clj)** - Declarative widget creation with Seesaw
```clojure
(defn create-ui []
  (s/frame :title "My App"
    :content (s/listbox :id :items)))
```

**4. Effects (effects.clj)** - Watchers sync state to UI
```clojure
(sf/watch! *app-state [:items]
  (fn [old new]
    (s/config! (s/select frame [:#items])
              :model new)))
```

## Learn by Example

**See [bd-viewer](../) - a complete working app showing this pattern in action.**

Study these files to understand the pattern:
- `src/bd_viewer/db.clj` - State management with ClosedRecord types
- `src/bd_viewer/events.clj` - Event handlers (no GUI imports!)
- `src/bd_viewer/effects.clj` - Watchers using swing-fx
- `src/bd_viewer/ui.clj` - Seesaw widgets + event wiring
- `src/bd_viewer/keyboard.clj` - Keyboard shortcuts

The bd-viewer app demonstrates:
- Loading data from external CLI (`bd list --json`)
- Filtering and searching through issues
- Master-detail UI layout with split panes
- Keyboard shortcuts (j/k navigation, ⌘R reload, ⌘D delete)
- Hot-reloading code while preserving state

## Installation

```clojure
;; deps.edn
{:deps {swing-fx/swing-fx {:local/root "./swing-fx"}
        seesaw/seesaw {:mvn/version "1.5.0"}}}
```

## Quick Start

```clojure
(ns myapp.core
  (:require [swing-fx.core :as sf]
            [swing-fx.closed-record :refer [closed-record]]
            [seesaw.core :as s]
            [clojure.spec.alpha :as s]))

;; 1. State (db.clj) - Single atom with ClosedRecord, no GUI imports!
(s/def ::count int?)
(s/def ::app-state (s/keys :req-un [::count]))

(defonce *state (atom (closed-record {:count 0} {:spec ::app-state})))

;; 2. Events (events.clj) - Pure handlers, no GUI imports!
(defmulti handle-event :event/type)
(defmethod handle-event ::increment [_]
  (swap! *state update :count inc))

;; 3. UI (ui.clj) - Declarative Seesaw
(defn create-ui []
  (s/frame :title "Counter"
    :content (s/vertical-panel
               :items [(s/label :id :counter "Count: 0")
                      (s/button :text "+"
                               :listen [:action
                                       (fn [_] (handle-event {:event/type ::increment}))])])))

;; 4. Effects (effects.clj) - Watchers with swing-fx
(defn setup-watchers! [frame]
  (sf/watch! *state [:count]
    (fn [old new]
      (s/config! (s/select frame [:#counter])
                :text (str "Count: " new)))))

;; 5. Entry Point (core.clj) - Tie it all together
(defn -main []
  (s/invoke-later
    (let [frame (create-ui)]
      (setup-watchers! frame)
      (s/show! frame))))
```

## API

The library provides a few essential functions for reactive Swing development.

### `watch!`

Watch an atom path and run handler on EDT when value changes.

```clojure
(sf/watch! *atom path handler)

;; Handler receives [old-value new-value]
(sf/watch! *state [:issues]
  (fn [old-issues new-issues]
    (update-list! new-issues)))
```

**Key features:**
- ✅ Automatic EDT safety - handler always runs on Swing thread
- ✅ Automatic diff checking - only calls handler when value actually changes
- ✅ Explicit and visible - you can see what path is watched and what happens
- ✅ No magic - no hidden subscriptions or dependency tracking

### `register-reload-hook!` and `reload!`

**The problem:** When you hot reload code with Cmd+Shift+R, keyboard shortcuts don't get re-registered automatically. You have to remember to add keyboard setup to your `rebuild-ui!` function, and it's easy to forget!

**The solution:** Register hooks that run automatically on every reload:

```clojure
;; In your core.clj startup code (runs once):
(defn -main []
  (let [frame (ui/create-main-frame)]
    ;; Setup keyboard shortcuts initially
    (kbd/setup-keyboard-shortcuts! frame)

    ;; Register hook so it reloads automatically!
    (sf/register-reload-hook! kbd/setup-keyboard-shortcuts!)

    ;; Any other setup that needs to reload can register too
    (sf/register-reload-hook!
      (fn [frame] (println "Reloading custom setup!")))))

;; In your rebuild-ui! function:
(defn rebuild-ui! []
  (when-let [frame @*frame]
    ;; ... rebuild UI widgets ...

    ;; Run all registered hooks automatically!
    (sf/reload! frame)))
```

**Benefits:**
- ✅ **Automatic** - Register once at startup, works forever
- ✅ **Impossible to forget** - Hooks run automatically on every reload
- ✅ **Flexible** - Register any setup logic that needs to run on reload
- ✅ **Clean** - No need to remember to call keyboard setup in rebuild-ui!

**Common use cases:**
- Keyboard shortcuts (always need to be re-registered)
- Custom event listeners
- Third-party library initialization
- Any setup that depends on fresh code

### `notify!` and `notify-error!`

Show toast-style notifications to give users feedback.

```clojure
;; Success notification (green, 3 seconds)
(sf/notify! frame "Issues reloaded!")
(sf/notify! frame "Issue bd-viewer-5 closed!")

;; Error notification (red, 5 seconds)
(sf/notify-error! frame "⚠️ Error occurred! Check logs.")
(sf/notify-error! frame "Failed to load issues")
```

**Features:**
- ✅ **Non-blocking** - Appears at top-right, auto-hides
- ✅ **Smart stacking** - New notifications replace old ones (no pile-up)
- ✅ **Color-coded** - Green for success, red for errors
- ✅ **Timed** - Success notifications hide after 3s, errors after 5s

**Example: Global exception handler**

Catch all uncaught exceptions and show error notifications:

```clojure
(defn setup-exception-handler! [frame]
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread throwable]
       (log/error :uncaught-exception
                  :message (.getMessage throwable))
       (sf/notify-error! frame "⚠️ Error occurred! Check logs.")))))

;; Call in -main after creating frame
(setup-exception-handler! frame)
```

Now any crash will show a red notification instead of silently failing!

**IMPORTANT: Testing exception handlers**

To test your exception handler, you MUST use `Thread` directly, NOT `future`:

```clojure
;; ❌ WRONG - future catches exceptions internally, handler won't trigger
(future (/ 1 0))

;; ✅ CORRECT - Thread makes exception truly uncaught
(.start (Thread. (fn [] (/ 1 0))))
```

**Why?** Clojure's `future` has built-in exception handling:
- Catches exceptions automatically
- Stores them inside the future object
- Only re-throws when you `@deref` the future
- Since you never deref, exception is silently swallowed!

Java's `Thread` has no exception handling:
- Exceptions are truly uncaught
- Trigger `Thread.setDefaultUncaughtExceptionHandler`
- Your error notification appears!

## Why This Pattern Works

### 1. Hot Reload
State persists across code reloads because of `defonce`:

```clojure
;; Change event handler code
(defmethod handle-event ::increment [_]
  (swap! *state update :count inc)
  (println "Count incremented!"))  ; Add logging

;; Reload in REPL
(require 'myapp.events :reload)

;; State is still there!
@*state  ;=> {:count 42}
```

### 2. Testability
Event handlers are pure functions - test without GUI!

```clojure
;; In REPL or test:
(reset! *state {:count 0})
(handle-event {:event/type ::increment})
(= 1 (:count @*state))  ;=> true
```

### 3. Debuggability
State is always visible:

```clojure
;; In REPL at any time:
@*state  ;=> {:count 42 :items [...]}

;; Trace state changes:
(add-watch *state :debug
  (fn [_ _ old new]
    (when (not= old new)
      (println "STATE CHANGED:")
      (clojure.data/diff old new))))
```

### 4. Extensibility
Add new events without touching existing code:

```clojure
;; In different namespace
(defmethod events/handle-event ::my-new-event [event]
  (swap! db/*app-state assoc :my-data (:data event)))

;; Just works!
```

## Why ClosedRecord is Essential

**Plain maps are DANGEROUS for UI state management!** Typos silently return nil, causing bugs that propagate through your code:

```clojure
;; Plain map - DANGEROUS! ❌
(def issue {:title "Bug fix" :description "Fix it" :priority 0})
(:titel issue)  ;=> nil (typo! but silent failure)

;; ClosedRecord - SAFE! ✅
(def issue (closed-record {:title "Bug fix" :description "Fix it" :priority 0}))
(:titel issue)  ;=> THROWS! "INVALID KEY ACCESS: :titel (valid keys: [:description :priority :title])"
```

**Why this matters in Swing apps:**
1. **Lots of field access** - You read issue fields constantly to populate UI components
2. **Silent nil bugs** - A typo in `:status` becomes `nil`, UI shows blank, hard to debug
3. **LLM safety** - When AI generates code, it might hallucinate key names
4. **Refactoring** - Rename a key, find all usages via exceptions (not runtime bugs)

**Real-world example:**
```clojure
;; In effects.clj - updating detail panel
(defn update-detail! [issue]
  (.setText title-label (:title issue))       ; Works ✅
  (.setText status-label (:staus issue)))     ; Typo! 
  
;; Plain map: Shows blank status (hard to debug)
;; ClosedRecord: THROWS immediately with helpful error!
```

**See [Why ClosedRecord](docs/WHY_CLOSED_RECORD.md) for the full story.**

## Comparison to Other Approaches

### vs. Traditional Swing
**Traditional:**
```clojure
(let [button (JButton. "Click")]
  (.addActionListener button
    (proxy [ActionListener] []
      (actionPerformed [e]
        (.setText label "Clicked!")))))
```

**swing-fx:**
```clojure
(s/button :id :click :text "Click"
  :listen [:action
           (fn [_] (events/handle-event {:event/type ::clicked}))])

(sf/watch! *state [:clicked]
  (fn [_ new]
    (s/config! label :text (if new "Clicked!" ""))))
```

### vs. Seesaw bind
**Seesaw bind (too magical):**
```clojure
(b/bind *state
  (b/transform :count)
  (b/property label :text))
;; Where does this run? How do I debug it?
```

**swing-fx (explicit):**
```clojure
(sf/watch! *state [:count]
  (fn [_ new]
    (s/config! label :text (str new))))
;; Clear: when :count changes, update label
```

### vs. re-frame
**re-frame (subscription magic):**
```clojure
(re-frame/reg-sub ::count
  (fn [db _] (:count db)))

(defn view []
  [:div @(re-frame/subscribe [::count])])
;; Complex dependency tracking
```

**swing-fx (explicit watchers):**
```clojure
(sf/watch! *state [:count]
  (fn [_ new]
    (s/config! label :text (str new))))
;; Simple: watch path, update widget
```

## Best Practices

### Split Panes: Avoid Mixing Pixels and Proportions

When using `left-right-split` or `top-bottom-split`, be careful with divider positioning:

**❌ BAD - Mixing pixels and proportions causes "snap back" behavior:**
```clojure
(s/left-right-split
  left-panel
  right-panel
  :divider-location 400      ;; Fixed pixels!
  :resize-weight 0.4)        ;; Proportional!
;; User drags divider → snaps back to 400px
```

**✅ GOOD - Use proportional values only:**
```clojure
(s/left-right-split
  left-panel
  right-panel
  :resize-weight 0.4)        ;; 40% left, 60% right
;; User adjustments stick, window resizing works correctly
```

**✅ ALSO GOOD - Both proportional:**
```clojure
(s/left-right-split
  left-panel
  right-panel
  :divider-location 0.4      ;; 0.0-1.0 = proportion
  :resize-weight 0.4)
```

**Why?** Mixing fixed pixels (`:divider-location 400`) with proportions (`:resize-weight 0.4`) creates conflicts. Swing might try to restore the pixel position, fighting user adjustments. Stick to proportional values for user-resizable splits!

### DO:
✅ Keep db.clj and events.clj framework-agnostic (no GUI imports)  
✅ Use one watcher per UI concern (clear and debuggable)  
✅ Use Seesaw's declarative API for widget creation  
✅ Use multimethod dispatch for events (extensible)  
✅ Use defonce for state (hot-reload friendly)  

### DON'T:
❌ Use Seesaw's bind for state→UI (use explicit watchers)  
❌ Mutate widgets directly in event handlers  
❌ Store business logic in UI code  
❌ Create circular dependencies (db→events→db)  
❌ Over-abstract (keep it simple and explicit)  

## File Structure

Recommended project layout:

```
myapp/
  src/myapp/
    core.clj       - Entry point, app initialization
    db.clj         - State management (no GUI!)
    events.clj     - Event handlers (no GUI!)
    ui.clj         - Widget creation (Seesaw)
    effects.clj    - Watchers (swing-fx)
    keyboard.clj   - Keyboard shortcuts (optional)
```

## Documentation

- [Pattern Guide](docs/PATTERN_GUIDE.md) - Complete explanation of the architecture
- [Design Exploration](docs/DESIGN_EXPLORATION.md) - How we arrived at this design
- [Seesaw Evaluation](docs/SEESAW_EVALUATION.md) - Why we chose Seesaw
- [Library Design](docs/LIBRARY_DESIGN.md) - Design philosophy and decisions
- [Extraction Analysis](docs/EXTRACTION_ANALYSIS.md) - How the pattern was extracted from mailmerge

## Why swing-fx?

**Compared to raw Swing:**
- ✅ 60% less boilerplate
- ✅ Declarative widget creation (Seesaw)
- ✅ Automatic UI updates (watchers)
- ✅ Testable event handlers
- ✅ Hot-reloadable

**Compared to re-frame:**
- ✅ No ClojureScript required (native desktop apps)
- ✅ Explicit watchers (no subscription magic)
- ✅ Smaller, simpler library (~20 LOC)
- ✅ Direct Swing access when needed
- ✅ Pattern you can understand in 5 minutes

## The Library is Tiny

The swing-fx library itself is ~20 lines of code. **That's intentional.**

The value isn't in the code volume - it's in the **pattern** demonstrated by bd-viewer.

- The library removes ceremony (EDT boilerplate, manual diff checking)
- The pattern provides structure (separation of db/events/effects/ui)
- The example shows it working in a real app

Together, these give you a maintainable, testable, reloadable Swing application.

## License

EPL 1.0 (same as Clojure)

## See Also

- [bd-viewer](../) - Complete example application
- [Seesaw](https://github.com/clj-commons/seesaw) - The widget library we build on
- [re-frame](https://github.com/day8/re-frame) - Inspiration for the pattern
