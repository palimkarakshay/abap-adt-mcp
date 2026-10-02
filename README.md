# abap-adt-mcp

An **Eclipse ADT (ABAP Development Tools) plugin** that brings
[`abap-mcp`](https://github.com/palimkarakshay/abap-mcp)'s offline ABAP analysis
into the ADT editor. Right-click an ABAP source and run **Lint with abap-mcp** or
**Cloud Readiness** — findings show up as Eclipse problem markers, no SAP system or
network required.

> Status: **foundation**. The Java ↔ abap-mcp bridge is complete and verified
> end-to-end on a bare JRE; the Eclipse PDE/Tycho wiring is scaffolded and builds
> against a real Eclipse target platform. See the [Roadmap](#roadmap).

## Architecture

```
  Eclipse ADT ABAP editor
        │  (ITextEditor / IDocument — generic Eclipse text API, NOT ADT internals)
        ▼
  AbapMcpLintHandler / AbapMcpReadinessHandler   ← thin Eclipse glue
        │  active editor source text + abapGit-style name
        ▼
  AbapMcpClient                                   ← the load-bearing bridge (Eclipse-free)
        │  writes a temp <name>.clas.abap, then ProcessBuilder:
        │  node <abap-mcp>/dist/cli.js lint <tmp> --json
        ▼
  abap-mcp CLI (Node)  ──▶  JSON  ──▶  MiniJson (dep-free parser)  ──▶  AbapMcpResult
        │
        ▼
  AbapMcpMarkers   →   Eclipse Problems view + editor gutter
```

**Design principle:** everything that does real work — process plumbing, JSON
parsing, mapping to findings — lives in `io.github.palimkarakshay.abapadtmcp.client`, which has
**zero Eclipse imports**. That package is exported and unit-testable on a plain JRE
(`BridgeSmokeTest`). The Eclipse handlers/markers are replaceable plumbing on top.

### Why the plugin does NOT compile against `com.sap.adt.*`

ADT's API bundles are not published on a public, stable, Tycho-consumable p2 update
site. Rather than couple to undocumented internals, the plugin treats the ADT ABAP
editor as what it is at runtime — an `ITextEditor` over an `IDocument` — and hooks it
through the standard `#TextEditorContext` popup in `plugin.xml`. If a future variant
needs ADT model APIs (e.g. to read the true object name/package), that goes **behind
`AbapMcpHandlerSupport` only**; the change is additive (add the ADT update site to the
target platform + bundles to `MANIFEST.MF`).

## Prerequisites

- **Eclipse** (2024-09 or similar) **with ABAP Development Tools (ADT) installed**.
- **Node.js ≥ 20** on `PATH` (or set an explicit path in preferences).
- **abap-mcp** built locally: clone `palimkarakshay/abap-mcp`, run
  `npm install && npm run build`, note the path to `dist/cli.js`.

## Build

### Option A — Eclipse PDE (interactive, recommended for development)

1. Import the project: *File → Import → Existing Projects into Workspace*.
2. Set the target platform: open `abap-adt-mcp.target`, click *Set as Active Target
   Platform* (resolves Eclipse Platform bundles from the p2 repo).
3. Launch a runtime Eclipse: *Run → Run As → Eclipse Application*. ADT must be in
   that runtime for the editor menu to appear on ABAP sources.

### Option B — Tycho (headless / CI)

```bash
mvn -B clean verify
```

Requires Maven 3.9+, JDK 17, and network access to the p2 repo declared in `pom.xml`
(`https://download.eclipse.org/releases/2024-09/`), or a local mirror. Produces an
OSGi bundle jar under `target/`. Override the release with `-Declipse.release=2025-03`.

## Verify the bridge without Eclipse (load-bearing path)

The half that can fail silently — Java shelling out to abap-mcp and parsing its
JSON — is provable on a bare JRE:

```bash
# from the project root
javac -d out src/io/github/palimkarakshay/abapadtmcp/client/*.java \
             test/io/github/palimkarakshay/abapadtmcp/client/BridgeSmokeTest.java

java -cp out io.github.palimkarakshay.abapadtmcp.client.BridgeSmokeTest \
     /home/akshay/projects/abap-mcp/dist/cli.js
```

Expected: a parser self-check `PASS`, then the parsed findings for a deliberately
dirty class, ending in `PASS: bridge parsed N findings end-to-end.`

## Configuration

Handlers read these instance preferences (a Preferences page is on the roadmap):

| key                  | meaning                                   | default |
|----------------------|-------------------------------------------|---------|
| `abapMcp.cliPath`    | path to `abap-mcp/dist/cli.js` (required) | *(empty)* |
| `abapMcp.nodePath`   | path to `node`                            | `node`  |
| `abapMcp.preset`     | `syntax-only` \| `style` \| `full`        | `full`  |
| `abapMcp.abapVersion`| `v758` \| `Cloud`                         | `Cloud` |

## Roadmap

1. **Preferences page** — UI for the four settings above (currently instance prefs).
2. **Marker-based diagnostics polish** — quick-fix to open the `docsUrl`; column-accurate
   `CHAR_START`/`CHAR_END` from the document offset (not just line markers).
3. **Cloud Readiness view** — finish `AbapMcpReadinessHandler`: call
   `readiness --json`, render `verdict` / `score` / `cloudBlockerCount` /
   `releasedApiFindings` in a dedicated view.
4. **ADT model integration (optional)** — use real object name/package via ADT APIs,
   behind `AbapMcpHandlerSupport`, when a stable target platform is available.
5. **Lint-on-save / incremental builder** — `IncrementalProjectBuilder` so findings
   refresh automatically.
6. **Scaffold + outline commands** — surface `scaffold_rap_bo` and `get_abap_outline`.

## License

MIT © Akshay Palimkar (palimkarakshay). See [LICENSE](LICENSE).
abap-mcp is a separate MIT project; this plugin shells out to its CLI.
