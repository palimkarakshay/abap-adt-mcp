# abap-adt-mcp

Eclipse **ADT (ABAP Development Tools) plugin** bridging the ADT editor to
[`abap-mcp`](https://github.com/palimkarakshay/abap-mcp) — right-click an ABAP source →
"Lint with abap-mcp" / "Cloud Readiness" → findings as Eclipse problem markers. Offline,
no SAP system. Lumivara product line: **SAP**. **Public MIT** (personal GitHub
`palimkarakshay`, per OSS rule). Status: foundation (bridge verified; Eclipse wiring scaffolded).

## Toolchain: Eclipse PDE + Tycho (Maven), JDK 17 — OSGi bundle, not npm

## Commands (authoritative)
- Bare-JRE bridge verify (the load-bearing test, no Eclipse needed):
  `javac -d out src/com/lumivara/abapadtmcp/client/*.java test/com/lumivara/abapadtmcp/client/BridgeSmokeTest.java`
  then `java -cp out com.lumivara.abapadtmcp.client.BridgeSmokeTest /home/akshay/projects/abap-mcp/dist/cli.js`
- Headless plugin build: `mvn -B clean verify` (needs Maven 3.9+, JDK 17, network to the
  p2 repo in `pom.xml`; Maven is NOT installed on codebox by default).
- Interactive: import into Eclipse, set `abap-adt-mcp.target` active, *Run As → Eclipse Application*.

## Layout
- `src/.../client/` — **Eclipse-free** bridge: `AbapMcpClient` (ProcessBuilder → abap-mcp CLI),
  `MiniJson` (dep-free parser), `AbapMcpResult`/`AbapMcpFinding`, `AbapMcpException`. Exported package.
- `src/.../handlers/` — `AbapMcpLintHandler` (working), `AbapMcpReadinessHandler` (stub),
  `AbapMcpHandlerSupport` (editor source + prefs; the ONLY place ADT coupling would live).
- `src/.../markers/AbapMcpMarkers.java` — findings → Problems view markers.
- `src/.../Activator.java` — bundle activator + preference defaults.
- `test/.../BridgeSmokeTest.java` — runnable `main` proving the bridge end-to-end.
- `META-INF/MANIFEST.MF` · `plugin.xml` · `build.properties` · `pom.xml` · `abap-adt-mcp.target`.

## Deploy: none hosted — ships as an Eclipse plugin (p2 update site / dropin) once built on a real ADT env.

## Gotchas / invariants
- **All real logic stays in `client/` (zero Eclipse imports)** so it's testable on a bare JRE;
  handlers/markers are thin plumbing. Do not pull Eclipse types into `client/`.
- **Decoupled from `com.sap.adt.*` on purpose** — those bundles aren't on a public p2 site; we
  hook the ADT editor via the generic `#TextEditorContext` popup + `ITextEditor`. ADT API usage
  (if ever) goes behind `AbapMcpHandlerSupport` only; it's additive (target + MANIFEST).
- abap-mcp skips files not named abapGit-style — the client always writes `*.clas.abap`/`*.prog.abap`.
- abap-mcp CLI exit codes: 0 ok · 1 findings · 2 usage error (parser treats 2 specially).
- Requires abap-mcp built (`npm run build` in its repo) so `dist/cli.js` exists; path is a preference.
- Marker type id `com.lumivara.abapadtmcp.abapMcpProblem` is shared between plugin.xml and `AbapMcpMarkers`.
