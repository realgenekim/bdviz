(ns bd-viewer.events
  "Event handlers for bd-viewer.

  All event handlers are pure functions: old-state → new-state
  NO SWING IMPORTS! This keeps events testable and framework-agnostic."
  (:require [bd-viewer.db :as db]
            [clojure.java.shell :as shell]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]))

;; ============================================================================
;; Event Dispatch
;; ============================================================================

(defmulti handle-event
  "Dispatch events based on :event/type.
  Returns nil (events mutate state via swap!)."
  :event/type)

;; Default handler for unknown events
(defmethod handle-event :default [event]
  (log/warn :handle-event/unknown
            :event-type (:event/type event)
            :event event))

;; ============================================================================
;; Selection Events
;; ============================================================================

(defmethod handle-event ::issue-selected
  [{:keys [issue-id index]}]
  (log/debug ::issue-selected :issue-id issue-id :index index)
  (swap! db/*app-state assoc
         :selected-issue issue-id
         :selected-index (or index -1)))

(defmethod handle-event ::next-issue
  [_event]
  (log/debug ::next-issue)
  (db/select-next-issue))

(defmethod handle-event ::prev-issue
  [_event]
  (log/debug ::prev-issue)
  (db/select-prev-issue))

;; ============================================================================
;; Filter Events
;; ============================================================================

(defmethod handle-event ::filter-changed
  [{:keys [text]}]
  (log/debug ::filter-changed :text text)
  (swap! db/*app-state assoc
         :filter-text text
         ;; Reset selection when filter changes
         :selected-issue nil
         :selected-index -1))

(defmethod handle-event ::clear-filter
  [_event]
  (log/debug ::clear-filter)
  (swap! db/*app-state assoc
         :filter-text ""
         :selected-issue nil
         :selected-index -1))

(defmethod handle-event ::toggle-open-filter
  [_event]
  (let [current (:show-open-only @db/*app-state)
        new-value (not current)]
    (log/info ::toggle-open-filter
              :old current
              :new new-value)
    (swap! db/*app-state assoc
           :show-open-only new-value
           ;; Reset selection when filter changes
           :selected-issue nil
           :selected-index -1)))

;; ============================================================================
;; Sort Events
;; ============================================================================

(defmethod handle-event ::sort-changed
  [{:keys [sort-by]}]
  (log/debug ::sort-changed :sort-by sort-by)
  (swap! db/*app-state assoc :sort-by sort-by))

;; ============================================================================
;; Reload Events
;; ============================================================================

(defmethod handle-event ::reload-issues
  [_event]
  (log/info ::reload-issues :start true)
  (try
    (let [issues (db/load-issues-from-bd)
          current-selected (:selected-issue @db/*app-state)
          ;; Check if currently selected issue still exists
          still-exists? (some #(= (:id %) current-selected) issues)]
      (swap! db/*app-state assoc :issues issues)
      ;; Clear selection if selected issue was deleted externally
      (when-not still-exists?
        (swap! db/*app-state assoc
               :selected-issue nil
               :selected-index -1))
      (log/info ::reload-issues
                :success true
                :count (count issues)))
    (catch Exception e
      (log/error ::reload-issues
                 :exception (.getMessage e))
      ;; TODO: Show error dialog to user
      nil)))

;; ============================================================================
;; CRUD Events - Create, Update, Delete
;; ============================================================================

(defmethod handle-event ::delete-issue
  [_event]
  (when-let [issue-id (:selected-issue @db/*app-state)]
    (log/info ::delete-issue :issue-id issue-id)
    (try
      (let [target-dir (db/get-target-dir)
            result (shell/sh "bd" "delete" issue-id :dir target-dir)
            exit-code (:exit result)]
        (if (zero? exit-code)
          (do
            (log/info ::delete-issue
                      :success true
                      :issue-id issue-id)
            ;; Remove from local state immediately
            (swap! db/*app-state
                   (fn [state]
                     (-> state
                         (update :issues
                                 (fn [issues]
                                   (filterv #(not= (:id %) issue-id) issues)))
                         (assoc :selected-issue nil
                                :selected-index -1)))))
          (do
            (log/error ::delete-issue
                       :failed true
                       :exit-code exit-code
                       :stderr (:err result))
            ;; Return error signal for notification
            {:error (str "Failed to delete: " (:err result))})))
      (catch Exception e
        (log/error ::delete-issue
                   :exception (.getMessage e))
        {:error (.getMessage e)}))))

(defmethod handle-event ::close-issue
  [_event]
  (when-let [issue-id (:selected-issue @db/*app-state)]
    (log/info ::close-issue :issue-id issue-id)
    (try
      (let [target-dir (db/get-target-dir)
            result (shell/sh "bd" "update" issue-id "--status" "closed" :dir target-dir)
            exit-code (:exit result)]
        (if (zero? exit-code)
          (do
            (log/info ::close-issue
                      :success true
                      :issue-id issue-id)
            ;; Reload issues to get updated data
            (handle-event {:event/type ::reload-issues})
            ;; Return success indicator for notification
            :success)
          (do
            (log/error ::close-issue
                       :failed true
                       :exit-code exit-code
                       :stderr (:err result))
            ;; Return error signal for notification
            {:error (str "Failed to close: " (:err result))})))
      (catch Exception e
        (log/error ::close-issue
                   :exception (.getMessage e))
        {:error (.getMessage e)}))))

(defmethod handle-event ::new-issue
  [{:keys [title description priority issue-type labels]}]
  (log/info ::new-issue
            :title title
            :priority priority
            :type issue-type)
  (try
    (let [;; Build bd create command
          args (cond-> ["bd" "create" title]
                 description (concat ["--description" description])
                 priority (concat ["--priority" (str priority)])
                 issue-type (concat ["--type" issue-type])
                 (and labels (seq labels))
                 (concat ["--labels" (clojure.string/join "," labels)]))
          result (apply shell/sh args)
          exit-code (:exit result)]
      (if (zero? exit-code)
        (do
          (log/info ::new-issue
                    :success true
                    :output (:out result))
          ;; Reload issues to get the new one
          (handle-event {:event/type ::reload-issues}))
        (do
          (log/error ::new-issue
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result))
          ;; TODO: Show error dialog
          nil)))
    (catch Exception e
      (log/error ::new-issue
                 :exception (.getMessage e))
      nil)))

(defmethod handle-event ::update-issue
  [{:keys [issue-id title description status priority issue-type]}]
  (log/info ::update-issue :issue-id issue-id)
  (try
    (let [;; Build bd update command
          args (cond-> ["bd" "update" issue-id]
                 title (concat ["--title" title])
                 description (concat ["--description" description])
                 status (concat ["--status" status])
                 priority (concat ["--priority" (str priority)])
                 issue-type (concat ["--type" issue-type]))
          result (apply shell/sh args)
          exit-code (:exit result)]
      (if (zero? exit-code)
        (do
          (log/info ::update-issue
                    :success true
                    :issue-id issue-id)
          ;; Reload issues to get updated data
          (handle-event {:event/type ::reload-issues}))
        (do
          (log/error ::update-issue
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result))
          ;; TODO: Show error dialog
          nil)))
    (catch Exception e
      (log/error ::update-issue
                 :exception (.getMessage e))
      nil)))

;; ============================================================================
;; Dialog Events (no-ops for now, will be handled by ui.clj)
;; ============================================================================

(defmethod handle-event ::show-new-issue-dialog
  [_event]
  (log/debug ::show-new-issue-dialog)
  ;; This will be handled by UI code opening a dialog
  ;; Event handler is just for logging
  nil)

(defmethod handle-event ::show-edit-dialog
  [{:keys [issue-id]}]
  (log/debug ::show-edit-dialog :issue-id issue-id)
  ;; This will be handled by UI code
  nil)

(defmethod handle-event ::show-delete-confirmation
  [{:keys [issue-id]}]
  (log/debug ::show-delete-confirmation :issue-id issue-id)
  ;; This will be handled by UI code
  nil)

(comment
  ;; REPL testing
  (require '[bd-viewer.db :as db])

  ;; Initialize
  (db/init-state!)

  ;; Test selection
  (handle-event {:event/type ::issue-selected
                 :issue-id "bd-viewer-1"
                 :index 0})

  (:selected-issue @db/*app-state)

  ;; Test navigation
  (handle-event {:event/type ::next-issue})
  (handle-event {:event/type ::prev-issue})

  ;; Test filter
  (handle-event {:event/type ::filter-changed :text "UI"})
  (:filter-text @db/*app-state)

  ;; Test reload
  (handle-event {:event/type ::reload-issues})

  ;; Test create (will actually create an issue!)
  (handle-event {:event/type ::new-issue
                 :title "Test issue from REPL"
                 :description "Testing event handler"
                 :priority 2
                 :issue-type "task"
                 :labels ["test" "repl"]})

  ;; Test delete (be careful!)
  ;; (handle-event {:event/type ::delete-issue})
  )

(defmethod handle-event ::reload-code
  [_event]
  (log/info ::reload-code :start true)
  (try
    ;; Reload all namespaces with fresh code
    ;; IMPORTANT: Reload db FIRST, before events, so new functions are available
    (require 'bd-viewer.db :reload)
    (require 'bd-viewer.events :reload)
    (require 'bd-viewer.effects.swing :reload)
    (require 'bd-viewer.keyboard :reload)
    (require 'bd-viewer.ui :reload)

    ;; Rebuild UI with fresh view functions
    ((resolve 'bd-viewer.ui/rebuild-ui!))

    (log/info ::reload-code :success true)
    (catch Exception e
      (log/error ::reload-code
                 :exception (.getMessage e)
                 :stacktrace (with-out-str (.printStackTrace e)))
      nil)))
