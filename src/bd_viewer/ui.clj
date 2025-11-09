(ns bd-viewer.ui
  "Swing UI components for bd-viewer."
  (:require [seesaw.core :as s]
            [swing-fx.core :as sf]
            [bd-viewer.db :as db]
            [bd-viewer.events :as events]
            [taoensso.timbre :as log])
  (:import [java.awt Font Dimension]))

;; ============================================================================
;; Hot Reload Support
;; ============================================================================

(defonce *frame (atom nil))

;; ============================================================================
;; UI Creation with Seesaw
;; ============================================================================

(defn create-content []
  "Create the UI content panel using Seesaw's declarative API.
  This is a PURE function - returns fresh widgets every time it's called.
  Called once at startup, and again on hot reload!"
  (s/border-panel
   :border 5

     ;; Top: Search bar and toolbar
   :north (s/border-panel
           :border [5 5 5 5]
           :west (s/label :text " Search: " :font (Font. Font/SANS_SERIF Font/PLAIN 16))
           :center (s/text :id :search-field
                           :columns 30
                           :font (Font. Font/SANS_SERIF Font/PLAIN 16))
           :east (s/horizontal-panel
                  :items [(s/button :id :reload-code-btn
                                    :text "Reload Code (⌘⇧R)"
                                    :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                          (s/button :id :reload-btn
                                    :text "Reload (⌘R)"
                                    :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                          (s/button :id :delete-btn
                                    :text "Delete (⌘D)"
                                    :font (Font. Font/SANS_SERIF Font/PLAIN 14))]))

     ;; Center: Split pane with issue list and detail panel
   :center (s/left-right-split
               ;; Left: Issue list - bigger monospaced font
            (s/scrollable
             (s/listbox :id :issue-list
                        :font (Font. Font/MONOSPACED Font/PLAIN 14)
                        :selection-mode :single)
             :preferred-size [400 :by 600])

               ;; Right: Detail panel
            (s/border-panel
             :id :detail-panel
             :border [10 10 10 10]

                 ;; Title at top - bigger and bold
             :north (s/label :id :title-label
                             :text "No issue selected"
                             :font (Font. Font/SANS_SERIF Font/BOLD 20))

                 ;; Description in center - bigger font
             :center (s/scrollable
                      (s/text :id :description-area
                              :multi-line? true
                              :editable? false
                              :rows 10
                              :columns 40
                              :wrap-lines? true
                              :font (Font. Font/SANS_SERIF Font/PLAIN 14)))

                 ;; Metadata at bottom - bigger font
             :south (s/vertical-panel
                     :id :metadata-panel
                     :border [10 10 10 10]
                     :items [(s/label :id :id-label :text "" :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                             (s/label :id :status-label :text "" :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                             (s/label :id :priority-label :text "" :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                             (s/label :id :type-label :text "" :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                             (s/label :id :labels-label :text "" :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                             (s/label :id :created-label :text "" :font (Font. Font/SANS_SERIF Font/PLAIN 14))
                             (s/label :id :updated-label :text "" :font (Font. Font/SANS_SERIF Font/PLAIN 14))]))

            :divider-location 400
            :resize-weight 0.4)))

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

  ;; Reload Code button
  (s/listen (s/select frame [:#reload-code-btn]) :action
            (fn [_]
              (events/handle-event {:event/type ::events/reload-code})))

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
;; Hot Reload
;; ============================================================================

(defn rebuild-ui!
  "Rebuild the UI from fresh code without restarting the app.
  This is the HOT RELOAD magic!
  
  1. Removes old content from frame
  2. Creates NEW content from fresh view functions
  3. Re-wires events to new widgets
  4. Re-sets up watchers
  5. Forces state refresh to populate new widgets
  6. Refreshes display
  
  Call this after (require 'bd-viewer.ui :reload)"
  []
  (when-let [frame @*frame]
    (sf/invoke-later
     (fn []
       (log/info :rebuild-ui! :start true)

       ;; Save current state before rebuild
       (let [current-selected (:selected-issue @db/*app-state)
             current-index (:selected-index @db/*app-state)]

         ;; Create fresh content from latest code
         (let [new-content (create-content)
               content-pane (.getContentPane frame)]

           ;; Remove old content
           (.removeAll content-pane)

           ;; Add new content
           (.add content-pane new-content java.awt.BorderLayout/CENTER)

           ;; Re-wire events to new widgets
           (wire-events! frame)

           ;; Store UI refs for effects
           (store-ui-refs! frame)

           ;; Re-setup watchers with new widgets
           ;; Note: This requires requiring effects.swing with :reload too
           (require 'bd-viewer.effects.swing :reload)
           ((resolve 'bd-viewer.effects.swing/setup-watchers!) frame)

           ;; Refresh display
           (.validate frame)
           (.repaint frame)

           ;; IMPORTANT: Force state refresh to populate new widgets!
           ;; Watchers only fire on CHANGES, so we need to trigger them
           ;; by temporarily changing state then restoring it
           (swap! db/*app-state assoc
                  :selected-issue nil
                  :selected-index -1)

           ;; Wait a tick then restore selection (triggers watchers)
           (when current-selected
             (sf/invoke-later
              (fn []
                (swap! db/*app-state assoc
                       :selected-issue current-selected
                       :selected-index current-index))))

           (log/info :rebuild-ui! :success true)))))))

;; ============================================================================
;; Main Frame Creation
;; ============================================================================

(defn create-main-frame []
  "Create and show the main application window.
  Uses defonce *frame atom for hot reload support.
  
  On first call: Creates frame, builds UI, shows window
  On subsequent calls: Returns existing frame (already visible)"
  (if @*frame
    @*frame ; Frame already exists
    (let [frame (s/frame :title "BD Viewer"
                         :size [1000 :by 700]
                         :on-close :exit)]
      (reset! *frame frame)

      ;; Build initial UI using rebuild-ui! so same code path
      ;; Create content
      (let [content (create-content)]
        (.add (.getContentPane frame) content java.awt.BorderLayout/CENTER))

      ;; Wire up event handlers
      (wire-events! frame)

      ;; Store UI references
      (store-ui-refs! frame)

      ;; Show window
      (s/show! frame)

      ;; Set initial focus to the issue list (not search bar)
      ;; This allows j/k navigation to work immediately!
      (sf/invoke-later
       (fn []
         (.requestFocusInWindow (s/select frame [:#issue-list]))))

      (log/info :create-main-frame :success true)
      frame)))
