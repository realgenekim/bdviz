# Force-Directed Graph Tab - Implementation Plan

## Philosophy: FAST FREQUENT FEEDBACK! 🚀

Each phase produces a **runnable, visible result**. Run `make run` after every phase to see progress!

---

## Phase 0: Dependencies & Hello World (5 minutes)
**Goal**: Verify GraphStream works in our project

### Tasks:
1. Add GraphStream to `project.clj` dependencies
2. Create tiny test namespace to verify it works
3. Run test to see a simple graph window

### Changes:
```clojure
;; project.clj - add to :dependencies
[org.graphstream/gs-core "2.0"]
[org.graphstream/gs-ui-swing "2.0"]
```

### Test Code:
```clojure
;; test/bd_viewer/graph_test.clj
(ns bd-viewer.graph-test
  (:import [org.graphstream.graph.implementations SingleGraph]
           [org.graphstream.ui.swing_viewer SwingViewer]))

(defn hello-graph []
  (let [graph (SingleGraph. "test")]
    (.addNode graph "A")
    (.addNode graph "B")
    (.addEdge graph "AB" "A" "B")
    (doto graph
      (.setAttribute "ui.stylesheet" 
                     "node { fill-color: red; size: 20px; }")
      (.display))
    graph))

;; Test: (hello-graph)
;; Should open window with 2 red nodes connected
```

### Run Test:
```bash
make runtests-once  # Verify compilation
# Then in REPL:
# (require 'bd-viewer.graph-test)
# (bd-viewer.graph-test/hello-graph)
```

**✅ Success Criteria**: See a graph window with 2 connected nodes

---

## Phase 1: Empty Graph Tab in Main UI (10 minutes)
**Goal**: Add new tab to existing UI, verify it appears

### Tasks:
1. Create `src/bd_viewer/ui/graph_tab.clj`
2. Add tab to main window in `src/bd_viewer/ui/main_window.clj`
3. Show placeholder panel with "Graph View" label

### New File:
```clojure
;; src/bd_viewer/ui/graph_tab.clj
(ns bd-viewer.ui.graph-tab
  (:require [swing-fx.core :as fx]))

(defn create-graph-panel []
  (fx/border-panel
    :center (fx/label 
              :text "🕸️  Dependency Graph View"
              :font (java.awt.Font. "Arial" java.awt.Font/BOLD 24)
              :halign :center)))
```

### Modify Existing:
```clojure
;; src/bd_viewer/ui/main_window.clj
(ns bd-viewer.ui.main-window
  (:require [bd-viewer.ui.list-tab :as list-tab]
            [bd-viewer.ui.graph-tab :as graph-tab]  ; ADD THIS
            [swing-fx.core :as fx]))

(defn create-main-window []
  (fx/frame
    :title "Beads Viewer"
    :content
    (fx/tabbed-panel
      :placement :top
      :tabs [{:title "📋 Issues"
              :tip "List view of all issues"
              :content (list-tab/create-list-panel)}
             
             {:title "🕸️  Graph"           ; ADD THIS TAB
              :tip "Dependency graph view"
              :content (graph-tab/create-graph-panel)}])))
```

### Run:
```bash
make run
```

**✅ Success Criteria**: 
- App launches
- See 2 tabs: "📋 Issues" and "🕸️  Graph"
- Graph tab shows placeholder text

---

## Phase 2: Static Graph with Hardcoded Data (20 minutes)
**Goal**: Show actual GraphStream graph embedded in Swing panel

### Tasks:
1. Create GraphStream graph with hardcoded issue nodes
2. Embed viewer into Swing panel
3. Style nodes with colors

### Update graph_tab.clj:
```clojure
(ns bd-viewer.ui.graph-tab
  (:require [swing-fx.core :as fx])
  (:import [org.graphstream.graph.implementations SingleGraph]
           [org.graphstream.ui.swing_viewer SwingViewer]
           [org.graphstream.ui.view Viewer]
           [javax.swing JPanel]
           [java.awt BorderLayout]))

(defn create-test-graph []
  "Create a graph with hardcoded test data"
  (let [graph (SingleGraph. "beads-test")]
    
    ;; Add test nodes
    (doto (.addNode graph "epic-001")
      (.setAttribute "ui.label" "Epic: Auth System")
      (.setAttribute "ui.class" "epic"))
    
    (doto (.addNode graph "task-001")
      (.setAttribute "ui.label" "Task: Login UI")
      (.setAttribute "ui.class" "in-progress"))
    
    (doto (.addNode graph "task-002")
      (.setAttribute "ui.label" "Task: Database")
      (.setAttribute "ui.class" "blocked"))
    
    (doto (.addNode graph "task-003")
      (.setAttribute "ui.label" "Task: Testing")
      (.setAttribute "ui.class" "open"))
    
    ;; Add edges
    (.addEdge graph "e1" "epic-001" "task-001" true)
    (.addEdge graph "e2" "epic-001" "task-002" true)
    (.addEdge graph "e3" "task-001" "task-002" true)  ; blocks
    (.addEdge graph "e4" "task-002" "task-003" true)  ; blocks
    
    ;; Apply stylesheet
    (.setAttribute graph "ui.stylesheet"
      "node {
         size: 30px;
         fill-color: gray;
         text-size: 14;
         text-alignment: under;
       }
       node.epic {
         fill-color: purple;
         size: 40px;
       }
       node.open {
         fill-color: green;
       }
       node.in-progress {
         fill-color: yellow;
       }
       node.blocked {
         fill-color: red;
       }
       edge {
         fill-color: gray;
         arrow-size: 8px, 6px;
       }")
    
    graph))

(defn embed-graph-viewer [graph]
  "Embed GraphStream viewer into a Swing JPanel"
  (let [viewer (SwingViewer. graph 
                             Viewer/ThreadingModel/GRAPH_IN_SWING_THREAD)
        view (.addDefaultView viewer false)]  ; false = embedded mode
    
    ;; Enable automatic layout
    (.enableAutoLayout viewer)
    
    ;; Wrap the view in a JPanel
    (doto (JPanel. (BorderLayout.))
      (.add view BorderLayout/CENTER))))

(defn create-graph-panel []
  (let [graph (create-test-graph)
        graph-panel (embed-graph-viewer graph)]
    
    (fx/border-panel
      :north (fx/label :text "Dependency Graph (Test Data)")
      :center graph-panel)))
```

### Run:
```bash
make run
```

**✅ Success Criteria**:
- Click "🕸️  Graph" tab
- See animated force-directed graph with 4 nodes
- Nodes colored: purple (epic), yellow (in-progress), red (blocked), green (open)
- Arrows show dependencies
- Can drag nodes around
- Layout auto-adjusts

---

## Phase 3: Load Real Beads Data (30 minutes)
**Goal**: Replace hardcoded data with actual `bd list --json` output

### Tasks:
1. Create data loading namespace
2. Parse JSON from bd command
3. Build graph from real issues
4. Handle parent-child relationships and dependencies

### New File:
```clojure
;; src/bd_viewer/data/graph_loader.clj
(ns bd-viewer.data.graph-loader
  (:require [clojure.java.shell :as shell]
            [cheshire.core :as json]))

(defn load-issues []
  "Load issues from bd list --json"
  (let [{:keys [out exit]} (shell/sh "bd" "list" "--json")]
    (if (zero? exit)
      (json/parse-string out true)
      (do
        (println "Error loading issues:" out)
        []))))

(defn parse-dependencies [issue]
  "Extract dependency relationships from issue"
  (let [deps (get issue :dependencies [])]
    (for [dep deps]
      {:from (:id issue)
       :to (:target dep)
       :type (:type dep)})))  ; "blocks", "related", "parent-child"

(defn issue-class [issue]
  "Get CSS class for issue status"
  (case (:status issue)
    "open" "open"
    "in_progress" "in-progress"
    "blocked" "blocked"
    "closed" "closed"
    "default"))

(defn build-graph-data [issues]
  "Convert issues into graph-friendly data structure"
  {:nodes (for [issue issues]
            {:id (:id issue)
             :label (str (:title issue))
             :class (issue-class issue)
             :type (or (:issue_type issue) "task")
             :priority (or (:priority issue) 99)})
   
   :edges (mapcat parse-dependencies issues)})
```

### Update graph_tab.clj:
```clojure
(ns bd-viewer.ui.graph-tab
  (:require [swing-fx.core :as fx]
            [bd-viewer.data.graph-loader :as loader])
  (:import [org.graphstream.graph.implementations SingleGraph]
           [org.graphstream.ui.swing_viewer SwingViewer]
           [org.graphstream.ui.view Viewer]
           [javax.swing JPanel]
           [java.awt BorderLayout]))

(defn create-graph-from-data [graph-data]
  "Create GraphStream graph from parsed data"
  (let [graph (SingleGraph. "beads-deps")]
    
    ;; Add nodes
    (doseq [{:keys [id label class type]} (:nodes graph-data)]
      (when-let [node (.addNode graph (str id))]
        (.setAttribute node "ui.label" label)
        (.setAttribute node "ui.class" class)
        (.setAttribute node "type" type)))
    
    ;; Add edges
    (doseq [{:keys [from to type]} (:edges graph-data)]
      (let [edge-id (str from "->" to)]
        (try
          (.addEdge graph edge-id (str from) (str to) true)
          (.setAttribute (.getEdge graph edge-id) "edge-type" type)
          (catch Exception e
            (println "Error adding edge" edge-id ":" (.getMessage e))))))
    
    ;; Apply stylesheet
    (.setAttribute graph "ui.stylesheet"
      "node {
         size: 25px;
         fill-color: gray;
         text-size: 12;
         text-alignment: under;
         stroke-mode: plain;
         stroke-color: black;
         stroke-width: 1px;
       }
       node.epic {
         fill-color: #9B59B6;
         size: 35px;
       }
       node.open {
         fill-color: #2ECC71;
       }
       node.in-progress {
         fill-color: #F39C12;
       }
       node.blocked {
         fill-color: #E74C3C;
       }
       node.closed {
         fill-color: #95A5A6;
       }
       edge {
         fill-color: #7F8C8D;
         arrow-size: 6px, 4px;
       }")
    
    graph))

(defonce *current-graph (atom nil))

(defn refresh-graph! []
  "Reload graph data from bd command"
  (let [issues (loader/load-issues)
        graph-data (loader/build-graph-data issues)]
    (println "Loaded" (count (:nodes graph-data)) "nodes," 
             (count (:edges graph-data)) "edges")
    (reset! *current-graph (create-graph-from-data graph-data))
    @*current-graph))

(defn create-graph-panel []
  (let [graph (refresh-graph!)
        graph-panel (embed-graph-viewer graph)
        
        refresh-btn (fx/button 
                      :text "🔄 Refresh"
                      :listen [:action (fn [e]
                                        (println "Refreshing graph...")
                                        (refresh-graph!))])]
    
    (fx/border-panel
      :north (fx/horizontal-panel 
               :items [(fx/label :text "Dependency Graph")
                       refresh-btn])
      :center graph-panel)))
```

### Run:
```bash
make run
```

**✅ Success Criteria**:
- Graph shows REAL issues from your .beads directory
- Nodes labeled with actual issue titles
- Colors match actual statuses
- Click "🔄 Refresh" to reload data
- See console output: "Loaded N nodes, M edges"

---

## Phase 4: Interactivity & Polish (30 minutes)
**Goal**: Click nodes to show details, improve styling

### Tasks:
1. Add click handlers to nodes
2. Show issue details on click
3. Add filters (show/hide edge types)
4. Improve layout and styling

### Add Click Handler:
```clojure
;; In graph_tab.clj

(defn setup-click-handler [viewer]
  "Add mouse click handler to show issue details"
  (let [view (.getDefaultView viewer)]
    (.addMouseListener view
      (proxy [java.awt.event.MouseAdapter] []
        (mouseClicked [e]
          (when-let [node (.getNodeAt view (.getX e) (.getY e))]
            (println "Clicked node:" (.getId node))
            ;; TODO: Show detail panel
            ))))))

(defn embed-graph-viewer [graph]
  (let [viewer (SwingViewer. graph 
                             Viewer/ThreadingModel/GRAPH_IN_SWING_THREAD)
        view (.addDefaultView viewer false)]
    
    (.enableAutoLayout viewer)
    (setup-click-handler viewer)  ; ADD THIS
    
    (doto (JPanel. (BorderLayout.))
      (.add view BorderLayout/CENTER))))
```

### Add Filter Controls:
```clojure
(defn create-graph-panel []
  (let [graph (refresh-graph!)
        graph-panel (embed-graph-viewer graph)
        
        ;; Controls
        refresh-btn (fx/button 
                      :text "🔄 Refresh"
                      :listen [:action (fn [_] (refresh-graph!))])
        
        blocks-cb (fx/checkbox 
                    :text "Show Blocks"
                    :selected? true)
        
        related-cb (fx/checkbox 
                     :text "Show Related"
                     :selected? false)
        
        controls (fx/horizontal-panel
                   :items [(fx/label :text "Dependency Graph")
                           [:fill-h 10]
                           blocks-cb
                           related-cb
                           [:fill-h 10]
                           refresh-btn])]
    
    (fx/border-panel
      :north controls
      :center graph-panel)))
```

### Run:
```bash
make run
```

**✅ Success Criteria**:
- Click nodes to see ID in console
- Filter checkboxes appear (functionality TBD)
- Layout looks clean

---

## Phase 5: Auto-Refresh & Live Updates (20 minutes)
**Goal**: Automatically detect changes to .beads directory

### Tasks:
1. Add timer to poll for changes
2. Highlight recently updated nodes
3. Show "LIVE" indicator

### Add Auto-Refresh:
```clojure
;; In graph_tab.clj

(defonce *refresh-timer (atom nil))

(defn start-auto-refresh! [refresh-fn interval-ms]
  "Start auto-refresh timer"
  (stop-auto-refresh!)  ; Stop existing timer
  (let [timer (javax.swing.Timer. 
                interval-ms
                (reify java.awt.event.ActionListener
                  (actionPerformed [_ e]
                    (refresh-fn))))]
    (.start timer)
    (reset! *refresh-timer timer)))

(defn stop-auto-refresh! []
  "Stop auto-refresh timer"
  (when-let [timer @*refresh-timer]
    (.stop timer)
    (reset! *refresh-timer nil)))

(defn create-graph-panel []
  (let [graph (refresh-graph!)
        graph-panel (embed-graph-viewer graph)
        
        auto-refresh-cb (fx/checkbox
                          :text "⚡ Auto-refresh (5s)"
                          :listen [:action (fn [e]
                                            (if (.isSelected (.getSource e))
                                              (start-auto-refresh! 
                                                #(refresh-graph!) 
                                                5000)
                                              (stop-auto-refresh!)))])
        
        controls (fx/horizontal-panel
                   :items [(fx/label :text "Dependency Graph")
                           [:fill-h 10]
                           auto-refresh-cb
                           (fx/button :text "🔄 Refresh"
                                     :listen [:action (fn [_] (refresh-graph!))])])]
    
    (fx/border-panel
      :north controls
      :center graph-panel)))
```

### Run:
```bash
make run
```

**✅ Success Criteria**:
- Enable "⚡ Auto-refresh" checkbox
- Graph updates every 5 seconds
- Try: `bd create "Test Issue" -t task` in terminal
- See new node appear automatically!

---

## Phase 6: Multi-Agent Support (Future)
**Goal**: Watch multiple .beads directories, show agent activity

### Tasks (for later):
1. Add directory picker to load multiple projects
2. Color nodes by project/agent
3. Pulse animation for recently updated nodes
4. Side panel showing "Active Issues" per agent

*This is Phase 6 - we'll implement after Phases 0-5 are working!*

---

## Testing Strategy

### After Each Phase:
```bash
# 1. Verify compilation
make runtests-once

# 2. Run the app
make run

# 3. Test the new feature
# - Click the Graph tab
# - Verify expected behavior
# - Check console for errors

# 4. Check logs if issues
tail -f 00LOGS.txt
```

### Create Test Issues:
```bash
# Create sample data for testing
bd create "Epic: Authentication System" -t epic -p 1
bd create "Task: Design login UI" -t task -p 1
bd create "Task: Implement database schema" -t task -p 2
bd create "Task: Write unit tests" -t task -p 3

# Add dependencies
bd dep add <task-1-id> <task-2-id> --type blocks
bd dep add <task-2-id> <task-3-id> --type blocks
```

---

## Rollback Plan

If something breaks:
1. **Check logs**: `cat 00LOGS.txt`
2. **Revert last change**: `git diff`, then undo edits
3. **Run previous phase**: Go back to last working phase
4. **Ask for help**: Share error from logs

---

## Dependencies to Add

```clojure
;; project.clj
:dependencies [...
               [org.graphstream/gs-core "2.0"]
               [org.graphstream/gs-ui-swing "2.0"]
               [cheshire "5.11.0"]  ; for JSON parsing
               ...]
```

---

## File Structure After Implementation

```
src/bd_viewer/
├── ui/
│   ├── main_window.clj       (MODIFIED: add graph tab)
│   ├── list_tab.clj          (existing)
│   └── graph_tab.clj         (NEW: graph visualization)
├── data/
│   └── graph_loader.clj      (NEW: load & parse bd data)
└── core.clj

test/bd_viewer/
└── graph_test.clj            (NEW: hello-world test)

docs/
├── graph-visualization-ideas.md
└── graph-tab-implementation-plan.md
```

---

## Estimated Timeline

- Phase 0: 5 minutes
- Phase 1: 10 minutes
- Phase 2: 20 minutes
- Phase 3: 30 minutes
- Phase 4: 30 minutes
- Phase 5: 20 minutes

**Total: ~2 hours to working auto-refreshing graph!**

---

## Success Metrics

### Phase 0: ✅ See hello-world graph window
### Phase 1: ✅ See "Graph" tab in main UI
### Phase 2: ✅ See animated graph with test data
### Phase 3: ✅ See real issues from .beads directory
### Phase 4: ✅ Click nodes, use filters
### Phase 5: ✅ Auto-refresh detects new issues

---

## Next Steps After Phase 5

1. **Critical Path Highlighting**: Compute longest path, paint it bold
2. **Cycle Detection**: Show warning if circular dependencies
3. **Detail Panel**: Click node → show full issue details in side panel
4. **Multi-Agent View**: Watch multiple .beads directories
5. **Export**: Save graph as PNG/SVG
6. **Layouts**: Add radial, hierarchical, timeline views

---

## Questions?

1. **Want me to start implementing Phase 0?**
2. **Any concerns about the approach?**
3. **Different phasing preference?**

**Ready to build? Let's start with Phase 0!** 🚀
