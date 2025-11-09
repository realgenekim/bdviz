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
        title (html-escape (subs (:title issue) 0 (min 50 (count (:title issue)))))
        status (:status issue)
        is-selected (= id selected-id)
        bg-color (if is-selected "#E3F2FD" "transparent")
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
         children (map issues-by-id children-ids)
         new-acc (conj acc issue)]
     (reduce (fn [a child]
               (when child
                 (flatten-tree child dep-map issues-by-id a)))
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

    (str "<html><body style='font-family: Monaco, monospace; font-size: 14px; padding: 10px;'>"
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
        (let [url-str (str (.getURL e))
              issue-id (last (clojure.string/split url-str #"#"))]
          (log/info :hyperlink-clicked :issue-id issue-id)
          ;; Find the issue in the flat list
          (let [flat-issues @(get-in @db/*app-state [:ui-refs :tree-flat-issues])
                index (first (keep-indexed
                              (fn [idx issue]
                                (when (= (:id issue) issue-id) idx))
                              flat-issues))]
            (when index
              (events/handle-event {:event/type ::events/issue-selected
                                    :issue-id issue-id
                                    :index index}))))))))

(defn refresh-tree-html!
  "Refresh the tree HTML with current selection highlighting."
  [text-pane issues deps selected-id]
  (let [html (generate-html-tree issues deps selected-id)]
    (sf/invoke-later
     (fn []
       (.setText text-pane html)))))

(defn create-tree-panel
  "Create tree view panel with keyboard navigation.
  
  Returns a map with:
  - :component - The JScrollPane containing the tree
  - :refresh-fn - Function to call to refresh the tree display
  - :flat-issues - Atom containing flat list of issues in display order"
  [issues deps]
  (let [selected-id (:selected-issue @db/*app-state)
        flat-issues (atom (build-flat-issue-list issues deps))
        html-tree (generate-html-tree issues deps selected-id)

        text-pane (doto (JTextPane.)
                    (.setContentType "text/html")
                    (.setText html-tree)
                    (.setEditable false)
                    (.setFont (Font. "Monaco" Font/PLAIN 14))
                    (.addHyperlinkListener (create-hyperlink-listener)))

        scroll-pane (JScrollPane. text-pane)

        refresh-fn (fn []
                     (let [selected-id (:selected-issue @db/*app-state)
                           issues (:issues @db/*app-state)
                           deps (beads-db/get-dependencies)
                           open-issues (filter #(not= "closed" (:status %)) issues)
                           open-ids (set (map :id open-issues))
                           open-deps (filter (fn [{:keys [issue-id depends-on-id]}]
                                               (and (open-ids issue-id)
                                                    (open-ids depends-on-id)))
                                             deps)]
                       (reset! flat-issues (build-flat-issue-list open-issues open-deps))
                       (refresh-tree-html! text-pane open-issues open-deps selected-id)))]

    (log/info :create-tree-panel
              :issue-count (count issues)
              :dep-count (count deps)
              :flat-count (count @flat-issues))

    {:component scroll-pane
     :text-pane text-pane
     :refresh-fn refresh-fn
     :flat-issues flat-issues}))

(defn create-tree-view
  "Create the tree view component for the main UI.
  
  Returns the component ready to be placed in the left pane of a split."
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

        tree-panel (create-tree-panel open-issues open-deps)]

    ;; Store references for refresh
    (swap! db/*app-state assoc-in [:ui-refs :tree-refresh-fn] (:refresh-fn tree-panel))
    (swap! db/*app-state assoc-in [:ui-refs :tree-flat-issues] (:flat-issues tree-panel))
    (swap! db/*app-state assoc-in [:ui-refs :tree-text-pane] (:text-pane tree-panel))

    (log/info :create-tree-view
              :success true
              :total-issues (count issues)
              :open-issues (count open-issues))

    (:component tree-panel)))
