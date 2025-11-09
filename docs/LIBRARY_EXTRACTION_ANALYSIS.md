# Functional Swing Library Extraction Analysis

## Executive Summary

After studying both **mailmerge** and **bd-viewer**, I've identified a clear, reusable functional Swing pattern that could be extracted into a library. However, a **Seesaw rewrite** might be even better for future projects. This document analyzes both approaches.

---

## The Pattern: What Was Extracted from Mailmerge

The functional Swing architecture successfully extracted from mailmerge to bd-viewer consists of 4 key layers:

### 1. **State Management** (db.clj)
- Single `defonce` atom holding all app state
- Framework-agnostic (NO GUI imports)
- Hot-reload friendly (state persists across reloads)
- Optional: Integration with clojure.spec
- Optional: ClosedRecord wrapping for type safety

### 2. **Event Handling** (events.clj)
- Multimethod dispatch on `:event/type`
- Pure-ish functions (state transformations via `swap!`)
- Framework-agnostic (NO GUI imports)
- Testable in isolation

### 3. **Effects System** (effects/swing.clj)
- ALL Swing mutations isolated here
- EDT-safe via `SwingUtilities/invokeLater`
- Selective UI updates (diff old/new state)
- Watcher-based reactivity

### 4. **UI Construction** (ui.clj or functional_swing.clj)
- Pure widget creation functions
- Event wiring to dispatch system
- Widget reference storage in state atom
- Keyboard shortcut registration

---

## Pattern Comparison: Mailmerge vs BD-Viewer

### Common Structure (Identical)

| Layer | Mailmerge | BD-Viewer | Extractable? |
|-------|-----------|-----------|--------------|
| State atom | `defonce *app-state` | `defonce *app-state` | ✅ Pattern |
| Event dispatch | `defmulti handle-event` | `defmulti handle-event` | ✅ Pattern |
| EDT safety | `invoke-on-edt!` | `SwingUtilities/invokeLater` | ✅ Function |
| Watchers | `add-watch` + diff | `add-watch` + diff | ✅ Pattern |
| Widget refs | In `*ui-components` atom | In state `:ui-refs` | ✅ Pattern |

### Differences (Application-Specific)

| Aspect | Mailmerge | BD-Viewer |
|--------|-----------|-----------|
| **Data Source** | TOML + CSV files | bd CLI JSON |
| **Main Widget** | Dropdown + TextArea | JList + Detail Panel |
| **State Shape** | `:current-sponsor-idx`, `:rendered-email` | `:issues`, `:selected-issue`, `:filter-text` |
| **Key Events** | `::sponsor-selected`, `::copy-to-clipboard` | `::issue-selected`, `::filter-changed` |
| **Extras** | Gmail integration, markdown rendering | ClosedRecord, state.edn debugging |

**Key Insight**: The **structure is identical**, only the **domain logic differs**.

---

## What Can Be Extracted Into a Library?

### Option 1: Minimal Library (Core Patterns Only)

Extract just the **reusable utilities**:

```clojure
(ns functional-swing.core)

;; 1. EDT Safety
(defn invoke-on-edt! [f]
  (javax.swing.SwingUtilities/invokeLater f))

;; 2. State Watcher Setup
(defn setup-state-watcher!
  "Add watcher that calls effect-fn when state changes.
  effect-fn receives [old-state new-state]."
  [state-atom effect-fn]
  (add-watch state-atom ::ui-sync
    (fn [_ _ old-state new-state]
      (when (not= old-state new-state)
        (invoke-on-edt! #(effect-fn old-state new-state))))))

;; 3. Hot Reload Helper
(defn reload-namespaces!
  "Reload namespaces in dependency order."
  [namespace-symbols]
  (doseq [ns-sym namespace-symbols]
    (require ns-sym :reload)))

;; 4. Frame Refresh
(defn refresh-frame! [frame panel]
  (.setContentPane frame panel)
  (.revalidate frame)
  (.repaint frame))

;; 5. Keyboard Shortcut Registration
(defn register-shortcut!
  "Register keyboard shortcut on frame.
  keys: vector of KeyEvent constants or KeyStroke
  action-fn: zero-arg function to call"
  [frame action-name keystroke action-fn]
  (let [content-pane (.getContentPane frame)
        input-map (.getInputMap content-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
        action-map (.getActionMap content-pane)]
    (.put input-map keystroke action-name)
    (.put action-map action-name
          (proxy [AbstractAction] []
            (actionPerformed [e] (action-fn))))))

;; 6. Dialog Helpers
(defn show-error! [parent title message]
  (JOptionPane/showMessageDialog parent message title JOptionPane/ERROR_MESSAGE))

(defn show-info! [parent title message]
  (JOptionPane/showMessageDialog parent message title JOptionPane/INFORMATION_MESSAGE))
```

**Pros**:
- Tiny, focused library
- No opinions about app structure
- Easy to maintain
- Learn the pattern explicitly

**Cons**:
- Doesn't enforce the pattern
- Still requires boilerplate in each app
- No reduction in code duplication

---

### Option 2: Opinionated Framework (Full Pattern Extraction)

Extract the **entire architecture pattern**:

```clojure
(ns functional-swing.framework)

;; State Management
(defmacro defstate
  "Define app state atom with automatic watcher setup."
  [name initial-state & {:keys [effects-fn]}]
  `(do
     (defonce ~name (atom ~initial-state))
     (when ~effects-fn
       (setup-state-watcher! ~name ~effects-fn))))

;; Event System
(defmacro defevent
  "Define event handler for multimethod dispatch."
  [event-type args & body]
  `(defmethod handle-event ~event-type [~args]
     ~@body))

;; Widget Construction
(defn widget [type & {:as props}]
  "Create Swing widget with props map."
  ...)

;; Hot Reload
(defn reload-app!
  "Hot reload all app namespaces and rebuild UI."
  [namespaces create-ui-fn frame]
  ...)
```

**Pros**:
- Enforces the pattern
- Reduces boilerplate dramatically
- Consistency across projects
- Could add developer tools (state inspector, event logger)

**Cons**:
- More complex library to maintain
- Harder to customize
- Learning curve for the framework
- Risk of over-abstraction

---

## Option 3: Seesaw Rewrite (The Alternative)

Instead of extracting a library from raw Swing, **use Seesaw** - an existing, mature library that already solves many problems.

### What Seesaw Provides

1. **Declarative UI Construction**
```clojure
(use 'seesaw.core)

;; Instead of imperative Java calls:
(def frame (JFrame. "My App"))
(.setSize frame 800 600)
(.setVisible frame true)

;; Use declarative functions:
(def frame (frame :title "My App"
                  :size [800 :by 600]
                  :visible? true))
```

2. **Selector-Based Updates** (like CSS!)
```clojure
;; Update widgets by selector, not by storing references
(config! frame [:JButton] :enabled? false)
(select frame [:#my-button])  ;; ID selector
(select frame [:.error])      ;; Class selector
```

3. **Simplified Event Handling**
```clojure
;; Instead of verbose Java listeners:
(.addActionListener button
  (reify ActionListener
    (actionPerformed [_ e] ...)))

;; Use simple functions:
(listen button :action (fn [e] ...))
```

4. **Layout DSL**
```clojure
;; Tree-like structure that matches visual hierarchy
(frame
  :title "BD Viewer"
  :content (border-panel
             :north (toolbar ...)
             :west (scrollable (listbox :model issues))
             :center (detail-panel ...)))
```

5. **Bindings** (Reactive-ish)
```clojure
;; Bind widget property to atom
(bind (atom "Hello") (text :text) my-label)
;; Atom changes automatically update label!
```

### How Seesaw Fits the Functional Pattern

You can **combine Seesaw with the functional pattern**:

```clojure
(ns myapp.ui
  (:require [seesaw.core :as ss]
            [myapp.db :as db]
            [myapp.events :as events]))

;; State management (same as before)
(defonce *app-state (atom {...}))

;; Event handling (same as before)
(defmulti handle-event :event/type)

;; UI construction (now with Seesaw!)
(defn create-ui []
  (ss/frame
    :title "BD Viewer"
    :size [1000 :by 700]
    :content (ss/border-panel
               :north (ss/horizontal-panel
                        :items [(ss/text :id :search-field
                                        :columns 30
                                        :listen [:document (fn [e]
                                                            (events/handle-event
                                                              {:event/type ::filter-changed
                                                               :text (ss/text e)}))])
                                (ss/button :text "Reload"
                                          :listen [:action (fn [_]
                                                            (events/handle-event
                                                              {:event/type ::reload-issues}))])])

               :center (ss/left-right-split
                         ;; Left: Issue list
                         (ss/scrollable
                           (ss/listbox :id :issue-list
                                      :model (map :title (:issues @*app-state))
                                      :listen [:selection (fn [e]
                                                           (when-let [idx (ss/selection e)]
                                                             (events/handle-event
                                                               {:event/type ::issue-selected
                                                                :index idx})))]))

                         ;; Right: Detail panel
                         (ss/vertical-panel :id :detail-panel
                                           :items [(ss/label :id :title-label "No selection")
                                                  (ss/text :id :description-area
                                                          :multi-line? true
                                                          :editable? false)])
                         :divider-location 400))))

;; Effects (now use Seesaw selectors!)
(defn update-ui! [old-state new-state frame]
  ;; No need to store widget references! Use selectors.
  (when (not= (:issues old-state) (:issues new-state))
    (ss/config! (ss/select frame [:#issue-list])
                :model (map :title (:issues new-state))))

  (when (not= (:selected-issue old-state) (:selected-issue new-state))
    (let [issue (get-issue-by-id (:selected-issue new-state))]
      (ss/config! (ss/select frame [:#title-label])
                  :text (:title issue))
      (ss/config! (ss/select frame [:#description-area])
                  :text (:description issue)))))

;; Watcher (same pattern!)
(add-watch *app-state ::ui-sync
  (fn [_ _ old new]
    (ss/invoke-later
      (update-ui! old new @*frame))))
```

### Seesaw Benefits

✅ **Drastically less boilerplate** - No manual Java interop
✅ **Declarative UI** - Easier to read and modify
✅ **Selector-based updates** - No widget reference management
✅ **Better layout DSL** - Tree structure matches visual hierarchy
✅ **Mature, documented** - Active community, good docs
✅ **Still allows functional pattern** - Works with atoms, events, watchers
✅ **Easier testing** - Can construct UI in tests without showing windows

### Seesaw Tradeoffs

❌ **Learning curve** - New API to learn (though simpler than raw Swing)
❌ **Another dependency** - External library instead of raw JDK
❌ **Less control** - Abstraction hides some Swing details
❌ **Binding magic** - Bindings are convenient but less explicit
❌ **Selector overhead** - ID/class lookups slower than direct references (negligible for UI)

---

## Recommendation: Hybrid Approach

### For bd-viewer (Current Project)
**Keep the current functional Swing pattern** because:
1. It's already working well
2. Pattern is proven and debuggable
3. Good learning experience with raw Swing
4. ClosedRecord + state.edn are valuable additions

### For Future Projects
**Use Seesaw + Functional Pattern** because:
1. Combine best of both worlds
2. Less boilerplate than raw Swing
3. Keep functional architecture (atom + events + watchers)
4. Faster development velocity

### Minimal Library Extraction
**Create tiny `functional-swing-utils`** library with just:
1. `invoke-on-edt!` - EDT safety wrapper
2. `setup-state-watcher!` - Watcher registration
3. `refresh-frame!` - Hot reload helper
4. `register-shortcut!` - Keyboard shortcut helper
5. Dialog helpers (`show-error!`, `show-info!`)

This gives you **utilities without enforcing architecture**.

---

## Example: Minimal Library Usage

### The Library (functional-swing-utils)

```clojure
(ns functional-swing-utils.core
  (:import [javax.swing SwingUtilities JComponent KeyStroke AbstractAction
                        JOptionPane]
           [java.awt.event KeyEvent]))

(defn invoke-on-edt!
  "Execute function on Swing Event Dispatch Thread."
  [f]
  (SwingUtilities/invokeLater f))

(defn setup-state-watcher!
  "Add watcher that calls effect-fn on EDT when state changes.
  effect-fn receives [old-state new-state]."
  ([state-atom effect-fn]
   (setup-state-watcher! state-atom ::ui-sync effect-fn))
  ([state-atom watch-key effect-fn]
   (add-watch state-atom watch-key
     (fn [_ _ old-state new-state]
       (when (not= old-state new-state)
         (invoke-on-edt! #(effect-fn old-state new-state)))))))

(defn remove-state-watcher!
  "Remove watcher by key."
  ([state-atom]
   (remove-state-watcher! state-atom ::ui-sync))
  ([state-atom watch-key]
   (remove-watch state-atom watch-key)))

(defn refresh-frame!
  "Replace frame content pane with new panel."
  [frame panel]
  (.setContentPane frame panel)
  (.revalidate frame)
  (.repaint frame))

(defn register-shortcut!
  "Register keyboard shortcut on frame.

  Examples:
    (register-shortcut! frame \"reload\" (cmd-r) #(reload!))
    (register-shortcut! frame \"delete\" (KeyStroke/getKeyStroke KeyEvent/VK_DELETE 0) #(delete!))"
  [frame action-name keystroke action-fn]
  (let [content-pane (.getContentPane frame)
        input-map (.getInputMap content-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
        action-map (.getActionMap content-pane)]
    (.put input-map keystroke action-name)
    (.put action-map action-name
          (proxy [AbstractAction] []
            (actionPerformed [e] (action-fn))))))

(defn cmd-keystroke
  "Create Cmd+key keystroke (cross-platform)."
  [key-event-vk]
  (let [cmd-mask (.getMenuShortcutKeyMaskEx (java.awt.Toolkit/getDefaultToolkit))]
    (KeyStroke/getKeyStroke key-event-vk cmd-mask)))

(defn cmd-shift-keystroke
  "Create Cmd+Shift+key keystroke."
  [key-event-vk]
  (let [cmd-mask (.getMenuShortcutKeyMaskEx (java.awt.Toolkit/getDefaultToolkit))
        shift-mask java.awt.event.InputEvent/SHIFT_DOWN_MASK]
    (KeyStroke/getKeyStroke key-event-vk (bit-or cmd-mask shift-mask))))

(defn show-error!
  "Show error dialog (modal)."
  ([message]
   (show-error! nil "Error" message))
  ([parent title message]
   (JOptionPane/showMessageDialog parent message title JOptionPane/ERROR_MESSAGE)))

(defn show-info!
  "Show info dialog (modal)."
  ([message]
   (show-info! nil "Info" message))
  ([parent title message]
   (JOptionPane/showMessageDialog parent message title JOptionPane/INFORMATION_MESSAGE)))

(defn show-confirm!
  "Show confirmation dialog. Returns true if user clicked Yes."
  ([message]
   (show-confirm! nil "Confirm" message))
  ([parent title message]
   (= JOptionPane/YES_OPTION
      (JOptionPane/showConfirmDialog parent message title
                                     JOptionPane/YES_NO_OPTION))))

;; Hot reload helpers
(defn reload-namespaces!
  "Reload namespaces in order. Pass symbols like 'myapp.db, 'myapp.events."
  [& namespace-symbols]
  (doseq [ns-sym namespace-symbols]
    (require ns-sym :reload)))

(defn reload-and-rebuild!
  "Hot reload namespaces and rebuild UI.

  Example:
    (reload-and-rebuild!
      frame
      'myapp.ui/create-main-panel
      'myapp.keyboard/setup-shortcuts
      'myapp.db 'myapp.events 'myapp.ui)"
  [frame create-ui-fn-sym setup-shortcuts-fn-sym & namespace-symbols]
  (apply reload-namespaces! namespace-symbols)
  (let [create-ui-fn (resolve create-ui-fn-sym)
        setup-shortcuts-fn (resolve setup-shortcuts-fn-sym)]
    (when (and frame create-ui-fn)
      (refresh-frame! frame (create-ui-fn))
      (when setup-shortcuts-fn
        (setup-shortcuts-fn frame)))))
```

### Using the Library in bd-viewer

```clojure
;; deps.edn
{:deps {functional-swing-utils {:local/root "../functional-swing-utils"}}}

;; In bd-viewer/effects/swing.clj
(ns bd-viewer.effects.swing
  (:require [functional-swing-utils.core :as fsu]
            [bd-viewer.db :as db]))

(defn update-issue-list! [old-state new-state]
  ;; Just use your effect logic - EDT safety handled by watcher
  ...)

(defn setup-watchers! []
  (fsu/setup-state-watcher! db/*app-state
    (fn [old new]
      (update-issue-list! old new)
      (update-selection! old new)
      (update-detail-panel! old new))))

;; In bd-viewer/keyboard.clj
(ns bd-viewer.keyboard
  (:require [functional-swing-utils.core :as fsu]
            [bd-viewer.events :as events])
  (:import [java.awt.event KeyEvent]))

(defn setup-shortcuts! [frame]
  (fsu/register-shortcut! frame "reload"
    (fsu/cmd-keystroke KeyEvent/VK_R)
    #(events/handle-event {:event/type ::events/reload-issues}))

  (fsu/register-shortcut! frame "delete"
    (fsu/cmd-keystroke KeyEvent/VK_D)
    #(events/handle-event {:event/type ::events/delete-issue}))

  (fsu/register-shortcut! frame "next"
    (KeyStroke/getKeyStroke KeyEvent/VK_J 0)
    #(events/handle-event {:event/type ::events/next-issue})))

;; In bd-viewer/events.clj
(defmethod handle-event ::reload-code [_]
  (let [frame (get-in @db/*app-state [:ui-refs :frame])]
    (fsu/reload-and-rebuild! frame
      'bd-viewer.ui/create-main-frame
      'bd-viewer.keyboard/setup-shortcuts
      'bd-viewer.db
      'bd-viewer.events
      'bd-viewer.effects.swing
      'bd-viewer.ui
      'bd-viewer.keyboard)))
```

**Result**: You get the utilities without losing flexibility!

---

## Concrete Next Steps

### Immediate (This Week)
1. ✅ Keep current bd-viewer as-is (it works!)
2. ✅ Document patterns in this file
3. ✅ Note what's reusable for future

### Short-term (Next Project)
1. Try **Seesaw + Functional Pattern** hybrid
2. Build small app to validate approach
3. Compare development velocity

### Medium-term (If Building Multiple Swing Apps)
1. Extract minimal `functional-swing-utils` library
2. Share between projects
3. Iterate based on usage

### Long-term (If Serious About Swing)
1. Consider building opinionated framework
2. Add developer tools (state inspector, event logger)
3. Write comprehensive guide

---

## Comparison Matrix

| Approach | Boilerplate | Flexibility | Learning Curve | Maintainability |
|----------|-------------|-------------|----------------|-----------------|
| **Raw Swing (Current)** | ⚠️ High | ✅ Maximum | ⚠️ Steep | ✅ Explicit |
| **Minimal Utils Library** | ⚠️ Medium-High | ✅ Very High | ✅ Low | ✅ Simple |
| **Opinionated Framework** | ✅ Low | ⚠️ Medium | ⚠️ Medium | ⚠️ Complex |
| **Seesaw + Functional** | ✅ Very Low | ✅ High | ⚠️ Medium | ✅ Mature |
| **Pure Seesaw (with bindings)** | ✅ Lowest | ⚠️ Medium | ⚠️ Medium | ⚠️ Magic |

---

## Conclusion

The **functional Swing pattern** successfully extracted from mailmerge is:
1. ✅ **Proven** - Works in both projects
2. ✅ **Clean** - Clear separation of concerns
3. ✅ **Testable** - Pure functions for logic
4. ✅ **Hot-reloadable** - Fast development iteration

**For extracting into a library:**

### Best Immediate Action
**Create minimal `functional-swing-utils`** with just:
- EDT safety helpers
- Watcher setup
- Hot reload helpers
- Keyboard shortcut registration
- Dialog helpers

This gives **90% of the benefit with 10% of the complexity**.

### Best Long-term Strategy
**Try Seesaw on next project**:
- Keep functional pattern (atom + events + watchers)
- Use Seesaw for UI construction
- Compare productivity vs raw Swing
- Decide based on real experience

### Don't Do (Yet)
**Don't build opinionated framework** unless:
- You're building 3+ Swing apps
- Pattern is fully stable
- You have time for maintenance

---

## Appendix: Seesaw Quick Start

If you want to try Seesaw with the functional pattern:

### deps.edn
```clojure
{:deps {seesaw/seesaw {:mvn/version "1.5.0"}}}
```

### Minimal Example
```clojure
(ns myapp.core
  (:require [seesaw.core :as ss]))

;; State
(defonce *state (atom {:count 0}))

;; Events
(defmulti handle-event :event/type)

(defmethod handle-event ::increment [_]
  (swap! *state update :count inc))

;; UI
(defn create-ui []
  (ss/frame
    :title "Counter"
    :content (ss/vertical-panel
               :items [(ss/label :id :counter :text "Count: 0")
                      (ss/button :text "Increment"
                                :listen [:action (fn [_]
                                                  (handle-event {:event/type ::increment}))])])))

;; Effects
(defn update-ui! [frame old new]
  (when (not= (:count old) (:count new))
    (ss/config! (ss/select frame [:#counter])
                :text (str "Count: " (:count new)))))

;; Start
(defn start! []
  (let [frame (create-ui)]
    (add-watch *state ::ui
      (fn [_ _ old new]
        (ss/invoke-later (update-ui! frame old new))))
    (ss/show! frame)))
```

**That's it!** Functional pattern + Seesaw simplicity.

---

## Final Recommendation

🎯 **For bd-viewer**: Keep current approach (it's working great!)

🎯 **For next project**: Try Seesaw + Functional Pattern

🎯 **For reusability**: Extract minimal `functional-swing-utils` library

This gives you the best of both worlds: proven patterns + reduced boilerplate + flexibility.
