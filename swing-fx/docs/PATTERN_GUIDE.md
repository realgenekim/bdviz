# The swing-fx Pattern Guide

## Overview

swing-fx is a pattern for building Swing applications with functional programming principles. It's not a large framework - the library itself is ~20 lines of code. The value is in the **pattern** it demonstrates.

## Core Philosophy

### Remove Ceremony, Not Control

Traditional Swing code is verbose and imperative. Functional approaches can be too magical. swing-fx finds the middle ground:

**✅ DO:** Remove boilerplate, automate repetitive tasks
**❌ DON'T:** Hide what's happening, create magic abstractions

## The Architecture

### Four Layers

```
User Actions
    ↓
Events (events.clj) - Pure event handlers
    ↓
State (db.clj) - Single atom, immutable data
    ↓
Watchers (effects.clj) - Detect changes
    ↓
UI Updates (Seesaw on EDT)
```

### 1. State Layer (`db.clj`)

**Single source of truth - no GUI imports!**

```clojure
(ns myapp.db
  (:require [clojure.spec.alpha :as s]))

;; Define your data schema
(s/def ::id string?)
(s/def ::title string?)
(s/def ::item (s/keys :req-un [::id ::title]))
(s/def ::items (s/coll-of ::item))
(s/def ::app-state (s/keys :req-un [::items]))

;; Single atom - persists across hot reloads
(defonce *app-state
  (atom {:items []
         :selected-item nil
         :filter-text ""}))

;; Pure query functions
(defn get-filtered-items [db filter-text]
  (if (empty? filter-text)
    (:items db)
    (filter #(clojure.string/includes? (:title %) filter-text)
            (:items db))))

;; Initialization
(defn init-state! []
  (reset! *app-state
          {:items (load-items-from-somewhere)
           :selected-item nil
           :filter-text ""}))
```

**Key principles:**
- ✅ NO GUI imports (javax.swing, seesaw, etc.)
- ✅ Pure functions for queries
- ✅ defonce for hot-reload persistence
- ✅ Clojure.spec for validation (optional)

### 2. Events Layer (`events.clj`)

**Pure event handlers - no GUI imports!**

```clojure
(ns myapp.events
  (:require [myapp.db :as db]))

;; Multimethod dispatch on event type
(defmulti handle-event :event/type)

;; Selection event
(defmethod handle-event ::item-selected [event]
  (swap! db/*app-state assoc :selected-item (:item-id event)))

;; Filter event
(defmethod handle-event ::filter-changed [event]
  (swap! db/*app-state assoc :filter-text (:text event)))

;; Load data event
(defmethod handle-event ::load-items [_]
  (let [items (load-items-from-api)]
    (swap! db/*app-state assoc :items items)))

;; Delete event
(defmethod handle-event ::delete-item [event]
  (swap! db/*app-state update :items
         (fn [items]
           (remove #(= (:id %) (:item-id event)) items))))
```

**Key principles:**
- ✅ NO GUI imports
- ✅ Multimethod dispatch for extensibility
- ✅ Pure-ish functions (side effects via swap!)
- ✅ Testable in REPL without UI

### 3. UI Layer (`ui.clj`)

**Declarative widget creation with Seesaw**

```clojure
(ns myapp.ui
  (:require [seesaw.core :as s]
            [myapp.db :as db]
            [myapp.events :as events]))

(defn create-ui []
  "Create the main UI using Seesaw's declarative API"
  (s/frame
    :title "My Application"
    :size [800 :by 600]
    :on-close :exit
    :content
    (s/border-panel
      :north (s/horizontal-panel
               :items [(s/label "Search:")
                       (s/text :id :search :columns 20)
                       (s/button :id :reload :text "Reload")])

      :center (s/left-right-split
                (s/scrollable (s/listbox :id :items))
                (s/border-panel :id :detail
                  :north (s/label :id :title "No selection")
                  :center (s/text :id :description
                                 :multi-line? true
                                 :editable? false))
                :divider-location 300))))

(defn wire-events! [frame]
  "Wire up event handlers using Seesaw's listen"
  ;; Search field
  (s/listen (s/select frame [:#search]) :document
    (fn [e]
      (events/handle-event {:event/type ::events/filter-changed
                           :text (s/text (s/select frame [:#search]))})))

  ;; List selection
  (s/listen (s/select frame [:#items]) :selection
    (fn [e]
      (when-let [idx (s/selection e)]
        (let [items (db/get-filtered-items @db/*app-state
                                           (:filter-text @db/*app-state))
              item (nth items idx)]
          (events/handle-event {:event/type ::events/item-selected
                               :item-id (:id item)})))))

  ;; Reload button
  (s/listen (s/select frame [:#reload]) :action
    (fn [_]
      (events/handle-event {:event/type ::events/load-items}))))

(defn setup-keyboard! [frame]
  "Setup keyboard shortcuts"
  (s/bind-key! frame "meta R"
               (fn [_] (events/handle-event {:event/type ::events/load-items})))
  (s/bind-key! frame "meta D"
               (fn [_] (events/handle-event {:event/type ::events/delete-item}))))
```

**Key principles:**
- ✅ Declarative widget creation (Seesaw)
- ✅ Tree-like structure matches visual layout
- ✅ Event wiring separate from creation
- ✅ Dispatch to events, don't mutate directly

### 4. Effects Layer (`effects.clj`)

**Watchers that sync state to UI**

```clojure
(ns myapp.effects
  (:require [swing-fx.core :as sf]
            [seesaw.core :as s]
            [myapp.db :as db]))

(defn setup-watchers! [frame]
  "Setup watchers that sync state changes to UI"

  ;; Watch items - update listbox
  (sf/watch! db/*app-state [:items]
    (fn [old new]
      (s/config! (s/select frame [:#items])
                :model (map :title new))))

  ;; Watch filter text - update listbox
  (sf/watch! db/*app-state [:filter-text]
    (fn [old new]
      (let [filtered (db/get-filtered-items @db/*app-state new)]
        (s/config! (s/select frame [:#items])
                  :model (map :title filtered)))))

  ;; Watch selection - update detail panel
  (sf/watch! db/*app-state [:selected-item]
    (fn [old-id new-id]
      (if-let [item (db/get-item-by-id @db/*app-state new-id)]
        (do
          (s/config! (s/select frame [:#title]) :text (:title item))
          (s/config! (s/select frame [:#description]) :text (:description item)))
        (do
          (s/config! (s/select frame [:#title]) :text "No selection")
          (s/config! (s/select frame [:#description]) :text ""))))))
```

**Key principles:**
- ✅ Explicit watchers (not Seesaw bind!)
- ✅ Each watcher watches ONE path
- ✅ Automatic EDT safety (via swing-fx)
- ✅ Automatic diff checking (only runs on change)

### 5. Entry Point (`core.clj`)

**Putting it all together**

```clojure
(ns myapp.core
  (:require [seesaw.core :as s]
            [myapp.db :as db]
            [myapp.ui :as ui]
            [myapp.effects :as effects])
  (:gen-class))

(defn -main [& args]
  ;; 1. Initialize state
  (db/init-state!)

  ;; 2. Create UI
  (s/invoke-later
    (let [frame (ui/create-ui)]
      ;; 3. Wire events
      (ui/wire-events! frame)

      ;; 4. Setup keyboard shortcuts
      (ui/setup-keyboard! frame)

      ;; 5. Setup watchers
      (effects/setup-watchers! frame)

      ;; 6. Show window
      (s/show! frame))))
```

## Why This Pattern Works

### 1. Testability

**Event handlers are pure functions:**

```clojure
;; In REPL or test:
(reset! db/*app-state {:items [] :selected-item nil})

(events/handle-event {:event/type ::events/item-selected
                     :item-id "item-1"})

(= "item-1" (:selected-item @db/*app-state))
;=> true
```

### 2. Hot Reload

**State persists, functions reload:**

```clojure
;; Change event handler code
(defmethod handle-event ::item-selected [event]
  (swap! db/*app-state assoc :selected-item (:item-id event))
  (println "Selected:" (:item-id event)))  ; Add logging

;; Reload in REPL
(require 'myapp.events :reload)

;; State is still there!
@db/*app-state
;=> {:items [...] :selected-item "item-1"}
```

### 3. Debuggability

**State is always visible:**

```clojure
;; In REPL at any time:
@db/*app-state
;=> {:items [{:id "1" :title "First"}
;             {:id "2" :title "Second"}]
;    :selected-item "1"
;    :filter-text ""}

;; Trace state changes:
(add-watch db/*app-state :debug
  (fn [_ _ old new]
    (when (not= old new)
      (println "STATE CHANGED:")
      (clojure.data/diff old new))))
```

### 4. Extensibility

**Add new events without touching existing code:**

```clojure
;; In different namespace
(defmethod events/handle-event ::my-new-event [event]
  (swap! db/*app-state assoc :my-data (:data event)))

;; Just works!
```

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

## Next Steps

1. Study [bd-viewer](../../src/bd_viewer/) - a complete real-world example
2. Read [Quick Start](QUICKSTART.md) - build your first app
3. See [Migration Guide](MIGRATION_GUIDE.md) - convert existing apps
4. Check [API Reference](API.md) - complete function documentation

## Questions?

The pattern is simple:
1. State in one atom (db.clj)
2. Events transform state (events.clj)
3. Watchers update UI (effects.clj)
4. Seesaw makes widgets nice (ui.clj)

That's it! Everything else is just details.
