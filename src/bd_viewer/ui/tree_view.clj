(ns bd-viewer.ui.tree-view
  "Tree view component with keyboard navigation.
  
  Similar to clickable tree, but designed to work as a standalone component
  with j/k navigation and selection highlighting."
  (:require [seesaw.core :as s]
            [swing-fx.core :as sf]
            [bd-viewer.db :as db]
            [bd-viewer.beads.sqlite :as beads-db]
            [bd-viewer.events :as events]
            [taoensso.timbre :as log])
  (:import [java.awt Font Color]
           [java.awt.event KeyEvent KeyAdapter]
           [javax.swing JTextPane JScrollPane]
           [javax.swing.event HyperlinkListener HyperlinkEvent$EventType]
           [javax.swing.text StyleConstants SimpleAttributeSet]
           [java.awt.event MouseAdapter]))

;; ============================================================================
;; Customization Variables - Change these to customize tree appearance!
;; ============================================================================

(def tree-indent-spaces
  "Number of spaces for each indentation level in the tree.
  Adjust this to make the tree more or less indented."
  2)

(def tree-font-family
  "Font family for tree display."
  "Monaco, monospace")

(def tree-font-size
  "Font size in pixels for tree display.
  Increase for larger text, decrease for smaller."
  11)

(def tree-color-open
  "Hex color for open issues (default green)."
  "black")

(def tree-color-in-progress
  "Hex color for in-progress issues (default yellow/orange)."
  "#FFC107")

(def tree-color-closed
  "Hex color for closed issues (default gray)."
  "#95A5A6")

(def tree-color-selected-bg
  "Background color for selected issue (default light blue)."
  "#E3F2FD")

(def tree-title-max-length
  "Maximum length of issue title to display in tree.
  Longer titles will be truncated."
  50)

;; ============================================================================
;; Tree Building Functions
;; ============================================================================

(defn build-dep-map
  "Build a map of issue-id -> [dependent-issue-ids]."
  [deps]
  (reduce (fn [acc {:keys [depends-on-id issue-id]}]
            (update acc depends-on-id (fnil conj []) issue-id))
          {}
          deps))

(defn find-roots
  "Find issues that have no dependencies (root nodes)."
  [issues deps]
  (let [has-deps (set (map :issue-id deps))]
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
  [issue selected-id]
  (let [id (:id issue)
        num (re-find #"\d+$" id)
        title (html-escape (subs (:title issue) 0 (min tree-title-max-length (count (:title issue)))))
        status (:status issue)
        is-selected (= id selected-id)
        bg-color (if is-selected tree-color-selected-bg "transparent")
        color (case status
                "open" tree-color-open
                "in-progress" tree-color-in-progress
                "closed" tree-color-closed
                "#000000")
        status-marker (case status
                        "in-progress" "● "
                        "open" "○ "
                        "closed" "✓ "
                        "  ")]
    (str "<a href='#" id "' style='display:block;color:" color
         ";text-decoration:none;padding:2px 4px;background-color:" bg-color ";'>"
         status-marker "#" num " " title
         "</a>")))

(defn render-tree-html
  "Recursively render tree structure as HTML."
  ([issue dep-map issues-by-id selected-id]
   (render-tree-html "" true issue dep-map issues-by-id selected-id))

  ([prefix is-last issue dep-map issues-by-id selected-id]
   (let [children-ids (get dep-map (:id issue) [])
         children (map issues-by-id children-ids)
         connector (if is-last "└── " "├── ")
         line (str prefix connector (render-issue-html issue selected-id) "<br>")
         ;; Build child prefix using configured indent spaces
         indent-str (apply str (repeat tree-indent-spaces "&nbsp;"))
         child-prefix (str prefix (if is-last indent-str (str "│" (apply str (repeat (dec tree-indent-spaces) "&nbsp")))))]

     (str line
          (apply str
                 (map-indexed
                  (fn [idx child]
                    (when child
                      (render-tree-html child-prefix
                                        (= idx (dec (count children)))
                                        child
                                        dep-map
                                        issues-by-id
                                        selected-id)))
                  children))))))

(defn flatten-tree
  "Flatten tree structure to a list of issues in display order.
  This allows us to navigate with j/k in the same order as displayed."
  ([issue dep-map issues-by-id]
   (flatten-tree issue dep-map issues-by-id []))

  ([issue dep-map issues-by-id acc]
   (let [children-ids (get dep-map (:id issue) [])
         children (keep issues-by-id children-ids)
         new-acc (conj acc issue)]
     (reduce (fn [a child]
               (flatten-tree child dep-map issues-by-id a))
             new-acc
             children))))

(defn build-flat-issue-list
  "Build a flat list of all issues in tree display order."
  [issues deps]
  (let [dep-map (build-dep-map deps)
        issues-by-id (into {} (map (fn [i] [(:id i) i]) issues))
        roots (find-roots issues deps)]
    (vec (mapcat #(flatten-tree % dep-map issues-by-id) roots))))

(defn generate-html-tree
  "Generate HTML tree from issues and dependencies."
  [issues deps selected-id]
  (let [dep-map (build-dep-map deps)
        issues-by-id (into {} (map (fn [i] [(:id i) i]) issues))
        roots (find-roots issues deps)]

    (str "<html><body style='font-family: " tree-font-family "; font-size: " tree-font-size "px; padding: 10px;'>"
         (if (seq roots)
           (apply str
                  (map-indexed
                   (fn [idx root]
                     (render-tree-html "" (= idx (dec (count roots)))
                                       root dep-map issues-by-id selected-id))
                   roots))
           "No issues to display")
         "</body></html>")))

(defn create-hyperlink-listener
  "Create listener to handle issue link clicks."
  []
  (reify HyperlinkListener
    (hyperlinkUpdate [this e]
      (when (= (.getEventType e) HyperlinkEvent$EventType/ACTIVATED)
        (let [description (.getDescription e)
              issue-id (when description
                         (last (clojure.string/split description #"#")))]
          (log/info :hyperlink-clicked :issue-id issue-id :description description)
          (when issue-id
            (let [issues (db/get-filtered-issues)
                  index (first (keep-indexed
                                (fn [idx issue]
                                  (when (= (:id issue) issue-id) idx))
                                issues))]
              (when index
                (events/handle-event {:event/type ::events/issue-selected
                                      :issue-id issue-id
                                      :index index})))))))))

(defn refresh-tree-html!
  "Refresh the tree HTML with current selection highlighting."
  [text-pane issues deps selected-id]
  (let [html (generate-html-tree issues deps selected-id)]
    (sf/invoke-later
     (fn []
       (.setText text-pane html)))))

(defn create-tree-panel
  "Create tree view panel with keyboard navigation."
  [issues deps]
  (let [selected-id (:selected-issue @db/*app-state)
        flat-issues-list (build-flat-issue-list issues deps)
        _ (swap! db/*app-state assoc :tree-flat-list flat-issues-list) ; Store for j/k nav

        ;; Store current issues/deps in atoms for refresh functions
        current-issues (atom issues)
        current-deps (atom deps)
        flat-issues (atom flat-issues-list)

        html-tree (generate-html-tree issues deps selected-id)

        text-pane (doto (JTextPane.)
                    (.setContentType "text/html")
                    (.setText html-tree)
                    (.setEditable false)
                    (.setFont (Font. "Monaco" Font/PLAIN 14))
                    (.addHyperlinkListener (create-hyperlink-listener)))

        scroll-pane (JScrollPane. text-pane)

        ;; FAST: Just update highlighting, no DB query!
        highlight-fn (fn []
                       (let [selected-id (:selected-issue @db/*app-state)]
                         (refresh-tree-html! text-pane @current-issues @current-deps selected-id)))

        ;; FULL: Rebuild tree with fresh data (called when issues/deps change)
        rebuild-fn (fn []
                     (let [selected-id (:selected-issue @db/*app-state)
                           issues (:issues @db/*app-state)
                           deps (:dependencies @db/*app-state) ; Use cached deps!
                           open-issues (filter #(not= "closed" (:status %)) issues)
                           open-ids (set (map :id open-issues))
                           open-deps (filter (fn [{:keys [issue-id depends-on-id]}]
                                               (and (open-ids issue-id)
                                                    (open-ids depends-on-id)))
                                             deps)
                           new-flat-list (build-flat-issue-list open-issues open-deps)]
                       (reset! current-issues open-issues)
                       (reset! current-deps open-deps)
                       (reset! flat-issues new-flat-list)
                       (swap! db/*app-state assoc :tree-flat-list new-flat-list) ; Update for j/k nav
                       (refresh-tree-html! text-pane open-issues open-deps selected-id)))]

    (log/info :create-tree-panel
              :issue-count (count issues)
              :dep-count (count deps)
              :flat-count (count @flat-issues))

    {:component scroll-pane
     :text-pane text-pane
     :highlight-fn highlight-fn ; Fast: just highlighting
     :rebuild-fn rebuild-fn ; Slow: full rebuild
     :flat-issues flat-issues}))

(defn create-tree-view
  "Create the tree view component for the main UI."
  []
  (let [issues (:issues @db/*app-state)
        deps (:dependencies @db/*app-state) ; Use cached deps!
        open-issues (filter #(not= "closed" (:status %)) issues)
        open-ids (set (map :id open-issues))
        open-deps (filter (fn [{:keys [issue-id depends-on-id]}]
                            (and (open-ids issue-id)
                                 (open-ids depends-on-id)))
                          deps)
        tree-panel (create-tree-panel open-issues open-deps)]

    ;; Store both refresh functions in ui-refs
    (swap! db/*app-state assoc-in [:ui-refs :tree-highlight-fn] (:highlight-fn tree-panel))
    (swap! db/*app-state assoc-in [:ui-refs :tree-rebuild-fn] (:rebuild-fn tree-panel))
    (swap! db/*app-state assoc-in [:ui-refs :tree-flat-issues] (:flat-issues tree-panel))
    (swap! db/*app-state assoc-in [:ui-refs :tree-text-pane] (:text-pane tree-panel))

    (log/info :create-tree-view
              :success true
              :total-issues (count issues)
              :open-issues (count open-issues))

    (:component tree-panel)))
