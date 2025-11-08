(ns bd-viewer.core
  "Main entry point for bd-viewer."
  (:require [bd-viewer.db :as db]
            [bd-viewer.ui :as ui]
            [bd-viewer.effects.swing :as fx]
            [taoensso.timbre :as log])
  (:gen-class))

(defn -main [& args]
  (log/info :bd-viewer/starting)

  ;; 1. Initialize state
  (db/init-state!)

  ;; 2. Setup reactive watchers
  (fx/setup-watchers!)

  ;; 3. Create and show UI
  (ui/create-main-frame)

  (log/info :bd-viewer/started))
