(ns bd-viewer.ui.graph-tab-svg
  "Interactive SVG graph visualization using Apache Batik.

  Fetches SVG from mermaid.ink and displays in interactive, zoomable JSVGCanvas."
  (:require [seesaw.core :as s]
            [bd-viewer.db :as db]
            [bd-viewer.beads.sqlite :as beads-db]
            [bd-viewer.mermaid :as mermaid]
            [taoensso.timbre :as log]
            [clojure.java.io :as io])
  (:import [java.awt Font BorderLayout]
           [java.io StringReader ByteArrayInputStream]
           [java.net URL]
           [javax.swing JPanel JScrollPane]
           [org.apache.batik.swing JSVGCanvas]
           [org.apache.batik.anim.dom SAXSVGDocumentFactory]
           [org.apache.batik.util XMLResourceDescriptor]))

(defn mermaid->svg-url
  "Convert Mermaid diagram to SVG URL from mermaid.ink."
  [mermaid-str]
  (let [encoder (java.util.Base64/getEncoder)
        b64 (.encodeToString encoder (.getBytes mermaid-str "UTF-8"))]
    (str "https://mermaid.ink/svg/" b64)))

(defn fetch-svg-from-mermaid
  "Fetch SVG string from mermaid.ink."
  [mermaid-str]
  (try
    (let [svg-url (mermaid->svg-url mermaid-str)]
      (log/info :fetch-svg-from-mermaid :fetching-from svg-url)
      (slurp svg-url))
    (catch Exception e
      (log/error :fetch-svg-from-mermaid :exception (.getMessage e))
      nil)))

(defn create-svg-canvas
  "Create JSVGCanvas from SVG string.

  Returns interactive, zoomable Batik canvas component."
  [svg-string]
  (try
    (let [canvas (JSVGCanvas.)
          doc-factory (SAXSVGDocumentFactory.
                       (XMLResourceDescriptor/getXMLParserClassName))
          ;; Use a base URI for resolving relative references in SVG
          ;; Using mermaid.ink as base since that's where SVGs come from
          svg-doc (.createDocument doc-factory "https://mermaid.ink/" (StringReader. svg-string))]

      ;; Set the document
      (.setSVGDocument canvas svg-doc)

      ;; Enable zoom/pan interactions
      (.setEnableZoomInteractor canvas true)
      (.setEnablePanInteractor canvas true)

      (log/info :create-svg-canvas :success true)
      canvas)
    (catch Exception e
      (log/error :create-svg-canvas :exception (.getMessage e))
      nil)))

(defn create-svg-panel
  "Create panel with interactive SVG visualization.

  Fetches SVG from mermaid.ink and displays in Batik canvas."
  [issues deps]
  (try
    (let [mermaid-str (mermaid/generate-mermaid-diagram issues deps)
          svg-string (fetch-svg-from-mermaid mermaid-str)]

      (if svg-string
        (do
          ;; Save SVG to file for debugging
          (let [target-dir (db/get-target-dir)
                output-file (str target-dir "/dependency-graph.svg")]
            (spit output-file svg-string)
            (log/info :save-svg :success true :file output-file))

          ;; Create interactive canvas
          (if-let [canvas (create-svg-canvas svg-string)]
            (doto (JPanel. (BorderLayout.))
              (.add canvas BorderLayout/CENTER))
            ;; Fallback if canvas creation fails
            (s/label :text "Error: Could not create SVG canvas"
                     :font (Font. Font/SANS_SERIF Font/BOLD 16))))

        ;; Fallback if SVG fetch fails
        (s/label :text "Error: Could not fetch SVG from mermaid.ink"
                 :font (Font. Font/SANS_SERIF Font/BOLD 16))))

    (catch Exception e
      (log/error :create-svg-panel :exception (.getMessage e))
      (s/label :text (str "Error: " (.getMessage e))
               :font (Font. Font/SANS_SERIF Font/BOLD 16)))))

(defn create-graph-panel
  "Create the interactive SVG graph panel.

  Shows all non-closed issues with their dependencies as zoomable SVG.

  Features:
  - Mouse wheel to zoom
  - Click and drag to pan
  - Scalable vector graphics (no pixelation)"
  []
  (let [issues (:issues @db/*app-state)
        deps (beads-db/get-dependencies)

        ;; Filter to non-closed issues
        open-issues (filter #(not= "closed" (:status %)) issues)

        label-text (str "Interactive SVG Graph (" (count open-issues) " open issues) - Scroll to zoom, drag to pan")
        svg-panel (create-svg-panel issues deps)

        panel (s/border-panel
               :north (s/label
                       :text label-text
                       :font (Font. Font/SANS_SERIF Font/BOLD 14)
                       :border 5)
               :center svg-panel)]

    (log/info :create-graph-panel
              :success true
              :total-issues (count issues)
              :open-issues (count open-issues))

    panel))
