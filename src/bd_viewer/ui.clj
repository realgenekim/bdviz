(ns bd-viewer.ui
  "Swing UI components for bd-viewer."
  (:require [bd-viewer.db :as db]
            [bd-viewer.events :as events]
            [taoensso.timbre :as log])
  (:import [javax.swing JFrame JList JPanel JTextField JButton JLabel JTextArea
                        JScrollPane JSplitPane DefaultListModel BorderFactory
                        ListSelectionModel SwingUtilities]
           [javax.swing.event ListSelectionListener DocumentListener]
           [java.awt BorderLayout GridLayout FlowLayout Font Color Dimension]
           [java.awt.event ActionListener]))

;; ============================================================================
;; Issue List (Left Panel)
;; ============================================================================

(defn create-issue-list []
  "Create the left-side JList showing all issues."
  (let [model (DefaultListModel.)
        jlist (doto (JList. model)
                (.setSelectionMode ListSelectionModel/SINGLE_SELECTION)
                (.setFont (Font. Font/MONOSPACED Font/PLAIN 12)))]
    ;; Wire up selection events
    (.addListSelectionListener jlist
      (reify ListSelectionListener
        (valueChanged [_ e]
          (when-not (.getValueIsAdjusting e)
            (let [index (.getSelectedIndex jlist)]
              (when (>= index 0)
                (let [filtered (db/get-filtered-issues)
                      issue (nth filtered index nil)]
                  (when issue
                    (events/handle-event
                      {:event/type ::events/issue-selected
                       :issue-id (:id issue)
                       :index index})))))))))
    ;; Store reference in state
    (swap! db/*app-state assoc-in [:ui-refs :issue-list] jlist)
    jlist))

;; ============================================================================
;; Detail Panel (Right Side)
;; ============================================================================

(defn create-detail-panel []
  "Create the right-side detail view."
  (let [;; Title
        title-label (doto (JLabel. "No issue selected")
                      (.setFont (Font. Font/SANS_SERIF Font/BOLD 16)))

        ;; Description
        desc-area (doto (JTextArea. 10 40)
                    (.setEditable false)
                    (.setLineWrap true)
                    (.setWrapStyleWord true)
                    (.setFont (Font. Font/SANS_SERIF Font/PLAIN 12)))
        desc-scroll (JScrollPane. desc-area)

        ;; Metadata labels
        id-label (JLabel. "")
        status-label (JLabel. "")
        priority-label (JLabel. "")
        type-label (JLabel. "")
        labels-label (JLabel. "")
        created-label (JLabel. "")
        updated-label (JLabel. "")

        ;; Layout
        metadata-panel (doto (JPanel. (GridLayout. 7 1 5 5))
                         (.add id-label)
                         (.add status-label)
                         (.add priority-label)
                         (.add type-label)
                         (.add labels-label)
                         (.add created-label)
                         (.add updated-label)
                         (.setBorder (BorderFactory/createEmptyBorder 10 10 10 10)))

        panel (doto (JPanel. (BorderLayout. 10 10))
                (.add title-label BorderLayout/NORTH)
                (.add desc-scroll BorderLayout/CENTER)
                (.add metadata-panel BorderLayout/SOUTH)
                (.setBorder (BorderFactory/createEmptyBorder 10 10 10 10)))]

    ;; Store references in state
    (swap! db/*app-state assoc-in [:ui-refs :detail-panel] panel)
    (swap! db/*app-state assoc-in [:ui-refs :title-label] title-label)
    (swap! db/*app-state assoc-in [:ui-refs :description-area] desc-area)
    (swap! db/*app-state assoc-in [:ui-refs :id-label] id-label)
    (swap! db/*app-state assoc-in [:ui-refs :status-label] status-label)
    (swap! db/*app-state assoc-in [:ui-refs :priority-label] priority-label)
    (swap! db/*app-state assoc-in [:ui-refs :type-label] type-label)
    (swap! db/*app-state assoc-in [:ui-refs :labels-label] labels-label)
    (swap! db/*app-state assoc-in [:ui-refs :created-label] created-label)
    (swap! db/*app-state assoc-in [:ui-refs :updated-label] updated-label)

    panel))

;; ============================================================================
;; Search Bar (Top)
;; ============================================================================

(defn create-search-bar []
  "Create the top search bar."
  (let [search-field (doto (JTextField. 30)
                       (.setFont (Font. Font/SANS_SERIF Font/PLAIN 14)))]
    ;; Listen to text changes
    (.. search-field getDocument
        (addDocumentListener
          (reify DocumentListener
            (insertUpdate [_ e]
              (events/handle-event
                {:event/type ::events/filter-changed
                 :text (.getText search-field)}))
            (removeUpdate [_ e]
              (events/handle-event
                {:event/type ::events/filter-changed
                 :text (.getText search-field)}))
            (changedUpdate [_ e]
              (events/handle-event
                {:event/type ::events/filter-changed
                 :text (.getText search-field)})))))
    ;; Store reference
    (swap! db/*app-state assoc-in [:ui-refs :search-field] search-field)
    search-field))

;; ============================================================================
;; Toolbar (Buttons)
;; ============================================================================

(defn create-toolbar []
  "Create toolbar with action buttons."
  (let [reload-btn (doto (JButton. "Reload (⌘R)")
                     (.addActionListener
                       (reify ActionListener
                         (actionPerformed [_ e]
                           (events/handle-event {:event/type ::events/reload-issues})))))

        delete-btn (doto (JButton. "Delete (⌘D)")
                     (.addActionListener
                       (reify ActionListener
                         (actionPerformed [_ e]
                           (events/handle-event {:event/type ::events/delete-issue})))))

        panel (doto (JPanel. (FlowLayout. FlowLayout/LEFT))
                (.add reload-btn)
                (.add delete-btn))]
    panel))

;; ============================================================================
;; Main Frame
;; ============================================================================

(defn create-main-frame []
  "Create and show the main application window."
  (let [frame (JFrame. "BD Viewer")

        ;; Components
        search-bar (create-search-bar)
        toolbar (create-toolbar)
        issue-list (create-issue-list)
        detail-panel (create-detail-panel)

        ;; Layout top panel
        top-panel (doto (JPanel. (BorderLayout. 5 5))
                    (.add (JLabel. " Search: ") BorderLayout/WEST)
                    (.add search-bar BorderLayout/CENTER)
                    (.add toolbar BorderLayout/EAST)
                    (.setBorder (BorderFactory/createEmptyBorder 5 5 5 5)))

        ;; Left panel with scroll
        left-panel (doto (JScrollPane. issue-list)
                     (.setPreferredSize (Dimension. 400 600)))

        ;; Split pane
        split-pane (doto (JSplitPane. JSplitPane/HORIZONTAL_SPLIT
                                     left-panel
                                     detail-panel)
                     (.setDividerLocation 400)
                     (.setResizeWeight 0.4))]

    ;; Assemble
    (doto (.getContentPane frame)
      (.setLayout (BorderLayout.))
      (.add top-panel BorderLayout/NORTH)
      (.add split-pane BorderLayout/CENTER))

    ;; Frame setup
    (doto frame
      (.setSize 1000 700)
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setLocationRelativeTo nil))  ; Center on screen

    ;; Store frame reference
    (swap! db/*app-state assoc-in [:ui-refs :frame] frame)

    ;; Show it!
    (.setVisible frame true)

    (log/info :create-main-frame :success true)
    frame))
