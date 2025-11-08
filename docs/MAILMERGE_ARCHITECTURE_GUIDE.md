# Mailmerge Project - Complete Architecture Guide

## Executive Summary

The **mailmerge** project is a sophisticated demonstration of building Clojure GUI applications with multiple architectural approaches. It's a real-world email generation tool for ETLS conference sponsorship emails, implemented in **3 different ways** (JavaFX, Functional Swing, Membrane) to explore trade-offs.

**Key Achievement**: Functional Swing architecture that uses **re-frame-inspired patterns** (multimethod event dispatch, centralized state management, effect isolation) within Swing to enable hot-reloading and clean separation of concerns.

---

## Project Structure

```
/Users/genekim/src.local/mailmerge/
├── Makefile                    # Build targets for different UI frameworks
├── deps.edn                    # Clojure dependencies
├── config.edn                  # Sample application configuration
├── dev.clj                     # Hot reload helper for REPL
│
├── src/mailmerge/
│   ├── core.clj               # Original Swing implementation (imperative)
│   ├── functional_swing.clj    # Modern Swing with re-frame patterns ⭐
│   ├── javafx_core.clj         # JavaFX + cljfx implementation
│   ├── membrane_core.clj       # Membrane immediate-mode GUI
│   │
│   ├── db.clj                 # Central state management (framework-agnostic)
│   ├── events.clj             # Event handlers (framework-agnostic business logic)
│   ├── effects/
│   │   └── swing.clj          # Swing-specific side effects (framework abstraction)
│   │
│   ├── config.clj             # TOML parsing + CSV file handling
│   ├── template.clj           # Email template rendering (Selmer)
│   ├── gmail.clj              # Gmail API integration
│   └── utils.clj              # Utility functions (clipboard, file ops)
│
├── plans/                     # Design documentation
│   ├── architecture.md
│   ├── hot-reload-explanation.md
│   ├── re-frame-refactoring-plan.md
│   └── functional-swing-vs-javafx-comparison.md
│
└── cljfx/                     # Vendor'd cljfx library
```

---

## 1. Overall Project Architecture

### Data Flow Overview

```
config.toml/config.edn
        ↓
config/load-config (TOML parsing + CSV loading)
        ↓
db/*app-state (central immutable state atom)
        ↓
events/handle-event (multimethod dispatch)
        ↓
state transformation + effect side effects
        ↓
effects/swing/* (Swing-specific mutations)
        ↓
UI components updated
```

### Core Design Principles

1. **Framework Agnostic Logic**: `db.clj` and `events.clj` contain zero GUI imports
2. **Effect Isolation**: All Swing mutations isolated in `effects/swing.clj`
3. **Event-Driven Architecture**: Use multimethods for event dispatch (like re-frame)
4. **Reactive UI Updates**: Add-watch on state atom triggers UI rebuilds
5. **Hot Reload Friendly**: `defonce` for persistent state, `resolve` for dynamic function calls

---

## 2. How Swing is Used in a Functional Style

### Traditional Swing Approach (❌ Not Used)

```clojure
;; Imperative, scattered state management
(let [button (JButton. "Click")]
  (.addActionListener button 
    (proxy [ActionListener] []
      (actionPerformed [e]
        (let [text (.getText text-field)]
          (.setText output-label (str "You said: " text)))))))

;; Problems:
;; - Business logic mixed with UI code
;; - No clear state management
;; - Impossible to test business logic
;; - UI mutations everywhere
```

### Functional Swing Approach (✅ Used in mailmerge)

**Step 1: Separate Pure State** (`db.clj`)
```clojure
(defonce *app-state (atom {:current-sponsor-idx 0
                           :csv-filename "book-outreach.csv"
                           :config nil
                           :rendered-email {:to "" :subject "" :body "" :cc ""}
                           :editable-body ""
                           :last-notification ""}))
```

**Step 2: Pure Event Handlers** (`events.clj` - zero Swing imports!)
```clojure
(defmulti handle-event :event/type)

(defmethod handle-event :mailmerge.functional-swing/sponsor-selected [event]
  (let [idx (:index event)
        config (:config @db/*app-state)
        sponsors (:sponsors config)
        selected-sponsor (nth sponsors idx)
        rendered (template/render-all config selected-sponsor)]
    ;; Pure data transformation!
    (swap! db/*app-state assoc
           :current-sponsor-idx idx
           :rendered-email rendered
           :editable-body (:body rendered)
           :last-notification (str "✅ " (:name selected-sponsor)))))
```

**Step 3: Widget Creation Functions** (`functional_swing.clj`)
```clojure
;; Pure functions that CREATE widgets with event handlers
(defn create-sponsor-dropdown [sponsors current-idx]
  (let [combo-box (javax.swing.JComboBox. (into-array (map :name sponsors)))]
    (.setSelectedIndex combo-box current-idx)
    (.addItemListener combo-box
                      (reify ItemListener
                        (itemStateChanged [this e]
                          ;; Dispatch to pure event handler!
                          (when (= (.getStateChange e) java.awt.event.ItemEvent/SELECTED)
                            (events/handle-event {:event/type ::sponsor-selected
                                                  :index (.getSelectedIndex combo-box)})))))
    combo-box))

;; No business logic here - just widget construction!
(defn create-text-field [text editable?]
  (doto (javax.swing.JTextField. text 30)
    (.setEditable editable?)
    (.setBackground (if editable? Color/WHITE (Color. 240 240 240)))))
```

**Step 4: Reactive UI Updates with Watchers**
```clojure
;; Smart state watcher - only update what changed
(add-watch db/*app-state :ui-updater
           (fn [key ref old-state new-state]
             (when (not= old-state new-state)
               ;; Only rebuild when meaningful changes occur
               (when-not (and (= (dissoc old-state :editable-body) 
                                 (dissoc new-state :editable-body))
                              (not= (:editable-body old-state) (:editable-body new-state)))
                 (fx/invoke-on-edt!
                  #(fx/update-widgets! old-state new-state))))))
```

**Step 5: Effect System for Side Effects** (`effects/swing.clj`)
```clojure
(ns mailmerge.effects.swing
  "Like re-frame's reg-fx - isolates Swing mutations")

(defonce *ui-components (atom {}))

(defn invoke-on-edt! [f]
  "Execute on Swing Event Dispatch Thread - safe threading!"
  (SwingUtilities/invokeLater f))

(defn update-widgets! [old-state new-state]
  "Selective UI updates - only change what's needed"
  (let [{:keys [notification-label dropdown-box to-field cc-field subject-field text-area]} 
        @*ui-components]
    
    ;; Only update if relevant state changed
    (when (and dropdown-box 
               (not= (:current-sponsor-idx old-state) (:current-sponsor-idx new-state)))
      (.setSelectedIndex dropdown-box (:current-sponsor-idx new-state)))
    
    (when (and to-field 
               (not= (:rendered-email old-state) (:rendered-email new-state)))
      (.setText to-field (:to (:rendered-email new-state))))))

(defn refresh-frame! [frame panel]
  "Rebuild frame for hot code reload"
  (.setContentPane frame panel)
  (.revalidate frame)
  (.repaint frame))
```

### Key Functional Swing Pattern Benefits

✅ **Testable**: Event handlers are pure functions - test without GUI
✅ **Reloadable**: State persists across hot reloads via `defonce`
✅ **Debuggable**: Inspect state with `@db/*app-state`
✅ **Composable**: Break UI into pure component creation functions
✅ **Reactive**: Watchers automatically sync UI to state changes
✅ **Framework-Agnostic**: `db.clj` and `events.clj` work with JavaFX, Swing, or web

---

## 3. Keyboard Handlers Initialization

### Pattern: Separate Keyboard Setup Function

```clojure
;; Forward declaration at top of file
(declare create-main-panel create-keyboard-shortcuts)

(defn create-keyboard-shortcuts [frame]
  "Set up keyboard shortcuts - functional style"
  (let [content-pane (.getContentPane frame)
        cmd-mask (.getMenuShortcutKeyMaskEx (Toolkit/getDefaultToolkit))]

    ;; ⌘T - Copy TO field
    (.put (.getInputMap content-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
          (KeyStroke/getKeyStroke KeyEvent/VK_T cmd-mask)
          "copy-to")
    (.put (.getActionMap content-pane) "copy-to"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              ;; Dispatch to event handler (not direct mutation!)
              (events/handle-event {:event/type ::copy-to-clipboard :field :to}))))

    ;; ⌘B - Copy CC field
    (.put (.getInputMap content-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
          (KeyStroke/getKeyStroke KeyEvent/VK_B cmd-mask)
          "copy-cc")
    (.put (.getActionMap content-pane) "copy-cc"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (events/handle-event {:event/type ::copy-to-clipboard :field :cc}))))

    ;; ⌘S - Copy Subject
    ;; ⌘E - Copy Email Body  
    ;; ⌘R - Reload Config
    ;; ⌘⇧R - Reload Code
    ;; ⌘N - Next Sponsor
    ;; ...
  ))
```

### Key Pattern: Input Maps + Action Maps

The Swing pattern uses **two levels of indirection**:

1. **InputMap**: Maps `KeyStroke` → String action name
2. **ActionMap**: Maps action name → `AbstractAction` to execute

This separation allows:
- Reusing same action multiple ways (keyboard + button)
- Dynamically rebinding shortcuts
- Per-component vs window-wide shortcuts

### Registration in Main App Startup

```clojure
(defn start-app! []
  (db/init-state!)
  (javax.swing.SwingUtilities/invokeLater
   (fn []
     (let [frame (doto (javax.swing.JFrame. "🚀 ETLS Mail Merge")
                   (.setDefaultCloseOperation javax.swing.JFrame/EXIT_ON_CLOSE)
                   (.setContentPane (create-main-panel @db/*app-state))
                   (.setSize 850 700)
                   (.setLocationRelativeTo nil)
                   (.setVisible true))]
       (swap! fx/*ui-components assoc :frame frame)
       ;; Set up keyboard shortcuts after frame is created
       (create-keyboard-shortcuts frame)
       ;; Set up reactive UI updates
       (setup-ui-reactivity! frame)))))
```

### Re-registration After Hot Reload

```clojure
(defmethod handle-event :mailmerge.functional-swing/reload-code [event]
  ;; Hot reload all namespaces in dependency order
  (require '[mailmerge.db] :reload)
  (require '[mailmerge.effects.swing] :reload)
  (require '[mailmerge.events] :reload)
  (require '[mailmerge.functional-swing] :reload)

  ;; Force UI rebuild using dynamically resolved functions
  (let [frame (:frame @fx/*ui-components)
        create-main-panel-fn (resolve 'mailmerge.functional-swing/create-main-panel)
        create-shortcuts-fn (resolve 'mailmerge.functional-swing/create-keyboard-shortcuts)]
    (when frame
      ;; Rebuild with new code
      (fx/refresh-frame! frame (create-main-panel-fn @db/*app-state))
      ;; Re-register keyboard shortcuts
      (create-shortcuts-fn frame)))
  (swap! db/*app-state assoc :last-notification "🔥 code + UI reloaded"))
```

---

## 4. Reload Code and Reload Config Features

### Config Reload Pattern

**Config Format** (`config.edn` or `config.toml`):
```clojure
{:email {:subject "..."
         :body "..."}
 :utm {:base-url "..."
       :source "email"
       :medium "sponsor" 
       :campaign "etls2025"}
 :sponsors [{:name "Company A"
             :emails ["contact@company.com"]
             :names "John"}
            ...]}
```

**Config Loading** (`config.clj`):
```clojure
(defn load-config
  "Load and parse config.toml and CSV from current directory"
  ([]
   (load-config "book-outreach.csv"))
  ([csv-filename]
   (let [toml-config (parse-toml-file "config.toml")
         csv-configs (:csv-configs toml-config)
         csv-config (find-csv-config csv-configs csv-filename)
         users (parse-csv-file csv-filename)]
     ;; Merge configs and return
     (-> toml-config
         (assoc :sponsors users)
         (assoc :csv-filename csv-filename)
         (assoc :current-csv-config csv-config)
         (assoc :email {:subject (:subject csv-config)
                        :body (:body csv-config)})))))
```

**Config Reload Event Handler**:
```clojure
(defmethod handle-event :mailmerge.functional-swing/reload-config [event]
  (let [current-csv (:csv-filename @db/*app-state)]
    (db/init-state! current-csv)  ;; Reload from disk
    (swap! db/*app-state assoc :last-notification "🔄 reloaded"))
  ;; UI auto-updates via watcher! No manual refresh needed.
  )
```

**How It Works**:
1. User clicks "🔄 Reload Config" button or presses ⌘R
2. Event handler calls `db/init-state!` which calls `config/load-config`
3. State atom is updated with new config
4. Watcher automatically triggers `update-widgets!` to refresh UI
5. No manual UI synchronization needed!

### Code Reload Pattern

**The Challenge**: 
- Clojure functions are stateless (good for reloading)
- But Java Swing objects are stateful (live in memory)
- After reload, old widgets still exist - need to rebuild UI

**The Solution: `resolve` + Hot Rebuild**

```clojure
(defmethod handle-event :mailmerge.functional-swing/reload-code [event]
  ;; Step 1: Reload all namespaces explicitly (:reload is NOT transitive!)
  (require '[mailmerge.db] :reload)
  (require '[mailmerge.effects.swing] :reload)
  (require '[mailmerge.events] :reload)
  (require '[mailmerge.functional-swing] :reload)

  ;; Step 2: Use `resolve` to get NEW function definitions
  ;; (Can't use var directly since it's in reloaded namespace)
  (let [frame (:frame @fx/*ui-components)
        create-main-panel-fn (resolve 'mailmerge.functional-swing/create-main-panel)
        create-shortcuts-fn (resolve 'mailmerge.functional-swing/create-keyboard-shortcuts)]
    (when frame
      ;; Step 3: Rebuild UI with NEW code
      (fx/refresh-frame! frame (create-main-panel-fn @db/*app-state))
      ;; Step 4: Re-register keyboard shortcuts with NEW code
      (create-shortcuts-fn frame)))
  
  ;; Step 5: Update status
  (swap! db/*app-state assoc :last-notification "🔥 code + UI reloaded"))
```

**Why This Works**:
- `resolve` looks up current var definition (gets new function after reload)
- Calling `create-main-panel-fn` builds NEW widgets with NEW code
- `refresh-frame!` replaces old content pane with new one
- Old widget objects are garbage collected
- State atom persists (via `defonce`) - no data loss!

### REPL Hot Reload Helper (`dev.clj`)

```clojure
(require '[mailmerge.core :as core] :reload-all)
(require '[mailmerge.config :as config] :reload-all)  
(require '[mailmerge.template :as template] :reload-all)

(defn hot-reload []
  "Reload all namespaces and refresh the GUI"
  (require '[mailmerge.core :as core] :reload-all)
  (require '[mailmerge.config :as config] :reload-all)
  (require '[mailmerge.template :as template] :reload-all)
  (core/reload-config)
  (println "🔥 Hot reloaded!"))

;; Usage: (hot-reload) in your REPL
```

---

## 5. Makefile Structure and MCP Integration

### Makefile Targets

```makefile
run:
	clj -J-Xdock:icon=icons/macosicon.png -J-Xdock:name="ETLS Mail Merge" -M -m mailmerge.core

membrane:
	clj -M -m mailmerge.membrane-core

javafx:
	clj -M:javafx

swing-functional:
	clj -M:swing-functional

runtests-once:
	@echo "No tests configured yet"

# Start nREPL server (auto-assigns port, writes to .nrepl-port)
nrepl:
	clj -M:nrepl

# Configure MCP server in Claude Code
mcp-configure:
	claude mcp add clojure-mcp -- /bin/sh -c 'PORT=$$(cat /Users/genekim/src.local/mailmerge/.nrepl-port); cd /Users/genekim/src.local/mailmerge && clojure -X:mcp :port $$PORT'

# Run MCP server (for testing)
run-mcp:
	PORT=$$(cat /Users/genekim/src.local/mailmerge/.nrepl-port); cd /Users/genekim/src.local/mailmerge && clojure -X:mcp :port $$PORT

# Clean compiled artifacts
clean:
	rm -rf .cpcache/ .nrepl-port

help:
	@echo "Available commands:"
	@echo "  make run           - Run mailmerge"
	@echo "  make membrane      - Run with membrane GUI"
	@echo "  make javafx        - Run with JavaFX GUI"
	@echo "  make swing-functional - Run with Swing GUI"
	@echo "  make nrepl         - Start nREPL server"
	@echo "  make mcp-configure - Configure MCP server"
	@echo "  make run-mcp       - Run MCP server"
	@echo "  make clean         - Clean artifacts"
```

### MCP (Model Context Protocol) Integration

The Makefile includes MCP server setup for Claude Code:

1. **Start nREPL server** (auto-assigns port):
   ```bash
   make nrepl
   ```
   - Writes port to `.nrepl-port` file
   - Allows MCP to connect for REPL operations

2. **Configure Claude Code** to use nREPL:
   ```bash
   make mcp-configure
   ```
   - Adds `clojure-mcp` server config
   - Claude Code can then execute Clojure code in your running app

3. **This enables in Claude Code**:
   - Execute arbitrary Clojure expressions
   - Inspect application state
   - Hot reload during development
   - Test event handlers directly

---

## 6. deps.edn File Structure

```clojure
{:paths ["src"]
 :deps {org.clojure/clojure {:mvn/version "1.12.0"}
        org.clojure/data.csv {:mvn/version "1.1.0"}
        net.java.dev.jna/jna {:mvn/version "5.15.0"}
        org.tomlj/tomlj {:mvn/version "1.1.0"}
        selmer/selmer {:mvn/version "1.12.61"}
        markdown-clj/markdown-clj {:mvn/version "1.12.1"}

        ;; Gmail API integration
        clojure-google-sheets/gmail {:local/root "../google-api/google-api"}
        
        ;; Membrane GUI framework
        com.phronemophobic/membrane {:git/sha "8ecbc9fce17d74026564126623b455d47359097e"
                                     :git/url "https://github.com/phronmophobic/membrane"}
        com.phronemophobic.membrane/skialib-macosx-aarch64 {:mvn/version "0.18-beta"}
        com.phronemophobic.membrane/skialib-macosx-x86-64 {:mvn/version "0.18-beta"}
        com.phronemophobic.membrane/skialib-linux-x86-64 {:mvn/version "0.18-beta"}
        
        com.phronemophobic/membrandt {:git/sha "66da338b158a58ef48bc3a17ebe25a98e1b11716"
                                      :git/url "https://github.com/phronmophobic/membrandt"}
        
        ;; Reveal for debugging
        vlaaad/reveal {:mvn/version "1.3.280"}}

 :aliases
 {:dev {:extra-paths ["dev"]
        :extra-deps {}}
  
  :membrane {:main-opts ["-m" "mailmerge.membrane-core"]}
  
  :javafx {:extra-deps {cljfx/cljfx {:mvn/version "1.7.22"}
                        org.openjfx/javafx-controls {:mvn/version "24.0.2"}
                        org.openjfx/javafx-fxml {:mvn/version "24.0.2"}}
           :main-opts ["-m" "mailmerge.javafx-core"]}
  
  :swing-functional {:jvm-opts ["-Xdock:icon=icons/macosicon.png" "-Xdock:name=ETLS Mail Merge"]
                     :main-opts ["-m" "mailmerge.functional-swing"]}
  
  :nrepl {:extra-paths ["test"]
          :extra-deps {nrepl/nrepl {:mvn/version "1.3.1"}}
          :jvm-opts ["-Djdk.attach.allowAttachSelf"]
          :main-opts ["-m" "nrepl.cmdline"]}}}
```

### Key Dependencies Explained

| Dependency | Purpose | Used By |
|---|---|---|
| `clojure` | Language runtime | All |
| `data.csv` | CSV file parsing | `config.clj` |
| `tomlj` | TOML config parsing | `config.clj` |
| `selmer` | Email template rendering | `template.clj` |
| `markdown-clj` | Markdown → HTML conversion | `events.clj` (HTML email) |
| `jna` | Native library access | - |
| `clojure-google-sheets/gmail` | Gmail API wrapper | `gmail.clj` |
| `membrane` + `membrandt` | Immediate-mode GUI framework | `membrane_core.clj` |
| `cljfx` | Clojure wrapper for JavaFX | `javafx_core.clj` |
| `javafx-controls/fxml` | JavaFX UI components | `javafx_core.clj` |
| `reveal` | REPL data visualization | Development debugging |

---

## 7. View/UI Separation Architecture

### Framework-Agnostic Core (Can Work Anywhere)

**`db.clj`** - No Swing/JavaFX imports
```clojure
(defonce *app-state (atom {:current-sponsor-idx 0
                           :csv-filename "book-outreach.csv"
                           :config nil
                           :rendered-email {:to "" :subject "" :body "" :cc ""}
                           :editable-body ""
                           :last-notification ""}))

(defn init-state! ([csv-filename]
  ;; Pure initialization logic - framework independent!
  ))
```

**`events.clj`** - No Swing/JavaFX imports
```clojure
;; Zero GUI code - pure business logic!
(defmulti handle-event :event/type)

(defmethod handle-event :mailmerge.functional-swing/sponsor-selected [event]
  ;; Only manipulates state atom
  (swap! db/*app-state assoc ...))
```

**`config.clj`**, **`template.clj`**, **`utils.clj`** - No GUI imports
```clojure
;; Pure data transformations
(defn load-config [csv-filename] ...)
(defn render-all [config sponsor] ...)
```

### Framework-Specific Effects Layer

**`effects/swing.clj`** - Swing-specific side effects
```clojure
(ns mailmerge.effects.swing
  "Like re-frame's reg-fx - all Swing mutations here")

(defonce *ui-components (atom {}))

(defn invoke-on-edt! [f] ...)
(defn show-error-dialog! [title message] ...)
(defn update-widgets! [old-state new-state] ...)
(defn refresh-frame! [frame panel] ...)
```

**Can be replaced with `effects/javafx.clj`** for porting to JavaFX!

### Framework-Specific UI Construction

**`functional_swing.clj`** - Swing UI construction
```clojure
;; Widget creation functions
(defn create-sponsor-dropdown [sponsors current-idx] ...)
(defn create-text-field [text editable?] ...)
(defn create-main-panel [state] ...)
(defn create-keyboard-shortcuts [frame] ...)
```

### Porting to New Framework (Example: JavaFX)

```
1. Keep: db.clj, events.clj, config.clj, template.clj
2. Create: effects/javafx.clj (new side effects)
3. Create: javafx_ui.clj (new widget construction)
4. Result: 100% code reuse for business logic!
```

This is exactly what they did - `javafx_core.clj` exists alongside `functional_swing.clj`!

---

## 8. Key Code Patterns for beads-viewer

### Pattern 1: State Management

```clojure
;; In db.clj
(defonce *app-state (atom {:selected-bead-id nil
                           :beads []
                           :filters {:type nil :color nil}
                           :last-notification ""}))

(defn init-state! []
  (let [beads (load-beads)]
    (reset! *app-state {:selected-bead-id nil
                        :beads beads
                        :filters {:type nil :color nil}
                        :last-notification "Ready!"})))
```

### Pattern 2: Event Handling

```clojure
;; In events.clj
(defmulti handle-event :event/type)

(defmethod handle-event ::bead-selected [event]
  (let [bead-id (:bead-id event)]
    (swap! db/*app-state assoc :selected-bead-id bead-id)))

(defmethod handle-event ::filter-changed [event]
  (let [filter-type (:filter-type event)
        filter-value (:filter-value event)]
    (swap! db/*app-state assoc-in [:filters filter-type] filter-value)))
```

### Pattern 3: Widget Creation

```clojure
;; In beads_viewer.clj
(defn create-bead-list-panel [beads selected-id]
  (let [list-model (javax.swing.DefaultListModel.)
        bead-list (javax.swing.JList. list-model)]
    (doseq [bead beads]
      (.addElement list-model (:name bead)))
    
    (.addListSelectionListener bead-list
      (reify javax.swing.event.ListSelectionListener
        (valueChanged [this e]
          (when-not (.getValueIsAdjusting e)
            (events/handle-event {:event/type ::bead-selected
                                  :bead-id (get-in beads [(.getSelectedIndex bead-list) :id])})))))
    bead-list))
```

### Pattern 4: Reactive UI Updates

```clojure
;; In beads_viewer.clj
(add-watch db/*app-state ::ui-sync
  (fn [_ _ old-state new-state]
    (when (not= (:selected-bead-id old-state) (:selected-bead-id new-state))
      (fx/invoke-on-edt!
       (fn []
         (fx/update-bead-detail! (:selected-bead-id new-state)))))))
```

### Pattern 5: Hot Reload

```clojure
;; In events.clj
(defmethod handle-event ::reload-code [event]
  (require '[beads.db] :reload)
  (require '[beads.effects.swing] :reload)
  (require '[beads.events] :reload)
  (require '[beads.beads-viewer] :reload)
  
  (let [frame (:frame @fx/*ui-components)
        create-main-panel-fn (resolve 'beads.beads-viewer/create-main-panel)
        create-shortcuts-fn (resolve 'beads.beads-viewer/create-keyboard-shortcuts)]
    (when frame
      (fx/refresh-frame! frame (create-main-panel-fn @db/*app-state))
      (create-shortcuts-fn frame)))
  
  (swap! db/*app-state assoc :last-notification "🔥 code reloaded"))
```

---

## 9. Complete File Structure for beads-viewer

Based on mailmerge patterns:

```
beads-viewer/
├── Makefile
├── deps.edn
├── dev.clj
├── config.edn
│
├── src/beads/
│   ├── db.clj                 # State management (framework-agnostic)
│   ├── events.clj             # Event handlers (framework-agnostic)
│   ├── effects/
│   │   └── swing.clj          # Swing mutations (framework-specific)
│   │
│   ├── beads-viewer.clj       # Main Swing UI (functional style)
│   ├── keyboard.clj           # Keyboard handler registration
│   │
│   ├── models/
│   │   ├── bead.clj           # Bead data model
│   │   └── collection.clj     # Collection management
│   │
│   ├── io/
│   │   ├── csv.clj            # CSV loading/saving
│   │   └── cache.clj          # Image caching
│   │
│   └── ui/
│       ├── bead-list.clj      # Bead list component
│       ├── bead-detail.clj    # Detail view component
│       ├── image-viewer.clj   # Image display
│       └── styles.clj         # Shared styling
│
└── plans/
    ├── architecture.md
    └── design-decisions.md
```

---

## 10. Key Design Decisions Summary

| Decision | Why | Benefit |
|---|---|---|
| **Functional Swing** | Enables hot reload + separation of concerns | Fast iteration + maintainable |
| **Multimethod dispatch** | Like re-frame event system | Extensible, testable event handling |
| **Central state atom** | Single source of truth | Debuggable, predictable |
| **Effect isolation** | All mutations in one place | Portable to other frameworks |
| **Widget creation functions** | Pure functions return widgets | Composable, reloadable UI |
| **Add-watch for reactivity** | Automatic UI sync | No manual refresh code |
| **`defonce` for persistence** | State survives hot reload | Seamless development |
| **`resolve` for hot code reload** | Get fresh function defs | UI rebuilds with new code |

---

## 11. Absolute File Paths for Key Files

| Purpose | Path |
|---|---|
| Main Swing UI | `/Users/genekim/src.local/mailmerge/src/mailmerge/functional_swing.clj` |
| State Management | `/Users/genekim/src.local/mailmerge/src/mailmerge/db.clj` |
| Event Handlers | `/Users/genekim/src.local/mailmerge/src/mailmerge/events.clj` |
| Swing Effects | `/Users/genekim/src.local/mailmerge/src/mailmerge/effects/swing.clj` |
| Config Parsing | `/Users/genekim/src.local/mailmerge/src/mailmerge/config.clj` |
| Template Rendering | `/Users/genekim/src.local/mailmerge/src/mailmerge/template.clj` |
| Build Config | `/Users/genekim/src.local/mailmerge/Makefile` |
| Dependencies | `/Users/genekim/src.local/mailmerge/deps.edn` |
| Architecture Docs | `/Users/genekim/src.local/mailmerge/plans/architecture.md` |
| Hot Reload Explainer | `/Users/genekim/src.local/mailmerge/plans/hot-reload-explanation.md` |
| Re-frame Plan | `/Users/genekim/src.local/mailmerge/plans/re-frame-refactoring-plan.md` |

