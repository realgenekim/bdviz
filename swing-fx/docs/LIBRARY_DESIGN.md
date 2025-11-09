# Functional-Swing Library Design (Re-frame Inspired)

## Philosophy: Make the Right Way the Easy Way

Like re-frame, we want developers to **naturally fall into the pattern** through API design, not just documentation.

---

## Core Insight from Re-frame

Re-frame's **registration-based API** creates natural organization:

```clojure
;; In events.clj - naturally goes here because of reg-event-*
(reg-event-db ::increment
  (fn [db _] (update db :count inc)))

;; In subs.clj - naturally goes here because of reg-sub
(reg-sub ::count
  (fn [db _] (:count db)))

;; In views.clj - naturally goes here because it uses subscriptions
(defn counter []
  [:div "Count: " @(subscribe [::count])])
```

**Key**: Each registration function **suggests where it belongs**.

---

## Functional-Swing Library API Design

### 1. Registration-Based Event System

```clojure
(ns functional-swing.events
  "Event registration and dispatch.")

(defonce ^:private event-registry (atom {}))

(defn reg-event
  "Register event handler. Conventionally called in events.clj namespace.

  Handler receives [db event-data] and returns new db value.
  Side effects should be registered separately via reg-effect.

  Example:
    (reg-event ::increment
      (fn [db _]
        (update db :count inc)))"
  [event-type handler-fn]
  (swap! event-registry assoc event-type handler-fn))

(defn dispatch
  "Dispatch event. Can be called from anywhere.

  Example:
    (dispatch {:event/type ::increment})"
  [event-data]
  (when-let [handler (get @event-registry (:event/type event-data))]
    (handler event-data)))

;; Multimethod fallback for advanced users
(defmulti handle-event :event/type)

(defn reg-event-handler
  "Register event handler using multimethod (advanced).
  For most cases, use reg-event instead."
  [event-type handler-fn]
  (defmethod handle-event event-type [event-data]
    (handler-fn event-data)))
```

### 2. State Management with Queries

```clojure
(ns functional-swing.db
  "State atom and query functions.")

(defonce ^:private query-registry (atom {}))

(defn reg-query
  "Register query function. Conventionally called in db.clj namespace.

  Query receives [db & args] and returns derived data.
  Think of this like re-frame subscriptions but non-reactive.

  Example:
    (reg-query ::filtered-issues
      (fn [db filter-text]
        (filter #(includes? (:title %) filter-text)
                (:issues db))))"
  [query-key query-fn]
  (swap! query-registry assoc query-key query-fn))

(defn query
  "Execute registered query against db.

  Example:
    (query db ::filtered-issues \"important\")"
  [db query-key & args]
  (when-let [query-fn (get @query-registry query-key)]
    (apply query-fn db args)))

(defn create-db
  "Create app state atom with optional initial state.
  Conventionally called in db.clj namespace.

  Options:
    :initial - Initial state map
    :spec - Clojure spec for validation (optional)

  Example:
    (defonce *app-state (create-db {:initial {:issues [] :filter \"\"}}))"
  [& {:keys [initial spec]}]
  (let [db (atom (or initial {}))]
    (when spec
      (add-watch db ::spec-validation
        (fn [_ _ _ new-state]
          (when-not (s/valid? spec new-state)
            (println "WARNING: State violates spec:"
                     (s/explain-str spec new-state))))))
    db))
```

### 3. Widget Registry (Encourages views.clj)

```clojure
(ns functional-swing.views
  "Widget constructor registration.")

(defonce ^:private widget-registry (atom {}))

(defn reg-widget
  "Register widget constructor. Conventionally called in views.clj namespace.

  Constructor receives [db ui-refs] and returns Swing component.
  Component should wire up events using dispatch.

  Example:
    (reg-widget ::issue-list
      (fn [db ui-refs]
        (let [list (JList.)]
          (listen list :selection
            (fn [e] (dispatch {:event/type ::issue-selected
                              :index (.getSelectedIndex list)})))
          list)))"
  [widget-key constructor-fn]
  (swap! widget-registry assoc widget-key constructor-fn))

(defn create-widget
  "Create widget from registry.

  Example:
    (create-widget ::issue-list db ui-refs)"
  [widget-key db ui-refs]
  (when-let [constructor (get @widget-registry widget-key)]
    (constructor db ui-refs)))

(defn create-layout
  "Create layout from declarative spec.

  Spec is vector: [widget-key props & children]

  Example:
    (create-layout db ui-refs
      [:frame {:title \"My App\"}
        [:border-panel
          [:north [:toolbar]]
          [:center [:split
                     [:left [:issue-list]]
                     [:right [:detail-panel]]]]]])"
  [db ui-refs spec]
  ;; Implementation: parse spec tree and create widgets
  ...)
```

### 4. Effect Registration (Encourages effects.clj)

```clojure
(ns functional-swing.effects
  "Side effect registration and execution.")

(defonce ^:private effect-registry (atom {}))

(defn reg-effect
  "Register effect handler. Conventionally called in effects.clj namespace.

  Effect handler receives effect-data and performs side effects.
  Should use invoke-on-edt! for Swing mutations.

  Example:
    (reg-effect ::update-label
      (fn [{:keys [label text]}]
        (invoke-on-edt! #(.setText label text))))"
  [effect-key handler-fn]
  (swap! effect-registry assoc effect-key handler-fn))

(defn run-effect
  "Execute registered effect.

  Example:
    (run-effect ::update-label {:label my-label :text \"Hello\"})"
  [effect-key effect-data]
  (when-let [handler (get @effect-registry effect-key)]
    (handler effect-data)))

(defn reg-state-watcher
  "Register watcher that triggers effects on state changes.
  Conventionally called in effects.clj namespace.

  Watcher receives [old-state new-state] and can dispatch effects.

  Example:
    (reg-state-watcher ::sync-ui
      (fn [old new]
        (when (not= (:issues old) (:issues new))
          (run-effect ::update-issue-list {:issues (:issues new)}))))"
  [watcher-key watcher-fn]
  ;; Store in registry and add-watch on app-state
  ...)
```

### 5. Project Scaffolding

```clojure
(ns functional-swing.scaffold
  "Project scaffolding and validation.")

(defn check-structure!
  "Check that project follows recommended structure.
  Prints warnings if structure is unconventional.

  Expected structure:
    src/
      myapp/
        core.clj       ; Entry point, app initialization
        db.clj         ; State atom, queries
        events.clj     ; Event handlers
        views.clj      ; Widget constructors
        effects.clj    ; Side effect handlers

  Call this in dev mode to validate structure."
  [project-ns]
  (let [expected-namespaces [(symbol (str project-ns ".core"))
                            (symbol (str project-ns ".db"))
                            (symbol (str project-ns ".events"))
                            (symbol (str project-ns ".views"))
                            (symbol (str project-ns ".effects"))]
        loaded (filter #(find-ns %) expected-namespaces)]
    (doseq [ns expected-namespaces]
      (when-not (some #{ns} loaded)
        (println "⚠️  Missing recommended namespace:" ns)
        (println "   Create src/" (str/replace (str ns) "." "/") ".clj")))

    ;; Check registrations happened in right places
    (validate-registrations!)))

(defn validate-registrations!
  "Warn if registrations happened in unconventional namespaces."
  []
  ;; Check event-registry - should be from *.events namespace
  ;; Check widget-registry - should be from *.views namespace
  ;; Check effect-registry - should be from *.effects namespace
  ...)

(defn create-project!
  "Scaffold new functional-swing project.

  Example:
    (create-project! \"myapp\" {:dir \"./myapp\"})"
  [project-name & {:keys [dir]}]
  (let [project-dir (or dir project-name)]
    ;; Create directory structure
    (create-dirs! project-dir)
    ;; Create template files
    (create-file! (str project-dir "/src/" project-name "/core.clj")
                  (template-core project-name))
    (create-file! (str project-dir "/src/" project-name "/db.clj")
                  (template-db project-name))
    (create-file! (str project-dir "/src/" project-name "/events.clj")
                  (template-events project-name))
    (create-file! (str project-dir "/src/" project-name "/views.clj")
                  (template-views project-name))
    (create-file! (str project-dir "/src/" project-name "/effects.clj")
                  (template-effects project-name))
    (create-file! (str project-dir "/deps.edn")
                  (template-deps project-name))
    (println "✅ Created functional-swing project:" project-name)
    (println "📂 Structure:")
    (println "   " project-dir "/")
    (println "      src/" project-name "/")
    (println "         core.clj     ; Entry point")
    (println "         db.clj       ; State management")
    (println "         events.clj   ; Event handlers")
    (println "         views.clj    ; Widget constructors")
    (println "         effects.clj  ; Side effects")
    (println "      deps.edn        ; Dependencies")
    (println)
    (println "Next steps:")
    (println "  cd" project-dir)
    (println "  clj -M -m" (str project-name ".core"))))
```

---

## Example: How This Encourages the Pattern

### Developer Experience

#### Step 1: Create Project
```bash
$ clj -M:functional-swing:scaffold myapp
✅ Created functional-swing project: myapp
📂 Structure:
   myapp/
      src/myapp/
         core.clj     ; Entry point
         db.clj       ; State management
         events.clj   ; Event handlers
         views.clj    ; Widget constructors
         effects.clj  ; Side effects
      deps.edn        ; Dependencies

Next steps:
  cd myapp
  clj -M -m myapp.core
```

**Result**: Developer has the right structure from the start.

#### Step 2: Look at Generated Files

**myapp/db.clj** (generated):
```clojure
(ns myapp.db
  "Application state and queries.

  This namespace defines:
  - App state atom (create-db)
  - Query functions (reg-query)

  Conventionally, all state-related code lives here."
  (:require [functional-swing.db :as fs-db]
            [clojure.spec.alpha :as s]))

;; Define your state spec
(s/def ::app-state
  (s/keys :req-un [::items ::selected-item]))

;; Create app state atom
(defonce *app-state
  (fs-db/create-db :initial {:items []
                             :selected-item nil}
                   :spec ::app-state))

;; Register queries
(fs-db/reg-query ::filtered-items
  (fn [db filter-text]
    (if (empty? filter-text)
      (:items db)
      (filter #(includes? (:name %) filter-text)
              (:items db)))))

;; Helper functions
(defn get-item-by-id [db id]
  (first (filter #(= (:id %) id) (:items db))))
```

**myapp/events.clj** (generated):
```clojure
(ns myapp.events
  "Event handlers.

  This namespace defines:
  - Event handlers (reg-event)

  Event handlers receive event data and transform state.
  They should be PURE functions (no side effects).

  Conventionally, all event handlers live here."
  (:require [functional-swing.events :as fs-events]
            [myapp.db :as db]))

;; Register event handlers
(fs-events/reg-event ::item-selected
  (fn [db {:keys [item-id]}]
    (swap! db/*app-state assoc :selected-item item-id)))

(fs-events/reg-event ::filter-changed
  (fn [db {:keys [text]}]
    (swap! db/*app-state assoc :filter-text text)))

(fs-events/reg-event ::load-items
  (fn [db _]
    (let [items (load-items-from-somewhere)]
      (swap! db/*app-state assoc :items items))))
```

**myapp/views.clj** (generated):
```clojure
(ns myapp.views
  "Widget constructors.

  This namespace defines:
  - Widget constructors (reg-widget)

  Widget constructors create Swing components and wire up events.
  They should use functional-swing.events/dispatch for actions.

  Conventionally, all UI construction code lives here."
  (:require [functional-swing.views :as fs-views]
            [functional-swing.events :as fs-events]
            [myapp.db :as db])
  (:import [javax.swing JList JPanel JLabel DefaultListModel]))

;; Register widget constructors
(fs-views/reg-widget ::item-list
  (fn [db ui-refs]
    (let [model (DefaultListModel.)
          jlist (JList. model)]
      ;; Populate model
      (doseq [item (:items @db/*app-state)]
        (.addElement model (:name item)))
      ;; Wire up events
      (.addListSelectionListener jlist
        (reify javax.swing.event.ListSelectionListener
          (valueChanged [_ e]
            (when-not (.getValueIsAdjusting e)
              (let [index (.getSelectedIndex jlist)
                    items (:items @db/*app-state)
                    item (nth items index nil)]
                (when item
                  (fs-events/dispatch {:event/type ::events/item-selected
                                      :item-id (:id item)})))))))
      ;; Store reference
      (swap! ui-refs assoc ::item-list jlist)
      jlist)))

(fs-views/reg-widget ::main-frame
  (fn [db ui-refs]
    (let [frame (JFrame. "My App")
          item-list (fs-views/create-widget ::item-list db ui-refs)]
      ;; Layout
      (doto (.getContentPane frame)
        (.setLayout (BorderLayout.))
        (.add (JScrollPane. item-list) BorderLayout/CENTER))
      ;; Setup
      (doto frame
        (.setSize 800 600)
        (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE))
      frame)))
```

**myapp/effects.clj** (generated):
```clojure
(ns myapp.effects
  "Side effect handlers.

  This namespace defines:
  - Effect handlers (reg-effect)
  - State watchers (reg-state-watcher)

  Effects perform Swing mutations, file I/O, network calls, etc.
  They are triggered by state changes via watchers.

  Conventionally, all side effects live here."
  (:require [functional-swing.effects :as fs-fx]
            [functional-swing.core :as fs]
            [myapp.db :as db])
  (:import [javax.swing DefaultListModel]))

;; Register effects
(fs-fx/reg-effect ::update-item-list
  (fn [{:keys [jlist items]}]
    (fs/invoke-on-edt!
      (fn []
        (let [model (.getModel jlist)]
          (.clear model)
          (doseq [item items]
            (.addElement model (:name item))))))))

(fs-fx/reg-effect ::update-detail-panel
  (fn [{:keys [panel item]}]
    (fs/invoke-on-edt!
      (fn []
        ;; Update panel with item details
        ...))))

;; Register watchers
(fs-fx/reg-state-watcher ::sync-ui
  (fn [old-state new-state ui-refs]
    ;; When items change, update list
    (when (not= (:items old-state) (:items new-state))
      (fs-fx/run-effect ::update-item-list
        {:jlist (::item-list @ui-refs)
         :items (:items new-state)}))

    ;; When selection changes, update detail panel
    (when (not= (:selected-item old-state) (:selected-item new-state))
      (let [item (db/get-item-by-id new-state (:selected-item new-state))]
        (fs-fx/run-effect ::update-detail-panel
          {:panel (::detail-panel @ui-refs)
           :item item})))))
```

**myapp/core.clj** (generated):
```clojure
(ns myapp.core
  "Application entry point.

  This namespace:
  - Initializes state
  - Sets up watchers
  - Creates and shows main window
  - Handles app lifecycle

  Conventionally, this is the only namespace with -main."
  (:require [functional-swing.core :as fs]
            [functional-swing.scaffold :as scaffold]
            [myapp.db :as db]
            [myapp.events :as events]
            [myapp.views :as views]
            [myapp.effects :as effects])
  (:gen-class))

(defn init! []
  "Initialize application state."
  (events/dispatch {:event/type ::events/load-items}))

(defn setup-watchers! [ui-refs]
  "Setup reactive watchers."
  (add-watch db/*app-state ::ui-sync
    (fn [_ _ old-state new-state]
      (when (not= old-state new-state)
        (effects/sync-ui old-state new-state ui-refs)))))

(defn start! []
  "Start the application."
  (let [ui-refs (atom {})
        frame (views/create-widget ::main-frame db/*app-state ui-refs)]
    ;; Initialize state
    (init!)
    ;; Setup reactivity
    (setup-watchers! ui-refs)
    ;; Show window
    (.setVisible frame true)
    ;; Store frame reference
    (swap! ui-refs assoc ::frame frame)))

(defn -main [& args]
  ;; Check project structure (dev mode only)
  (when (System/getProperty "functional-swing.dev")
    (scaffold/check-structure! 'myapp))

  ;; Start app
  (fs/invoke-on-edt! start!))
```

#### Step 3: Developer Tries to Put Code in Wrong Place

```clojure
;; Developer tries to define event handler in views.clj
(ns myapp.views
  (:require [functional-swing.events :as fs-events]))

(fs-events/reg-event ::my-event  ; ⚠️ WRONG NAMESPACE!
  (fn [db _] ...))
```

**With dev mode enabled:**
```
⚠️  WARNING: Event ::my-event registered in myapp.views
   Events should be registered in myapp.events
   Consider moving this registration to events.clj
```

**Result**: Developer learns the convention immediately.

---

## How This Encourages the Pattern

### 1. Registration Functions Suggest Location

| Function | Suggests Namespace | Natural Mental Model |
|----------|-------------------|---------------------|
| `reg-event` | `events.clj` | "This is an event handler" |
| `reg-query` | `db.clj` | "This queries state" |
| `reg-widget` | `views.clj` | "This creates UI" |
| `reg-effect` | `effects.clj` | "This performs side effects" |

### 2. Generated Comments Explain Why

Each generated file has docstring explaining:
- What belongs in this namespace
- What doesn't belong here
- Where related code lives

### 3. Dev Mode Validation

```bash
$ clj -J-Dfunctional-swing.dev=true -M -m myapp.core
⚠️  Missing recommended namespace: myapp.effects
   Create src/myapp/effects.clj
✅ Event handlers: 3 registered (all in myapp.events)
⚠️  Widget ::item-list created inline in myapp.core
   Consider registering with reg-widget in views.clj
```

### 4. Template Provides Example

New developers see:
1. The right structure immediately
2. Example code in each namespace
3. Comments explaining conventions
4. Working app that follows the pattern

---

## Comparison with Current Approach

### Current (Manual Pattern)

**Pros:**
- ✅ Explicit, nothing hidden
- ✅ Full control
- ✅ Easy to understand flow

**Cons:**
- ❌ No enforcement of structure
- ❌ Easy to mix concerns
- ❌ No guidance for new developers
- ❌ Manual boilerplate

### With Registration-Based Library

**Pros:**
- ✅ Pattern is enforced through API
- ✅ Less boilerplate
- ✅ Clear guidance for structure
- ✅ Development-time validation
- ✅ Discoverable (can inspect registries)
- ✅ Hot-reload friendly

**Cons:**
- ⚠️ Slight indirection (registry lookup)
- ⚠️ Learning curve for registration model
- ⚠️ Less explicit than direct function calls

---

## Migration Path from Current bd-viewer

### Step 1: Add Library Dependency

```clojure
;; deps.edn
{:deps {functional-swing {:local/root "../functional-swing"}}}
```

### Step 2: Convert Event Handlers

**Before:**
```clojure
(ns bd-viewer.events)

(defmulti handle-event :event/type)

(defmethod handle-event ::issue-selected [event]
  (swap! db/*app-state assoc :selected-issue (:issue-id event)))
```

**After:**
```clojure
(ns bd-viewer.events
  (:require [functional-swing.events :as fs-events]))

(fs-events/reg-event ::issue-selected
  (fn [db {:keys [issue-id]}]
    (swap! db assoc :selected-issue issue-id)))
```

### Step 3: Convert Widget Constructors

**Before:**
```clojure
(ns bd-viewer.ui)

(defn create-issue-list []
  (let [jlist (JList.)]
    ;; setup...
    jlist))
```

**After:**
```clojure
(ns bd-viewer.views
  (:require [functional-swing.views :as fs-views]))

(fs-views/reg-widget ::issue-list
  (fn [db ui-refs]
    (let [jlist (JList.)]
      ;; setup...
      jlist)))
```

### Step 4: Convert Effects

**Before:**
```clojure
(ns bd-viewer.effects.swing)

(defn update-issue-list! [old new]
  (SwingUtilities/invokeLater ...))

(defn setup-watchers! []
  (add-watch db/*app-state ::sync-ui
    (fn [_ _ old new]
      (update-issue-list! old new))))
```

**After:**
```clojure
(ns bd-viewer.effects
  (:require [functional-swing.effects :as fs-fx]))

(fs-fx/reg-effect ::update-issue-list
  (fn [{:keys [jlist issues]}]
    ...))

(fs-fx/reg-state-watcher ::sync-ui
  (fn [old new ui-refs]
    (when (not= (:issues old) (:issues new))
      (fs-fx/run-effect ::update-issue-list
        {:jlist (::issue-list @ui-refs)
         :issues (:issues new)}))))
```

---

## Additional Features for Pattern Enforcement

### 1. Namespace Linter

```clojure
(defn lint-namespace-usage
  "Check that namespaces follow conventions."
  [project-ns]
  (let [rules {:events #{`reg-event `handle-event}
               :views #{`reg-widget `create-widget}
               :effects #{`reg-effect `reg-state-watcher}
               :db #{`reg-query `create-db}}]
    (doseq [[ns-suffix expected-fns] rules]
      (let [ns-sym (symbol (str project-ns "." (name ns-suffix)))]
        (when-let [ns-obj (find-ns ns-sym)]
          ;; Check that this namespace only uses appropriate functions
          ;; Warn if mixing concerns
          ...)))))
```

### 2. Registry Inspector (REPL Tool)

```clojure
(defn show-registrations
  "Show all registrations in project (dev tool)."
  []
  (println "📋 Registered Events:")
  (doseq [[event-type handler] @event-registry]
    (println "  " event-type (meta handler)))

  (println "\n🎨 Registered Widgets:")
  (doseq [[widget-key constructor] @widget-registry]
    (println "  " widget-key (meta constructor)))

  (println "\n⚡ Registered Effects:")
  (doseq [[effect-key handler] @effect-registry]
    (println "  " effect-key (meta handler))))

;; Usage in REPL:
;; => (show-registrations)
;; 📋 Registered Events:
;;    :myapp.events/issue-selected {:ns myapp.events :line 12}
;;    :myapp.events/filter-changed {:ns myapp.events :line 16}
;; ...
```

### 3. Event Flow Tracer

```clojure
(defn enable-event-tracing!
  "Enable event tracing for debugging."
  []
  (alter-var-root #'dispatch
    (fn [original-dispatch]
      (fn [event-data]
        (println "→ EVENT:" (:event/type event-data))
        (let [result (original-dispatch event-data)]
          (println "← RESULT:" result)
          result)))))

;; Usage:
;; => (enable-event-tracing!)
;; ;; Now clicking in UI shows:
;; → EVENT: :myapp.events/issue-selected
;; ← RESULT: {:issues [...] :selected-issue "bd-1"}
```

---

## Documentation Structure

### 1. Conceptual First (Like Re-frame)

**Getting Started Guide:**
1. Why functional-swing? (The problems it solves)
2. Core concepts (State, Events, Views, Effects)
3. File structure and why
4. Your first app (tutorial)
5. Common patterns

### 2. API Reference Second

Only after understanding concepts.

### 3. Migration Guides

- From raw Swing
- From Seesaw
- From current functional pattern

---

## Final Design: Minimal + Enforcing

### Option A: Minimal Utils + Linting

Provide:
- Utility functions (EDT safety, etc.)
- Linter that checks structure
- Template generator
- **No registration** - keep it simple

**Enforcement:** Linting warnings, not runtime

### Option B: Registration-Based (Re-frame Style)

Provide:
- Registration functions
- Runtime validation
- Event tracing
- State inspection
- Template generator

**Enforcement:** API design + runtime checks

---

## Recommendation

**Start with Option A (Minimal + Linting):**

1. **Phase 1:** Minimal utility library
   - EDT safety
   - Watcher helpers
   - Hot reload utilities
   - Keyboard shortcuts

2. **Phase 2:** Add linting
   - Check namespace structure
   - Warn on convention violations
   - Don't enforce, just guide

3. **Phase 3:** Add template generator
   - `functional-swing.scaffold/create-project!`
   - Generates recommended structure
   - Includes examples

4. **Phase 4 (Optional):** Registration model
   - Only if building 3+ apps
   - After pattern is proven stable
   - When benefits outweigh complexity

This gives **guidance without lock-in**, following the principle:

> "Make the right way easy, but don't make other ways impossible."

---

## Concrete Next Step

Create `functional-swing-utils` with:

1. **Core utilities** (`core.clj`)
2. **Linter** (`lint.clj`)
3. **Template generator** (`scaffold.clj`)
4. **Documentation** emphasizing structure

The linter and templates do the **heavy lifting** for encouraging the pattern, without requiring a registration model.
