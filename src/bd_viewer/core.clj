(ns bd-viewer.core
  "Main entry point for bd-viewer."
  (:require [bd-viewer.db :as db]
            [bd-viewer.ui :as ui]
            [bd-viewer.effects.swing :as fx]
            [bd-viewer.keyboard :as kbd]
            [swing-fx.core :as sf]
            [taoensso.timbre :as log]
            [logging.main :as glog]
            [clojure.java.io :as io]
            [clojure.pprint :as pp])
  (:gen-class))

;; Configure unified logging - send all Java logging frameworks to timbre
(glog/configure-logging! glog/config)

(defn dump-state-to-file!
  "Write current app state to state.edn for debugging."
  []
  (try
    (let [state @db/*app-state
          ;; Remove UI refs (can't serialize Swing objects)
          clean-state (dissoc state :ui-refs)]
      (spit "state.edn"
            (with-out-str (pp/pprint clean-state)))
      (log/debug :dump-state-to-file! :success true))
    (catch Exception e
      (log/error :dump-state-to-file! :exception (.getMessage e)))))

(defn setup-exception-handler!
  "Setup global exception handler to show error notifications."
  [frame]
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread throwable]
       (log/error :uncaught-exception
                  :thread (.getName thread)
                  :exception (.getMessage throwable)
                  :stacktrace (with-out-str (.printStackTrace throwable)))
       (sf/notify-error! frame "⚠️ Error occurred! Check logs.")))))

(defn -main [& args]
  (log/info :bd-viewer/starting)

  ;; 1. Initialize state
  (db/init-state!)

  ;; 2. Create and show UI
  (let [frame (ui/create-main-frame)]

    ;; 3. Setup global exception handler
    (setup-exception-handler! frame)

    ;; 4. Setup reactive watchers with swing-fx (after UI exists!)
    (fx/setup-watchers! frame)

    ;; 5. Setup keyboard shortcuts (after UI exists!)
    (kbd/setup-keyboard-shortcuts! frame)

    ;; 6. Register keyboard shortcuts to reload automatically on Cmd+Shift+R
    ;; IMPORTANT: Use resolve to get fresh function on each reload!
    ;; This hook will be called automatically by rebuild-ui! via sf/reload!
    (sf/register-reload-hook!
     (fn [frame]
       ((resolve 'bd-viewer.keyboard/setup-keyboard-shortcuts!) frame)))

    ;; 7. Add state dump watcher for debugging
    (add-watch db/*app-state ::dump-state
               (fn [_ _ old-state new-state]
                 (dump-state-to-file!)))

    ;; 8. Initial state dump
    (dump-state-to-file!)

    ;; 9. Select first issue if there are any issues
    ;; This will trigger watchers which will populate the UI
    (when (seq (:issues @db/*app-state))
      (db/select-issue-by-index 0)))

  (log/info :bd-viewer/started))
