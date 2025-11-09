(ns bd-viewer.ui
  "Swing UI components for bd-viewer."
  (:require [seesaw.core :as s]
            [bd-viewer.db :as db]
            [bd-viewer.events :as events]
            [taoensso.timbre :as log])
  (:import [java.awt Font Dimension]))

;; ============================================================================
;; UI Creation with Seesaw
;; ============================================================================

(defn create-ui []
  "Create the main UI using Seesaw's declarative API."
  (s/frame
   :title "BD Viewer"
   :size [1000 :by 700]
   :on-close :exit
   :content
   (s/border-panel
    :border 5

      ;; Top: Search bar and toolbar
    :north (s/border-panel
            :border [5 5 5 5]
            :west (s/label " Search: ")
            :center (s/text :id :search-field
                            :columns 30
                            :font (Font. Font/SANS_SERIF Font/PLAIN 14))
            :east (s/horizontal-panel
                   :items [(s/button :id :reload-btn
                                     :text "Reload (⌘R)")
                           (s/button :id :delete-btn
                                     :text "Delete (⌘D)")]))

      ;; Center: Split pane with issue list and detail panel
    :center (s/left-right-split
                ;; Left: Issue list
             (s/scrollable
              (s/listbox :id :issue-list
                         :font (Font. Font/MONOSPACED Font/PLAIN 12)
                         :selection-mode :single)
              :preferred-size [400 :by 600])

                ;; Right: Detail panel
             (s/border-panel
              :id :detail-panel
              :border [10 10 10 10]

                  ;; Title at top
              :north (s/label :id :title-label
                              :text "No issue selected"
                              :font (Font. Font/SANS_SERIF Font/BOLD 16))

                  ;; Description in center
              :center (s/scrollable
                       (s/text :id :description-area
                               :multi-line? true
                               :editable? false
                               :rows 10
                               :columns 40
                               :wrap-lines? true
                               :font (Font. Font/SANS_SERIF Font/PLAIN 12)))

                  ;; Metadata at bottom
              :south (s/vertical-panel
                      :id :metadata-panel
                      :border [10 10 10 10]
                      :items [(s/label :id :id-label :text "")
                              (s/label :id :status-label :text "")
                              (s/label :id :priority-label :text "")
                              (s/label :id :type-label :text "")
                              (s/label :id :labels-label :text "")
                              (s/label :id :created-label :text "")
                              (s/label :id :updated-label :text "")]))

             :divider-location 400
             :resize-weight 0.4))))

;; ============================================================================
;; Event Wiring
;; ============================================================================

(defn wire-events! [frame]
  "Wire up event handlers using Seesaw's listen."

  ;; Search field - listen to document changes
  (s/listen (s/select frame [:#search-field]) :document
            (fn [e]
              (events/handle-event
               {:event/type ::events/filter-changed
                :text (s/text (s/select frame [:#search-field]))})))

  ;; Issue list - listen to selection changes
  (s/listen (s/select frame [:#issue-list]) :selection
            (fn [e]
              (when-let [selected (s/selection e)]
                (let [index (.getSelectedIndex (s/to-widget e))
                      filtered (db/get-filtered-issues)
                      issue (nth filtered index nil)]
                  (when issue
                    (events/handle-event
                     {:event/type ::events/issue-selected
                      :issue-id (:id issue)
                      :index index}))))))

  ;; Reload button
  (s/listen (s/select frame [:#reload-btn]) :action
            (fn [_]
              (events/handle-event {:event/type ::events/reload-issues})))

  ;; Delete button
  (s/listen (s/select frame [:#delete-btn]) :action
            (fn [_]
              (events/handle-event {:event/type ::events/delete-issue}))))

;; ============================================================================
;; UI Reference Storage
;; ============================================================================

(defn store-ui-refs! [frame]
  "Store UI component references in app state for effects layer."
  (swap! db/*app-state assoc-in [:ui-refs :frame] frame)
  (swap! db/*app-state assoc-in [:ui-refs :issue-list] (s/select frame [:#issue-list]))
  (swap! db/*app-state assoc-in [:ui-refs :search-field] (s/select frame [:#search-field]))
  (swap! db/*app-state assoc-in [:ui-refs :detail-panel] (s/select frame [:#detail-panel]))
  (swap! db/*app-state assoc-in [:ui-refs :title-label] (s/select frame [:#title-label]))
  (swap! db/*app-state assoc-in [:ui-refs :description-area] (s/select frame [:#description-area]))
  (swap! db/*app-state assoc-in [:ui-refs :id-label] (s/select frame [:#id-label]))
  (swap! db/*app-state assoc-in [:ui-refs :status-label] (s/select frame [:#status-label]))
  (swap! db/*app-state assoc-in [:ui-refs :priority-label] (s/select frame [:#priority-label]))
  (swap! db/*app-state assoc-in [:ui-refs :type-label] (s/select frame [:#type-label]))
  (swap! db/*app-state assoc-in [:ui-refs :labels-label] (s/select frame [:#labels-label]))
  (swap! db/*app-state assoc-in [:ui-refs :created-label] (s/select frame [:#created-label]))
  (swap! db/*app-state assoc-in [:ui-refs :updated-label] (s/select frame [:#updated-label])))

;; ============================================================================
;; Main Frame Creation
;; ============================================================================

(defn create-main-frame []
  "Create and show the main application window."
  (let [frame (create-ui)]

    ;; Wire up event handlers
    (wire-events! frame)

    ;; Store UI references
    (store-ui-refs! frame)

    ;; Show window
    (s/show! frame)

    ;; Set initial focus to the issue list (not search bar)
    ;; This allows j/k navigation to work immediately!
    (s/invoke-later
     (fn []
       (.requestFocusInWindow (s/select frame [:#issue-list]))))

    (log/info :create-main-frame :success true)
    frame))
