(ns bd-viewer.ui.graph-tab
  "Graph visualization tab for bd-viewer - shows dependency graph.

  Phase 1: Placeholder panel ✅
  Phase 2: Static graph with hardcoded data (this file!)
  Phase 3: Load real Beads data from bd command
  Phase 4: Interactivity (click nodes, filters)
  Phase 5: Auto-refresh and live updates"
  (:require [seesaw.core :as s])
  (:import [java.awt Font BorderLayout]
           [org.graphstream.graph.implementations SingleGraph]
           [org.graphstream.ui.swing_viewer SwingViewer]
           [org.graphstream.ui.view Viewer Viewer$ThreadingModel]
           [javax.swing JPanel]))

;; Helper function: GraphStream's setAttribute requires varargs as Object array
(defn set-attr! [element attr-name value]
  (.setAttribute element attr-name (into-array Object [value])))

(defn create-test-graph []
  "Create a graph with hardcoded test data simulating Beads issues.

  This demonstrates the graph structure we'll use for real data:
  - Epic node (purple, large)
  - Task nodes (colored by status)
  - Directed edges showing dependencies"
  (let [graph (SingleGraph. "beads-test")]

    ;; Create epic node
    (doto (.addNode graph "epic-001")
      (set-attr! "ui.label" "epic-001\nEpic: Auth System")
      (set-attr! "ui.class" "epic"))

    ;; Create task nodes with different statuses
    (doto (.addNode graph "task-001")
      (set-attr! "ui.label" "task-001\nTask: Login UI")
      (set-attr! "ui.class" "inprogress"))

    (doto (.addNode graph "task-002")
      (set-attr! "ui.label" "task-002\nTask: Database")
      (set-attr! "ui.class" "blocked"))

    (doto (.addNode graph "task-003")
      (set-attr! "ui.label" "task-003\nTask: Testing")
      (set-attr! "ui.class" "open"))

    (doto (.addNode graph "task-004")
      (set-attr! "ui.label" "task-004\nTask: Deploy")
      (set-attr! "ui.class" "closed"))

    ;; Add edges showing relationships
    (.addEdge graph "e1" "epic-001" "task-001" true)  ; epic -> task-001
    (.addEdge graph "e2" "epic-001" "task-002" true)  ; epic -> task-002
    (.addEdge graph "e3" "task-001" "task-002" true)  ; task-001 blocks task-002
    (.addEdge graph "e4" "task-002" "task-003" true)  ; task-002 blocks task-003
    (.addEdge graph "e5" "epic-001" "task-004" true)  ; epic -> task-004

    ;; Apply stylesheet for colors and sizing
    (set-attr! graph "ui.stylesheet"
               "node {
                  size: 30px;
                  fill-color: gray;
                  text-size: 42;
                  text-style: bold;
                  text-alignment: under;
                  stroke-mode: plain;
                  stroke-color: black;
                  stroke-width: 2px;
                }
                node.epic {
                  fill-color: #9B59B6;
                  size: 45px;
                  text-size: 48;
                  text-style: bold;
                }
                node.open {
                  fill-color: #2ECC71;
                }
                node.inprogress {
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
                  arrow-size: 8px, 6px;
                }")

    graph))

(defn embed-graph-viewer [graph]
  "Embed GraphStream viewer into a Swing JPanel.

  Uses GRAPH_IN_GUI_THREAD mode for proper Swing integration.
  Returns a JPanel containing the graph view."
  (let [viewer (SwingViewer. graph Viewer$ThreadingModel/GRAPH_IN_GUI_THREAD)
        view (.addDefaultView viewer false)]  ; false = embedded mode

    ;; Enable automatic layout
    (.enableAutoLayout viewer)

    ;; Wrap the view in a JPanel
    (doto (JPanel. (BorderLayout.))
      (.add view BorderLayout/CENTER))))

(defn create-graph-panel []
  "Create the graph visualization panel.

  Phase 2: Show actual GraphStream graph with hardcoded test data.
  Returns a border-panel with graph view and header."
  (let [graph (create-test-graph)
        graph-panel (embed-graph-viewer graph)]

    (s/border-panel
     :north (s/label
             :text "Dependency Graph (Test Data)"
             :font (Font. Font/SANS_SERIF Font/BOLD 16)
             :border 5)
     :center graph-panel)))
