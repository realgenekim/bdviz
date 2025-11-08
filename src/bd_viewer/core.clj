(ns bd-viewer.core
  "Main entry point for bd-viewer."
  (:require [bd-viewer.db :as db]
            [bd-viewer.ui :as ui]
            [bd-viewer.effects.swing :as fx]
            [bd-viewer.keyboard :as kbd]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [clojure.pprint :as pp])
  (:gen-class))

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

(defn -main [& args]
  (log/info :bd-viewer/starting)

  ;; 1. Initialize state
  (db/init-state!)

  ;; 2. Create and show UI
  (let [frame (ui/create-main-frame)]

    ;; 3. Setup reactive watchers (after UI exists!)
    (fx/setup-watchers!)

    ;; 4. Setup keyboard shortcuts (after UI exists!)
    (kbd/setup-keyboard-shortcuts! frame)

    ;; 5. Add state dump watcher for debugging
    (add-watch db/*app-state ::dump-state
      (fn [_ _ old-state new-state]
        (dump-state-to-file!)))

    ;; 6. Trigger initial UI population
    ;; Watchers only fire on changes, so we do initial update manually
    (let [state @db/*app-state]
      (fx/update-issue-list! {} state)
      (fx/update-detail-panel! {} state)
      (dump-state-to-file!))  ; Initial dump

    ;; 7. Select first issue if there are any issues
    (when (seq (:issues @db/*app-state))
      (db/select-issue-by-index 0)))

  (log/info :bd-viewer/started))
