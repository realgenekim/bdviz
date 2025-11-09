# Mermaid Rendering Options for bd-viewer

## Current Implementation: mermaid.ink API

**How it works:**
- Generate Mermaid syntax from issues/deps
- Base64 encode and send to `https://mermaid.ink/img/{base64}`
- Fetch rendered PNG image
- Display in Swing JLabel

**Pros:**
- ✅ Zero setup - works immediately
- ✅ No dependencies
- ✅ Perfect rendering quality
- ✅ Simple code (60 lines)

**Cons:**
- ❌ Requires internet connection
- ❌ Likely has rate limits (unknown)
- ❌ Privacy concern (sends diagrams externally)
- ❌ Could go down or become slow
- ❌ No offline capability

---

## Option 1: mermaid-cli (mmdc) ⭐ RECOMMENDED FOR PRODUCTION

**Installation:**
```bash
npm install -g @mermaid-js/mermaid-cli
```

**How it works:**
- Official CLI tool from Mermaid team
- Uses Puppeteer (headless Chrome) to render locally
- Shell out from Clojure to generate PNG/SVG

**Code:**
```clojure
(defn render-with-mmdc [mermaid-str output-file]
  (spit "temp.mmd" mermaid-str)
  (let [result (shell/sh "mmdc" "-i" "temp.mmd" "-o" output-file 
                         "-b" "transparent" "-w" 2000 "-H" 1500)]
    (when (zero? (:exit result))
      (ImageIO/read (io/file output-file)))))
```

**Pros:**
- ✅ Official tool, well-maintained
- ✅ Same quality as mermaid.ink
- ✅ Local, no rate limits
- ✅ No internet required
- ✅ Supports PNG and SVG
- ✅ Can customize background, theme, size

**Cons:**
- ⚠️ Requires Node.js + npm installed
- ⚠️ Puppeteer startup overhead (~1-2 seconds)
- ⚠️ Heavier than just fetching image

**Complexity:** LOW - just shell command  
**Setup time:** 5 minutes  
**Best for:** Production deployments, offline use

---

## Option 2: SVG with Apache Batik

**Approach:**
- Fetch SVG from `https://mermaid.ink/svg/{base64}` instead of PNG
- Use Apache Batik to render SVG in Java
- Can cache SVG as text (smaller than PNG)

**Dependencies (deps.edn):**
```clojure
{org.apache.xmlgraphics/batik-transcoder {:mvn/version "1.17"}
 org.apache.xmlgraphics/batik-codec {:mvn/version "1.17"}}
```

**Code:**
```clojure
(import '[org.apache.batik.transcoder.image PNGTranscoder]
        '[org.apache.batik.transcoder TranscoderInput TranscoderOutput])

(defn svg->buffered-image [svg-string]
  (let [transcoder (PNGTranscoder.)
        input (TranscoderInput. (StringReader. svg-string))
        output-stream (ByteArrayOutputStream.)
        output (TranscoderOutput. output-stream)]
    (.transcode transcoder input output)
    (ImageIO/read (ByteArrayInputStream. (.toByteArray output-stream)))))
```

**Pros:**
- ✅ Pure JVM, no external processes
- ✅ SVG is scalable (zoom without pixelation)
- ✅ Can cache SVG as text (smaller files)
- ✅ Batik is mature and stable

**Cons:**
- ❌ Still requires mermaid.ink for initial render
- ⚠️ Batik is heavy (multiple dependencies)
- ⚠️ More complex than PNG

**Complexity:** MEDIUM  
**Best for:** When you want vector graphics and scaling

---

## Option 3: Smart Cache + Fallback ⭐ PRAGMATIC CHOICE

**How it works:**
1. Hash Mermaid content to create cache key
2. Check cache first (`cache/mermaid-{hash}.png`)
3. If miss, try mermaid.ink
4. If mermaid.ink fails, fall back to mmdc (if available)
5. Cache result for next time

**Code:**
```clojure
(defn cache-key [mermaid-str]
  (str "cache/mermaid-" (hash mermaid-str) ".png"))

(defn fetch-diagram-cached [mermaid-str]
  (let [cache-file (cache-key mermaid-str)]
    (if (.exists (io/file cache-file))
      ;; Cache hit - instant load!
      (do
        (log/info :cache-hit cache-file)
        (ImageIO/read (io/file cache-file)))
      ;; Cache miss - try online first
      (or (when-let [img (try-mermaid-ink mermaid-str)]
            (ImageIO/write img "png" (io/file cache-file))
            img)
          ;; Fall back to mmdc if installed
          (when (mmdc-installed?)
            (render-with-mmdc mermaid-str cache-file))))))
```

**Pros:**
- ✅ Fast after first render (uses cache)
- ✅ Works offline after first load
- ✅ Gracefully handles rate limits
- ✅ Falls back to local rendering if needed
- ✅ Best of both worlds

**Cons:**
- ⚠️ More code paths to maintain
- ⚠️ Requires disk space for cache

**Complexity:** MEDIUM  
**Best for:** Real-world usage with reliability

---

## Option 4: Playwright Node.js Script

**Custom render script (render-mermaid.js):**
```javascript
const puppeteer = require('puppeteer');
const fs = require('fs');

(async () => {
  const mermaid = fs.readFileSync(process.argv[2], 'utf8');
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  await page.setContent(`
    <html><body>
    <script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>
    <div class="mermaid">${mermaid}</div>
    <script>mermaid.initialize({startOnLoad:true});</script>
    </body></html>
  `);
  await page.waitForTimeout(1000);
  await page.screenshot({ path: process.argv[3], fullPage: true });
  await browser.close();
})();
```

**Pros:**
- ✅ Full control over rendering
- ✅ Can customize everything
- ✅ Uses real browser (perfect rendering)

**Cons:**
- ❌ Requires Node.js
- ❌ Slow (browser startup)
- ❌ More complex than mmdc

**Complexity:** MEDIUM-HIGH  
**Best for:** When you need custom rendering options

---

## Option 5: JavaFX WebView (Interactive)

**How it works:**
- Start local HTTP server serving Mermaid HTML
- Embed JavaFX WebView in Swing (JFXPanel)
- Fully interactive diagram (zoom, pan, click)

**Code outline:**
```clojure
(require '[org.httpkit.server :as http])

(defn start-mermaid-server [mermaid-str]
  (http/run-server 
    (fn [req]
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (str "<html><body>"
                 "<script src='https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js'></script>"
                 "<div class='mermaid'>" mermaid-str "</div>"
                 "<script>mermaid.initialize({startOnLoad:true});</script>"
                 "</body></html>")})
    {:port 9999}))

;; In Swing:
(import '[javafx.embed.swing JFXPanel]
        '[javafx.scene.web WebView])

(doto (JFXPanel.)
  (.setScene (Scene. (WebView.)))
  (.load "http://localhost:9999"))
```

**Pros:**
- ✅ Fully interactive diagrams
- ✅ Can update without restart
- ✅ Professional look

**Cons:**
- ❌ Requires JavaFX (mixing with Swing)
- ❌ More complex
- ❌ Memory overhead

**Complexity:** HIGH  
**Best for:** When you want interactive diagrams

---

## 🚫 DON'T DO: GraalVM JavaScript + Mermaid.js

**Why not:**
- Mermaid.js expects full DOM environment
- Would need to mock browser APIs (jsdom-like)
- Extremely fragile
- Breaks with Mermaid updates
- NOT worth the complexity

**Verdict:** Avoid this approach

---

## Rate Limit Mitigation (Current mermaid.ink)

Even without local rendering, we can reduce API calls:

### 1. Content-based Caching
```clojure
;; Only fetch if diagram content changes
(def diagram-cache (atom {}))

(defn fetch-if-changed [mermaid-str]
  (let [content-hash (hash mermaid-str)]
    (or (@diagram-cache content-hash)
        (when-let [img (fetch-from-mermaid-ink mermaid-str)]
          (swap! diagram-cache assoc content-hash img)
          img))))
```

### 2. State-based Regeneration
```clojure
;; Only regenerate when issues actually change
(add-watch db/*app-state :diagram-cache
  (fn [_ _ old-state new-state]
    (when (not= (:issues old-state) (:issues new-state))
      (invalidate-diagram-cache!))))
```

### 3. Lazy Loading
```clojure
;; Only fetch when graph tab is opened (already implemented!)
```

### 4. Debouncing
```clojure
;; If we add live updates, debounce diagram generation
(defn debounce-diagram-update [f delay-ms]
  (let [timer (atom nil)]
    (fn [& args]
      (when @timer (.cancel @timer))
      (reset! timer (Timer. delay-ms 
                            (fn [] (apply f args)))))))
```

---

## RECOMMENDATION for bd-viewer

### Immediate (Now):
✅ **Keep current mermaid.ink + add caching**
- Hash-based cache in `./cache/` directory
- Cache hit = instant load
- Reduces API calls by 90%+
- 10 lines of code

### Short-term (Optional):
⭐ **Add mmdc as fallback**
- Document: "Optional: install mmdc for offline use"
- Auto-detect if mmdc is available
- Fall back gracefully if mermaid.ink fails
- Provides offline capability

### Long-term (Future):
🚀 **Consider JavaFX WebView for interactivity**
- Click nodes to jump to issues
- Zoom and pan
- Live updates
- Professional graph viewer

---

## Implementation Priority

1. **Phase 1** (5 min): Add file-based caching to current implementation
2. **Phase 2** (15 min): Add mmdc detection and fallback
3. **Phase 3** (optional): Switch to SVG + Batik for scalability
4. **Phase 4** (optional): Interactive WebView for advanced features

---

## Summary Table

| Option | Setup Time | Offline? | Rate Limits? | Complexity | Quality |
|--------|-----------|----------|--------------|------------|---------|
| mermaid.ink (current) | 0 min | ❌ | ⚠️ Yes | LOW | ⭐⭐⭐⭐⭐ |
| + File cache | 5 min | After 1st | ✅ Mitigated | LOW | ⭐⭐⭐⭐⭐ |
| mermaid-cli (mmdc) | 5 min | ✅ Yes | ✅ None | LOW | ⭐⭐⭐⭐⭐ |
| SVG + Batik | 30 min | ❌ | ⚠️ Yes | MEDIUM | ⭐⭐⭐⭐⭐ |
| Cache + mmdc fallback | 20 min | ✅ Yes | ✅ None | MEDIUM | ⭐⭐⭐⭐⭐ |
| JavaFX WebView | 2 hours | ✅ Yes | ✅ None | HIGH | ⭐⭐⭐⭐⭐ |
| Custom Puppeteer | 1 hour | ✅ Yes | ✅ None | HIGH | ⭐⭐⭐⭐⭐ |

**Winner: Cache + mmdc fallback** - Best reliability with minimal complexity
