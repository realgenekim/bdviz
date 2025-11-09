# SVG Rendering Approaches - Study from simulator-couch-paint

## How simulator-couch-paint Renders SVG

### Architecture Overview

**TL;DR:** They use **GraalVM JavaScript + Vega + Skia** instead of Batik!

```
Clojure Data → Vega Spec (JSON) → GraalVM JS (Vega.js) → SVG String → Skia Renderer → Display
```

### Key Components

#### 1. GraalVM JavaScript Engine (`my_darkstar.clj`)

**What it does:**
- Embeds a full JavaScript engine in the JVM
- Loads Vega.js and Vega-Lite.js libraries
- Executes JavaScript to render data visualizations

**Code Pattern:**
```clojure
(def engine
  (let [engine (.getEngineByName (javax.script.ScriptEngineManager.) "graal.js")
        bindings (.getBindings engine javax.script.ScriptContext/ENGINE_SCOPE)]
    (.put bindings "polyglot.js.allowAllAccess" true)
    (.eval engine (slurp (clojure.java.io/resource "vega.js")))
    (.eval engine (slurp (clojure.java.io/resource "vega-lite.js")))
    engine))
```

**Brilliant Insight:** They create Clojure functions that call JavaScript functions!

```clojure
(defn make-js-fn [js-text]
  (let [^java.util.function.Function f (.eval engine js-text)]
    (fn [& args] (.apply f (to-array args)))))

;; Now you can call JavaScript from Clojure!
(def vega-spec->svg
  (make-js-fn "function(spec) {
    return new vega.View(vega.parse(JSON.parse(spec)),
                         {renderer:'svg'})
           .finalize()
           .toSVG(1.0);
  }"))
```

#### 2. Vega/Vega-Lite (`vega.clj`)

**What it is:**
- Declarative grammar for data visualization
- Like GraphViz but for data charts/plots
- Outputs SVG

**Example Vega-Lite Spec:**
```clojure
{:data {:values [{:x 1 :y 2} {:x 2 :y 4}]}
 :mark "line"
 :encoding {:x {:field "x" :type "quantitative"}
            :y {:field "y" :type "quantitative"}}}
```

#### 3. Skia Rendering (`gk_membrane_ui.clj`)

**What it does:**
- Skia is Google's 2D graphics library (used in Chrome, Android)
- Membrane provides Clojure wrapper
- Renders SVG strings directly

**Code Pattern:**
```clojure
(defui furniture-vega-graph
  [{:keys [frame sim-state]}]
  (let [svg (->> sim-state
                 prepare-data
                 create-vega-spec
                 vega>svg)]
    (skia/svg svg)))  ;; <- Skia renders the SVG string!
```

---

## Comparison: Batik vs GraalVM+Skia

| Aspect | Batik (bd-viewer) | GraalVM+Skia (simulator) |
|--------|-------------------|--------------------------|
| **SVG Source** | Mermaid.ink (internet) | Generated locally |
| **JavaScript** | None | Full V8 engine embedded |
| **Rendering** | Java2D / JSVGCanvas | Skia (native graphics) |
| **Interactivity** | Zoom/pan via Batik | Custom via Membrane UI |
| **Dependencies** | ~10MB (Batik JARs) | ~50MB (GraalVM + Skia) |
| **Speed** | 2-3 seconds (network) | ~200ms (local) |
| **Offline** | No (needs mermaid.ink) | Yes (all local) |
| **Flexibility** | Limited to Mermaid | Any JS charting lib! |

---

## 🚀 EXCITING NEW IDEAS THIS UNLOCKS!

### 1. **Run ANY JavaScript Visualization Library**

You could use:
- **D3.js** - Most powerful visualization library
- **Cytoscape.js** - Advanced graph layouts
- **Vis.js** - Network diagrams
- **ECharts** - Beautiful Chinese charting library
- **Plotly.js** - Interactive plots

**Example for bd-viewer:**
```clojure
;; Use Cytoscape.js for graph layout
(def cytoscape-render
  (make-js-fn "function(nodes, edges) {
    var cy = cytoscape({
      elements: { nodes: nodes, edges: edges },
      layout: { name: 'dagre' }  // Directed acyclic graph layout
    });
    return cy.svg({full: true});
  }"))
```

### 2. **Dynamic Graph Layouts**

Instead of static Mermaid, use force-directed layouts:
- **D3 Force** - Physics-based node positioning
- **Dagre** - Hierarchical layouts for DAGs
- **CoSE** - Compound Spring Embedder

**Why this is cool:** Automatically arranges nodes to minimize edge crossings!

### 3. **Interactive Vega Charts**

Use Vega for **dependency metrics**:
- Issue burndown charts
- Dependency complexity over time
- Priority distribution pie charts
- Status timelines

**Example:**
```clojure
{:data {:values (map #(hash-map :priority (:priority %)
                                :count 1)
                     issues)}
 :mark "bar"
 :encoding {:x {:field "priority" :type "ordinal"}
            :y {:aggregate "count" :type "quantitative"}}}
```

### 4. **Client-Side Rendering (No Server)**

GraalVM lets you run JavaScript *in the JVM*:
- No Node.js required
- No external processes
- Everything happens in-process
- Can access Clojure data structures directly

### 5. **Unified Charting System**

Create a **universal chart renderer** for bd-viewer:
```clojure
(defn render-chart [spec-type data]
  (case spec-type
    :mermaid (mermaid->svg data)
    :vega (vega->svg data)
    :d3 (d3->svg data)
    :cytoscape (cytoscape->svg data)))
```

### 6. **Animated Graphs**

Vega supports animations! You could show:
- Issues moving through states (open → in-progress → closed)
- Dependencies being added over time
- Priority changes visualized as color transitions

---

## Should bd-viewer Use This Approach?

### Pros:
✅ **100% offline** - no mermaid.ink dependency
✅ **Much faster** - local rendering in ~200ms vs 2-3 sec
✅ **More flexible** - can use ANY JavaScript viz library
✅ **Better layouts** - force-directed, hierarchical, etc.
✅ **Vega for analytics** - burndown charts, metrics, dashboards

### Cons:
❌ **Heavyweight** - GraalVM + Skia = ~50MB+ dependencies
❌ **Complexity** - More moving parts
❌ **Membrane** - simulator uses Membrane UI (not Swing)
  - Would need to adapt Skia rendering to Swing
  - OR: Keep using Batik but feed it GraalVM-generated SVG!

---

## Hybrid Approach: Best of Both Worlds 💡

**Idea:** Use GraalVM to generate SVG locally, then render with Batik!

```clojure
;; 1. Use GraalVM + D3.js to generate SVG (fast, offline)
(def d3-layout
  (make-js-fn "function(nodes, edges) {
    // Use D3 force simulation for layout
    var simulation = d3.forceSimulation(nodes)
      .force('link', d3.forceLink(edges))
      .force('charge', d3.forceManyBody())
      .tick(100);  // Run 100 iterations

    // Generate SVG from positioned nodes
    return generateSVG(simulation.nodes(), edges);
  }"))

;; 2. Feed SVG to existing Batik renderer
(defn create-d3-graph-panel [issues deps]
  (let [svg-string (d3-layout (issues->nodes issues)
                              (deps->edges deps))
        canvas (create-svg-canvas svg-string)]  ;; Existing Batik code!
    canvas))
```

**Benefits:**
- ✅ Local SVG generation (no mermaid.ink)
- ✅ Use existing Batik rendering code
- ✅ Keep Swing (no need for Membrane)
- ✅ Access to all JavaScript viz libraries
- ✅ Still zoomable/pannable via Batik

---

## Recommendation for bd-viewer

**Phase 1 (Current):** Use Batik + mermaid.ink (already done ✅)

**Phase 2 (Next):** Add GraalVM + keep Batik rendering
- Embed GraalVM JavaScript
- Use D3.js for force-directed layout
- Generate SVG locally (offline!)
- Render with existing Batik code

**Phase 3 (Future):** Add Vega for analytics
- Burndown charts
- Dependency metrics
- Priority distributions
- Timeline views

---

## Code Example: D3 Force Layout in bd-viewer

```clojure
(ns bd-viewer.ui.graph-tab-d3
  (:require [bd-viewer.ui.graph-tab-svg :as svg]))

;; Initialize GraalVM with D3.js
(def engine
  (let [engine (.getEngineByName (javax.script.ScriptEngineManager.) "graal.js")]
    (.put (.getBindings engine javax.script.ScriptContext/ENGINE_SCOPE)
          "polyglot.js.allowAllAccess" true)
    (.eval engine (slurp (io/resource "d3.v7.min.js")))
    engine))

;; Create D3 force layout function
(def d3-force-layout
  (make-js-fn "function(nodes, edges) {
    var simulation = d3.forceSimulation(nodes)
      .force('link', d3.forceLink(edges).distance(100))
      .force('charge', d3.forceManyBody().strength(-300))
      .force('center', d3.forceCenter(400, 300))
      .tick(100);

    // Convert to SVG
    var svg = '<svg width=\"800\" height=\"600\">';
    edges.forEach(e => {
      svg += `<line x1=\"${e.source.x}\" y1=\"${e.source.y}\"
                    x2=\"${e.target.x}\" y2=\"${e.target.y}\"
                    stroke=\"#999\" stroke-width=\"2\"/>`;
    });
    nodes.forEach(n => {
      svg += `<circle cx=\"${n.x}\" cy=\"${n.y}\" r=\"20\" fill=\"#69b3a2\"/>`;
      svg += `<text x=\"${n.x}\" y=\"${n.y}\" text-anchor=\"middle\">${n.id}</text>`;
    });
    svg += '</svg>';
    return svg;
  }"))

;; Use with existing Batik rendering!
(defn create-graph-panel []
  (let [nodes (issues->d3-nodes issues)
        edges (deps->d3-edges deps)
        svg-string (d3-force-layout nodes edges)
        canvas (svg/create-svg-canvas svg-string)]  ;; Reuse Batik!
    canvas))
```

---

## Summary

The simulator-couch-paint approach is **brilliant** because:
1. Full JavaScript ecosystem available (D3, Vega, etc.)
2. Local rendering (fast, offline)
3. Declarative specs (Vega-Lite) are very clean
4. Can generate complex visualizations with minimal code

For bd-viewer, the **hybrid approach** (GraalVM generates SVG → Batik renders) gives us:
- Best of both worlds
- Minimal changes to existing code
- Unlocks JavaScript visualization libraries
- Stays in Swing (no Membrane dependency)

**This is HUGE!** 🚀
