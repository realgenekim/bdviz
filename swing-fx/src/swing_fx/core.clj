(ns swing-fx.core
  "Core utilities for functional Swing development.

  This library provides just ONE key function: watch!
  Everything else comes from Seesaw.

  The value isn't in code volume (this is ~20 LOC), but in the PATTERN
  demonstrated by bd-viewer."
  (:require [seesaw.invoke :as invoke]))

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
