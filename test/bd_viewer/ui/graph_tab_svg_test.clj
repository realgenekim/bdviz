(ns bd-viewer.ui.graph-tab-svg-test
  "Test for SVG graph tab functionality."
  (:require [clojure.test :refer [deftest is testing]]
            [bd-viewer.ui.graph-tab-svg :as svg])
  (:import [org.apache.batik.swing JSVGCanvas]))

(deftest test-create-svg-canvas-with-valid-svg
  (testing "create-svg-canvas should not throw NullPointerException with valid SVG"
    ;; This test verifies the fix for: "Cannot invoke 'org.apache.batik.util.ParsedURL.toString0' because 'uri' is null"
    ;; The bug was caused by passing nil as the URI parameter to SAXSVGDocumentFactory.createDocument
    ;; Fixed by providing "https://mermaid.ink/" as the base URI

    ;; Minimal valid SVG string
    (let [svg-string "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\">
  <circle cx=\"50\" cy=\"50\" r=\"40\" fill=\"blue\"/>
</svg>"
          canvas (svg/create-svg-canvas svg-string)]

      ;; Should not be nil (would be nil if exception occurred)
      (is (some? canvas)
          "Canvas should be created successfully without NullPointerException")

      ;; Should be a JSVGCanvas instance
      (is (instance? JSVGCanvas canvas)
          "Should return a JSVGCanvas instance"))))

(deftest test-create-svg-canvas-with-invalid-svg
  (testing "create-svg-canvas should handle invalid SVG gracefully"
    (let [invalid-svg "not valid svg at all"
          canvas (svg/create-svg-canvas invalid-svg)]

      ;; Should return nil on error, not throw exception
      (is (nil? canvas)
          "Should return nil for invalid SVG"))))

(deftest test-mermaid-svg-url-generation
  (testing "mermaid->svg-url should generate correct URL"
    (let [mermaid-str "graph TD\nA-->B"
          url (svg/mermaid->svg-url mermaid-str)]

      ;; Should start with correct base URL
      (is (.startsWith url "https://mermaid.ink/svg/")
          "URL should start with mermaid.ink SVG endpoint")

      ;; Should contain base64 encoded content
      (is (> (count url) (count "https://mermaid.ink/svg/"))
          "URL should contain base64 encoded diagram"))))
