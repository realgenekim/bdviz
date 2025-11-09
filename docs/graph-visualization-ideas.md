# Graph Visualization Ideas for bd-viewer

## Vision: Multi-Tab Interface with Real-Time Agent Monitoring

### Tab 1: Traditional List View (Existing)
- Hierarchical outline
- Epic → Task breakdown
- Status, priority, assignee columns

### Tab 2: **Graph Visualization** (New!)
- Visual representation of issues and relationships
- Real-time multi-agent monitoring
- Interactive navigation and filtering

---

## Core Visualization Approaches

### Approach 1: Force-Directed Dependency Graph
**Best for**: Understanding blocking relationships and critical paths

```
                    ┌─────────────┐
                    │  Epic-001   │
                    │  Auth System│
                    │  [OPEN]     │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │                         │
         ┌────▼─────┐             ┌────▼─────┐
         │Task-001.1│             │Task-001.2│
         │Login UI  │────blocks───▶Design DB │
         │[PROGRESS]│             │[OPEN]    │
         └────┬─────┘             └────┬─────┘
              │                        │
              │related                 │blocks
              │                        │
         ┌────▼─────┐             ┌────▼─────┐
         │Task-001.3│             │Task-001.4│
         │Unit Tests│             │API Endpts│
         │[BLOCKED] │◀───blocks───│[PROGRESS]│
         └──────────┘             └──────────┘

Legend:
  ──────▶  blocks (hard blocker)
  ─ ─ ─▶  related (soft association)
  ══════▶  parent-child (hierarchy)
  
Node Colors:
  🟢 OPEN (ready to work)
  🟡 IN_PROGRESS (actively worked)
  🔴 BLOCKED (waiting)
  ⚫ CLOSED (done)
```

**Interactive Features**:
- Click node → expand its ego-network (1-2 hops)
- Hover → show tooltip (title, assignee, estimated time)
- Double-click → open detail pane
- Drag nodes to rearrange (physics keeps updating)

---

### Approach 2: Radial/Sunburst Layout
**Best for**: Epic-centric views with many children

```
                        ┌────────────┐
                        │  Epic-001  │
                        │Auth System │
                        └─────┬──────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
   ┌────▼────┐          ┌─────▼────┐          ┌────▼────┐
   │Task .1  │          │ Task .2  │          │Task .3  │
   │Login UI │          │ Database │          │Testing  │
   └────┬────┘          └────┬─────┘          └────┬────┘
        │                    │                      │
   ┌────┴────┐          ┌────┴────┐          ┌─────┴────┐
   │Sub .1.1 │          │Sub .2.1 │          │Sub .3.1  │
   │Frontend │          │Schema   │          │Unit Tests│
   └─────────┘          └─────────┘          └──────────┘

Multi-Epic Radial View:
                    ╔════════════════╗
                    ║   Project X    ║
                    ╚════════════════╝
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   ┌────▼────┐        ┌────▼────┐       ┌────▼────┐
   │ Epic A  │        │ Epic B  │       │ Epic C  │
   │ Auth    │        │ Search  │       │ Deploy  │
   └─────────┘        └─────────┘       └─────────┘
     (children)         (children)        (children)
```

---

### Approach 3: Swimlane + Dependency Arrows
**Best for**: Showing progress flow and blockers across stages

```
┌──────────────────────────────────────────────────────────┐
│ OPEN                  IN_PROGRESS          BLOCKED       │CLOSED
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────┐                                             │
│  │Epic-001 │──────────────┐                              │
│  │Auth     │              │                              │
│  └─────────┘              │                              │
│                           │                              │
│  ┌─────────┐         ┌────▼─────┐                        │
│  │Task .2  │         │ Task .1  │                        │
│  │Database │─blocks─▶│ Login UI │                        │
│  └─────────┘         └──────────┘                        │
│                                                           │
│                           │                              │
│                           │blocks                        │
│                           ▼                              │
│                      ┌─────────┐                         │
│                      │Task .3  │                         │
│                      │Testing  │                         │
│                      └─────────┘                         │
│                                                           │
└──────────────────────────────────────────────────────────┘

Arrows show blocking dependencies across columns.
```

---

### Approach 4: Timeline/Critical Path View
**Best for**: Understanding what's blocking the critical path

```
Time ────────────────────────────────────────────────────▶

Day 1      Day 2      Day 3      Day 4      Day 5
  │          │          │          │          │
  ├──[Epic-001: Auth System]──────────────────────────────┤
  │                                                        │
  ├─[Task .1]──┐                                          │
  │  Login UI  │                                          │
  └────────────┘                                          │
               │                                          │
               └─blocks─▶[Task .2]───────┐               │
                         Database        │               │
                         └───────────────┘               │
                                          │               │
                                          └─blocks─▶[Task .3]
                                                    Testing
                                                    └──────┘

Critical Path (longest): Task .1 → .2 → .3 (5 days)
Bottleneck: Task .2 (Database) - blocks everything else
```

---

## MULTI-AGENT MONITORING VIEW 🚀

### Approach 5: Real-Time Multi-Directory Agent Visualization

**Scenario**: You have 3 agents working on different Beads projects simultaneously

```
┌─────────────────────────────────────────────────────────────────┐
│  Real-Time Agent Activity                     🔴 LIVE           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Agent-1 (.beads/project-a)         Agent-2 (.beads/project-b)  │
│  ┌──────────────────┐               ┌──────────────────┐        │
│  │  ⚡ WORKING       │               │  💤 IDLE         │        │
│  │  Task a-42       │               │  Last: 2m ago    │        │
│  │  "Fix auth bug"  │               └──────────────────┘        │
│  │  Progress: 65%   │                                           │
│  │  Updated: 3s ago │                                           │
│  └──────────────────┘                                           │
│        ↓ blocks                                                 │
│  ┌──────────────────┐                                           │
│  │  ⏸️  BLOCKED      │                                           │
│  │  Task a-43       │               Agent-3 (.beads/project-c)  │
│  │  "Deploy auth"   │               ┌──────────────────┐        │
│  │  Waiting on a-42 │               │  ⚡ WORKING       │        │
│  └──────────────────┘               │  Task c-15       │        │
│                                     │  "API tests"     │        │
│                                     │  Progress: 20%   │        │
│                                     │  Updated: 1s ago │        │
│                                     └──────────────────┘        │
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│ Activity Timeline:                                              │
│ Agent-1: ████████████░░░░░░░░░░ (65% on a-42)                  │
│ Agent-2: ░░░░░░░░░░░░░░░░░░░░░░ (idle)                         │
│ Agent-3: ████░░░░░░░░░░░░░░░░░░ (20% on c-15)                  │
└─────────────────────────────────────────────────────────────────┘
```

**Data Sources**:
- Watch `.beads/issues.jsonl` for each directory
- Detect changes via file watchers or polling
- Track `updated_at` timestamps to show recency
- Infer "actively working" if updates within last 30s

**Visual Indicators**:
- ⚡ Pulsing border = updated in last 30 seconds
- 💤 Dimmed = no updates in 5+ minutes
- 🔥 Red glow = blocked for 10+ minutes
- ✅ Green checkmark = just closed

---

### Approach 6: Combined Dependency + Agent Activity Graph

```
Legend:
  🟢 READY (available)
  🟡 ACTIVE (agent working now)
  🔴 BLOCKED (waiting)
  ⚫ CLOSED (done)
  ⚡ Pulsing = updated <30s ago

                    ┌──────────────┐
                    │  Epic-001    │
                    │  Multi-Agent │
                    │  Refactor    │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              │                         │
         ┌────▼─────┐              ┌───▼──────┐
         │Task .1   │              │Task .2   │
    🟡⚡ │Refactor  │───blocks────▶│Test Suite│ 🔴
   Agent1│Core Logic│              │          │
         │[PROGRESS]│              │[BLOCKED] │
         └────┬─────┘              └──────────┘
              │
              │related
              │
         ┌────▼─────┐              ┌──────────┐
         │Task .3   │              │Task .4   │
    🟢   │Update Docs───related───▶│Deploy    │ 🟢
         │[OPEN]    │              │[OPEN]    │
         └──────────┘              └────┬─────┘
                                        │
                                   🟡⚡  │
                                  Agent2│
                                  Working│
                                        ▼
                                   ┌──────────┐
                                   │Task .4.1 │
                                   │Config    │
                                   │[PROGRESS]│
                                   └──────────┘

Status Panel:
┌──────────────────────────────────┐
│ Agent-1: Working Task .1 (65%)   │
│ Agent-2: Working Task .4.1 (40%) │
│ Blocked: 1 (Task .2)             │
│ Ready: 2 (Task .3, .4)           │
└──────────────────────────────────┘
```

---

## Implementation Strategy

### Tech Stack for Graph Rendering

**Option A: GraphStream** (Recommended for Java/Swing)
```clojure
(require '[clojure.java.io :as io])
(import '[org.graphstream.graph.implementations SingleGraph]
        '[org.graphstream.ui.swing_viewer SwingViewer]
        '[org.graphstream.ui.view Viewer])

(defn create-dependency-graph [issues]
  (let [graph (SingleGraph. "beads-deps")]
    ;; Add nodes
    (doseq [issue issues]
      (let [node (.addNode graph (:id issue))]
        (.setAttribute node "ui.label" (:title issue))
        (.setAttribute node "ui.class" (name (:status issue)))))
    
    ;; Add edges
    (doseq [issue issues
            dep (:dependencies issue)]
      (.addEdge graph 
                (str (:id issue) "-" (:target dep))
                (:id issue)
                (:target dep)
                true)) ;; directed
    
    ;; Apply stylesheet
    (.setAttribute graph "ui.stylesheet" 
                   "node.OPEN { fill-color: green; }
                    node.BLOCKED { fill-color: red; }
                    node.IN_PROGRESS { fill-color: yellow; }
                    edge { fill-color: gray; }")
    
    graph))

(defn show-graph [graph]
  (let [viewer (SwingViewer. graph Viewer/ThreadingModel/GRAPH_IN_ANOTHER_THREAD)]
    (.enableAutoLayout viewer)
    (.addDefaultView viewer)))
```

**Option B: JGraphX** (More control, manual layouts)
```clojure
(import '[com.mxgraph.swing mxGraphComponent]
        '[com.mxgraph.view mxGraph])

(defn create-jgraphx-graph [issues]
  (let [graph (mxGraph.)
        parent (.getDefaultParent graph)]
    (.beginUpdate graph)
    (try
      ;; Add vertices
      (let [vertices (atom {})]
        (doseq [issue issues]
          (swap! vertices assoc (:id issue)
                 (.insertVertex graph parent nil (:title issue)
                                100 100 80 30))))
      
      ;; Add edges
      (doseq [issue issues
              dep (:dependencies issue)]
        (.insertEdge graph parent nil ""
                     (get @vertices (:id issue))
                     (get @vertices (:target dep))))
      
      (finally
        (.endUpdate graph)))
    
    (mxGraphComponent. graph)))
```

---

### Real-Time Update Strategy

```clojure
(ns bd-viewer.graph-monitor
  (:require [clojure.java.shell :as shell]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(defonce *active-agents (atom {}))
(defonce *update-interval 1000) ;; 1 second polling

(defn poll-beads-directory [dir-path]
  "Poll a .beads directory for changes"
  (let [{:keys [out]} (shell/sh "bd" "list" "--json" :dir dir-path)
        issues (json/parse-string out true)]
    {:path dir-path
     :issues issues
     :last-updated (System/currentTimeMillis)}))

(defn watch-directories [dirs]
  "Start watching multiple Beads directories"
  (future
    (loop []
      (doseq [dir dirs]
        (try
          (let [data (poll-beads-directory dir)]
            (swap! *active-agents assoc dir data))
          (catch Exception e
            (println "Error polling" dir ":" (.getMessage e)))))
      
      (Thread/sleep *update-interval)
      (recur))))

(defn get-active-issues []
  "Get issues updated in last 30 seconds across all agents"
  (let [now (System/currentTimeMillis)
        threshold (* 30 1000)] ;; 30 seconds
    (for [[dir data] @*active-agents
          issue (:issues data)
          :let [updated (parse-timestamp (:updated_at issue))]
          :when (< (- now updated) threshold)]
      (assoc issue :agent-dir dir :is-active true))))

;; Start monitoring
(watch-directories [".beads" 
                    "../agent-1/.beads" 
                    "../agent-2/.beads"])
```

---

### UI Integration: Tabbed Interface

```clojure
(ns bd-viewer.ui.tabs
  (:require [seesaw.core :as s]
            [bd-viewer.ui.list-view :as list-view]
            [bd-viewer.ui.graph-view :as graph-view]))

(defn create-main-window []
  (s/frame
    :title "Beads Viewer"
    :content
    (s/tabbed-panel
      :tabs [{:title "📋 List View"
              :content (list-view/create-list-panel)}
             
             {:title "🕸️  Dependency Graph"
              :content (graph-view/create-force-directed-graph)}
             
             {:title "🌐 Epic Radial"
              :content (graph-view/create-radial-layout)}
             
             {:title "🚦 Kanban + Deps"
              :content (graph-view/create-swimlane-view)}
             
             {:title "⚡ Live Agent Monitor"
              :content (graph-view/create-multi-agent-view)}])))
```

---

## Recommended First Implementation

**Start with**: Force-Directed Dependency Graph + Live Updates

### Why?
1. **Shows relationships clearly** - blocks, parent-child, related
2. **Interactive** - click to expand, hover for details
3. **Visual status** - color-coded by state
4. **Easy to extend** - add agent monitoring later

### MVP Features:
- [ ] Load issues from `bd list --json`
- [ ] Parse dependencies (blocks, parent-child, related)
- [ ] Render with GraphStream force-directed layout
- [ ] Color nodes by status (green/yellow/red/gray)
- [ ] Shape nodes by type (epic=hexagon, task=circle, bug=diamond)
- [ ] Click node → show detail panel
- [ ] Filter: show only blocks edges, toggle related/parent-child
- [ ] Auto-refresh every 5 seconds
- [ ] Highlight "ready" nodes (no blockers)

### Phase 2 Enhancements:
- [ ] Multi-agent monitoring (watch multiple .beads dirs)
- [ ] Pulsing animation for recently updated nodes
- [ ] Critical path highlighting (longest estimated path)
- [ ] Cycle detection warning banner
- [ ] Export graph as PNG/SVG

---

## ASCII Art: Full Multi-Agent Dashboard

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Beads Multi-Agent Dashboard                                    [◐ LIVE] │
├────────────┬─────────────────────────────────────────────────────────────┤
│  AGENTS    │  DEPENDENCY GRAPH                                           │
├────────────┤                                                             │
│            │              ┌──────────┐                                   │
│ Agent-1    │              │ Epic-001 │                                   │
│ ⚡ ACTIVE  │              │  Auth    │                                   │
│ Task a-42  │              └────┬─────┘                                   │
│            │                   │                                         │
│ Agent-2    │         ┌─────────┴─────────┐                              │
│ 💤 IDLE    │         │                   │                              │
│ 2m ago     │    ┌────▼────┐         ┌────▼────┐                         │
│            │    │Task .1  │─blocks─▶│Task .2  │                         │
│ Agent-3    │  ⚡│Login UI │         │Database │ 🔴                       │
│ ⚡ ACTIVE  │    │[PROGRESS│         │[BLOCKED]│                         │
│ Task c-15  │    └─────────┘         └─────────┘                         │
│            │                                                             │
├────────────┤                                                             │
│  STATS     │    ┌─────────┐         ┌─────────┐                         │
├────────────┤    │Task .3  │         │Task .4  │                         │
│ Ready: 2   │  🟢│Docs     │         │Deploy   │ 🟢                      │
│ Active: 2  │    │[OPEN]   │         │[OPEN]   │                         │
│ Blocked: 1 │    └─────────┘         └─────────┘                         │
│ Closed: 15 │                                                             │
│            │                                                             │
├────────────┤                                                             │
│ FILTERS    │  Legend:                                                   │
├────────────┤  🟢 READY  🟡 ACTIVE  🔴 BLOCKED  ⚫ CLOSED               │
│ [✓] Blocks │  ──▶ blocks   ─ ─▶ related   ══▶ parent-child             │
│ [✓] Parent │  ⚡ Updated <30s ago                                       │
│ [ ] Related│                                                             │
│            │                                                             │
│ [Refresh]  │  [Expand All] [Collapse] [Export PNG] [Critical Path]     │
└────────────┴─────────────────────────────────────────────────────────────┘
```

---

## Next Steps

1. **Choose visualization approach** - Recommend force-directed + agent monitor
2. **Integrate GraphStream** - Add dependencies to project.clj
3. **Build graph data model** - Parse `bd list --json` into nodes/edges
4. **Create graph panel** - Embed GraphStream viewer in Swing panel
5. **Add interactivity** - Click handlers, filters, tooltips
6. **Implement auto-refresh** - Poll .beads directories every 1-5 seconds
7. **Add agent monitoring** - Watch multiple directories, show active work
8. **Polish UI** - Colors, animations, critical path highlighting

---

## Questions for User

1. **Which visualization style do you prefer?**
   - Force-directed (best for dependencies)
   - Radial (best for epic-centric)
   - Swimlane (best for kanban-style)
   - Timeline (best for critical path)
   - Multi-agent dashboard (best for monitoring)

2. **How many agent directories will you monitor?**
   - Just one project?
   - Multiple projects (2-5)?
   - Many projects (5+)?

3. **Refresh rate preference?**
   - Real-time (1s polling, heavy)
   - Frequent (5s polling, balanced)
   - Occasional (30s polling, light)
   - Manual refresh only

4. **Priority features?**
   - Dependency visualization (blocks chains)
   - Epic/hierarchy view
   - Agent activity monitoring
   - Critical path analysis
   - Cycle detection

---

**Let's build the graph view! Which approach should we start with?** 🚀
