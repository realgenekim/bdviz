(ns bd-viewer.graphstream-test
  "Unit tests to verify GraphStream library works without errors.
   These tests run in headless mode (no GUI windows) and verify:
   - GraphStream classes can be instantiated
   - Nodes and edges can be created
   - Graph data structures work correctly
   - No exceptions are thrown during basic operations"
  (:require [clojure.test :refer [deftest is testing]])
  (:import [org.graphstream.graph.implementations SingleGraph]
           [org.graphstream.graph Node Edge]))

;; Helper function: GraphStream's setAttribute requires varargs as Object array
(defn set-attr! [element attr-name value]
  (.setAttribute element attr-name (into-array Object [value])))

(deftest graphstream-basic-graph-creation
  (testing "Can create a basic GraphStream graph without errors"
    (let [graph (SingleGraph. "test-graph")]
      (is (not (nil? graph)) "Graph should be created")
      (is (= "test-graph" (.getId graph)) "Graph should have correct ID"))))

(deftest graphstream-add-nodes
  (testing "Can add nodes to graph"
    (let [graph (SingleGraph. "test-nodes")]
      ;; Add nodes
      (let [node-a (.addNode graph "A")
            node-b (.addNode graph "B")
            node-c (.addNode graph "C")]

        ;; Verify nodes exist
        (is (not (nil? node-a)) "Node A should exist")
        (is (not (nil? node-b)) "Node B should exist")
        (is (not (nil? node-c)) "Node C should exist")

        ;; Verify node count
        (is (= 3 (.getNodeCount graph)) "Graph should have 3 nodes")

        ;; Verify we can retrieve nodes
        (is (= node-a (.getNode graph "A")) "Should retrieve node A")
        (is (= node-b (.getNode graph "B")) "Should retrieve node B")
        (is (= node-c (.getNode graph "C")) "Should retrieve node C")))))

(deftest graphstream-add-edges
  (testing "Can add edges between nodes"
    (let [graph (SingleGraph. "test-edges")]
      ;; Add nodes
      (.addNode graph "A")
      (.addNode graph "B")
      (.addNode graph "C")

      ;; Add edges
      (let [edge-ab (.addEdge graph "AB" "A" "B" true) ; directed edge
            edge-bc (.addEdge graph "BC" "B" "C" true)]

        ;; Verify edges exist
        (is (not (nil? edge-ab)) "Edge AB should exist")
        (is (not (nil? edge-bc)) "Edge BC should exist")

        ;; Verify edge count
        (is (= 2 (.getEdgeCount graph)) "Graph should have 2 edges")

        ;; Verify edge properties
        (is (= "A" (.getId (.getSourceNode edge-ab))) "Edge AB source should be A")
        (is (= "B" (.getId (.getTargetNode edge-ab))) "Edge AB target should be B")
        (is (= "B" (.getId (.getSourceNode edge-bc))) "Edge BC source should be B")
        (is (= "C" (.getId (.getTargetNode edge-bc))) "Edge BC target should be C")))))

(deftest graphstream-node-attributes
  (testing "Can set and get node attributes"
    (let [graph (SingleGraph. "test-attrs")
          node (.addNode graph "N1")]

      ;; Set attributes using helper
      (set-attr! node "label" "Test Node")
      (set-attr! node "status" "open")
      (set-attr! node "priority" 1)

      ;; Get attributes
      (is (= "Test Node" (.getAttribute node "label")) "Label should match")
      (is (= "open" (.getAttribute node "status")) "Status should match")
      (is (= 1 (.getAttribute node "priority")) "Priority should match"))))

(deftest graphstream-edge-attributes
  (testing "Can set and get edge attributes"
    (let [graph (SingleGraph. "test-edge-attrs")]
      (.addNode graph "A")
      (.addNode graph "B")

      (let [edge (.addEdge graph "AB" "A" "B" true)]
        ;; Set attributes using helper
        (set-attr! edge "type" "blocks")
        (set-attr! edge "weight" 5)

        ;; Get attributes
        (is (= "blocks" (.getAttribute edge "type")) "Edge type should match")
        (is (= 5 (.getAttribute edge "weight")) "Edge weight should match")))))

(deftest graphstream-graph-traversal
  (testing "Can iterate over nodes and edges"
    (let [graph (SingleGraph. "test-traversal")]
      ;; Create a simple graph: A -> B -> C
      (.addNode graph "A")
      (.addNode graph "B")
      (.addNode graph "C")
      (.addEdge graph "AB" "A" "B" true)
      (.addEdge graph "BC" "B" "C" true)

      ;; Collect node IDs
      (let [node-ids (set (map #(.getId %) (iterator-seq (.iterator (.nodes graph)))))]
        (is (= #{"A" "B" "C"} node-ids) "Should have all 3 node IDs"))

      ;; Collect edge IDs
      (let [edge-ids (set (map #(.getId %) (iterator-seq (.iterator (.edges graph)))))]
        (is (= #{"AB" "BC"} edge-ids) "Should have both edge IDs")))))

(deftest graphstream-stylesheet
  (testing "Can set graph stylesheet without errors"
    (let [graph (SingleGraph. "test-style")]
      (.addNode graph "A")

      ;; Set stylesheet using helper
      (set-attr! graph "ui.stylesheet" "node { fill-color: red; size: 20px; }")

      ;; Verify it was set
      (is (string? (.getAttribute graph "ui.stylesheet"))
          "Stylesheet should be a string"))))

(deftest graphstream-complex-graph
  (testing "Can create a more complex graph structure"
    (let [graph (SingleGraph. "beads-test")]

      ;; Create epic node
      (let [epic (.addNode graph "epic-001")]
        (set-attr! epic "ui.label" "Epic: Auth System")
        (set-attr! epic "type" "epic")
        (set-attr! epic "status" "open"))

      ;; Create task nodes
      (let [task1 (.addNode graph "task-001")]
        (set-attr! task1 "ui.label" "Task: Login UI")
        (set-attr! task1 "type" "task")
        (set-attr! task1 "status" "in-progress"))

      (let [task2 (.addNode graph "task-002")]
        (set-attr! task2 "ui.label" "Task: Database")
        (set-attr! task2 "type" "task")
        (set-attr! task2 "status" "blocked"))

      ;; Create edges
      (.addEdge graph "e1" "epic-001" "task-001" true)
      (.addEdge graph "e2" "epic-001" "task-002" true)
      (.addEdge graph "e3" "task-001" "task-002" true)

      ;; Verify graph structure
      (is (= 3 (.getNodeCount graph)) "Should have 3 nodes")
      (is (= 3 (.getEdgeCount graph)) "Should have 3 edges")

      ;; Verify attributes
      (is (= "Epic: Auth System"
             (.getAttribute (.getNode graph "epic-001") "ui.label"))
          "Epic label should match")
      (is (= "task"
             (.getAttribute (.getNode graph "task-001") "type"))
          "Task type should match"))))

(deftest graphstream-no-gui-errors
  (testing "GraphStream operations don't require GUI in headless mode"
    ;; This test verifies that basic graph operations work in headless mode
    ;; (when running in CI/test environments without X11)
    (let [graph (SingleGraph. "headless-test")]

      ;; All these operations should work without GUI
      (.addNode graph "node1")
      (.addNode graph "node2")
      (.addEdge graph "edge1" "node1" "node2")

      (set-attr! graph "ui.stylesheet" "node { fill-color: blue; }")
      (set-attr! (.getNode graph "node1") "ui.label" "Node 1")

      ;; If we got here without exceptions, headless mode works
      (is true "All headless operations completed without errors"))))

(comment
  ;; Manual REPL test - this will open a window
  (defn hello-graph-visual []
    (let [graph (SingleGraph. "visual-test")]
      (.addNode graph "A")
      (.addNode graph "B")
      (.addEdge graph "AB" "A" "B")
      (set-attr! graph "ui.stylesheet" "node { fill-color: red; size: 20px; }")
      (.display graph)
      graph))

  ;; Run in REPL (NOT in tests - will fail in headless mode):
  ;; (hello-graph-visual)
  )
