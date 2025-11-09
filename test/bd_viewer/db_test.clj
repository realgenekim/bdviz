(ns bd-viewer.db-test
  (:require [clojure.test :refer [deftest is testing]]
            [bd-viewer.db :as db]
            [closed-record.core :refer [closed-record]]))

(deftest test-issue-sorting
  (testing "Issues are sorted by numeric ID, not lexicographic"
    ;; Create test issues with IDs that would sort differently
    ;; if sorted as strings vs numbers
    (let [issue-9 (closed-record {:id "project-9"
                                  :title "Issue 9"
                                  :status "open"
                                  :priority 2
                                  :issue-type "bug"
                                  :created-at "2024-01-01"
                                  :updated-at "2024-01-01"}
                                 {:spec ::db/issue
                                  :relax-constructor-constraints? true})
          issue-10 (closed-record {:id "project-10"
                                   :title "Issue 10"
                                   :status "open"
                                   :priority 2
                                   :issue-type "bug"
                                   :created-at "2024-01-02"
                                   :updated-at "2024-01-02"}
                                  {:spec ::db/issue
                                   :relax-constructor-constraints? true})
          issue-97 (closed-record {:id "project-97"
                                   :title "Issue 97"
                                   :status "open"
                                   :priority 2
                                   :issue-type "bug"
                                   :created-at "2024-01-03"
                                   :updated-at "2024-01-03"}
                                  {:spec ::db/issue
                                   :relax-constructor-constraints? true})
          issue-100 (closed-record {:id "project-100"
                                    :title "Issue 100"
                                    :status "open"
                                    :priority 2
                                    :issue-type "bug"
                                    :created-at "2024-01-04"
                                    :updated-at "2024-01-04"}
                                   {:spec ::db/issue
                                    :relax-constructor-constraints? true})]

      ;; Set up state with unsorted issues
      (reset! db/*app-state
              {:issues [issue-97 issue-9 issue-100 issue-10]
               :selected-issue nil
               :selected-index -1
               :filter-text ""
               :sort-by :priority
               :show-open-only false
               :ui-refs {}})

      ;; Get filtered (sorted) issues
      (let [sorted-issues (db/get-filtered-issues)
            sorted-ids (mapv :id sorted-issues)]

        ;; Should be sorted numerically: 9, 10, 97, 100
        ;; NOT lexicographically: 10, 100, 9, 97
        (is (= ["project-9" "project-10" "project-97" "project-100"]
               sorted-ids)
            "Issues should be sorted by numeric ID, oldest (lowest number) first")))))

(deftest test-issue-sorting-with-filter
  (testing "Filtered issues maintain numeric sort order"
    (let [issue-5 (closed-record {:id "task-5"
                                  :title "Open issue 5"
                                  :status "open"
                                  :priority 2
                                  :issue-type "task"
                                  :created-at "2024-01-01"
                                  :updated-at "2024-01-01"}
                                 {:spec ::db/issue
                                  :relax-constructor-constraints? true})
          issue-15 (closed-record {:id "task-15"
                                   :title "In-progress issue 15"
                                   :status "in-progress"
                                   :priority 2
                                   :issue-type "task"
                                   :created-at "2024-01-02"
                                   :updated-at "2024-01-02"}
                                  {:spec ::db/issue
                                   :relax-constructor-constraints? true})
          issue-50 (closed-record {:id "task-50"
                                   :title "Open issue 50"
                                   :status "open"
                                   :priority 2
                                   :issue-type "task"
                                   :created-at "2024-01-03"
                                   :updated-at "2024-01-03"}
                                  {:spec ::db/issue
                                   :relax-constructor-constraints? true})]

      ;; Test with show-open-only filter
      (reset! db/*app-state
              {:issues [issue-50 issue-5 issue-15]
               :selected-issue nil
               :selected-index -1
               :filter-text ""
               :sort-by :priority
               :show-open-only true
               :ui-refs {}})

      (let [filtered-issues (db/get-filtered-issues)
            filtered-ids (mapv :id filtered-issues)]

        ;; Should only show open issues (not in-progress), sorted: 5, 50
        (is (= ["task-5" "task-50"]
               filtered-ids)
            "Open-only filter should maintain numeric sort order")))))
