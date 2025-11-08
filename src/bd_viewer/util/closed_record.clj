(ns bd-viewer.util.closed-record
  "ClosedRecord - A map wrapper that validates key access against a schema.

  Designed to catch invalid key access early, especially useful when working
  with LLMs that may hallucinate map keys.

  Example:
    (def user {:user-id \"U123\" :name \"Alice\"})
    (def cr (closed-record user))

    ;; Valid access works normally
    (:name cr)                    ;=> \"Alice\"
    (get cr :user-id)             ;=> \"U123\"
    (assoc cr :name \"Bob\")       ;=> updated ClosedRecord

    ;; Invalid access fails loudly
    (:invalid-key cr)             ;=> throws ex-info
    (assoc cr :bad-key \"x\")      ;=> throws ex-info

  Options:
    :schema                - Set of allowed keys (default: keys from data)
    :throw-on-invalid-read - Throw on invalid reads (default: true)
    :throw-on-invalid-write - Throw on invalid writes (default: true)
    :spec                  - Derive schema from clojure.spec"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.set]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]))

;; Sentinel value for invalid key access
(def INVALID-KEY :INVALID-KEY)

;; Spec support functions

(defn- extract-keys-from-spec
  "Extract the set of valid keys from a clojure.spec.

  Supports s/keys forms with:
  - :req-un (required unqualified keys)
  - :opt-un (optional unqualified keys)
  - :req (required namespaced keys)
  - :opt (optional namespaced keys)

  Examples:
    (s/def ::user (s/keys :req-un [::user-id ::username]
                          :opt-un [::email]))
    (extract-keys-from-spec ::user)
    ;=> #{:user-id :username :email}

    (s/def ::person (s/keys :req [:person/id :person/name]))
    (extract-keys-from-spec ::person)
    ;=> #{:person/id :person/name}"
  [spec-kw]
  (when spec-kw
    (try
      (when-let [spec-form (s/form spec-kw)]
        (cond
          ;; Handle (s/keys :req-un [...] :opt-un [...] :req [...] :opt [...])
          (and (seq? spec-form)
               (= 'clojure.spec.alpha/keys (first spec-form)))
          (let [args (apply hash-map (rest spec-form))
                ;; Unqualified keys - remove namespace
                req-un (map #(keyword (name %)) (get args :req-un []))
                opt-un (map #(keyword (name %)) (get args :opt-un []))
                ;; Namespaced keys - keep as-is
                req (get args :req [])
                opt (get args :opt [])]
            (set (concat req-un opt-un req opt)))

          ;; Unsupported spec form
          :else
          (do
            (log/warn :extract-keys-from-spec/unsupported-form
                      :spec spec-kw
                      :form spec-form)
            nil)))
      (catch Exception e
        (log/error :extract-keys-from-spec/error
                   :spec spec-kw
                   :exception (.getMessage e))
        nil))))

;; Recursive nested spec support functions

(defn- extract-nested-specs
  "Extract nested spec keywords from a parent spec.

  Returns map of key-name -> spec-keyword for nested specs.

  Example:
    (s/def ::user-entity (s/keys :opt-un [::user-slack-id ::name]))
    (s/def ::message (s/keys :req-un [::message-key ::user-obj]))

    (extract-nested-specs ::message)
    ;=> {:user-obj :slack-archive.util.closed-record-test/user-entity}"
  [spec-kw]
  (when spec-kw
    (try
      (when-let [spec-form (s/form spec-kw)]
        (when (and (seq? spec-form)
                   (= 'clojure.spec.alpha/keys (first spec-form)))
          (let [args (apply hash-map (rest spec-form))
                all-key-specs (concat (get args :req-un [])
                                      (get args :opt-un [])
                                      (get args :req [])
                                      (get args :opt []))]
            ;; For each key spec, check if it has a registered spec
            (->> all-key-specs
                 (keep (fn [key-spec]
                         (let [key-name (keyword (name key-spec))]
                           ;; Try to get the spec for this key
                           (when-let [nested-spec (s/get-spec key-spec)]
                             ;; Return [key-name spec-keyword]
                             [key-name key-spec]))))
                 (into {})))))
      (catch Exception e
        (log/warn :extract-nested-specs/error
                  :spec spec-kw
                  :exception (.getMessage e))
        {}))))

(declare closed-record) ; Forward declaration for recursion

(defn- wrap-nested-value
  "Recursively wrap a value based on its type and nested spec.

  - Map: Wrap as ClosedRecord with nested spec
  - Vector/List of maps: Wrap each map as ClosedRecord
  - Nil or other values: Return as-is"
  [value nested-spec-kw opts]
  (cond
    ;; Nil - return as-is
    (nil? value)
    nil

    ;; Single map - wrap it recursively
    (map? value)
    (closed-record value (assoc opts :spec nested-spec-kw))

    ;; Collection of maps - wrap each element
    (and (coll? value)
         (not (empty? value))
         (every? map? value))
    (into (empty value) ; Preserve collection type (vector/list)
          (map #(closed-record % (assoc opts :spec nested-spec-kw)))
          value)

    ;; Other values (strings, numbers, etc.) - return as-is
    :else
    value))

(defn- apply-recursive-wrapping
  "Apply recursive wrapping to nested maps in data.

  For each key in nested-specs, wrap the corresponding value
  as a ClosedRecord (if it's a map or collection of maps)."
  [data nested-specs opts]
  (if (empty? nested-specs)
    data
    (reduce-kv
     (fn [acc key nested-spec-kw]
       (if-let [value (get acc key)]
         (assoc acc key (wrap-nested-value value nested-spec-kw opts))
         acc))
     data
     nested-specs)))

(deftype ClosedRecord [data schema config]
  ;; ILookup: Handles (get cr :key) and (:key cr) syntax
  clojure.lang.ILookup
  (valAt [this k]
    ;; Called when no default is provided
    (if (contains? schema k)
      (get data k nil)
      (let [msg (format "INVALID KEY ACCESS: %s (valid keys: %s)" k (vec (sort schema)))]
        (log/error :closed-record/invalid-key-access
                   :key k
                   :valid-keys (sort schema))
        (println "**** " msg " ****")
        (if (:throw-on-invalid-read config)
          (throw (ex-info msg {:key k
                               :schema schema
                               :type :invalid-key-access}))
          INVALID-KEY))))
  (valAt [this k not-found]
    ;; Called when a default value is provided - honor the default for invalid keys
    (if (contains? schema k)
      (get data k not-found)
      ;; When a default is explicitly provided, return it (don't throw)
      ;; This matches standard Clojure map behavior: (get {} :missing "default") => "default"
      not-found))

  ;; IFn: Handles (cr :key) syntax
  clojure.lang.IFn
  (invoke [this k]
    (.valAt this k))
  (invoke [this k not-found]
    (.valAt this k not-found))

  ;; Associative: Handles assoc, dissoc, contains?, etc.
  clojure.lang.Associative
  (assoc [this k v]
    (if (contains? schema k)
      (ClosedRecord. (assoc data k v) schema config)
      (let [msg (format "INVALID KEY ASSOC: %s (valid keys: %s)" k (vec (sort schema)))]
        (log/error :closed-record/invalid-key-assoc
                   :key k
                   :valid-keys (sort schema))
        (println "**** " msg " ****")
        (if (:throw-on-invalid-write config)
          (throw (ex-info msg {:key k
                               :schema schema
                               :type :invalid-key-assoc}))
          INVALID-KEY))))
  (containsKey [this k]
    (contains? schema k))
  (entryAt [this k]
    (when (contains? schema k)
      (find data k)))

  ;; IPersistentMap: Handles dissoc
  clojure.lang.IPersistentMap
  (assocEx [this k v]
    ;; assocEx throws if key already exists
    (if (contains? schema k)
      (if (contains? data k)
        (throw (ex-info "Key already present" {:key k}))
        (.assoc this k v))
      (throw (ex-info "Key not in schema" {:key k :schema schema}))))
  (without [this k]
    (if (contains? schema k)
      (ClosedRecord. (dissoc data k) schema config)
      ;; Dissoc of non-existent key is idempotent in Clojure
      this))

  ;; Seqable: Handles seq, keys, vals
  clojure.lang.Seqable
  (seq [this]
    (seq data))

  ;; Counted: Handles count
  clojure.lang.Counted
  (count [this]
    (count data))

  ;; IPersistentCollection: Handles cons, empty, equiv
  clojure.lang.IPersistentCollection
  (cons [this o]
    (cond
      ;; Handle MapEntry (from (first {:a 1}))
      (map-entry? o)
      (.assoc this (key o) (val o))

      ;; Handle vector pair like [:key val]
      (and (vector? o) (= 2 (count o)))
      (.assoc this (nth o 0) (nth o 1))

      ;; Handle map (used by merge) - filter to only valid keys
      (map? o)
      (reduce (fn [acc [k v]]
                ;; Only assoc if key is in schema (silently skip invalid keys)
                (if (contains? schema k)
                  (.assoc acc k v)
                  acc))
              this
              o)

      ;; Invalid input
      :else
      (throw (ex-info "cons expects MapEntry, [key val] vector, or map"
                      {:input o :type (type o)}))))
  (empty [this]
    (ClosedRecord. {} schema config))
  (equiv [this other]
    (if (instance? ClosedRecord other)
      (and (= data (.-data ^ClosedRecord other))
           (= schema (.-schema ^ClosedRecord other)))
      ;; Compare with regular map
      (= data other)))

  ;; Iterable: Required for Java interop
  java.lang.Iterable
  (iterator [this]
    (.iterator ^Iterable (seq data)))

  ;; Object: toString, equals, hashCode for REPL display and equality
  Object
  (toString [this]
    (str "#ClosedRecord" (pr-str data)))
  (equals [this other]
    ;; Java equals for bidirectional equality with maps
    (if (instance? ClosedRecord other)
      (and (= data (.-data ^ClosedRecord other))
           (= schema (.-schema ^ClosedRecord other)))
      ;; Allow equality with plain maps (compare data only)
      (and (map? other)
           (= data other))))
  (hashCode [this]
    ;; Must match hashCode of underlying map for proper equality
    (.hashCode ^Object data)))

;; Constructor functions

(defn closed-record
  "Create a ClosedRecord that validates key access against schema.

  Options:
  - :schema                         - Set of allowed keys (explicit)
  - :spec                           - clojure.spec keyword to derive schema from
  - :recursive                      - Recursively wrap nested maps (default: false)
  - :throw-on-invalid-read          - Throw exception on invalid key access (default: true)
  - :throw-on-invalid-write         - Throw exception on invalid key assoc (default: true)
  - :relax-constructor-constraints? - Allow extra keys in data beyond spec (default: false)
                                      Use when receiving data with keys you don't control
                                      (e.g., Slack API responses). Prevents tight coupling
                                      between spec and external data sources.

  Schema precedence (highest to lowest):
  1. Explicit :schema in options
  2. :spec in options (extracts keys from spec)
  3. :spec in data's metadata
  4. Derive from data's keys

  DEFAULTS:
  - Invalid reads: Throw exception (strict by default)
  - Invalid writes: Throw exception (strict by default)
  - Constructor constraints: Strict (no extra keys allowed)

  This design catches typos and invalid key access immediately, preventing bugs
  from propagating through your code.

  Examples:
    ;; Strict mode (default) - throws on invalid reads
    (closed-record {:id 1 :name \"Alice\"})

    ;; Explicit schema (allows keys not present in initial data)
    (closed-record {:id 1} {:schema #{:id :name :email}})

    ;; With clojure.spec - extracts keys automatically
    (s/def ::user (s/keys :req-un [::user-id ::username]))
    (closed-record {:user-id \"U123\"} {:spec ::user})

    ;; Spec in metadata (reads automatically)
    (def data ^{:spec ::user} {:user-id \"U123\"})
    (closed-record data)  ; Uses ::user spec from metadata

    ;; Recursive wrapping - protects nested maps too!
    (s/def ::user-entity (s/keys :opt-un [::user-slack-id ::name]))
    (s/def ::message (s/keys :req-un [::message-key ::text ::user-obj]))
    (def msg (closed-record msg-data {:spec ::message :recursive true}))
    ;; Now nested :user-obj is ALSO a ClosedRecord
    (-> msg :user-obj :name)        ;=> \"alice\" ✅
    (-> msg :user-obj :nam)         ;=> THROWS! ✅ (typo caught in nested field!)

    ;; Lenient mode - return :INVALID-KEY instead of throwing
    (closed-record {:id 1} {:throw-on-invalid-read false})

    ;; Allow invalid writes (not recommended)
    (closed-record {:id 1} {:throw-on-invalid-write false})

    ;; Accept external data with extra keys (relaxed constructor)
    ;; Use case: Slack API returns extra fields we don't need in our spec
    (closed-record {:id 1 :name \"Alice\" :api-version \"2.0\"}
                   {:spec ::user :relax-constructor-constraints? true})"
  ([data] (closed-record data nil))
  ([data opts]
   (let [spec-kw (or (:spec opts) (-> data meta :spec))

         ;; Schema precedence: explicit > spec option > metadata spec > derive
         schema (or
                 ;; 1. Explicit schema
                 (:schema opts)

                 ;; 2. Spec in options
                 (when spec-kw
                   (extract-keys-from-spec spec-kw))

                 ;; 3. Spec in metadata (already checked in spec-kw)
                 nil

                 ;; 4. Derive from data
                 (set (keys data)))

         ;; Validate: if spec is provided and relax-constructor-constraints? is false,
         ;; ensure data doesn't have extra keys beyond the spec
         _ (when (and spec-kw
                      (not (:relax-constructor-constraints? opts)))
             (let [data-keys (set (keys data))
                   extra-keys (clojure.set/difference data-keys schema)]
               (when (seq extra-keys)
                 (throw (ex-info (format "Data contains keys not in spec: %s (allowed: %s)"
                                         (vec (sort extra-keys))
                                         (vec (sort schema)))
                                 {:extra-keys extra-keys
                                  :schema schema
                                  :spec spec-kw
                                  :type :invalid-constructor-keys})))))

         ;; If :relax-constructor-constraints? is true, filter data to only include valid keys
         ;; This allows external data to have extra keys without failing construction
         filtered-data (if (:relax-constructor-constraints? opts)
                         (select-keys data schema)
                         data)

         ;; Extract nested specs if recursive mode enabled
         nested-specs (when (and (:recursive opts) spec-kw)
                        (extract-nested-specs spec-kw))

         ;; Apply recursive wrapping to nested maps
         wrapped-data (if (and (:recursive opts) nested-specs (not (empty? nested-specs)))
                        (do
                          (log/debug :closed-record/recursive-wrapping
                                     :spec spec-kw
                                     :nested-specs (keys nested-specs))
                          (apply-recursive-wrapping filtered-data nested-specs opts))
                        filtered-data)

         config {:throw-on-invalid-read (if (contains? opts :throw-on-invalid-read)
                                          (:throw-on-invalid-read opts)
                                          true) ; Default: true (throw)
                 :throw-on-invalid-write (if (contains? opts :throw-on-invalid-write)
                                           (:throw-on-invalid-write opts)
                                           true)}] ; Default: true (throw)
     (ClosedRecord. wrapped-data schema config))))

;; Helper functions

(defn closed-record?
  "Returns true if x is a ClosedRecord."
  [x]
  (instance? ClosedRecord x))

(defn valid-keys
  "Returns the set of valid keys for this ClosedRecord."
  [^ClosedRecord cr]
  (.-schema cr))

(defn underlying-map
  "Returns the underlying map data from a ClosedRecord.
  Useful for interop with functions that expect plain maps."
  [^ClosedRecord cr]
  (.-data cr))

(defn to-map
  "Converts a ClosedRecord back to a plain Clojure map.
  Alias for underlying-map."
  [^ClosedRecord cr]
  (.-data cr))

(defn with-schema
  "Returns a new ClosedRecord with an updated schema.
  Useful for adding additional valid keys after construction."
  [^ClosedRecord cr new-schema]
  (ClosedRecord. (.-data cr) new-schema (.-config cr)))

(defn add-valid-keys
  "Returns a new ClosedRecord with additional valid keys added to schema."
  [^ClosedRecord cr & ks]
  (with-schema cr (into (.-schema cr) ks)))

;; ============================================================================
;; Schema Export for clj-kondo Integration
;; ============================================================================

;; Global registry of schemas for clj-kondo export.
;; Map of type-name (keyword) -> schema (set of keywords).
;;
;; Example: {:schema/message #{:message-key :ts :user-id}
;;           :schema/user #{:user-id :name :display-name}}
(defonce ^:private schema-registry (atom {}))

(defn register-schema!
  "Register a ClosedRecord's schema for clj-kondo export.

  Call this when creating ClosedRecords to make their schemas available
  for static analysis. Typically called in query functions or test fixtures.

  Args:
    type-name - Keyword naming this type (e.g., :schema/message)
    cr        - ClosedRecord instance whose schema to register

  Returns:
    The registered schema (set of keywords)

  Example:
    (defn get-messages-wrapped []
      (let [messages (mapv closed-record (db/get-messages))]
        (when-let [msg (first messages)]
          (register-schema! :schema/message msg))
        messages))"
  [type-name ^ClosedRecord cr]
  (let [schema (valid-keys cr)]
    (swap! schema-registry assoc type-name schema)
    (log/debug :register-schema!
               :type type-name
               :keys (count schema))
    schema))

(defn export-schemas-for-clj-kondo!
  "Export all registered schemas to .clj-kondo/closed-record-schemas.edn.

  This creates a schema file that clj-kondo hooks can read to provide
  lint-time validation of map key access.

  Call this:
  - In test fixtures (auto-export on every test run)
  - In REPL after loading data
  - In CI to verify schemas haven't changed

  Returns:
    Map of exported schemas

  Side effects:
    - Creates .clj-kondo/ directory if it doesn't exist
    - Writes .clj-kondo/closed-record-schemas.edn
    - Prints summary to stdout

  Example:
    (db/reload!)
    (def messages (db/get-messages-wrapped))  ; Registers :schema/message
    (export-schemas-for-clj-kondo!)
    ;; ✅ Exported 1 schemas to .clj-kondo/closed-record-schemas.edn"
  []
  (let [schemas @schema-registry
        output-file ".clj-kondo/closed-record-schemas.edn"]
    (io/make-parents output-file)
    (spit output-file (with-out-str (clojure.pprint/pprint schemas)))
    (println "✅ Exported" (count schemas) "schemas to" output-file)
    (log/info :export-schemas-for-clj-kondo!
              :count (count schemas)
              :file output-file)
    schemas))

(defn clear-schema-registry!
  "Clear all registered schemas.
  Useful for testing or when you want to re-register from scratch."
  []
  (reset! schema-registry {})
  (log/debug :clear-schema-registry! :cleared true))

(defn list-registered-schemas
  "List all currently registered schemas.
  Returns map of type-name -> schema."
  []
  @schema-registry)

(defn schema-diff
  "Compare runtime schemas with clj-kondo schemas.

  Returns:
    Map with keys:
      :only-runtime   - Types only in runtime registry
      :only-lint      - Types only in clj-kondo file
      :schema-diff    - Types with different keys

  Example:
    (schema-diff)
    ;=> {:only-runtime #{:schema/new-type}
         :only-lint #{}
         :schema-diff {:schema/message {:runtime #{:new-field}
                                         :lint #{:old-field}}}}"
  []
  (let [runtime-schemas @schema-registry
        lint-file ".clj-kondo/closed-record-schemas.edn"
        lint-schemas (try
                       (edn/read-string (slurp lint-file))
                       (catch Exception e
                         (log/warn :schema-diff :no-lint-file
                                   :file lint-file
                                   :error (.getMessage e))
                         {}))
        runtime-types (set (keys runtime-schemas))
        lint-types (set (keys lint-schemas))
        only-runtime (clojure.set/difference runtime-types lint-types)
        only-lint (clojure.set/difference lint-types runtime-types)
        common-types (clojure.set/intersection runtime-types lint-types)
        schema-diffs (into {}
                           (keep (fn [type-name]
                                   (let [runtime-keys (get runtime-schemas type-name)
                                         lint-keys (get lint-schemas type-name)]
                                     (when (not= runtime-keys lint-keys)
                                       [type-name
                                        {:runtime (clojure.set/difference runtime-keys lint-keys)
                                         :lint (clojure.set/difference lint-keys runtime-keys)}])))
                                 common-types))]
    {:only-runtime only-runtime
     :only-lint only-lint
     :schema-diff schema-diffs}))

(defn print-schema-diff
  "Print human-readable schema diff.

  Example:
    (print-schema-diff)
    ;; ⚠️  Schema differences detected:
    ;;
    ;; New types (runtime only):
    ;;   - :schema/message
    ;;
    ;; Schema mismatches:
    ;;   :schema/user
    ;;     Runtime has: [:email :phone]
    ;;     Lint has: [:address]"
  []
  (let [{:keys [only-runtime only-lint schema-diff]} (schema-diff)]
    (if (and (empty? only-runtime)
             (empty? only-lint)
             (empty? schema-diff))
      (println "✅ Schemas in sync!")
      (do
        (println "⚠️  Schema differences detected:\n")
        (when (seq only-runtime)
          (println "New types (runtime only):")
          (doseq [type-name only-runtime]
            (println "  -" type-name))
          (println))
        (when (seq only-lint)
          (println "Removed types (lint only):")
          (doseq [type-name only-lint]
            (println "  -" type-name))
          (println))
        (when (seq schema-diff)
          (println "Schema mismatches:")
          (doseq [[type-name {:keys [runtime lint]}] schema-diff]
            (println "  " type-name)
            (when (seq runtime)
              (println "    Runtime has:" (vec (sort runtime))))
            (when (seq lint)
              (println "    Lint has:" (vec (sort lint)))))
          (println))))))

(comment
  ;; Basic usage (LENIENT MODE - default)
  (def user {:user-id "U123" :name "Alice" :email "alice@example.com"})
  (def cr (closed-record user))

  ;; Valid access works normally
  (:name cr) ; => "Alice"
  (get cr :user-id) ; => "U123"
  (cr :email) ; => "alice@example.com"
  (-> cr :name) ; => "Alice"

  ;; Invalid access returns :INVALID-KEY (default: lenient mode)
  (:invalid-key cr) ; => :INVALID-KEY (logs error)

  ;; Valid update works
  (def cr2 (assoc cr :name "Bob"))
  (:name cr2) ; => "Bob"

  ;; Invalid update THROWS (always strict)
  (try
    (assoc cr :invalid-key "value")
    (catch Exception e
      (ex-data e))) ; => {:key :invalid-key, :schema #{...}}

  ;; Works with threading
  (-> cr
      (assoc :name "Charlie")
      :name) ; => "Charlie"

  ;; Explicit schema (allows future keys)
  (def cr3 (closed-record {:id 1}
                          {:schema #{:id :name :email}}))
  (assoc cr3 :name "Alice") ; => works! :name in schema

  ;; STRICT MODE - throw on invalid reads
  (def strict-cr (closed-record user {:throw-on-invalid-read true}))
  (try
    (:invalid-key strict-cr)
    (catch Exception e
      (ex-message e))) ; => "INVALID KEY ACCESS: :invalid-key ..."

  ;; Helper functions
  (valid-keys cr) ; => #{:user-id :name :email}
  (to-map cr) ; => {:user-id "U123", :name "Alice", ...}
  (closed-record? cr) ; => true

  ;; Add valid keys after construction
  (def cr4 (add-valid-keys cr :phone :address))
  (assoc cr4 :phone "555-1234") ; => works!

  ;; Integration example: wrap database results
  (require '[slack-archive.db.files :as db])
  (def messages (db/get-messages))
  (def first-msg (first messages))

  ;; Wrap in ClosedRecord to catch typos
  (def cr-msg (closed-record first-msg))

  ;; This will fail loudly if you typo the key
  (-> cr-msg :mesage-key) ; => :INVALID-KEY (typo caught!)
  (-> cr-msg :message-key)) ; => works
