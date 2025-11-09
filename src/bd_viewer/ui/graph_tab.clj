(ns bd-viewer.ui.graph-tab
  "Graph visualization tab for bd-viewer - shows dependency graph.

  Phase 1: Placeholder panel ✅
  Phase 2: Static graph with hardcoded data ✅
  Phase 3: Load real Beads data from bd command ✅
  Phase 4: Dependency edges and interactivity (this file!)
  Phase 5: Auto-refresh and live updates"
  (:require [seesaw.core :as s]
            [bd-viewer.db :as db]
            [bd-viewer.beads.sqlite :as beads-db]
            [taoensso.timbre :as log]
            [clojure.java.io :as io])
  (:import [java.awt Font BorderLayout]
           [java.awt.image BufferedImage]
           [javax.imageio ImageIO]
           [org.graphstream.graph.implementations SingleGraph]
           [org.graphstream.ui.swing_viewer SwingViewer]
           [org.graphstream.ui.view Viewer Viewer$ThreadingModel]
           [javax.swing JPanel]))

;; Helper function: GraphStream's setAttribute requires varargs as Object array
(defn set-attr! [element attr-name value]
  (.setAttribute element attr-name (into-array Object [value])))

(defn create-test-graph []
  "MINIMAL TEST: Just 2 nodes and 1 edge to debug edge rendering."
  (let [graph (SingleGraph. "minimal-test")]

    ;; Create just 2 nodes
    (doto (.addNode graph "node-1")
      (set-attr! "ui.label" "Node 1")
      (set-attr! "ui.class" "open"))

    (doto (.addNode graph "node-2")
      (set-attr! "ui.label" "Node 2")
      (set-attr! "ui.class" "blocked"))

    ;; Add ONE edge
    (.addEdge graph "edge-1-2" "node-1" "node-2" true)

    ;; Apply stylesheet
    (set-attr! graph "ui.stylesheet"
               "node {
                  size: 50px;
                  fill-color: gray;
                  text-size: 24;
                  text-style: bold;
                }
                node.open {
                  fill-color: #2ECC71;
                }
                node.blocked {
                  fill-color: #E74C3C;
                }
                edge {
                  fill-color: #000000;
                  size: 5px;
                }")

    graph))

(defn embed-graph-viewer [graph]
  "Embed GraphStream viewer into a Swing JPanel.

  Uses GRAPH_IN_GUI_THREAD mode for proper Swing integration.
  Returns a JPanel containing the graph view."
  (let [viewer (SwingViewer. graph Viewer$ThreadingModel/GRAPH_IN_GUI_THREAD)
        view (.addDefaultView viewer false)] ; false = embedded mode

    ;; Enable automatic layout
    (.enableAutoLayout viewer)

    ;; Wrap the view in a JPanel
    (doto (JPanel. (BorderLayout.))
      (.add view BorderLayout/CENTER))))

(defn status-class [status]
  "Map issue status to CSS class name."
  (case status
    "open" "open"
    "in_progress" "inprogress"
    "in-progress" "inprogress"
    "blocked" "blocked"
    "closed" "closed"
    "default"))

(defn format-node-label
  "Format node label to show meaningful task context.
  Format: #15 Phase 4: Interact..."
  [id title]
  (let [id-num (or (re-find #"\d+$" id) id)
        ;; Take first 20 chars of title for context
        short-title (subs title 0 (min 20 (count title)))]
    (str "#" id-num " " short-title)))

;; Graph node dimensions (width × height in pixels)
(def node-size "250px, 40px")

;; Graph text size (in points)
(def node-text-size 20)

(comment
  (test-graph-render!))

(def graph-style
  "Graph styling - wide rectangular boxes with normal text"
  {:node {:shape "box"
          :size node-size ; 250px wide × 40px tall
          :fill-color "white"
          :stroke-mode "plain"
          :stroke-color "black"
          :stroke-width "1px"
          :text-size node-text-size ; 20pt font
          :text-alignment "center"}
   :node-open {:fill-color "#E8F8F5"
               :stroke-color "#2ECC71"
               :stroke-width "2px"}
   :node-blocked {:fill-color "#FADBD8"
                  :stroke-color "#E74C3C"
                  :stroke-width "2px"}
   :edge {:fill-color "#000000"
          :size 2}})

(defn style-map->css
  "Convert style map to GraphStream CSS string."
  [style-map]
  (letfn [(css-entry [[k v]]
            (str "  " (name k) ": " v (when (number? v) "px") ";"))
          (css-block [selector styles]
            (str selector " {\n"
                 (clojure.string/join "\n" (map css-entry styles))
                 "\n}"))]
    (clojure.string/join "\n"
                         (remove nil?
                                 [(css-block "node" (:node style-map))
                                  (when (:node-open style-map) (css-block "node.open" (:node-open style-map)))
                                  (when (:node-blocked style-map) (css-block "node.blocked" (:node-blocked style-map)))
                                  (css-block "edge" (:edge style-map))]))))

(defn create-graph-from-issues [issues]
  "Create a GraphStream graph from real Beads issues.

  Phase 3: Load from actual bd data! ✅
  Phase 4: Add dependency edges! 🚀
  - Reads issues from app state
  - Creates nodes for all issues
  - Queries SQLite for dependencies
  - Creates edges with different styles per dependency type"
  (let [graph (SingleGraph. "beads-deps")]

    ;; Add nodes for all issues
    (doseq [issue issues]
      (let [node (.addNode graph (:id issue))
            status (:status issue)
            title (:title issue)]
        ;; Set label with task context
        (set-attr! node "ui.label" (format-node-label (:id issue) title))
        ;; Set CSS class: open or blocked (others stay gray)
        (when (= "open" status)
          (set-attr! node "ui.class" "open"))
        (when (= "blocked" status)
          (set-attr! node "ui.class" "blocked"))))

    ;; Phase 4: Add edges from dependencies! 🎯
    (let [dependencies (beads-db/get-dependencies)]
      (log/info :create-graph-from-issues/edges
                :dependency-count (count dependencies))
      (doseq [{:keys [issue-id depends-on-id type]} dependencies]
        (let [from-node (.getNode graph depends-on-id)
              to-node (.getNode graph issue-id)]
          (when (and from-node to-node)
            (.addEdge graph (str issue-id "->" depends-on-id) depends-on-id issue-id true)))))

    ;; Apply stylesheet from Clojure data
    (set-attr! graph "ui.stylesheet" (style-map->css graph-style))

    graph))

(defn save-screenshot!
  "Save a screenshot of the graph panel to graph.png for debugging."
  [panel]
  (try
    (let [width (.getWidth panel)
          height (.getHeight panel)
          image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
          graphics (.createGraphics image)]
      (.paint panel graphics)
      (ImageIO/write image "png" (io/file "graph.png"))
      (log/info :save-screenshot! :success true :file "graph.png")
      (.dispose graphics))
    (catch Exception e
      (log/error :save-screenshot! :exception (.getMessage e)))))

(defn test-graph-render!
  "REPL helper: Render graph and save to graph.png.
  
  Usage:
    (require '[bd-viewer.ui.graph-tab :as gt])
    (require '[bd-viewer.db :as db])
    (db/init-state!)
    (gt/test-graph-render!)
    ;; Check graph.png
    ;; Adjust sizes, then:
    (require '[bd-viewer.ui.graph-tab :as gt] :reload)
    (gt/test-graph-render!)
  "
  []
  (let [issues (:issues @db/*app-state)
        graph (create-graph-from-issues issues)
        viewer (org.graphstream.ui.swing_viewer.SwingViewer.
                graph
                org.graphstream.ui.view.Viewer$ThreadingModel/GRAPH_IN_GUI_THREAD)
        view (.addDefaultView viewer false)]
    (.enableAutoLayout viewer)

    ;; Wait for layout to stabilize
    (Thread/sleep 2000)

    ;; Create image
    (let [width 1200
          height 800
          image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
          graphics (.createGraphics image)]
      (.setSize view (java.awt.Dimension. width height))
      (.paint view graphics)
      (ImageIO/write image "png" (io/file "graph.png"))
      (println "✅ Saved graph.png")
      (.dispose graphics)
      (.close viewer))

    :done))

(defn create-graph-panel []
  "Create the graph visualization panel.

  Phase 3: Show actual Beads issues from bd command!
  Returns a border-panel with graph view and header."
  (let [issues (:issues @db/*app-state)
        graph (if (empty? issues)
                (create-test-graph) ; Fallback to test data if no issues
                (create-graph-from-issues issues))
        graph-panel (embed-graph-viewer graph)
        label-text (if (empty? issues)
                     "Dependency Graph (Test Data - No Issues Loaded)"
                     (str "Dependency Graph (" (count issues) " issues)"))
        panel (s/border-panel
               :north (s/label
                       :text label-text
                       :font (Font. Font/SANS_SERIF Font/BOLD 16)
                       :border 5)
               :center graph-panel)]

    ;; Save screenshot after a delay (let graph layout stabilize)
    (future
      (Thread/sleep 2000)
      (save-screenshot! graph-panel))

    panel))

(comment

  (test-graph-render!))
