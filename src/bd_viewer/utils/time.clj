(ns bd-viewer.utils.time
  "Time and date formatting utilities."
  (:import [java.time Instant Duration ZonedDateTime ZoneId]
           [java.time.format DateTimeFormatter]))

(defn parse-iso-timestamp
  "Parse ISO 8601 timestamp string to Instant.
  Returns nil if parsing fails."
  [timestamp-str]
  (try
    (Instant/parse timestamp-str)
    (catch Exception e
      nil)))

(defn time-ago
  "Format timestamp as human-friendly relative time (e.g., '3 days ago').
  
  timestamp-str: ISO 8601 timestamp string
  
  Returns: String like '3 days ago', 'just now', '2 hours ago', etc."
  [timestamp-str]
  (if-let [instant (parse-iso-timestamp timestamp-str)]
    (let [now (Instant/now)
          duration (Duration/between instant now)
          seconds (.getSeconds duration)
          minutes (quot seconds 60)
          hours (quot minutes 60)
          days (quot hours 24)
          weeks (quot days 7)
          months (quot days 30)
          years (quot days 365)]
      (cond
        (< seconds 10) "just now"
        (< seconds 60) (str seconds " seconds ago")
        (< minutes 60) (if (= minutes 1)
                         "1 minute ago"
                         (str minutes " minutes ago"))
        (< hours 24) (if (= hours 1)
                       "1 hour ago"
                       (str hours " hours ago"))
        (< days 7) (if (= days 1)
                     "1 day ago"
                     (str days " days ago"))
        (< days 30) (if (= weeks 1)
                      "1 week ago"
                      (str weeks " weeks ago"))
        (< days 365) (if (= months 1)
                       "1 month ago"
                       (str months " months ago"))
        :else (if (= years 1)
                "1 year ago"
                (str years " years ago"))))
    "unknown"))

(defn format-timestamp
  "Format timestamp as both human-friendly relative time AND absolute date.
  
  timestamp-str: ISO 8601 timestamp string
  
  Returns: String like '3 days ago (2025-01-06 14:30)'"
  [timestamp-str]
  (if-let [instant (parse-iso-timestamp timestamp-str)]
    (let [relative (time-ago timestamp-str)
          zoned (ZonedDateTime/ofInstant instant (ZoneId/systemDefault))
          formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm")
          absolute (.format zoned formatter)]
      (str relative " (" absolute ")"))
    timestamp-str)) ; Return original if parsing fails

(comment
  ;; Test relative time formatting
  (time-ago "2025-01-06T12:00:00Z")
  (time-ago "2025-01-01T12:00:00Z")

  ;; Test full formatting
  (format-timestamp "2025-01-06T12:00:00Z"))
