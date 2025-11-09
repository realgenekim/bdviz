(ns bd-viewer.ui.graph-tab-ascii
  "ASCII tree graph visualization - like 'git log --graph'

  Renders dependency graph as text tree using Unicode box-drawing characters."
  (:require [seesaw.core :as s]
            [bd-viewer.db :as db]
            [bd-viewer.beads.sqlite :as beads-db]
            [taoensso.timbre :as log])
  (:import [java.awt Font Color]
           [javax.swing JTextArea JScrollPane]))

(defn build-dep-map
  "Build a map of issue-id -> [dependent-issue-ids]

  Example: {\"bd-1\" [\"bd-2\" \"bd-3\"], \"bd-2\" [\"bd-4\"]}"
  [deps]
  (reduce (fn [acc {:keys [depends-on-id issue-id]}]
            (update acc depends-on-id (fnil conj []) issue-id))
          {}
          deps))

(defn find-roots
  "Find issues that have no dependencies (root nodes).
  
  Root nodes are issues that don't depend on anything - they are at the top of the tree."
  [issues deps]
  (let [;; Set of issue IDs that HAVE dependencies (depend on something)
        has-deps (set (map :issue-id deps))]
    ;; Return issues that are NOT in this set (have no dependencies)
    (filter (fn [issue]
              (not (has-deps (:id issue))))
            issues)))

(defn render-issue-line
  "Format a single issue line with color and status."
  [issue]
  (let [id (:id issue)
        num (re-find #"\d+$" id)
        title (subs (:title issue) 0 (min 40 (count (:title issue))))
        status (:status issue)
        status-marker (case status
                        "in-progress" "● "
                        "open" "○ "
                        "closed" "✓ "
                        "  ")]
    (str status-marker "#" num " " title)))

(defn render-tree
  "Recursively render tree structure.

  prefix: String prefix for current line (e.g. '│   ')
  is-last: Boolean indicating if this is the last child
  issue: Current issue to render
  dep-map: Map of issue-id -> [child-issue-ids]
  issues-by-id: Map of issue-id -> issue"
  ([issue dep-map issues-by-id]
   (render-tree "" true issue dep-map issues-by-id))

  ([prefix is-last issue dep-map issues-by-id]
   (let [children-ids (get dep-map (:id issue) [])
         children (map issues-by-id children-ids)
         connector (if is-last "└── " "├── ")
         line (str prefix connector (render-issue-line issue) "\n")
         child-prefix (str prefix (if is-last "    " "│   "))]

     (str line
          (apply str
                 (map-indexed
                  (fn [idx child]
                    (when child
                      (render-tree child-prefix
                                   (= idx (dec (count children)))
                                   child
                                   dep-map
                                   issues-by-id)))
                  children))))))

(defn generate-ascii-tree
  "Generate ASCII tree from issues and dependencies."
  [issues deps]
  (let [dep-map (build-dep-map deps)
        issues-by-id (into {} (map (fn [i] [(:id i) i]) issues))
        roots (find-roots issues deps)]

    (if (seq roots)
      (apply str
             (map-indexed
              (fn [idx root]
                (render-tree "" (= idx (dec (count roots))) root dep-map issues-by-id))
              roots))
      "No issues to display")))

(defn create-ascii-panel
  "Create panel with ASCII tree visualization."
  [issues deps]
  (let [tree-text (generate-ascii-tree issues deps)
        text-area (doto (JTextArea. tree-text)
                    (.setFont (Font. "Monaco" Font/PLAIN 13))
                    (.setEditable false)
                    (.setBackground Color/WHITE)
                    (.setForeground Color/BLACK))
        scroll-pane (JScrollPane. text-area)]

    (log/info :create-ascii-panel
              :issue-count (count issues)
              :dep-count (count deps))

    scroll-pane))

(defn create-graph-panel
  "Create the ASCII tree graph panel.

  Shows all non-closed issues with their dependencies in text format."
  []
  (let [issues (:issues @db/*app-state)
        deps (beads-db/get-dependencies)

        ;; Filter to non-closed issues
        open-issues (filter #(not= "closed" (:status %)) issues)
        open-ids (set (map :id open-issues))

        ;; Filter deps to only include open issues
        open-deps (filter (fn [{:keys [issue-id depends-on-id]}]
                            (and (open-ids issue-id)
                                 (open-ids depends-on-id)))
                          deps)

        label-text (str "ASCII Dependency Tree (" (count open-issues) " open issues)")
        ascii-panel (create-ascii-panel open-issues open-deps)

        panel (s/border-panel
               :north (s/label
                       :text label-text
                       :font (Font. Font/SANS_SERIF Font/BOLD 16)
                       :border 5)
               :center ascii-panel)]

    (log/info :create-graph-panel
              :success true
              :total-issues (count issues)
              :open-issues (count open-issues))

    panel))
