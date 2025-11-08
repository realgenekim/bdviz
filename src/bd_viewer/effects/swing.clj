(ns bd-viewer.effects.swing
  "Swing effects system - all UI mutations happen here.

  Watchers observe state changes and trigger EDT-safe UI updates."
  (:require [bd-viewer.db :as db]
            [bd-viewer.events :as events]
            [taoensso.timbre :as log])
  (:import [javax.swing SwingUtilities DefaultListModel JList JLabel JTextArea]
           [java.awt Color]))

;; ============================================================================
;; Issue List Updates
;; ============================================================================

(defn update-issue-list!
  "Update JList when :issues or :filter-text changes."
  [old-state new-state]
  (when (or (not= (:issues old-state) (:issues new-state))
            (not= (:filter-text old-state) (:filter-text new-state)))
    (SwingUtilities/invokeLater
      (fn []
        (when-let [jlist ^JList (get-in new-state [:ui-refs :issue-list])]
          (let [^DefaultListModel model (.getModel jlist)
                filtered-issues (db/get-filtered-issues)]
            (log/debug :update-issue-list!
                       :count (count filtered-issues))
            (.clear model)
            (doseq [issue filtered-issues]
              ;; Display as: "bd-viewer-1 [P0] Title here"
              (let [display-text (str (:id issue)
                                     " [" (db/priority-label (:priority issue)) "] "
                                     (:title issue))]
                (.addElement model display-text)))))))))

;; ============================================================================
;; Selection Updates
;; ============================================================================

(defn update-selection!
  "Update JList selection when :selected-index changes."
  [old-state new-state]
  (when (not= (:selected-index old-state) (:selected-index new-state))
    (SwingUtilities/invokeLater
      (fn []
        (when-let [jlist ^JList (get-in new-state [:ui-refs :issue-list])]
          (let [index (:selected-index new-state)]
            (log/debug :update-selection! :index index)
            (if (>= index 0)
              (do
                (.setSelectedIndex jlist index)
                (.ensureIndexIsVisible jlist index))  ; Scroll to make visible
              (.clearSelection jlist))))))))

;; ============================================================================
;; Detail Panel Updates
;; ============================================================================

(defn update-detail-panel!
  "Update detail panel when :selected-issue changes."
  [old-state new-state]
  (when (not= (:selected-issue old-state) (:selected-issue new-state))
    (SwingUtilities/invokeLater
      (fn []
        (let [issue-id (:selected-issue new-state)
              issue (when issue-id (db/get-issue-by-id issue-id))
              refs (:ui-refs new-state)]
          (log/debug :update-detail-panel!
                     :issue-id issue-id
                     :has-issue (some? issue))

          (if issue
            ;; Populate with issue data
            (do
              (when-let [^JLabel title-label (:title-label refs)]
                (.setText title-label (str (:title issue))))

              (when-let [^JTextArea desc-area (:description-area refs)]
                (.setText desc-area (or (:description issue) "")))

              (when-let [^JLabel id-label (:id-label refs)]
                (.setText id-label (str "ID: " (:id issue))))

              (when-let [^JLabel status-label (:status-label refs)]
                (let [status (:status issue)]
                  (.setText status-label (str "Status: " status))
                  ;; Color code status
                  (.setForeground status-label
                                 (case status
                                   "open" (Color. 34 139 34)      ; Forest green
                                   "in-progress" (Color. 255 140 0) ; Dark orange
                                   "closed" (Color. 128 128 128)   ; Gray
                                   Color/BLACK))))

              (when-let [^JLabel priority-label (:priority-label refs)]
                (.setText priority-label
                         (str "Priority: " (db/priority-label (:priority issue)))))

              (when-let [^JLabel type-label (:type-label refs)]
                (.setText type-label
                         (str "Type: " (:issue-type issue))))

              (when-let [^JLabel labels-label (:labels-label refs)]
                (let [labels (:labels issue)]
                  (.setText labels-label
                           (if (seq labels)
                             (str "Labels: " (clojure.string/join ", " labels))
                             "Labels: none"))))

              (when-let [^JLabel created-label (:created-label refs)]
                (.setText created-label
                         (str "Created: " (:created-at issue))))

              (when-let [^JLabel updated-label (:updated-label refs)]
                (.setText updated-label
                         (str "Updated: " (:updated-at issue)))))

            ;; No issue selected - clear everything
            (do
              (when-let [^JLabel title-label (:title-label refs)]
                (.setText title-label "No issue selected"))
              (when-let [^JTextArea desc-area (:description-area refs)]
                (.setText desc-area ""))
              (when-let [^JLabel id-label (:id-label refs)]
                (.setText id-label ""))
              (when-let [^JLabel status-label (:status-label refs)]
                (.setText status-label ""))
              (when-let [^JLabel priority-label (:priority-label refs)]
                (.setText priority-label ""))
              (when-let [^JLabel type-label (:type-label refs)]
                (.setText type-label ""))
              (when-let [^JLabel labels-label (:labels-label refs)]
                (.setText labels-label ""))
              (when-let [^JLabel created-label (:created-label refs)]
                (.setText created-label ""))
              (when-let [^JLabel updated-label (:updated-label refs)]
                (.setText updated-label "")))))))))

;; ============================================================================
;; Search Field Updates
;; ============================================================================

(defn update-search-field!
  "Sync search field text (needed for programmatic filter changes)."
  [old-state new-state]
  (when (not= (:filter-text old-state) (:filter-text new-state))
    (SwingUtilities/invokeLater
      (fn []
        (when-let [search-field (get-in new-state [:ui-refs :search-field])]
          (let [current-text (.getText search-field)
                new-text (:filter-text new-state)]
            ;; Only update if different (avoid infinite loop)
            (when (not= current-text new-text)
              (log/debug :update-search-field! :text new-text)
              (.setText search-field new-text))))))))

;; ============================================================================
;; Watcher Setup
;; ============================================================================

(defn setup-watchers!
  "Register state watchers that trigger UI updates.
  Call this once during app initialization."
  []
  (log/info :setup-watchers! :start true)
  (add-watch db/*app-state ::sync-ui
    (fn [_ _ old-state new-state]
      ;; Only update what changed
      (update-issue-list! old-state new-state)
      (update-selection! old-state new-state)
      (update-detail-panel! old-state new-state)
      (update-search-field! old-state new-state)))
  (log/info :setup-watchers! :success true))

(defn remove-watchers!
  "Remove watchers (useful for hot reload)."
  []
  (remove-watch db/*app-state ::sync-ui))

(comment
  ;; REPL testing
  (require '[bd-viewer.db :as db])

  ;; Setup
  (db/init-state!)
  (setup-watchers!)

  ;; Test state changes (won't work until UI is created)
  (swap! db/*app-state assoc :selected-issue "bd-viewer-1")
  (swap! db/*app-state assoc :filter-text "UI")

  ;; Remove watchers
  (remove-watchers!)
  )
