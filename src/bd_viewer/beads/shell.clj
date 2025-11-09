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
  "Find the .db file in the target directory's .beads/ folder.
  Returns the absolute path to the database file, or nil if not found."
  []
  (let [target-dir (get-target-dir)
        beads-dir (clojure.java.io/file target-dir ".beads")]
    (when (.exists beads-dir)
      (let [db-files (->> (.listFiles beads-dir)
                          (filter #(.endsWith (.getName %) ".db"))
                          (sort-by #(.getName %)))]
        (when (seq db-files)
          (.getAbsolutePath (first db-files)))))))

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
          result (shell/sh "bd" "show" issue-id "--json" :dir target-dir)
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
          ;; Build command args from opts
          args (concat ["bd" "create"]
                       (when (:title opts) ["--title" (:title opts)])
                       (when (:description opts) ["--description" (:description opts)])
                       (when (:status opts) ["--status" (:status opts)])
                       (when (:priority opts) ["--priority" (str (:priority opts))])
                       (when (:issue-type opts) ["--type" (:issue-type opts)])
                       (when (:assignee opts) ["--assignee" (:assignee opts)])
                       ["--json"])
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
          args (concat ["bd" "update" issue-id]
                       (when (:title opts) ["--title" (:title opts)])
                       (when (:description opts) ["--description" (:description opts)])
                       (when (:status opts) ["--status" (:status opts)])
                       (when (:priority opts) ["--priority" (str (:priority opts))])
                       (when (:issue-type opts) ["--type" (:issue-type opts)])
                       (when (:assignee opts) ["--assignee" (:assignee opts)])
                       ["--json"])
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
          result (shell/sh "bd" "delete" issue-id :dir target-dir)
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
