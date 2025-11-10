(ns bd-viewer.state.derived
  "Derived state management - automatic recomputation when dependencies change.
  
  This makes it IMPOSSIBLE to forget to update derived state when base state changes.
  
  Pattern:
  - Base state: :issues, :show-open-only (user-controlled)
  - Derived state: :tree-flat-list (computed automatically from base state)
  - ONE place defines derivation logic
  - Watcher ensures it's ALWAYS up to date"
  (:require [bd-viewer.beads.sqlite :as beads-db]
            [taoensso.timbre :as log]))

(defn build-dep-map
  "Build map of issue-id → [dependent-issue-ids]."
  [deps]
  (reduce (fn [acc {:keys [issue-id depends-on-id]}]
            (update acc depends-on-id (fnil conj []) issue-id))
          {}
          deps))

(defn find-roots
  "Find root issues (issues that don't depend on anything)."
  [issues deps]
  (let [has-deps (set (map :issue-id deps))]
    (filter (fn [issue] (not (has-deps (:id issue)))) issues)))

(defn flatten-tree
  "Flatten tree structure to a list of issues in display order (depth-first)."
  ([issue dep-map issues-by-id]
   (flatten-tree issue dep-map issues-by-id []))

  ([issue dep-map issues-by-id acc]
   (let [children-ids (get dep-map (:id issue) [])
         children (keep issues-by-id children-ids)
         new-acc (conj acc issue)]
     (reduce (fn [a child]
               (flatten-tree child dep-map issues-by-id a))
             new-acc
             children))))

(defn build-flat-issue-list
  "Build a flat list of all issues in tree display order.
  This is the SINGLE SOURCE OF TRUTH for tree ordering."
  [issues deps]
  (let [dep-map (build-dep-map deps)
        issues-by-id (into {} (map (fn [i] [(:id i) i]) issues))
        roots (find-roots issues deps)]
    (vec (mapcat #(flatten-tree % dep-map issues-by-id) roots))))

(defn compute-derived-state
  "Compute ALL derived state from base state.
  
  This function is the SINGLE SOURCE OF TRUTH for all derived values.
  Called automatically whenever dependencies change.
  
  Base state (inputs):
  - :issues - All issues
  - :dependencies - Cached dependencies (from DB)
  - :show-open-only - Filter toggle
  
  Derived state (outputs):
  - :tree-flat-list - Issues in tree display order"
  [state]
  (let [issues (:issues state)
        deps (:dependencies state) ; Use cached deps!
        show-open-only (:show-open-only state)

        ;; Filter to open issues if needed
        filtered-issues (if show-open-only
                          (filterv #(not= "closed" (:status %)) issues)
                          issues)

        ;; Filter dependencies (only between open issues if filtered)
        open-ids (set (map :id filtered-issues))
        filtered-deps (if show-open-only
                        (filter (fn [{:keys [issue-id depends-on-id]}]
                                  (and (open-ids issue-id)
                                       (open-ids depends-on-id)))
                                deps)
                        deps)

        ;; Compute tree-flat-list
        tree-flat-list (build-flat-issue-list filtered-issues filtered-deps)]

    (log/debug :compute-derived-state
               :issue-count (count issues)
               :filtered-count (count filtered-issues)
               :tree-flat-count (count tree-flat-list))

    ;; Return ONLY the derived values (not the whole state)
    {:tree-flat-list tree-flat-list}))

(defn setup-derived-state-watcher!
  "Setup automatic recomputation of derived state.
  
  Watches base state (:issues, :dependencies, :show-open-only) and AUTOMATICALLY
  recomputes derived state whenever they change.
  
  This makes it IMPOSSIBLE to forget to update tree-flat-list!"
  [state-atom]
  (add-watch state-atom ::derived-state
             (fn [_key _ref old-state new-state]
               ;; Only recompute if dependencies changed
               (when (or (not= (:issues old-state) (:issues new-state))
                         (not= (:dependencies old-state) (:dependencies new-state))
                         (not= (:show-open-only old-state) (:show-open-only new-state)))
                 (log/debug :derived-state-updating)
                 (let [derived (compute-derived-state new-state)]
                   ;; Merge derived state back into atom
                   ;; Use swap! to avoid triggering this watcher again
                   (swap! state-atom
                          (fn [s]
                            (merge s derived))))))))

(comment
  ;; REPL testing
  (require '[bd-viewer.db :as db])

  ;; Setup derived state watcher
  (setup-derived-state-watcher! db/*app-state)

  ;; Now ANY change to :issues or :show-open-only AUTOMATICALLY updates :tree-flat-list!
  (swap! db/*app-state assoc :show-open-only false)
  ;; Check that tree-flat-list was automatically updated
  (count (:tree-flat-list @db/*app-state))

  ;; Toggle back
  (swap! db/*app-state assoc :show-open-only true)
  (count (:tree-flat-list @db/*app-state)))
