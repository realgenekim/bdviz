(ns swing-fx.core
  "Core utilities for functional Swing development.

  This library provides utilities for functional Swing:
  - watch! - Explicit watchers with EDT safety
  - notify! - Toast-like notifications

  The value isn't in code volume, but in the PATTERN demonstrated by bd-viewer."
  (:require [seesaw.invoke :as invoke]
            [seesaw.core :as s])
  (:import [javax.swing Timer JLabel JWindow]
           [java.awt Color Font BorderLayout Dimension]))

(defn watch!
  "Watch atom at path and run handler on EDT when value changes.
  Handler receives [old-value new-value].

  This is the core abstraction: explicit watchers with automatic diff checking
  and EDT safety. Unlike Seesaw's bind, this makes the data flow visible.

  Examples:

    ; Watch issues list, update UI when it changes
    (watch! *state [:issues]
      (fn [old new]
        (s/config! my-list :model new)))

    ; Watch selected item, update detail panel
    (watch! *state [:selected-issue]
      (fn [old-id new-id]
        (when new-id
          (update-detail-panel! new-id))))

    ; Watch multiple paths separately (clear and explicit)
    (watch! *state [:filter-text]
      (fn [old new]
        (filter-list! new)))

  Key design decisions:
  - Explicit: You can SEE what path is watched and what happens
  - EDT-safe: Handler always runs on Swing event dispatch thread
  - Auto-diff: Only calls handler when value actually changes
  - No magic: No hidden subscriptions or dependency tracking"
  [*atom path handler]
  (add-watch *atom (gensym "watch-")
             (fn [_ _ old-state new-state]
               (let [old-val (get-in old-state path)
                     new-val (get-in new-state path)]
                 (when (not= old-val new-val)
                   (invoke/invoke-later
                    (fn []
                      (handler old-val new-val))))))))

;; ============================================================================
;; Notifications
;; ============================================================================

(defonce ^:private notification-timer (atom nil))

(defn notify!
  "Show a toast-style notification in the upper right corner of the frame.
  Auto-hides after 3 seconds.

  Examples:
    (notify! frame \"Issues reloaded!\")
    (notify! frame \"Showing all issues\")
    (notify! frame \"Deleted issue bd-viewer-5\")"
  [frame message]
  (invoke/invoke-later
   (fn []
     ;; Cancel existing timer if any
     (when-let [timer @notification-timer]
       (.stop timer))

     ;; Create notification window
     (let [window (JWindow. frame)
           label (doto (JLabel. message)
                   (.setFont (Font. Font/SANS_SERIF Font/BOLD 14))
                   (.setForeground Color/WHITE)
                   (.setOpaque true)
                   (.setBackground (Color. 0 0 0 200)) ; Semi-transparent black
                   (.setBorder (javax.swing.BorderFactory/createEmptyBorder 10 20 10 20)))]

       ;; Setup window
       (.add (.getContentPane window) label BorderLayout/CENTER)
       (.pack window)

       ;; Position in upper right corner
       (let [frame-bounds (.getBounds frame)
             window-width (.getWidth window)
             x (+ (.x frame-bounds) (- (.width frame-bounds) window-width 20))
             y (+ (.y frame-bounds) 50)]
         (.setLocation window x y))

       ;; Show window
       (.setVisible window true)

       ;; Auto-hide after 3 seconds
       (let [timer (Timer. 3000
                           (reify java.awt.event.ActionListener
                             (actionPerformed [_ _]
                               (.setVisible window false)
                               (.dispose window))))]
         (.setRepeats timer false)
         (.start timer)
         (reset! notification-timer timer))))))

(comment
  ;; Example usage
  (def *state (atom {:count 0 :name "Alice"}))

  ;; Watch count
  (watch! *state [:count]
          (fn [old new]
            (println "Count changed:" old "->" new)))

  (swap! *state update :count inc)
  ;; => Count changed: 0 -> 1

  ;; Watch name
  (watch! *state [:name]
          (fn [old new]
            (println "Name changed:" old "->" new)))

  (swap! *state assoc :name "Bob")
  ;; => Name changed: Alice -> Bob

  ;; Updating something else doesn't trigger watchers
  (swap! *state assoc :other "value")
  ;; => (no output - only watched paths trigger handlers)
  )
