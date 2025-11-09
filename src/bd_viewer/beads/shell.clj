(ns bd-viewer.beads.shell
  "Clean abstraction for Beads CLI interactions.
  
  All `bd` command-line operations go through this namespace.
  This provides a single source of truth for shell-based Beads data access."
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [taoensso.timbre :as log]))

;; ============================================================================
;; Configuration
;; ============================================================================

(defn get-target-dir
  "Get the target directory for bd commands.
  Respects BD_VIEWER_DIR environment variable."
  []
  (or (System/getenv "BD_VIEWER_DIR") "."))

(defn get-target-db
  "Find or construct the database path for the target directory.
  
  Strategy:
  1. Look for a .db file that matches the directory name (e.g., slack-retriever.db)
  2. If not found, look for any .db file in .beads/
  3. If still not found, construct the path based on directory name
  
  This prevents bd from using auto-discovery which can find the wrong database."
  []
  (let [target-dir (get-target-dir)
        ;; Get the directory basename (e.g., 'slack-retriever' from '../slack-retriever')
        dir-name (.getName (clojure.java.io/file target-dir))
        beads-dir (clojure.java.io/file target-dir ".beads")
        expected-db-name (str dir-name ".db")]

    (if (.exists beads-dir)
      ;; .beads directory exists - look for database
      (let [db-files (->> (.listFiles beads-dir)
                          (filter #(.endsWith (.getName %) ".db"))
                          vec)
            ;; Try to find database matching directory name
            matching-db (first (filter #(= (.getName %) expected-db-name) db-files))]
        (if matching-db
          ;; Found matching database
          (.getAbsolutePath matching-db)
          ;; No matching database - use first .db file or construct path
          (if (seq db-files)
            (.getAbsolutePath (first db-files))
            ;; No database exists - return expected path for bd to create
            (.getAbsolutePath (clojure.java.io/file beads-dir expected-db-name)))))
      ;; .beads directory doesn't exist - return expected path
      (.getAbsolutePath (clojure.java.io/file beads-dir expected-db-name)))))

;; ============================================================================
;; Core API
;; ============================================================================

(defn list-issues
  "Execute `bd list --json` and return vector of issue maps.
  
  Returns empty vector if no issues or on error.
  Each issue map contains: :id :title :description :status :priority
                          :issue-type :labels :created-at :updated-at
                          :assignee :closed-at"
  []
  (log/info :beads.shell/list-issues :start true)
  (try
    (let [target-dir (get-target-dir)
          target-db (get-target-db)
          args (cond-> ["bd"]
                 target-db (concat ["--db" target-db])
                 :always (concat ["list" "--json"]))
          result (apply shell/sh (concat args [:dir target-dir]))
          exit-code (:exit result)]
      (log/info :beads.shell/list-issues :target-dir target-dir :target-db target-db)
      (if (zero? exit-code)
        (let [raw-output (:out result)
              ;; Handle both array response and null (empty)
              parsed (if (or (empty? raw-output)
                             (= "null" (clojure.string/trim raw-output)))
                       []
                       (json/read-str raw-output :key-fn keyword))]
          (log/info :beads.shell/list-issues
                    :success true
                    :count (count parsed))
          parsed)
        (do
          (log/error :beads.shell/list-issues
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result))
          [])))
    (catch Exception e
      (log/error :beads.shell/list-issues
                 :exception (.getMessage e))
      [])))

(defn show-issue
  "Execute `bd show <issue-id> --json` and return issue map.
  
  Returns nil on error or if issue not found."
  [issue-id]
  (log/info :beads.shell/show-issue :issue-id issue-id)
  (try
    (let [target-dir (get-target-dir)
          target-db (get-target-db)
          args (cond-> ["bd"]
                 target-db (concat ["--db" target-db])
                 :always (concat ["show" issue-id "--json"]))
          result (apply shell/sh (concat args [:dir target-dir]))
          exit-code (:exit result)]
      (if (zero? exit-code)
        (let [parsed (json/read-str (:out result) :key-fn keyword)]
          (log/info :beads.shell/show-issue :success true)
          parsed)
        (do
          (log/error :beads.shell/show-issue
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result))
          nil)))
    (catch Exception e
      (log/error :beads.shell/show-issue
                 :exception (.getMessage e))
      nil)))

(defn create-issue!
  "Execute `bd create` with given options.
  
  Options map may contain:
    :title       - Issue title (required)
    :description - Issue description
    :status      - Status (open, in-progress, closed)
    :priority    - Priority (0-4)
    :issue-type  - Type (bug, feature, task, epic, chore)
    :assignee    - Assignee username
    
  Returns created issue map or nil on error."
  [opts]
  (log/info :beads.shell/create-issue! :opts opts)
  (try
    (let [target-dir (get-target-dir)
          target-db (get-target-db)
          ;; Build command args from opts
          args (cond-> ["bd"]
                 target-db (concat ["--db" target-db])
                 :always (concat ["create"])
                 (:title opts) (concat ["--title" (:title opts)])
                 (:description opts) (concat ["--description" (:description opts)])
                 (:status opts) (concat ["--status" (:status opts)])
                 (:priority opts) (concat ["--priority" (str (:priority opts))])
                 (:issue-type opts) (concat ["--type" (:issue-type opts)])
                 (:assignee opts) (concat ["--assignee" (:assignee opts)])
                 :always (concat ["--json"]))
          result (apply shell/sh (concat args [:dir target-dir]))
          exit-code (:exit result)]
      (if (zero? exit-code)
        (let [parsed (json/read-str (:out result) :key-fn keyword)]
          (log/info :beads.shell/create-issue! :success true :issue-id (:id parsed))
          parsed)
        (do
          (log/error :beads.shell/create-issue!
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result))
          nil)))
    (catch Exception e
      (log/error :beads.shell/create-issue!
                 :exception (.getMessage e))
      nil)))

(defn update-issue!
  "Execute `bd update <issue-id>` with given options.
  
  Options map may contain:
    :title       - New title
    :description - New description
    :status      - New status
    :priority    - New priority
    :issue-type  - New type
    :assignee    - New assignee
    
  Returns updated issue map or nil on error."
  [issue-id opts]
  (log/info :beads.shell/update-issue! :issue-id issue-id :opts opts)
  (try
    (let [target-dir (get-target-dir)
          target-db (get-target-db)
          args (cond-> ["bd"]
                 target-db (concat ["--db" target-db])
                 :always (concat ["update" issue-id])
                 (:title opts) (concat ["--title" (:title opts)])
                 (:description opts) (concat ["--description" (:description opts)])
                 (:status opts) (concat ["--status" (:status opts)])
                 (:priority opts) (concat ["--priority" (str (:priority opts))])
                 (:issue-type opts) (concat ["--type" (:issue-type opts)])
                 (:assignee opts) (concat ["--assignee" (:assignee opts)])
                 :always (concat ["--json"]))
          result (apply shell/sh (concat args [:dir target-dir]))
          exit-code (:exit result)]
      (if (zero? exit-code)
        (let [parsed (json/read-str (:out result) :key-fn keyword)]
          (log/info :beads.shell/update-issue! :success true)
          parsed)
        (do
          (log/error :beads.shell/update-issue!
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result))
          nil)))
    (catch Exception e
      (log/error :beads.shell/update-issue!
                 :exception (.getMessage e))
      nil)))

(defn delete-issue!
  "Execute `bd delete <issue-id>`.
  
  Returns true on success, false on error."
  [issue-id]
  (log/info :beads.shell/delete-issue! :issue-id issue-id)
  (try
    (let [target-dir (get-target-dir)
          target-db (get-target-db)
          args (cond-> ["bd"]
                 target-db (concat ["--db" target-db])
                 :always (concat ["delete" issue-id]))
          result (apply shell/sh (concat args [:dir target-dir]))
          exit-code (:exit result)]
      (if (zero? exit-code)
        (do
          (log/info :beads.shell/delete-issue! :success true)
          true)
        (do
          (log/error :beads.shell/delete-issue!
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result))
          false)))
    (catch Exception e
      (log/error :beads.shell/delete-issue!
                 :exception (.getMessage e))
      false)))
