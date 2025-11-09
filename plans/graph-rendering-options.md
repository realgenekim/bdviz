# Graph Rendering Implementation Plan

## Goal
Implement three alternative graph rendering options to replace/supplement the current mermaid.ink approach.

## Three Implementations

### 1. ASCII Tree (`graph_tab_ascii.clj`)
**Approach:** Text-based tree rendering like `git log --graph`

**Features:**
- Use Unicode box-drawing characters: `├──`, `└──`, `│`
- Display in JTextArea with monospace font
- Color-code by status (open=green, in-progress=yellow)
- Zero external dependencies

**Algorithm:**
1. Build dependency tree from issues + deps
2. Traverse tree depth-first
3. Track indentation levels and branches
4. Generate text with proper box-drawing characters
5. Display in scrollable JTextArea

**Benefits:**
- Instant rendering
- No external dependencies
- Works offline
- Easy to copy/paste

---

### 2. Batik SVG (`graph_tab_svg.clj`)
**Approach:** Interactive SVG rendering with Apache Batik

**Two sub-options:**
- **Option A:** Fetch SVG from `mermaid.ink/svg/` (requires internet)
- **Option B:** Generate DOT → GraphViz SVG → Batik (offline, requires `dot` installed)

**Features:**
- Interactive JSVGCanvas (zoom, pan)
- Scalable vector graphics (no pixelation)
- Professional appearance

**Implementation:**
1. Add Batik dependencies to deps.edn
2. Generate or fetch SVG
3. Create JSVGCanvas component
4. Load SVG into canvas
5. Wrap in scroll pane

**Benefits:**
- Zoomable/pannable
- Professional quality
- Scalable graphics

**Trade-offs:**
- Batik is heavyweight (~10MB)
- Requires GraphViz for offline mode

---

### 3. JTextPane Clickable Tree (`graph_tab_jtextpane.clj`)
**Approach:** HTML-rendered tree with clickable issue links

**Features:**
- HTML rendering in JTextPane
- Clickable issue IDs (hyperlinks)
- Unicode tree characters
- Styled with CSS-in-HTML

**Implementation:**
1. Build tree structure
2. Generate HTML with `<a href='#ID'>` links
3. Set up HyperlinkListener to handle clicks
4. Jump to issue when clicked

**Benefits:**
- Interactive (clickable)
- Good for hierarchical data
- No external dependencies
- Native Swing component

---

## Dependencies to Add

```clojure
;; deps.edn
{:deps {org.apache.xmlgraphics/batik-swing {:mvn/version "1.17"}
        org.apache.xmlgraphics/batik-codec {:mvn/version "1.17"}
        org.apache.xmlgraphics/batik-transcoder {:mvn/version "1.17"}}}
```

---

## File Structure

```
src/bd_viewer/ui/
├── graph_tab2.clj         (existing - mermaid.ink PNG)
├── graph_tab_ascii.clj    (new - ASCII tree)
├── graph_tab_svg.clj      (new - Batik SVG)
└── graph_tab_jtextpane.clj (new - Clickable HTML)
```

---

## Common Utilities

All three will share:
- Issue filtering (show open only)
- Dependency graph building
- Tree traversal algorithms

Create `src/bd_viewer/graph_utils.clj`:
- `build-dependency-tree` - Convert flat deps to tree structure
- `filter-open-issues` - Filter issues by status
- `topological-sort` - Order issues for display

---

## Testing Plan

1. Test each implementation independently
2. Verify all compile: `make runtests-once`
3. Compare rendering quality
4. Benchmark performance (ASCII should be fastest)
5. Test with different graph sizes

---

## Future Integration

Once all three are working, create a tabbed interface or dropdown to switch between:
1. Mermaid PNG (current)
2. ASCII Tree (fast, text)
3. SVG Interactive (zoom/pan)
4. Clickable Tree (hierarchical)

User can choose their preferred visualization!

---

## Implementation Order

1. ✅ ASCII tree (simplest, no dependencies)
2. ✅ JTextPane clickable (Swing native, no dependencies)
3. ✅ Batik SVG (most complex, new dependencies)

Start simple, add complexity progressively.

---

## Notes

- All implementations should handle cycles gracefully
- All should display orphaned issues (no deps)
- All should color-code by status
- All should be scrollable
- All should regenerate on Cmd+R
