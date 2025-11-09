# Re-Swing Design Exploration: What Actually Feels Good?

## The Journey: From Re-frame Clone to "Just Fix The Pain"

This document captures the exploration of what a functional Swing library should actually be.

**TL;DR:** We tried 3 styles. **Style 3 (Just Fix Annoying Parts) wins** because it removes ceremony without hiding behavior.

---

## The Original Question

> "Should we build a re-frame-like library for Swing that enforces the db/events/views pattern?"

After exploring this, the answer is: **No, but...**

The pattern (db.clj, events.clj, ui.clj) is already good. What we need is to **remove the painful boilerplate**, not enforce structure.

---

## Style 1: Minimal Helpers (Not Enough Help)

**Concept:** Just provide helper functions, no opinions.

### Code Example

```clojure
;; re-swing/core.clj
(ns re-swing.core)

(defn on-edt [f]
  (SwingUtilities/invokeLater f))

(defn listen
  "Generic listener - you specify the type."
  [widget event-type handler]
  (case event-type
    :selection (add-selection-listener widget handler)
    :action (add-action-listener widget handler)
    :text-change (add-document-listener widget handler)))

(defn watch-and-update [*atom path widget update-fn]
  (add-watch *atom (gensym)
    (fn [_ _ old new]
      (when (not= (get-in old path) (get-in new path))
        (on-edt #(update-fn widget (get-in new path)))))))
```

### Usage

```clojure
(ns bdviewer2.ui
  (:require [re-swing.core :as swing]))

(defn create-issue-list [*state]
  (let [jlist (JList.)]
    (swing/listen jlist :selection
      (fn [e]
        (events/handle-event {...})))

    (swing/watch-and-update *state [:issues] jlist
      (fn [list issues]
        (update-list-model list issues)))

    jlist))
```

### Assessment

**Pros:**
- ✅ Simple to understand
- ✅ Minimal abstraction

**Cons:**
- ❌ Generic `listen` - have to remember event types
- ❌ Doesn't save enough lines
- ❌ Still feels like Java with helpers

**Verdict:** Not enough benefit for the effort.

---

## Style 2: Component Builders (Too Much Magic)

**Concept:** Declarative component creation with auto-wiring.

### Code Example

```clojure
;; re-swing/components.clj
(ns re-swing.components)

(defn list-box
  "Create a JList with automatic wiring.

  Options:
    :items - Initial items
    :on-select - Selection handler
    :watch - [atom path] to auto-update
    :renderer - Custom cell renderer"
  [& {:keys [items on-select watch renderer]}]
  (let [model (DefaultListModel.)
        jlist (JList. model)]

    ;; Setup items
    (when items
      (doseq [item items]
        (.addElement model item)))

    ;; Setup selection handler
    (when on-select
      (.addListSelectionListener jlist
        (reify ListSelectionListener
          (valueChanged [_ e]
            (when-not (.getValueIsAdjusting e)
              (let [idx (.getSelectedIndex jlist)
                    item (when (>= idx 0) (.getElementAt model idx))]
                (on-select item idx)))))))

    ;; Setup auto-update watcher
    (when watch
      (let [[*atom path] watch]
        (watch-and-update *atom path jlist
          (fn [list new-items]
            (.clear model)
            (doseq [item new-items]
              (.addElement model item))))))

    jlist))

(defn frame [& {:keys [title size content]}]
  ...)

(defn border-panel [& {:keys [north south east west center]}]
  ...)
```

### Usage

```clojure
(ns bdviewer2.ui
  (:require [re-swing.components :as ui]))

(defn create-ui []
  (ui/frame
    :title "BD Viewer"
    :size [1000 700]
    :content
    (ui/border-panel
      :north (ui/toolbar
               :items [(ui/search-field
                         :on-change #(events/filter-changed! %))
                       (ui/button "Reload" #(events/reload-issues!))])

      :center (ui/split-pane
                :left (ui/list-box
                        :watch [db/*state [:issues]]
                        :on-select (fn [issue idx]
                                    (events/select-issue! (:id issue) idx))
                        :renderer issue-renderer)

                :right (ui/detail-panel
                         :title {:watch [db/*state [:selected-issue :title]]}
                         :description {:watch [db/*state [:selected-issue :description]]})))))
```

### Assessment

**Pros:**
- ✅ Very concise
- ✅ Declarative, tree-like structure
- ✅ Auto-wiring of watchers

**Cons:**
- ❌ Too much magic - where does the watcher run?
- ❌ What if I need to customize beyond the options?
- ❌ Learning a DSL instead of Swing
- ❌ Hard to debug - lots of indirection
- ❌ Large library to maintain

**Verdict:** Too clever. Fights you when you need something custom.

---

## Style 3: Just Fix Annoying Parts ⭐ WINNER

**Concept:** Small, focused helpers that remove ceremony without hiding behavior.

### Code Example

```clojure
;; re-swing/core.clj (~150 LOC total)
(ns re-swing.core
  (:import [javax.swing SwingUtilities JComponent KeyStroke AbstractAction]
           [javax.swing.event ListSelectionListener DocumentListener]
           [java.awt.event ActionListener KeyEvent]
           [java.awt Toolkit]))

;; ============================================================================
;; EDT Safety
;; ============================================================================

(defn on-edt!
  "Run function on Event Dispatch Thread.
  Safe to call from EDT (no-op in that case)."
  [f]
  (if (SwingUtilities/isEventDispatchThread)
    (f)
    (SwingUtilities/invokeLater f)))

;; ============================================================================
;; Watchers (Automatic Diff + EDT)
;; ============================================================================

(defn watch!
  "Watch atom path and run handler on EDT when value changes.
  Handler receives [old-value new-value].

  Example:
    (watch! *state [:issues]
      (fn [old new]
        (.updateModel my-list new)))"
  [*atom path handler]
  (add-watch *atom (gensym "watch-")
    (fn [_ _ old-state new-state]
      (let [old-val (get-in old-state path)
            new-val (get-in new-state path)]
        (when (not= old-val new-val)
          (on-edt! #(handler old-val new-val)))))))

;; ============================================================================
;; Listeners (Less Java Boilerplate)
;; ============================================================================

(defn on-selection
  "Add selection listener to JList.
  Handler receives selected index.
  Handles getValueIsAdjusting check automatically.

  Example:
    (on-selection my-list
      (fn [idx]
        (when (>= idx 0)
          (handle-selection idx))))"
  [jlist handler]
  (.addListSelectionListener jlist
    (reify ListSelectionListener
      (valueChanged [_ e]
        (when-not (.getValueIsAdjusting e)
          (handler (.getSelectedIndex jlist)))))))

(defn on-action
  "Add action listener to button.
  Handler receives ActionEvent.

  Example:
    (on-action my-button
      (fn [e]
        (do-something)))"
  [button handler]
  (.addActionListener button
    (reify ActionListener
      (actionPerformed [_ e]
        (handler e)))))

(defn on-text-change
  "Add document listener to text field/area.
  Handler receives current text on any change.

  Example:
    (on-text-change search-field
      (fn [text]
        (filter-items text)))"
  [text-component handler]
  (.. text-component getDocument
      (addDocumentListener
        (reify DocumentListener
          (insertUpdate [_ e] (handler (.getText text-component)))
          (removeUpdate [_ e] (handler (.getText text-component)))
          (changedUpdate [_ e] (handler (.getText text-component)))))))

;; ============================================================================
;; Keyboard Shortcuts (Way Less Verbose!)
;; ============================================================================

(defn- parse-key-string
  "Parse key string like 'cmd-r' or 'shift-j' into modifiers and key code."
  [key-str]
  (let [parts (clojure.string/split key-str #"-")
        cmd-mask (.getMenuShortcutKeyMaskEx (Toolkit/getDefaultToolkit))
        shift-mask java.awt.event.InputEvent/SHIFT_DOWN_MASK
        alt-mask java.awt.event.InputEvent/ALT_DOWN_MASK

        modifiers (reduce
                   (fn [mask part]
                     (case part
                       "cmd" (bit-or mask cmd-mask)
                       "shift" (bit-or mask shift-mask)
                       "alt" (bit-or mask alt-mask)
                       mask))
                   0
                   (butlast parts))

        key-part (last parts)
        key-code (if (= 1 (count key-part))
                   ;; Single char like "j" or "r"
                   (.. (Character/toUpperCase (first key-part)) (int))
                   ;; Named key like "DELETE"
                   (get {"delete" KeyEvent/VK_DELETE
                         "enter" KeyEvent/VK_ENTER
                         "escape" KeyEvent/VK_ESCAPE
                         "tab" KeyEvent/VK_TAB
                         "space" KeyEvent/VK_SPACE}
                        (clojure.string/lower-case key-part)))]
    [modifiers key-code]))

(defn kbd!
  "Register keyboard shortcut on frame.
  Handler receives KeyEvent.

  Key string format:
    - Single key: 'j', 'k', 'o'
    - With cmd: 'cmd-r', 'cmd-s'
    - With shift: 'shift-j', 'cmd-shift-r'
    - Named keys: 'delete', 'enter', 'escape'

  Examples:
    (kbd! frame \"cmd-r\" #(reload!))
    (kbd! frame \"j\" #(next-item))
    (kbd! frame \"shift-j\" #(prev-item))
    (kbd! frame \"delete\" #(delete-item))"
  [frame key-str handler]
  (let [[modifiers key-code] (parse-key-string key-str)
        keystroke (KeyStroke/getKeyStroke key-code modifiers)
        action-name (str (gensym "action-"))]
    (.. frame getContentPane
        (getInputMap JComponent/WHEN_IN_FOCUSED_WINDOW)
        (put keystroke action-name))
    (.. frame getContentPane
        (getActionMap)
        (put action-name
             (proxy [AbstractAction] []
               (actionPerformed [e] (handler e)))))))

;; ============================================================================
;; Dialog Helpers (Optional)
;; ============================================================================

(defn confirm!
  "Show yes/no confirmation dialog.
  Returns true if user clicked Yes."
  ([message]
   (confirm! nil "Confirm" message))
  ([parent title message]
   (= javax.swing.JOptionPane/YES_OPTION
      (javax.swing.JOptionPane/showConfirmDialog
       parent message title
       javax.swing.JOptionPane/YES_NO_OPTION))))

(defn error!
  "Show error dialog."
  ([message]
   (error! nil "Error" message))
  ([parent title message]
   (javax.swing.JOptionPane/showMessageDialog
    parent message title
    javax.swing.JOptionPane/ERROR_MESSAGE)))
```

### Usage - Before and After

#### Keyboard Shortcuts

**BEFORE (9 lines of ceremony):**
```clojure
(let [content-pane (.getContentPane frame)
      cmd-mask (.getMenuShortcutKeyMaskEx (Toolkit/getDefaultToolkit))]
  (.put (.getInputMap content-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
        (KeyStroke/getKeyStroke KeyEvent/VK_R cmd-mask)
        "reload")
  (.put (.getActionMap content-pane) "reload"
        (proxy [AbstractAction] []
          (actionPerformed [e]
            (events/handle-event {:event/type ::events/reload-issues})))))
```

**AFTER (1 line!):**
```clojure
(kbd! frame "cmd-r" #(events/handle-event {:event/type ::events/reload-issues}))
```

#### List Selection Listener

**BEFORE (9 lines):**
```clojure
(.addListSelectionListener jlist
  (reify ListSelectionListener
    (valueChanged [_ e]
      (when-not (.getValueIsAdjusting e)
        (let [index (.getSelectedIndex jlist)]
          (when (>= index 0)
            (let [filtered (db/get-filtered-issues)
                  issue (nth filtered index)]
              (events/handle-event {:event/type ::events/issue-selected
                                   :issue-id (:id issue)}))))))))
```

**AFTER (5 lines):**
```clojure
(on-selection jlist
  (fn [idx]
    (when (>= idx 0)
      (let [issue (nth (db/get-filtered-issues) idx)]
        (events/handle-event {:event/type ::events/issue-selected
                             :issue-id (:id issue)})))))
```

#### State Watchers

**BEFORE (7 lines with manual diff):**
```clojure
(add-watch db/*app-state ::sync-ui
  (fn [_ _ old-state new-state]
    (when (not= (:issues old-state) (:issues new-state))
      (SwingUtilities/invokeLater
        (fn []
          (let [jlist (get-in new-state [:ui-refs :issue-list])]
            (update-issue-list! jlist (:issues new-state))))))))
```

**AFTER (3 lines with automatic diff + EDT):**
```clojure
(watch! db/*app-state [:issues]
  (fn [old new]
    (update-issue-list! my-jlist new)))
```

#### Text Field Listener

**BEFORE (10 lines of DocumentListener boilerplate):**
```clojure
(.. search-field getDocument
    (addDocumentListener
      (reify DocumentListener
        (insertUpdate [_ e]
          (events/handle-event
            {:event/type ::events/filter-changed
             :text (.getText search-field)}))
        (removeUpdate [_ e]
          (events/handle-event
            {:event/type ::events/filter-changed
             :text (.getText search-field)}))
        (changedUpdate [_ e]
          (events/handle-event
            {:event/type ::events/filter-changed
             :text (.getText search-field)})))))
```

**AFTER (3 lines):**
```clojure
(on-text-change search-field
  (fn [text]
    (events/handle-event {:event/type ::events/filter-changed :text text})))
```

### Full Example: bd-viewer with re-swing

```clojure
(ns bdviewer2.ui
  (:require [re-swing.core :as rs]
            [bdviewer2.db :as db]
            [bdviewer2.events :as events])
  (:import [javax.swing JFrame JList JPanel JTextField JButton JLabel JTextArea
                        JScrollPane JSplitPane DefaultListModel BorderFactory]
           [java.awt BorderLayout GridLayout FlowLayout Font Color Dimension]))

(defn create-issue-list []
  "Create issue list with auto-update."
  (let [model (DefaultListModel.)
        jlist (doto (JList. model)
                (.setFont (Font. Font/MONOSPACED Font/PLAIN 12)))]

    ;; Selection handler - way less verbose!
    (rs/on-selection jlist
      (fn [idx]
        (when (>= idx 0)
          (let [issue (nth (db/get-filtered-issues) idx)]
            (events/handle-event {:event/type ::events/issue-selected
                                 :issue-id (:id issue)
                                 :index idx})))))

    ;; Auto-update when issues change - automatic diff + EDT!
    (rs/watch! db/*app-state [:issues]
      (fn [_ new-issues]
        (.clear model)
        (doseq [issue (db/get-filtered-issues)]
          (.addElement model (format "%s [P%d] %s"
                                    (:id issue)
                                    (:priority issue)
                                    (:title issue))))))

    ;; Also watch filter changes
    (rs/watch! db/*app-state [:filter-text]
      (fn [_ _]
        (.clear model)
        (doseq [issue (db/get-filtered-issues)]
          (.addElement model (format "%s [P%d] %s"
                                    (:id issue)
                                    (:priority issue)
                                    (:title issue))))))

    jlist))

(defn create-search-bar []
  "Create search field with auto-filtering."
  (let [field (JTextField. 30)]
    ;; Text change handler - no DocumentListener boilerplate!
    (rs/on-text-change field
      (fn [text]
        (events/handle-event {:event/type ::events/filter-changed :text text})))
    field))

(defn create-toolbar []
  "Create toolbar with buttons."
  (let [reload-btn (JButton. "Reload (⌘R)")
        delete-btn (JButton. "Delete (⌘D)")]

    ;; Button handlers - clean!
    (rs/on-action reload-btn
      (fn [_] (events/handle-event {:event/type ::events/reload-issues})))

    (rs/on-action delete-btn
      (fn [_]
        (when (rs/confirm! "Delete selected issue?")
          (events/handle-event {:event/type ::events/delete-issue}))))

    (doto (JPanel. (FlowLayout. FlowLayout/LEFT))
      (.add reload-btn)
      (.add delete-btn))))

(defn create-detail-panel []
  "Create detail panel with auto-updating labels."
  (let [title-label (JLabel. "No issue selected")
        desc-area (JTextArea. 10 40)
        id-label (JLabel. "")
        status-label (JLabel. "")
        priority-label (JLabel. "")

        metadata-panel (doto (JPanel. (GridLayout. 3 1))
                         (.add id-label)
                         (.add status-label)
                         (.add priority-label))

        panel (doto (JPanel. (BorderLayout.))
                (.add title-label BorderLayout/NORTH)
                (.add (JScrollPane. desc-area) BorderLayout/CENTER)
                (.add metadata-panel BorderLayout/SOUTH))]

    ;; Auto-update when selection changes - one watcher!
    (rs/watch! db/*app-state [:selected-issue]
      (fn [_ issue-id]
        (if-let [issue (db/get-issue-by-id issue-id)]
          (do
            (.setText title-label (:title issue))
            (.setText desc-area (:description issue ""))
            (.setText id-label (str "ID: " (:id issue)))
            (.setText status-label (str "Status: " (:status issue)))
            (.setText priority-label (str "Priority: P" (:priority issue))))
          (do
            (.setText title-label "No issue selected")
            (.setText desc-area "")
            (.setText id-label "")
            (.setText status-label "")
            (.setText priority-label "")))))

    panel))

(defn create-main-frame []
  "Create main application window."
  (let [frame (JFrame. "BD Viewer")

        ;; Components
        search-bar (create-search-bar)
        toolbar (create-toolbar)
        issue-list (create-issue-list)
        detail-panel (create-detail-panel)

        ;; Layout
        top-panel (doto (JPanel. (BorderLayout.))
                    (.add (JLabel. " Search: ") BorderLayout/WEST)
                    (.add search-bar BorderLayout/CENTER)
                    (.add toolbar BorderLayout/EAST))

        split-pane (doto (JSplitPane. JSplitPane/HORIZONTAL_SPLIT
                                     (JScrollPane. issue-list)
                                     detail-panel)
                     (.setDividerLocation 400))]

    ;; Assemble frame
    (doto (.getContentPane frame)
      (.setLayout (BorderLayout.))
      (.add top-panel BorderLayout/NORTH)
      (.add split-pane BorderLayout/CENTER))

    ;; Keyboard shortcuts - SO CLEAN NOW!
    (rs/kbd! frame "cmd-r" #(events/handle-event {:event/type ::events/reload-issues}))
    (rs/kbd! frame "cmd-d" #(events/handle-event {:event/type ::events/delete-issue}))
    (rs/kbd! frame "j" #(events/handle-event {:event/type ::events/next-issue}))
    (rs/kbd! frame "k" #(events/handle-event {:event/type ::events/prev-issue}))
    (rs/kbd! frame "o" #(events/handle-event {:event/type ::events/toggle-open-filter}))
    (rs/kbd! frame "cmd-shift-r" #(reload-code!))

    ;; Frame setup
    (doto frame
      (.setSize 1000 700)
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setLocationRelativeTo nil)
      (.setVisible true))

    frame))
```

### Assessment

**Pros:**
- ✅ **Clear** - Each function does exactly one thing
- ✅ **Obvious** - Reading `(kbd! frame "j" #(...))` tells you exactly what happens
- ✅ **No magic** - EDT wrapping and diff checking are explicit in function names
- ✅ **Small** - ~150 LOC library, easy to understand and maintain
- ✅ **Composable** - Use helpers independently or together
- ✅ **Debuggable** - When something breaks, you know where to look
- ✅ **Doesn't fight you** - Need custom behavior? Drop down to Swing
- ✅ **30-40% less code** - Real reduction in boilerplate

**Cons:**
- ⚠️ Still some Swing knowledge required (but way less!)

**Verdict:** ⭐ **This is the one!** Removes pain without hiding control flow.

---

## Why Style 3 Wins

### 1. It Reads Like What It Does

```clojure
;; You read this and KNOW what happens:
(kbd! frame "cmd-r" #(reload!))
(watch! *state [:issues] (fn [old new] (update-list! new)))
(on-selection jlist (fn [idx] (select-item idx)))
```

No mental translation needed. No wondering "where does this get called?"

### 2. The Pain Points Disappear

**Pain Point 1: EDT Boilerplate**
- Before: `SwingUtilities/invokeLater` everywhere
- After: Automatic in `watch!`, available as `on-edt!`

**Pain Point 2: Listener Verbosity**
- Before: 9 lines of `reify` and interface methods
- After: 3 lines with clear intent

**Pain Point 3: Manual Diff Checking**
- Before: Every watcher manually checks `(not= old new)`
- After: `watch!` does it automatically

**Pain Point 4: Keyboard Shortcuts**
- Before: 9 lines per shortcut with InputMap/ActionMap
- After: 1 line with readable key syntax

### 3. It's Still Explicit

```clojure
;; You can SEE:
;; - What atom is being watched
;; - What path is being monitored
;; - What happens when it changes
(watch! db/*app-state [:issues]
  (fn [old new]
    (update-issue-list! new)))

;; vs hidden magic:
;; - Where does this run?
;; - When does it update?
;; - How do I debug it?
(list-box :watch [db/*app-state [:issues]])
```

### 4. Small Surface Area

**7 functions:**
1. `on-edt!` - EDT safety
2. `watch!` - Atom watchers
3. `on-selection` - List selection
4. `on-action` - Button clicks
5. `on-text-change` - Text input
6. `kbd!` - Keyboard shortcuts
7. `confirm!` / `error!` - Dialogs

**That's it!** Learn these 7 functions and you're done.

### 5. Easy to Debug

```clojure
;; Something not updating?
;; Look at the watcher:
(watch! *state [:issues]
  (fn [old new]
    (println "ISSUES CHANGED:" old "->" new)  ; Add debug print
    (update-list! new)))

;; Keyboard shortcut not firing?
;; Look at the registration:
(kbd! frame "j"
  (fn [e]
    (println "J PRESSED!")  ; Add debug print
    (next-item!)))
```

No registry to inspect, no hidden indirection.

---

## Code Savings Comparison

### Keyboard Shortcuts

| Metric | Before | After | Savings |
|--------|--------|-------|---------|
| Lines per shortcut | 9 | 1 | **89%** |
| Lines for 6 shortcuts | 54 | 6 | **89%** |

### Listeners

| Metric | Before | After | Savings |
|--------|--------|-------|---------|
| List selection | 9 | 5 | **44%** |
| Button click | 5 | 2 | **60%** |
| Text change | 10 | 3 | **70%** |

### Watchers

| Metric | Before | After | Savings |
|--------|--------|-------|---------|
| Lines per watcher | 7 | 3 | **57%** |
| Typical file (4 watchers) | 28 | 12 | **57%** |

### Overall

**Estimated total code reduction: 35-45%** in UI-heavy files

---

## The Core Philosophy

> **Remove ceremony, not control.**

- ✅ Remove: Boilerplate, repetition, noise
- ❌ Don't remove: Clarity, debuggability, flexibility

### What This Means in Practice

**Good helper:**
```clojure
(kbd! frame "j" #(next!))
;; Clear: Press j → call next!
;; Removes: InputMap/ActionMap ceremony
;; Keeps: Explicit connection between key and action
```

**Too much magic:**
```clojure
(auto-bind *state [:items] my-list)
;; Unclear: How does it update? When? On what thread?
;; Removes: Too much - now you need docs to understand
;; Hides: The actual behavior
```

---

## Implementation Notes

### Library Structure

```
re-swing/
  src/re_swing/
    core.clj          ; All 7 core functions (~150 LOC)
  test/re_swing/
    core_test.clj     ; Unit tests
  README.md           ; Usage guide
  deps.edn
```

### Dependencies

**Zero dependencies!** Just JDK Swing.

### Testing Strategy

- Unit tests for each helper function
- Integration test with real widgets
- Manual testing in bdviewer2

---

## Next Steps

1. ✅ **Implement re-swing library** (~2 hours)
   - Core functions
   - Docstrings
   - Unit tests

2. ✅ **Build bdviewer2** (~3 hours)
   - Full feature parity with bd-viewer
   - Use re-swing exclusively
   - Measure code reduction

3. ✅ **Compare** (~1 hour)
   - Line count
   - Code samples side-by-side
   - Developer experience notes

4. ✅ **Document** (~1 hour)
   - Migration guide
   - Best practices
   - API reference

---

## Conclusion

**Style 3 (Just Fix Annoying Parts) is the winner** because:

1. Solves real pain points (EDT, listeners, watchers, keyboard shortcuts)
2. Small, focused library (~150 LOC)
3. Doesn't hide what's happening
4. Easy to debug
5. 35-45% code reduction
6. Doesn't enforce structure - use where helpful

This is a library I **actually want to use**, not one that feels like homework.

Let's build it! 🚀
