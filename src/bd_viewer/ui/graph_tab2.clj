(ns bd-viewer.ui.graph-tab2
  "Graph visualization tab for bd-viewer - shows dependency graph using Mermaid.

  Replaced GraphStream with Mermaid.ink rendering for clean, overlap-free diagrams!"
  (:require [seesaw.core :as s]
            [bd-viewer.db :as db]
            [bd-viewer.beads.sqlite :as beads-db]
            [bd-viewer.mermaid :as mermaid]
            [taoensso.timbre :as log]
            [clojure.java.io :as io])
  (:import [java.awt Font BorderLayout]
           [javax.swing JPanel JLabel ImageIcon JScrollPane]))

(defn create-diagram-panel
  "Create panel displaying Mermaid diagram as image.
  
  Fetches diagram from mermaid.ink with 2000px width for better readability,
  and displays in a scrollable panel."
  [issues deps]
  (try
    (let [mermaid-str (mermaid/generate-mermaid-diagram issues deps)
          ;; Request 2000px wide image from mermaid.ink for better readability
          img (mermaid/fetch-diagram-image mermaid-str :width 2000)]
      (if img
        (do
          ;; Save diagram to target directory
          (let [target-dir (db/get-target-dir)
                output-file (str target-dir "/dependency-graph.png")]
            (javax.imageio.ImageIO/write img "png" (io/file output-file))
            (log/info :save-diagram :success true :file output-file))

          ;; Display in UI with scroll pane
          (let [icon (ImageIcon. img)
                label (JLabel. icon)
                scroll-pane (JScrollPane. label)]
            scroll-pane))
        ;; Error case - show message
        (s/label :text "Error: Could not fetch diagram from mermaid.ink"
                 :font (Font. Font/SANS_SERIF Font/BOLD 16))))
    (catch Exception e
      (log/error :create-diagram-panel :exception (.getMessage e))
      (s/label :text (str "Error: " (.getMessage e))
               :font (Font. Font/SANS_SERIF Font/BOLD 16)))))

(defn create-graph-panel
  "Create the graph visualization panel using Mermaid.

  Shows all non-closed issues with their dependencies."
  []
  (let [issues (:issues @db/*app-state)
        deps (beads-db/get-dependencies)

        ;; Filter to non-closed issues
        open-issues (filter #(not= "closed" (:status %)) issues)

        label-text (str "Dependency Graph (" (count open-issues) " open issues)")
        diagram-panel (create-diagram-panel issues deps)

        panel (s/border-panel
               :north (s/label
                       :text label-text
                       :font (Font. Font/SANS_SERIF Font/BOLD 16)
                       :border 5)
               :center diagram-panel)]

    (log/info :create-graph-panel :success true
              :total-issues (count issues)
              :open-issues (count open-issues))

    panel))
