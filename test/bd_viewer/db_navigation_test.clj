(ns bd-viewer.db-navigation-test
  "Unit tests for context-aware j/k navigation in Tree View vs other tabs."
  (:require [clojure.test :refer :all]
            [bd-viewer.db :as db]
            [closed-record.core :refer [closed-record]]))

(defn make-test-issue
  "Create a test issue with minimal fields."
  [id title status]
  (closed-record
   {:id id
    :title title
    :status status
    :priority 1
    :issue-type "task"
    :created-at "2025-01-01T00:00:00Z"
    :updated-at "2025-01-01T00:00:00Z"}
   {:spec ::db/issue
    :relax-constructor-constraints? true}))

(deftest test-navigation-numeric-order
  (testing "j/k navigation uses numeric order in non-Tree-View tabs"
    ;; Setup: Issues in numeric order (as they'd appear in Issues tab)
    (reset! db/*app-state
            {:issues [(make-test-issue "proj-1" "First" "open")
                      (make-test-issue "proj-5" "Fifth" "open")
                      (make-test-issue "proj-10" "Tenth" "open")
                      (make-test-issue "proj-15" "Fifteenth" "open")]
             :selected-issue "proj-1"
             :selected-index 0
             :filter-text ""
             :show-open-only true
             :tree-flat-list nil ; No tree list
             :active-tab 2 ; Issues tab (not Tree View)
             :ui-refs {}})

    ;; Test: j (next) should move from proj-1 → proj-5
    (db/select-next-issue)
    (is (= "proj-5" (:selected-issue @db/*app-state))
        "j should select next issue in numeric order")
    (is (= 1 (:selected-index @db/*app-state)))

    ;; Test: j again should move to proj-10
    (db/select-next-issue)
    (is (= "proj-10" (:selected-issue @db/*app-state)))
    (is (= 2 (:selected-index @db/*app-state)))

    ;; Test: k (prev) should move back to proj-5
    (db/select-prev-issue)
    (is (= "proj-5" (:selected-issue @db/*app-state))
        "k should select previous issue in numeric order")
    (is (= 1 (:selected-index @db/*app-state)))))

(deftest test-navigation-tree-order
  (testing "j/k navigation uses tree order in Tree View tab"
    ;; Setup: Tree order is different from numeric order
    ;; Tree: proj-10 (root) → proj-5 (child) → proj-1 (grandchild) → proj-15 (sibling)
    (let [tree-flat-list [(make-test-issue "proj-10" "Root" "open")
                          (make-test-issue "proj-5" "Child" "open")
                          (make-test-issue "proj-1" "Grandchild" "open")
                          (make-test-issue "proj-15" "Sibling" "open")]]
      (reset! db/*app-state
              {:issues [(make-test-issue "proj-1" "Grandchild" "open")
                        (make-test-issue "proj-5" "Child" "open")
                        (make-test-issue "proj-10" "Root" "open")
                        (make-test-issue "proj-15" "Sibling" "open")]
               :selected-issue "proj-10"
               :selected-index 0 ; First in tree order
               :filter-text ""
               :show-open-only true
               :tree-flat-list tree-flat-list ; Tree display order!
               :active-tab 0 ; Tree View tab
               :ui-refs {}})

      ;; Test: j (next) should move in TREE order: proj-10 → proj-5
      (db/select-next-issue)
      (is (= "proj-5" (:selected-issue @db/*app-state))
          "j should select next issue in tree order, not numeric order")
      (is (= 1 (:selected-index @db/*app-state)))

      ;; Test: j again should move to proj-1 (next in tree, not numeric)
      (db/select-next-issue)
      (is (= "proj-1" (:selected-issue @db/*app-state))
          "j should continue in tree order")
      (is (= 2 (:selected-index @db/*app-state)))

      ;; Test: k (prev) should move back in tree order: proj-1 → proj-5
      (db/select-prev-issue)
      (is (= "proj-5" (:selected-issue @db/*app-state))
          "k should move backward in tree order")
      (is (= 1 (:selected-index @db/*app-state))))))

(deftest test-navigation-boundary-conditions
  (testing "Navigation handles boundary conditions correctly"
    (reset! db/*app-state
            {:issues [(make-test-issue "proj-1" "First" "open")
                      (make-test-issue "proj-2" "Second" "open")
                      (make-test-issue "proj-3" "Third" "open")]
             :selected-issue "proj-3"
             :selected-index 2 ; Last item
             :filter-text ""
             :show-open-only true
             :tree-flat-list nil
             :active-tab 2
             :ui-refs {}})

    ;; Test: j at end should stay at end
    (db/select-next-issue)
    (is (= "proj-3" (:selected-issue @db/*app-state))
        "j at last item should stay at last item")
    (is (= 2 (:selected-index @db/*app-state)))

    ;; Move to first item
    (swap! db/*app-state assoc :selected-issue "proj-1" :selected-index 0)

    ;; Test: k at beginning should stay at beginning
    (db/select-prev-issue)
    (is (= "proj-1" (:selected-issue @db/*app-state))
        "k at first item should stay at first item")
    (is (= 0 (:selected-index @db/*app-state)))))

(deftest test-tab-switching-changes-navigation-mode
  (testing "Switching between tabs changes navigation context"
    (let [tree-flat-list [(make-test-issue "proj-10" "Root" "open")
                          (make-test-issue "proj-5" "Child" "open")
                          (make-test-issue "proj-1" "Leaf" "open")]]
      (reset! db/*app-state
              {:issues [(make-test-issue "proj-1" "Leaf" "open")
                        (make-test-issue "proj-5" "Child" "open")
                        (make-test-issue "proj-10" "Root" "open")]
               :selected-issue "proj-10"
               :selected-index 0
               :filter-text ""
               :show-open-only true
               :tree-flat-list tree-flat-list
               :active-tab 0 ; Start in Tree View
               :ui-refs {}})

      ;; In Tree View (tab 0): j should use tree order (proj-10 → proj-5)
      (db/select-next-issue)
      (is (= "proj-5" (:selected-issue @db/*app-state))
          "In Tree View, j uses tree order")

      ;; Switch to Issues tab (tab 2)
      (swap! db/*app-state assoc :active-tab 2 :selected-index 0 :selected-issue "proj-1")

      ;; In Issues tab: j should use numeric order (proj-1 → proj-5)
      (db/select-next-issue)
      (is (= "proj-5" (:selected-issue @db/*app-state))
          "In Issues tab, j uses numeric order"))))

(comment
  ;; Run tests
  (run-tests 'bd-viewer.db-navigation-test)

  ;; Run specific test
  (test-navigation-tree-order))
