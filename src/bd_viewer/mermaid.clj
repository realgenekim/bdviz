(ns bd-viewer.mermaid
  "Mermaid diagram generation for bd-viewer.
  
  Generates Mermaid syntax from issues and dependencies,
  then renders via mermaid.ink API."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [taoensso.timbre :as log])
  (:import [java.util Base64]
           [java.net URL]
           [javax.imageio ImageIO]))

(defn mermaid->image-url
  "Convert Mermaid diagram string to mermaid.ink image URL.
  
  Uses Base64 encoding to embed diagram in URL.
  
  Options:
  - :width - Image width in pixels (optional)"
  [mermaid-str & {:keys [width]}]
  (let [encoder (Base64/getEncoder)
        b64 (.encodeToString encoder (.getBytes mermaid-str "UTF-8"))
        base-url (str "https://mermaid.ink/img/" b64)]
    (if width
      (str base-url "?width=" width)
      base-url)))

(defn generate-mermaid-diagram
  "Generate Mermaid diagram from issues and dependencies.
  
  Options:
  - :filter-fn - Function to filter issues (default: show all non-closed)
  - :layout - :td (top-down) or :lr (left-right), default :td
  
  Returns Mermaid syntax string."
  [issues deps & {:keys [filter-fn layout]
                  :or {filter-fn #(not= "closed" (:status %))
                       layout :td}}]
  (let [;; Filter issues
        filtered-issues (filter filter-fn issues)
        filtered-ids (set (map :id filtered-issues))

        ;; Filter deps to only include filtered issues
        filtered-deps (filter (fn [{:keys [issue-id depends-on-id]}]
                                (and (filtered-ids issue-id)
                                     (filtered-ids depends-on-id)))
                              deps)

        ;; Generate nodes
        nodes (str/join "\n"
                        (map (fn [issue]
                               (let [num (re-find #"\d+$" (:id issue))
                                     title (subs (:title issue) 0 (min 30 (count (:title issue))))
                                     status (:status issue)
                                     class-str (case status
                                                 "open" ":::open"
                                                 "in_progress" ":::inprogress"
                                                 "")]
                                 (str "  " num "[\"#" num " " title "\"]" class-str)))
                             filtered-issues))

        ;; Generate edges
        edges (str/join "\n"
                        (map (fn [{:keys [issue-id depends-on-id]}]
                               (let [from-num (re-find #"\d+$" depends-on-id)
                                     to-num (re-find #"\d+$" issue-id)]
                                 (str "  " from-num " --> " to-num)))
                             filtered-deps))

        ;; Layout direction
        direction (case layout :lr "LR" "TD")

        ;; Assemble Mermaid
        mermaid (str "graph " direction "\n"
                     nodes "\n\n"
                     (when (seq filtered-deps) (str edges "\n\n"))
                     "  classDef open fill:#E8F8F5,stroke:#2ECC71,stroke-width:2px\n"
                     "  classDef inprogress fill:#FFF3CD,stroke:#FFC107,stroke-width:2px")]

    (log/info :generate-mermaid-diagram
              :issue-count (count filtered-issues)
              :dep-count (count filtered-deps))
    mermaid))

(defn fetch-diagram-image
  "Fetch rendered Mermaid diagram from mermaid.ink.
  
  Options:
  - :width - Image width in pixels (optional)
  
  Returns BufferedImage or nil on error."
  [mermaid-str & {:keys [width]}]
  (try
    (let [url-str (mermaid->image-url mermaid-str :width width)
          url (URL. url-str)]
      (log/info :fetch-diagram-image :fetching-from url-str)
      (ImageIO/read url))
    (catch Exception e
      (log/error :fetch-diagram-image :exception (.getMessage e))
      nil)))

(defn save-diagram!
  "Generate and save Mermaid diagram to file.
  
  Returns :done on success, :error on failure."
  [issues deps output-file & opts]
  (try
    (let [mermaid (apply generate-mermaid-diagram issues deps opts)
          img (fetch-diagram-image mermaid)]
      (when img
        (ImageIO/write img "png" (io/file output-file))
        (log/info :save-diagram! :success true :file output-file)
        :done))
    (catch Exception e
      (log/error :save-diagram! :exception (.getMessage e))
      :error)))
