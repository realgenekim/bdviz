(ns bd-viewer.beads.sqlite
  "Clean abstraction for Beads SQLite database queries.
  
  All direct SQLite database access goes through this namespace.
  This provides a single source of truth for database-based Beads data access.
  
  Note: The Beads database schema includes:
  - issues table (synced from issues.jsonl)
  - dependencies table (issue_id, depends_on_id, type)
  - Dependency types: 'blocks', 'parent-child', 'related', 'discovered-from'"
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]))

;; ============================================================================
;; Configuration
;; ============================================================================

(defn get-target-dir
  "Get the target directory for Beads database.
  Respects BD_VIEWER_DIR environment variable."
  []
  (or (System/getenv "BD_VIEWER_DIR") "."))

(defn db-path
  "Get the full path to the Beads SQLite database for the target directory.
  
  Strategy:
  1. Look for a .db file that matches the directory name (e.g., slack-retriever.db)
  2. If not found, look for any .db file in .beads/
  3. If still not found, construct the path based on directory name
  
  This prevents querying the wrong database when running against different projects."
  []
  (let [target-dir (get-target-dir)
        ;; Get the directory basename (e.g., 'slack-retriever' from '../slack-retriever')
        dir-name (.getName (io/file target-dir))
        beads-dir (io/file target-dir ".beads")
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
            ;; No database exists - return expected path (will fail query but that's okay)
            (.getAbsolutePath (io/file beads-dir expected-db-name)))))
      ;; .beads directory doesn't exist - return expected path
      (.getAbsolutePath (io/file beads-dir expected-db-name)))))

;; ============================================================================
;; SQLite Query Helper
;; ============================================================================

(defn- query-sqlite
  "Execute a SQLite query and return the result as a string.
  
  Uses sqlite3 CLI for simplicity. Returns stdout on success, nil on error.
  
  Example:
    (query-sqlite \"SELECT * FROM dependencies WHERE issue_id = 'bd-viewer-15';\")"
  [sql]
  (try
    (let [result (shell/sh "sqlite3" (db-path) sql)
          exit-code (:exit result)]
      (if (zero? exit-code)
        (:out result)
        (do
          (log/error :beads.sqlite/query-sqlite
                     :failed true
                     :exit-code exit-code
                     :stderr (:err result)
                     :sql sql)
          nil)))
    (catch Exception e
      (log/error :beads.sqlite/query-sqlite
                 :exception (.getMessage e)
                 :sql sql)
      nil)))

(defn- parse-dependency-row
  "Parse a pipe-delimited dependency row into a map.
  
  Input: 'bd-viewer-15|bd-viewer-14|parent-child'
  Output: {:issue-id 'bd-viewer-15' :depends-on-id 'bd-viewer-14' :type 'parent-child'}"
  [row]
  (let [[issue-id depends-on-id dep-type] (str/split row #"\|")]
    {:issue-id issue-id
     :depends-on-id depends-on-id
     :type dep-type}))

;; ============================================================================
;; Dependency Queries
;; ============================================================================

(defn get-dependencies
  "Get all dependencies from the Beads database.
  
  Returns vector of dependency maps:
    [{:issue-id 'bd-viewer-15'
      :depends-on-id 'bd-viewer-14'
      :type 'parent-child'}
     ...]
     
  Dependency types:
    - 'blocks': issue-id is blocked by depends-on-id
    - 'parent-child': depends-on-id is parent of issue-id
    - 'related': issues are related
    - 'discovered-from': issue-id was discovered from depends-on-id"
  []
  (log/info :beads.sqlite/get-dependencies :start true)
  (if-let [result (query-sqlite "SELECT issue_id, depends_on_id, type FROM dependencies ORDER BY issue_id;")]
    (let [rows (str/split-lines result)
          deps (mapv parse-dependency-row (remove empty? rows))]
      (log/info :beads.sqlite/get-dependencies
                :success true
                :count (count deps))
      deps)
    (do
      (log/warn :beads.sqlite/get-dependencies :no-results true)
      [])))

(defn get-dependencies-for
  "Get all dependencies where the given issue depends on other issues.
  
  Returns vector of dependency maps for the given issue-id.
  
  Example:
    (get-dependencies-for 'bd-viewer-15')
    => [{:issue-id 'bd-viewer-15' :depends-on-id 'bd-viewer-14' :type 'parent-child'}]"
  [issue-id]
  (log/info :beads.sqlite/get-dependencies-for :issue-id issue-id)
  (if-let [result (query-sqlite
                   (str "SELECT issue_id, depends_on_id, type FROM dependencies "
                        "WHERE issue_id = '" issue-id "';"))]
    (let [rows (str/split-lines result)
          deps (mapv parse-dependency-row (remove empty? rows))]
      (log/info :beads.sqlite/get-dependencies-for
                :success true
                :count (count deps))
      deps)
    (do
      (log/warn :beads.sqlite/get-dependencies-for :no-results true)
      [])))

(defn get-dependents-for
  "Get all issues that depend on the given issue (reverse lookup).
  
  Returns vector of dependency maps where depends-on-id matches the given issue-id.
  
  Example:
    (get-dependents-for 'bd-viewer-14')
    => [{:issue-id 'bd-viewer-15' :depends-on-id 'bd-viewer-14' :type 'parent-child'}
        {:issue-id 'bd-viewer-16' :depends-on-id 'bd-viewer-14' :type 'parent-child'}
        ...]"
  [issue-id]
  (log/info :beads.sqlite/get-dependents-for :issue-id issue-id)
  (if-let [result (query-sqlite
                   (str "SELECT issue_id, depends_on_id, type FROM dependencies "
                        "WHERE depends_on_id = '" issue-id "';"))]
    (let [rows (str/split-lines result)
          deps (mapv parse-dependency-row (remove empty? rows))]
      (log/info :beads.sqlite/get-dependents-for
                :success true
                :count (count deps))
      deps)
    (do
      (log/warn :beads.sqlite/get-dependents-for :no-results true)
      [])))

(defn get-dependencies-by-type
  "Get all dependencies of a specific type.
  
  Type should be one of: 'blocks', 'parent-child', 'related', 'discovered-from'
  
  Returns vector of dependency maps."
  [dep-type]
  (log/info :beads.sqlite/get-dependencies-by-type :type dep-type)
  (if-let [result (query-sqlite
                   (str "SELECT issue_id, depends_on_id, type FROM dependencies "
                        "WHERE type = '" dep-type "';"))]
    (let [rows (str/split-lines result)
          deps (mapv parse-dependency-row (remove empty? rows))]
      (log/info :beads.sqlite/get-dependencies-by-type
                :success true
                :count (count deps))
      deps)
    (do
      (log/warn :beads.sqlite/get-dependencies-by-type :no-results true)
      [])))

(comment
  ;; REPL testing
  (db-path)
  ;; => "./.beads/bd-viewer.db"

  (get-dependencies)
  ;; => [{:issue-id "bd-viewer-15" :depends-on-id "bd-viewer-14" :type "parent-child"} ...]

  (get-dependencies-for "bd-viewer-15")
  ;; => [{:issue-id "bd-viewer-15" :depends-on-id "bd-viewer-14" :type "parent-child"}]

  (get-dependents-for "bd-viewer-14")
  ;; => [{:issue-id "bd-viewer-15" ...} {:issue-id "bd-viewer-16" ...} ...]

  (get-dependencies-by-type "blocks")
  ;; => [{:issue-id "bd-viewer-16" :depends-on-id "bd-viewer-15" :type "blocks"} ...]
  )
