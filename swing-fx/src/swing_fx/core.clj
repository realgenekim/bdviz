(ns swing-fx.core
  "Core utilities for functional Swing development.

  This library provides utilities for functional Swing:
  - watch! - Explicit watchers with EDT safety
  - invoke-later - EDT-safe execution wrapper
  - set-selection! - Set JList selection reliably
  - notify! - Toast-like notifications

  The value isn't in code volume, but in the PATTERN demonstrated by bd-viewer.
  
  ═══════════════════════════════════════════════════════════════════════════
  CRITICAL: Which Seesaw Functions to Use (and NOT Use)
  ═══════════════════════════════════════════════════════════════════════════
  
  ✅ SAFE TO USE (these work correctly):
     - seesaw.core/select          (find widgets by ID)
     - seesaw.core/config!         (set widget properties)
     - seesaw.core/text            (get text from widgets)
     - seesaw.core/text!           (set text in widgets)
     - seesaw.core/listen          (add event listeners)
     - seesaw.core/frame           (create frames)
     - seesaw.core/button          (create buttons)
     - seesaw.core/label           (create labels)
     - seesaw.core/listbox         (create listboxes)
     - seesaw.core/scrollable      (add scrollbars)
     - seesaw.core/border-panel    (layout managers)
     - seesaw.core/horizontal-panel
     - seesaw.core/vertical-panel
  
  ❌ DO NOT USE (these have bugs - use swing-fx alternatives):
     - seesaw.invoke/invoke-later  → Use swing-fx.core/invoke-later instead!
     - seesaw.core/selection!      → Use swing-fx.core/set-selection! instead!
  
  Why? Seesaw's invoke-later doesn't actually execute lambdas on the EDT,
  and selection! returns nil without setting the selection. We've verified
  these bugs through extensive debugging. Use the swing-fx wrappers which
  call Java Swing APIs directly.
  ═══════════════════════════════════════════════════════════════════════════"
  (:require [seesaw.core :as s])
  (:import [javax.swing Timer JLabel JWindow JList SwingUtilities]
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
  (let [watch-key (keyword (str "watch-" (hash path)))]
    (add-watch *atom watch-key
               (fn [k ref old-state new-state]
                 (let [old-val (get-in old-state path)
                       new-val (get-in new-state path)]
                   (when (not= old-val new-val)
                     (javax.swing.SwingUtilities/invokeLater
                      (reify Runnable
                        (run [_]
                          (handler old-val new-val))))))))))

;; ============================================================================
;; EDT-Safe Execution (replaces buggy seesaw.invoke/invoke-later)
;; ============================================================================

(defn invoke-later
  "Execute function on the Swing Event Dispatch Thread.
  
  ⚠️  DO NOT use seesaw.invoke/invoke-later - it has a bug where lambdas don't execute!
  ✅  Use this function instead - it uses SwingUtilities/invokeLater directly.
  
  Examples:
    (invoke-later
      (fn []
        (s/config! my-label :text \"Updated!\")))
    
    ; Or with inline lambda
    (invoke-later #(println \"On EDT!\"))"
  [f]
  (SwingUtilities/invokeLater
   (reify Runnable
     (run [_] (f)))))

;; ============================================================================
;; JList Selection (replaces buggy seesaw.core/selection!)
;; ============================================================================

(defn set-selection!
  "Set the selected index in a JList.
  
  ⚠️  DO NOT use seesaw.core/selection! - it returns nil without setting selection!
  ✅  Use this function instead - it uses .setSelectedIndex directly.
  
  Examples:
    ; Select index 0
    (set-selection! my-listbox 0)
    
    ; Clear selection
    (set-selection! my-listbox nil)
    
    ; With auto-scroll
    (set-selection! my-listbox 5 :scroll true)"
  ([listbox index]
   (set-selection! listbox index :scroll false))
  ([listbox index & {:keys [scroll]}]
   (let [^JList jlist listbox]
     (if (nil? index)
       (.clearSelection jlist)
       (do
         (.setSelectedIndex jlist index)
         (when scroll
           (.ensureIndexIsVisible jlist index)))))))

(defn get-selection
  "Get the currently selected index from a JList.
  
  Returns the selected index or nil if nothing selected.
  
  Examples:
    (get-selection my-listbox)
    ;; => 2
    
    ; Check if something is selected
    (when-let [idx (get-selection my-listbox)]
      (println \"Selected index:\" idx))"
  [listbox]
  (let [^JList jlist listbox
        idx (.getSelectedIndex jlist)]
    (when (>= idx 0) idx)))

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
