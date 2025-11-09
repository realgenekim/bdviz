(ns bd-viewer.effects.swing
  "Swing effects system - all UI mutations happen here.

  Uses swing-fx watchers to observe state changes and trigger EDT-safe UI updates."
  (:require [swing-fx.core :as sf]
            [seesaw.core :as s]
            [bd-viewer.db :as db]
            [taoensso.timbre :as log])
  (:import [javax.swing DefaultListModel JList JLabel JTextArea Timer]
           [java.awt Color Font]))

;; ============================================================================
;; Watcher Setup with swing-fx
;; ============================================================================

(defn format-issue-item
  "Format an issue for display in the list.
  Adds '* ' prefix for in-progress issues, '  ' for open/closed."
  [issue]
  (let [prefix (if (= "in-progress" (:status issue)) "* " "  ")]
    (str prefix
         (:id issue)
         " [" (db/priority-label (:priority issue)) "] "
         (:title issue))))

(defn setup-watchers!
  "Register state watchers using swing-fx.
  Each watcher is explicit and EDT-safe automatically!"
  [frame]
  (log/info :setup-watchers! :start true)

  ;; Watch issues list - update when issues or filter changes
  (sf/watch! db/*app-state [:issues]
             (fn [old new]
               ;; Update main issues list
               (when-let [listbox (s/select frame [:#issue-list])]
                 (let [filtered-issues (db/get-filtered-issues)
                       display-items (mapv format-issue-item filtered-issues)]
                   (log/debug :update-issue-list! :count (count filtered-issues))
                   (s/config! listbox :model display-items)))

               ;; Update list view listbox
               (when-let [listbox (s/select frame [:#list-view-listbox])]
                 (let [filtered-issues (db/get-filtered-issues)
                       display-items (mapv format-issue-item filtered-issues)]
                   (log/debug :update-list-view-listbox! :count (count filtered-issues))
                   (s/config! listbox :model display-items)))))

  ;; Watch filter text - update list when filter changes
  (sf/watch! db/*app-state [:filter-text]
             (fn [old new]
               ;; Update main issues list
               (when-let [listbox (s/select frame [:#issue-list])]
                 (let [filtered-issues (db/get-filtered-issues)
                       display-items (mapv format-issue-item filtered-issues)]
                   (log/debug :update-filtered-list! :count (count filtered-issues))
                   (s/config! listbox :model display-items)))

               ;; Update list view listbox
               (when-let [listbox (s/select frame [:#list-view-listbox])]
                 (let [filtered-issues (db/get-filtered-issues)
                       display-items (mapv format-issue-item filtered-issues)]
                   (s/config! listbox :model display-items)))

               ;; Also sync search field text (for programmatic filter changes)
               (when-let [search-field (s/select frame [:#search-field])]
                 (let [current-text (s/text search-field)]
                   (when (not= current-text new)
                     (log/debug :update-search-field! :text new)
                     (s/text! search-field new))))))

  ;; Watch show-open-only toggle - update list and tree
  (sf/watch! db/*app-state [:show-open-only]
             (fn [old new]
               ;; Update issues list
               (when-let [listbox (s/select frame [:#issue-list])]
                 (let [filtered-issues (db/get-filtered-issues)
                       display-items (mapv format-issue-item filtered-issues)]
                   (log/debug :update-open-only-filter! :show-open-only new :count (count filtered-issues))
                   (s/config! listbox :model display-items)))

               ;; Update list view listbox
               (when-let [listbox (s/select frame [:#list-view-listbox])]
                 (let [filtered-issues (db/get-filtered-issues)
                       display-items (mapv format-issue-item filtered-issues)]
                   (s/config! listbox :model display-items)))

               ;; Update tree view
               (when-let [refresh-fn (get-in @db/*app-state [:ui-refs :tree-refresh-fn])]
                 (refresh-fn))))

  ;; Watch selected index - update JList selection
  (sf/watch! db/*app-state [:selected-index]
             (fn [old new]
               ;; Update main issues list
               (when-let [^javax.swing.JList listbox (s/select frame [:#issue-list])]
                 (log/debug :update-selection! :index new)
                 (if (>= new 0)
                   (do
                     (.setSelectedIndex listbox new)
                     (.ensureIndexIsVisible listbox new))
                   (.clearSelection listbox)))

               ;; Update list view listbox
               (when-let [^javax.swing.JList listbox (s/select frame [:#list-view-listbox])]
                 (if (>= new 0)
                   (do
                     (.setSelectedIndex listbox new)
                     (.ensureIndexIsVisible listbox new))
                   (.clearSelection listbox)))))

  ;; Watch selected issue - update detail panels and tree highlighting
  (sf/watch! db/*app-state [:selected-issue]
             (fn [old new]
               (let [issue (when new (db/get-issue-by-id new))]
                 (log/debug :update-detail-panel! :issue-id new :has-issue (some? issue))

                 ;; Helper to update detail panel labels
                 (letfn [(update-detail-panel! [prefix]
                           (if issue
                             ;; Populate with issue data
                             (do
                               (when-let [title-label (s/select frame [(keyword (str prefix "title-label"))])]
                                 (s/config! title-label :text (:title issue)))

                               (when-let [desc-area (s/select frame [(keyword (str prefix "description-area"))])]
                                 (s/config! desc-area :text (or (:description issue) "")))

                               (when-let [id-label (s/select frame [(keyword (str prefix "id-label"))])]
                                 (s/config! id-label :text (str "ID: " (:id issue))))

                               (when-let [^JLabel status-label (s/select frame [(keyword (str prefix "status-label"))])]
                                 (let [status (:status issue)]
                                   (.setText status-label (str "Status: " status))
                                   ;; Color code status
                                   (.setForeground status-label
                                                   (case status
                                                     "open" (Color. 34 139 34) ; Forest green
                                                     "in-progress" (Color. 255 140 0) ; Dark orange
                                                     "closed" (Color. 128 128 128) ; Gray
                                                     Color/BLACK))))

                               (when-let [priority-label (s/select frame [(keyword (str prefix "priority-label"))])]
                                 (s/config! priority-label
                                            :text (str "Priority: " (db/priority-label (:priority issue)))))

                               (when-let [type-label (s/select frame [(keyword (str prefix "type-label"))])]
                                 (s/config! type-label
                                            :text (str "Type: " (:issue-type issue))))

                               (when-let [labels-label (s/select frame [(keyword (str prefix "labels-label"))])]
                                 (let [labels (:labels issue)]
                                   (s/config! labels-label
                                              :text (if (seq labels)
                                                      (str "Labels: " (clojure.string/join ", " labels))
                                                      "Labels: none"))))

                               (when-let [created-label (s/select frame [(keyword (str prefix "created-label"))])]
                                 (s/config! created-label
                                            :text (str "Created: " (:created-at issue))))

                               (when-let [updated-label (s/select frame [(keyword (str prefix "updated-label"))])]
                                 (s/config! updated-label
                                            :text (str "Updated: " (:updated-at issue)))))

                             ;; No issue selected - clear everything
                             (do
                               (when-let [title-label (s/select frame [(keyword (str prefix "title-label"))])]
                                 (s/config! title-label :text "No issue selected"))
                               (when-let [desc-area (s/select frame [(keyword (str prefix "description-area"))])]
                                 (s/config! desc-area :text ""))
                               (when-let [id-label (s/select frame [(keyword (str prefix "id-label"))])]
                                 (s/config! id-label :text ""))
                               (when-let [status-label (s/select frame [(keyword (str prefix "status-label"))])]
                                 (s/config! status-label :text ""))
                               (when-let [priority-label (s/select frame [(keyword (str prefix "priority-label"))])]
                                 (s/config! priority-label :text ""))
                               (when-let [type-label (s/select frame [(keyword (str prefix "type-label"))])]
                                 (s/config! type-label :text ""))
                               (when-let [labels-label (s/select frame [(keyword (str prefix "labels-label"))])]
                                 (s/config! labels-label :text ""))
                               (when-let [created-label (s/select frame [(keyword (str prefix "created-label"))])]
                                 (s/config! created-label :text ""))
                               (when-let [updated-label (s/select frame [(keyword (str prefix "updated-label"))])]
                                 (s/config! updated-label :text "")))))]

                   ;; Update all three detail panels (issues tab, tree tab, list tab)
                   (update-detail-panel! "#")
                   (update-detail-panel! "#tree-")
                   (update-detail-panel! "#list-")

                   ;; Refresh tree view to highlight selected issue
                   (when-let [refresh-fn (get-in @db/*app-state [:ui-refs :tree-refresh-fn])]
                     (refresh-fn))))))

  ;; IMPORTANT: Do initial population on EDT!
  ;; Watchers only fire on changes, so we need to manually populate initially
  (javax.swing.SwingUtilities/invokeLater
   (fn []
     (when-let [listbox (s/select frame [:#issue-list])]
       (let [filtered-issues (db/get-filtered-issues)
             display-items (mapv format-issue-item filtered-issues)]
         (log/info :initial-population! :count (count filtered-issues))
         (s/config! listbox :model display-items)))

     (when-let [listbox (s/select frame [:#list-view-listbox])]
       (let [filtered-issues (db/get-filtered-issues)
             display-items (mapv format-issue-item filtered-issues)]
         (log/info :initial-population-list-view! :count (count filtered-issues))
         (s/config! listbox :model display-items)))))

  (log/info :setup-watchers! :success true))

(comment
  ;; REPL testing
  (require '[bd-viewer.db :as db])

  ;; Setup
  (db/init-state!)

  ;; Watchers will be set up when UI is created
  ;; Test state changes
  (swap! db/*app-state assoc :selected-issue "bd-viewer-1")
  (swap! db/*app-state assoc :filter-text "UI"))
