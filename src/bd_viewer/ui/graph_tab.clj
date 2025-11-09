(ns bd-viewer.ui.graph-tab
  "Graph visualization tab for bd-viewer - shows dependency graph.

  Phase 1: Placeholder panel (this file!)
  Phase 2: Static graph with hardcoded data
  Phase 3: Load real Beads data from bd command
  Phase 4: Interactivity (click nodes, filters)
  Phase 5: Auto-refresh and live updates"
  (:require [seesaw.core :as s])
  (:import [java.awt Font]))

(defn create-graph-panel []
  "Create the graph visualization panel.

  Phase 1: Simple placeholder to verify tab integration works.
  Returns a border-panel with centered label."
  (s/border-panel
   :border 20
   :center (s/label
            :text "🕸️  Dependency Graph View"
            :font (Font. Font/SANS_SERIF Font/BOLD 24)
            :halign :center
            :valign :center)))
