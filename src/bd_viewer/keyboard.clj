(ns bd-viewer.keyboard
  "Keyboard shortcuts for bd-viewer.

  Vim-style navigation and power-user shortcuts!"
  (:require [bd-viewer.events :as events]
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
              (events/handle-event {:event/type ::events/delete-issue}))))

    ;; Delete key - Delete issue
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_DELETE 0)
          "delete-issue-del")
    (.put action-map "delete-issue-del"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/delete-pressed)
              (events/handle-event {:event/type ::events/delete-issue}))))

    ;; Cmd+Delete - Delete issue (alternate)
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_DELETE cmd-mask)
          "delete-issue-cmd-del")
    (.put action-map "delete-issue-cmd-del"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/cmd-delete-pressed)
              (events/handle-event {:event/type ::events/delete-issue}))))

    ;; ========================================================================
    ;; Reload: Cmd+R
    ;; ========================================================================

    ;; Cmd+R - Reload issues
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_R cmd-mask)
          "reload-issues")
    (.put action-map "reload-issues"
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (log/debug :keyboard/cmd-r-pressed)
              (events/handle-event {:event/type ::events/reload-issues}))))

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
              (events/handle-event {:event/type ::events/clear-filter})))))

  (log/info :setup-keyboard-shortcuts! :success true)
  (log/info :keyboard/shortcuts-enabled
            :j "next issue"
            :k "previous issue"
            :cmd-d "delete"
            :delete "delete"
            :cmd-delete "delete"
            :cmd-r "reload"
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
