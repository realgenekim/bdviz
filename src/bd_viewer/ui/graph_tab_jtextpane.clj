(ns bd-viewer.ui.graph-tab-jtextpane
  "Clickable HTML tree graph visualization.

  Renders dependency graph as HTML tree with clickable issue links."
  (:require [seesaw.core :as s]
            [bd-viewer.db :as db]
            [bd-viewer.beads.sqlite :as beads-db]
            [bd-viewer.events :as events]
            [taoensso.timbre :as log])
  (:import [java.awt Font]
           [javax.swing JTextPane JScrollPane]
           [javax.swing.event HyperlinkListener HyperlinkEvent$EventType]))

(defn build-dep-map
  "Build a map of issue-id -> [dependent-issue-ids]."
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

(defn html-escape
  "Escape HTML special characters."
  [s]
  (-> s
      (clojure.string/replace "&" "&amp;")
      (clojure.string/replace "<" "&lt;")
      (clojure.string/replace ">" "&gt;")
      (clojure.string/replace "\"" "&quot;")))

(defn render-issue-html
  "Format a single issue as HTML with clickable link."
  [issue]
  (let [id (:id issue)
        num (re-find #"\d+$" id)
        title (html-escape (subs (:title issue) 0 (min 40 (count (:title issue)))))
        status (:status issue)
        color (case status
                "open" "#2ECC71"
                "in-progress" "#FFC107"
                "closed" "#95A5A6"
                "#000000")
        status-marker (case status
                        "in-progress" "● "
                        "open" "○ "
                        "closed" "✓ "
                        "  ")]
    (str "<a href='#" id "' style='color:" color ";text-decoration:none;'>"
         status-marker "#" num " " title
         "</a>")))

(defn render-tree-html
  "Recursively render tree structure as HTML.

  prefix: HTML string prefix for current line
  is-last: Boolean indicating if this is the last child
  issue: Current issue to render
  dep-map: Map of issue-id -> [child-issue-ids]
  issues-by-id: Map of issue-id -> issue"
  ([issue dep-map issues-by-id]
   (render-tree-html "" true issue dep-map issues-by-id))

  ([prefix is-last issue dep-map issues-by-id]
   (let [children-ids (get dep-map (:id issue) [])
         children (map issues-by-id children-ids)
         connector (if is-last "└── " "├── ")
         line (str prefix connector (render-issue-html issue) "<br>")
         child-prefix (str prefix (if is-last "&nbsp;&nbsp;&nbsp;&nbsp;" "│&nbsp;&nbsp;&nbsp;"))]

     (str line
          (apply str
                 (map-indexed
                  (fn [idx child]
                    (when child
                      (render-tree-html child-prefix
                                        (= idx (dec (count children)))
                                        child
                                        dep-map
                                        issues-by-id)))
                  children))))))

(defn generate-html-tree
  "Generate HTML tree from issues and dependencies."
  [issues deps]
  (let [dep-map (build-dep-map deps)
        issues-by-id (into {} (map (fn [i] [(:id i) i]) issues))
        roots (find-roots issues deps)]

    (str "<html><body style='font-family: Monaco, monospace; font-size: 13px; padding: 10px;'>"
         (if (seq roots)
           (apply str
                  (map-indexed
                   (fn [idx root]
                     (render-tree-html "" (= idx (dec (count roots))) root dep-map issues-by-id))
                   roots))
           "No issues to display")
         "</body></html>")))

(defn create-hyperlink-listener
  "Create listener to handle issue link clicks."
  []
  (reify HyperlinkListener
    (hyperlinkUpdate [this e]
      (when (= (.getEventType e) HyperlinkEvent$EventType/ACTIVATED)
        (let [url-str (str (.getURL e))
              issue-id (last (clojure.string/split url-str #"#"))]
          (log/info :hyperlink-clicked :issue-id issue-id)
          ;; Find the issue index and select it
          (let [issues (db/get-filtered-issues)
                index (first (keep-indexed
                              (fn [idx issue]
                                (when (= (:id issue) issue-id) idx))
                              issues))]
            (when index
              (events/handle-event {:event/type ::events/issue-selected
                                    :issue-id issue-id
                                    :index index}))))))))

(defn create-html-panel
  "Create panel with HTML tree visualization."
  [issues deps]
  (let [html-tree (generate-html-tree issues deps)
        text-pane (doto (JTextPane.)
                    (.setContentType "text/html")
                    (.setText html-tree)
                    (.setEditable false)
                    (.setFont (Font. "Monaco" Font/PLAIN 13))
                    (.addHyperlinkListener (create-hyperlink-listener)))
        scroll-pane (JScrollPane. text-pane)]

    (log/info :create-html-panel
              :issue-count (count issues)
              :dep-count (count deps))

    scroll-pane))

(defn create-graph-panel
  "Create the clickable HTML tree graph panel.

  Shows all non-closed issues with their dependencies as clickable HTML."
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

        label-text (str "Clickable Dependency Tree (" (count open-issues) " open issues)")
        html-panel (create-html-panel open-issues open-deps)

        panel (s/border-panel
               :north (s/label
                       :text label-text
                       :font (Font. Font/SANS_SERIF Font/BOLD 16)
                       :border 5)
               :center html-panel)]

    (log/info :create-graph-panel
              :success true
              :total-issues (count issues)
              :open-issues (count open-issues))

    panel))
