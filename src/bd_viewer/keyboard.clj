(ns bd-viewer.keyboard
  "Keyboard shortcuts for bd-viewer.

  Vim-style navigation and power-user shortcuts!"
  (:require [bd-viewer.events :as events]
            [bd-viewer.db :as db]
            [swing-fx.core :as sf]
            [taoensso.timbre :as log])
  (:import [javax.swing JComponent KeyStroke AbstractAction]
           [java.awt.event KeyEvent]
           [java.awt Toolkit]))

(defn setup-keyboard-shortcuts!
  "Register global keyboard shortcuts.
  Call this once after creating the main frame."
  [frame]
  (log/info :setup-keyboard-shortcuts! :start true)

  (let [content-pane (.getContentPane frame)
        input-map (.getInputMap content-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
        action-map (.getActionMap content-pane)
        cmd-mask (.getMenuShortcutKeyMaskEx (Toolkit/getDefaultToolkit))]

    ;; ========================================================================
    ;; Vim-style Navigation: j/k for next/previous
    ;; ========================================================================

    ;; j - Next issue (vim down)
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_J 0)
          "next-issue")
    (.put action-map "next-issue"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/j-pressed)
              (events/handle-event {:event/type ::events/next-issue}))))

    ;; k - Previous issue (vim up)
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_K 0)
          "prev-issue")
    (.put action-map "prev-issue"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/k-pressed)
              (events/handle-event {:event/type ::events/prev-issue}))))

    ;; ========================================================================
    ;; Delete: Cmd+D, Delete, Cmd+Delete
    ;; ========================================================================

    ;; Cmd+D - Delete issue
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_D cmd-mask)
          "delete-issue")
    (.put action-map "delete-issue"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/cmd-d-pressed)
              (let [result (events/handle-event {:event/type ::events/delete-issue})]
                (when (:error result)
                  (sf/notify-error! frame (:error result)))))))

    ;; Delete key - Delete issue
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_DELETE 0)
          "delete-issue-del")
    (.put action-map "delete-issue-del"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/delete-pressed)
              (let [result (events/handle-event {:event/type ::events/delete-issue})]
                (when (:error result)
                  (sf/notify-error! frame (:error result)))))))

    ;; Cmd+Delete - Delete issue (alternate)
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_DELETE cmd-mask)
          "delete-issue-cmd-del")
    (.put action-map "delete-issue-cmd-del"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/cmd-delete-pressed)
              (let [result (events/handle-event {:event/type ::events/delete-issue})]
                (when (:error result)
                  (sf/notify-error! frame (:error result)))))))

    ;; ========================================================================
    ;; Reload: Cmd+R
    ;; ========================================================================

;; Cmd+Shift+R - Reload code (hot reload!)
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_R (bit-or cmd-mask java.awt.event.InputEvent/SHIFT_DOWN_MASK))
          "reload-code")
    (.put action-map "reload-code"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/cmd-shift-r-pressed)
              (events/handle-event {:event/type ::events/reload-code})
              (sf/notify! frame "Code reloaded!"))))

    ;; Cmd+R - Reload issues
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_R cmd-mask)
          "reload-issues")
    (.put action-map "reload-issues"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/cmd-r-pressed)
              (events/handle-event {:event/type ::events/reload-issues})
              (let [count (count (:issues @db/*app-state))]
                (sf/notify! frame (str "Reloaded " count " issues!"))))))

    ;; ========================================================================
    ;; Search: Cmd+F, Escape
    ;; ========================================================================

    ;; Cmd+F - Focus search field
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_F cmd-mask)
          "focus-search")
    (.put action-map "focus-search"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/cmd-f-pressed)
              ;; This will be handled by UI code to focus the search field
              ;; For now, just log
              (log/info :keyboard/focus-search :todo "Focus search field"))))

    ;; Escape - Clear search filter
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_ESCAPE 0)
          "clear-filter")
    (.put action-map "clear-filter"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/escape-pressed)
              (events/handle-event {:event/type ::events/clear-filter}))))

    ;; ========================================================================
    ;; Toggle: o - Toggle between open issues only / all issues
    ;; ========================================================================

    ;; o - Toggle open/all filter
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_O 0)
          "toggle-open")
    (.put action-map "toggle-open"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/o-pressed)
              (events/handle-event {:event/type ::events/toggle-open-filter})
              ;; Show notification
              (let [show-open? (:show-open-only @db/*app-state)]
                (sf/notify! frame (if show-open?
                                    "Showing open issues only"
                                    "Showing all issues"))))))

    ;; ========================================================================
    ;; Close Issue: c - Mark current issue as closed
    ;; ========================================================================

    ;; c - Close current issue
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_C 0)
          "close-issue")
    (.put action-map "close-issue"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/c-pressed)
              (when-let [issue-id (:selected-issue @db/*app-state)]
                (let [result (events/handle-event {:event/type ::events/close-issue})]
                  (cond
                    (= result :success)
                    (sf/notify! frame (str "Issue " issue-id " closed!"))

                    (:error result)
                    (sf/notify-error! frame (:error result))))))))

    ;; ========================================================================
    ;; TEST: z - Trigger divide-by-zero error (tests error notification)
    ;; ========================================================================

    ;; z - Test error notification with divide by zero
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_Z 0)
          "test-error")
    (.put action-map "test-error"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/info :keyboard/z-pressed :testing "Triggering divide-by-zero error")
              ;; Use Thread directly so exception triggers uncaught handler
              ;; (future catches exceptions internally and won't trigger the handler)
              (.start (Thread. (fn [] (/ 1 0))))))))

  (log/info :setup-keyboard-shortcuts! :success true)
  (log/info :keyboard/shortcuts-enabled
            :j "next issue"
            :k "previous issue"
            :o "toggle open/all"
            :c "close issue"
            :z "test error notification (divide by zero)"
            :cmd-d "delete"
            :delete "delete"
            :cmd-delete "delete"
            :cmd-shift-r "reload code (hot reload!)"
            :cmd-r "reload issues"
            :cmd-f "focus search (TODO)"
            :escape "clear filter"))

(comment
  ;; REPL testing
  (require '[bd-viewer.core :as core])
  (require '[bd-viewer.db :as db])

  ;; Get the frame
  (def frame (get-in @db/*app-state [:ui-refs :frame]))

  ;; Setup shortcuts
  (setup-keyboard-shortcuts! frame)

  ;; Now try pressing j, k, Cmd+D, etc. in the UI!
  )
