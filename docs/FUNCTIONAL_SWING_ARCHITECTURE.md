# Functional Swing Architecture for bd-viewer

This document explains the functional programming style used in the mailmerge project and how we'll apply it to bd-viewer.

## Core Philosophy

Traditional imperative Swing code mixes state mutations, UI updates, and business logic. The functional approach **separates concerns**:

1. **Pure State** - Immutable data in atoms
2. **Pure Events** - Functions that transform state (no side effects)
3. **Effects System** - Isolated mutations that sync UI to state
4. **Reactive Updates** - Watchers automatically propagate changes

This gives us **hot reloading**, **testability**, and **reasoning about state changes**.

## Architecture Layers

```
┌─────────────────────────────────────────────────┐
│  User Interaction (clicks, keys, etc.)         │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│  Event Dispatch (events.clj)                    │
│  - Multimethod based on :event/type             │
│  - NO Swing imports                             │
│  - Pure functions: old-state → new-state        │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│  State Atom (db.clj)                            │
│  - Single source of truth                       │
│  - Immutable data structures                    │
│  - defonce for hot reload persistence           │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│  Watchers (add-watch)                           │
│  - Triggered on state changes                   │
│  - Compute diffs between old/new state          │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│  Effects System (effects/swing.clj)             │
│  - ALL Swing mutations happen here              │
│  - SwingUtilities/invokeLater for EDT safety    │
│  - Update only what changed                     │
└─────────────────────────────────────────────────┘
```

## 1. State Management (db.clj)

**Pattern**: Single atom holds all application state

```clojure
(ns bd-viewer.db)

(defonce *app-state
  (atom {:issues []              ; All beads issues
         :selected-issue nil     ; Currently selected issue ID
         :filter-text ""         ; Search filter text
         :sort-by :priority      ; Sort criterion
         :ui-refs {}}))          ; References to Swing widgets

(defn init-state! []
  "Load issues from bd CLI and reset state"
  (let [issues (load-issues-from-bd)]
    (swap! *app-state assoc
           :issues issues
           :selected-issue nil
           :filter-text "")))

(defn load-issues-from-bd []
  "Shell out to 'bd list --json' and parse results"
  (-> (shell/sh "bd" "list" "--json")
      :out
      (json/parse-string true)))
```

**Why defonce?** When you reload code, the atom persists. Your UI state survives hot reloads!

## 2. Event Handlers (events.clj)

**Pattern**: Multimethod dispatch with NO Swing imports

```clojure
(ns bd-viewer.events
  (:require [bd-viewer.db :as db]))

(defmulti handle-event :event/type)

;; Select an issue from the list
(defmethod handle-event ::issue-selected [event]
  (swap! db/*app-state assoc
         :selected-issue (:issue-id event)))

;; Update search filter
(defmethod handle-event ::filter-changed [event]
  (swap! db/*app-state assoc
         :filter-text (:text event)))

;; Delete currently selected issue
(defmethod handle-event ::delete-issue [event]
  (when-let [issue-id (:selected-issue @db/*app-state)]
    (shell/sh "bd" "delete" issue-id)
    (swap! db/*app-state update
           :issues
           (fn [issues]
             (remove #(= (:id %) issue-id) issues)))))

;; Create new issue
(defmethod handle-event ::new-issue [event]
  (let [{:keys [title description]} event
        result (shell/sh "bd" "create" title
                        "--description" description)]
    (when (= 0 (:exit result))
      (handle-event {:event/type ::reload-issues}))))

;; Reload issues from disk
(defmethod handle-event ::reload-issues [_]
  (swap! db/*app-state assoc
         :issues (db/load-issues-from-bd)))
```

**Key Insight**: Event handlers are pure-ish functions. They call `swap!` to transform state, but they never directly manipulate Swing widgets.

## 3. Effects System (effects/swing.clj)

**Pattern**: All Swing mutations isolated here

```clojure
(ns bd-viewer.effects.swing
  (:require [bd-viewer.db :as db])
  (:import [javax.swing SwingUtilities JList DefaultListModel]))

(defn update-issue-list! [old-state new-state]
  "Update JList when :issues changes"
  (when (not= (:issues old-state) (:issues new-state))
    (SwingUtilities/invokeLater
      (fn []
        (let [^JList jlist (get-in @db/*app-state [:ui-refs :issue-list])
              ^DefaultListModel model (.getModel jlist)
              issues (:issues new-state)]
          (.clear model)
          (doseq [issue issues]
            (.addElement model (:id issue))))))))

(defn update-selected-issue! [old-state new-state]
  "Update detail panel when :selected-issue changes"
  (when (not= (:selected-issue old-state)
              (:selected-issue new-state))
    (SwingUtilities/invokeLater
      (fn []
        (let [issue-id (:selected-issue new-state)
              issue (first (filter #(= (:id %) issue-id)
                                   (:issues new-state)))
              detail-panel (get-in @db/*app-state [:ui-refs :detail-panel])]
          (populate-detail-panel! detail-panel issue))))))

(defn setup-watchers! []
  "Register state watchers that trigger UI updates"
  (add-watch db/*app-state ::sync-ui
    (fn [_ _ old-state new-state]
      (update-issue-list! old-state new-state)
      (update-selected-issue! old-state new-state))))
```

**Critical**: Always use `SwingUtilities/invokeLater` - Swing is not thread-safe!

## 4. Widget Creation (ui.clj)

**Pattern**: Functions that create and wire up Swing components

```clojure
(ns bd-viewer.ui
  (:require [bd-viewer.events :as events]
            [bd-viewer.db :as db])
  (:import [javax.swing JFrame JList JPanel JTextField JButton
                        DefaultListModel JSplitPane JScrollPane
                        BorderFactory]
           [java.awt BorderLayout GridLayout]))

(defn create-issue-list []
  "Create the left-side JList showing all issues"
  (let [model (DefaultListModel.)
        jlist (doto (JList. model)
                (.setSelectionMode ListSelectionModel/SINGLE_SELECTION))]
    ;; Wire up selection events
    (.addListSelectionListener jlist
      (reify ListSelectionListener
        (valueChanged [_ e]
          (when-not (.getValueIsAdjusting e)
            (let [selected (.getSelectedValue jlist)]
              (events/handle-event
                {:event/type ::events/issue-selected
                 :issue-id selected}))))))
    ;; Store reference for later updates
    (swap! db/*app-state assoc-in [:ui-refs :issue-list] jlist)
    jlist))

(defn create-search-bar []
  "Create the top search bar"
  (let [search-field (JTextField. 20)]
    (.addActionListener search-field
      (proxy [ActionListener] []
        (actionPerformed [e]
          (events/handle-event
            {:event/type ::events/filter-changed
             :text (.getText search-field)}))))
    search-field))

(defn create-detail-panel []
  "Create the right-side detail view"
  (let [panel (JPanel. (BorderLayout.))]
    (swap! db/*app-state assoc-in [:ui-refs :detail-panel] panel)
    panel))

(defn create-main-frame []
  "Create and show the main application window"
  (let [frame (JFrame. "BD Viewer")
        search-bar (create-search-bar)
        issue-list (create-issue-list)
        detail-panel (create-detail-panel)

        ;; Layout
        left-panel (JScrollPane. issue-list)
        split-pane (JSplitPane. JSplitPane/HORIZONTAL_SPLIT
                               left-panel
                               detail-panel)
        top-panel (doto (JPanel. (BorderLayout.))
                    (.add search-bar BorderLayout/CENTER))]

    (.setDividerLocation split-pane 250)

    (doto (.getContentPane frame)
      (.setLayout (BorderLayout.))
      (.add top-panel BorderLayout/NORTH)
      (.add split-pane BorderLayout/CENTER))

    (doto frame
      (.setSize 1000 600)
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setVisible true))

    frame))
```

## 5. Keyboard Shortcuts

**Pattern**: Separate initialization function using InputMap/ActionMap

```clojure
(ns bd-viewer.keyboard
  (:require [bd-viewer.events :as events])
  (:import [javax.swing JComponent KeyStroke]
           [java.awt.event KeyEvent]
           [java.awt Toolkit]))

(defn setup-keyboard-shortcuts! [frame]
  "Register global keyboard shortcuts"
  (let [content-pane (.getContentPane frame)
        input-map (.getInputMap content-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
        action-map (.getActionMap content-pane)
        cmd-mask (.getMenuShortcutKeyMaskEx (Toolkit/getDefaultToolkit))]

    ;; Cmd+N - New Issue
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_N cmd-mask)
          "new-issue")
    (.put action-map "new-issue"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (events/handle-event {:event/type ::events/show-new-issue-dialog}))))

    ;; Cmd+D or Delete - Delete Issue
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_D cmd-mask)
          "delete-issue")
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_DELETE 0)
          "delete-issue")
    (.put action-map "delete-issue"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (events/handle-event {:event/type ::events/delete-issue}))))

    ;; Cmd+R - Reload Issues
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_R cmd-mask)
          "reload-issues")
    (.put action-map "reload-issues"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (events/handle-event {:event/type ::events/reload-issues}))))))
```

## 6. Hot Reload Support

**Pattern**: Use `resolve` to get reloaded functions

```clojure
(ns bd-viewer.reload
  (:require [clojure.tools.namespace.repl :refer [refresh]]))

(defn reload-code! []
  "Reload all namespaces and rebuild UI"
  (require 'bd-viewer.ui :reload)
  (require 'bd-viewer.events :reload)
  (require 'bd-viewer.effects.swing :reload)
  (require 'bd-viewer.keyboard :reload)

  ;; Get freshly loaded functions using resolve
  (let [create-frame (resolve 'bd-viewer.ui/create-main-frame)
        setup-keys (resolve 'bd-viewer.keyboard/setup-keyboard-shortcuts!)]

    ;; Close old frame if exists
    (when-let [old-frame (get-in @db/*app-state [:ui-refs :frame])]
      (.dispose old-frame))

    ;; Create new frame with reloaded code
    (let [new-frame (create-frame)]
      (setup-keys new-frame)
      (swap! db/*app-state assoc-in [:ui-refs :frame] new-frame))))

(defn reload-config! []
  "Reload issues from bd without reloading code"
  (events/handle-event {:event/type ::events/reload-issues}))
```

## 7. Main Entry Point

```clojure
(ns bd-viewer.core
  (:require [bd-viewer.db :as db]
            [bd-viewer.ui :as ui]
            [bd-viewer.keyboard :as keyboard]
            [bd-viewer.effects.swing :as fx])
  (:gen-class))

(defn -main [& args]
  ;; 1. Initialize state
  (db/init-state!)

  ;; 2. Setup reactive watchers
  (fx/setup-watchers!)

  ;; 3. Create UI
  (let [frame (ui/create-main-frame)]

    ;; 4. Setup keyboard shortcuts
    (keyboard/setup-keyboard-shortcuts! frame)

    ;; 5. Store frame reference
    (swap! db/*app-state assoc-in [:ui-refs :frame] frame)))
```

## How This Applies to bd-viewer

### State Structure
```clojure
{:issues [{:id "bd-viewer-1"
           :title "..."
           :description "..."
           :status "open"
           :priority 0
           :labels ["planning"]
           :created_at "..."
           :updated_at "..."}]
 :selected-issue "bd-viewer-1"
 :filter-text ""
 :sort-by :priority
 :ui-refs {:frame #<JFrame>
           :issue-list #<JList>
           :detail-panel #<JPanel>
           :search-field #<JTextField>}}
```

### Event Types
- `::issue-selected` - User clicks an issue
- `::filter-changed` - User types in search
- `::delete-issue` - User presses Delete
- `::new-issue` - User presses Cmd+N
- `::reload-issues` - Reload from `bd list --json`
- `::reload-code` - Hot reload all namespaces
- `::sort-changed` - Change sort order

### UI Layout
```
┌────────────────────────────────────────────────┐
│  [Search: ___________________]  [New] [Delete] │
├──────────────┬─────────────────────────────────┤
│ bd-viewer-1  │ Title: Create UI architecture... │
│ bd-viewer-2  │ Description:                     │
│ bd-viewer-3  │ Design the overall architecture..│
│              │                                  │
│              │ Status: open                     │
│              │ Priority: P0                     │
│              │ Labels: planning, architecture   │
│              │                                  │
│              │ Created: 2025-11-08              │
│              │ Updated: 2025-11-08              │
└──────────────┴─────────────────────────────────┘
```

## Benefits of This Architecture

1. **Hot Reload** - Change code, reload, UI persists state
2. **Testability** - Event handlers are pure functions
3. **Debugging** - Watch state changes in REPL
4. **Maintainability** - Clear separation of concerns
5. **Extensibility** - Add new events without touching UI code

## Next Steps

1. Copy Makefile from mailmerge
2. Copy deps.edn structure
3. Implement db.clj with beads integration
4. Implement events.clj with issue operations
5. Implement effects/swing.clj for UI updates
6. Implement ui.clj with layout
7. Implement keyboard.clj with shortcuts
8. Add reload functionality
9. Test hot reload workflow
