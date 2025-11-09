(ns bd-viewer.db
  "State management for bd-viewer.

  Uses ClosedRecord to ensure type-safe access to state - no more silent nil values!"
  (:require [clojure.spec.alpha :as s]
            [bd-viewer.beads.shell :as beads]
            [closed-record.core :refer [closed-record]]
            [taoensso.timbre :as log]))

;; ============================================================================
;; Beads Issue Schema (from bd list --json)
;; ============================================================================

(s/def ::id string?)
(s/def ::title string?)
(s/def ::description (s/nilable string?))
(s/def ::status #{"open" "closed" "in-progress"})
(s/def ::priority (s/int-in 0 5)) ; P0-P4
(s/def ::issue-type #{"bug" "feature" "task" "epic" "chore"})
(s/def ::labels (s/coll-of string?))
(s/def ::created-at string?) ; ISO timestamp
(s/def ::updated-at string?) ; ISO timestamp
(s/def ::assignee (s/nilable string?))
(s/def ::closed-at (s/nilable string?))

(s/def ::issue
  (s/keys :req-un [::id ::title ::status ::priority ::issue-type ::created-at ::updated-at]
          :opt-un [::description ::labels ::assignee ::closed-at]))

;; ============================================================================
;; Application State
;; ============================================================================

(defonce *app-state
  (atom {:issues [] ; Vector of ClosedRecord issues
         :selected-issue nil ; Currently selected issue ID (string)
         :selected-index -1 ; Index in filtered list for j/k navigation
         :filter-text "" ; Search filter text
         :sort-by :priority ; Sort criterion
         :show-open-only true ; Toggle: show only open issues (o key)
         :ui-refs {}})) ; References to Swing widgets

;; ============================================================================
;; Loading Issues from BD CLI
;; ============================================================================

(defn get-target-dir
  "Get the target directory for bd commands.
  Delegates to beads.shell for consistency."
  []
  (beads/get-target-dir))

(defn load-issues-from-bd
  "Load issues from Beads using beads.shell abstraction.
  Returns vector of ClosedRecord-wrapped issues."
  []
  (log/info :load-issues-from-bd :start true)
  (let [raw-issues (beads/list-issues)
        ;; Wrap each issue in ClosedRecord for type safety
        issues (mapv #(closed-record % {:spec ::issue
                                        :relax-constructor-constraints? true})
                     raw-issues)]
    (log/info :load-issues-from-bd
              :success true
              :count (count issues))
    issues))

;; ============================================================================
;; State Initialization
;; ============================================================================

(defn init-state!
  "Load issues from bd CLI and reset state.
  Call this on startup and when reloading config."
  []
  (log/info :init-state! :start true)
  (let [issues (load-issues-from-bd)]
    (swap! *app-state assoc
           :issues issues
           :selected-issue nil
           :selected-index -1
           :filter-text "")
    (log/info :init-state!
              :success true
              :issue-count (count issues))
    issues))

;; ============================================================================
;; Filtering and Sorting
;; ============================================================================

(defn filter-issues
  "Filter issues by text search (title + description).
  Returns filtered vector of issues."
  [issues filter-text]
  (if (or (nil? filter-text) (empty? filter-text))
    issues
    (let [lower-filter (clojure.string/lower-case filter-text)]
      (filterv (fn [issue]
                 (let [title (or (:title issue) "")
                       desc (or (:description issue) "")]
                   (or (clojure.string/includes?
                        (clojure.string/lower-case title)
                        lower-filter)
                       (clojure.string/includes?
                        (clojure.string/lower-case desc)
                        lower-filter))))
               issues))))

(defn sort-issues
  "Sort issues by the given criterion.
  Options: :priority, :created-at, :updated-at, :status"
  [issues sort-by]
  (case sort-by
    :priority (sort-by :priority issues)
    :created-at (sort-by :created-at issues)
    :updated-at (sort-by :updated-at #(compare %2 %1) issues) ; Reverse: newest first
    :status (sort-by :status issues)
    issues)) ; Default: no sorting

(defn get-filtered-issues
  "Get filtered and sorted issues from current state.
  Always sorts by issue ID number - oldest first."
  []
  (let [state @*app-state
        issues (:issues state)
        filter-text (:filter-text state)
        show-open-only (:show-open-only state)
        ;; Extract numeric part from issue ID for proper numeric sorting
        extract-number (fn [issue]
                         (let [id (:id issue)]
                           (if-let [num-str (re-find #"\d+$" id)]
                             (Integer/parseInt num-str)
                             0)))]
    (-> issues
        ;; First filter by open status if needed
        (#(if show-open-only
            (filterv (fn [issue] (= "open" (:status issue))) %)
            %))
        ;; Then apply text filter
        (filter-issues filter-text)
        ;; Finally sort by numeric issue ID (oldest/lowest first)
        (#(sort-by extract-number %)))))

;; ============================================================================
;; Selection Helpers (for j/k navigation)
;; ============================================================================

(defn get-selected-issue
  "Get the currently selected issue (ClosedRecord) or nil."
  []
  (when-let [issue-id (:selected-issue @*app-state)]
    (first (filter #(= (:id %) issue-id)
                   (:issues @*app-state)))))

(defn select-issue-by-index
  "Select issue at given index in filtered list.
  Returns the selected issue ID or nil."
  [index]
  (let [filtered (get-filtered-issues)]
    (when (and (>= index 0) (< index (count filtered)))
      (let [issue (nth filtered index)
            issue-id (:id issue)]
        (swap! *app-state assoc
               :selected-issue issue-id
               :selected-index index)
        issue-id))))

(defn select-next-issue
  "Select the next issue in filtered list (j key)."
  []
  (let [current-index (:selected-index @*app-state)
        filtered (get-filtered-issues)
        next-index (min (inc current-index) (dec (count filtered)))]
    (when (>= next-index 0)
      (select-issue-by-index next-index))))

(defn select-prev-issue
  "Select the previous issue in filtered list (k key)."
  []
  (let [current-index (:selected-index @*app-state)
        prev-index (max (dec current-index) 0)]
    (when (>= prev-index 0)
      (select-issue-by-index prev-index))))

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn get-issue-by-id
  "Get issue by ID from state. Returns ClosedRecord or nil."
  [issue-id]
  (first (filter #(= (:id %) issue-id)
                 (:issues @*app-state))))

(defn priority-label
  "Convert priority number to label (0 -> P0, 1 -> P1, etc.)"
  [priority]
  (str "P" priority))

(comment
  ;; REPL testing
  (init-state!)

  (count (:issues @*app-state))

  (first (:issues @*app-state))

  ;; Test ClosedRecord - this should work:
  (-> @*app-state :issues first :title)

  ;; Test ClosedRecord - this should THROW:
  (-> @*app-state :issues first :titel) ; Typo!

  ;; Test filtering
  (swap! *app-state assoc :filter-text "UI")
  (count (get-filtered-issues))

  ;; Test navigation
  (select-issue-by-index 0)
  (select-next-issue)
  (select-prev-issue)
  (get-selected-issue))
