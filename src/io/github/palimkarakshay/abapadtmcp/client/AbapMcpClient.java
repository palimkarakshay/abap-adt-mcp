package io.github.palimkarakshay.abapadtmcp.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The Java &harr; abap-mcp bridge. Owns the entire interaction with the abap-mcp
 * CLI: write the editor's source to a temp abapGit-named file, shell out to
 * {@code node <abap-mcp>/dist/cli.js lint <tmp> --json}, capture stdout, and parse
 * it into {@link AbapMcpResult}.
 *
 * <p><b>Deliberately Eclipse-free.</b> No SWT, no OSGi, no Eclipse runtime imports.
 * That is what lets the load-bearing logic (process plumbing + JSON parsing) be
 * compiled and unit-tested on a bare JRE, and lets the Eclipse handlers stay thin.
 *
 * <p>abapGit naming invariant: abap-mcp's engine infers the object type from the
 * file name and <i>skips</i> files that don't match {@code *.clas.abap} /
 * {@code *.prog.abap} / etc. This client always writes a {@code *.clas.abap} (or a
 * caller-supplied) suffix so the source is actually analyzed.
 */
public final class AbapMcpClient {

    /** Configuration for locating Node and abap-mcp; injected so it's testable. */
    public static final class Config {
        /** Path to the {@code node} executable. Default {@code "node"} (rely on PATH). */
        public String nodePath = "node";
        /** Absolute path to abap-mcp's CLI entry, i.e. {@code <abap-mcp>/dist/cli.js}. */
        public String cliPath;
        /** {@code v758} (on-prem baseline) or {@code Cloud}. Null = CLI default. */
        public String abapVersion;
        /** {@code syntax-only} | {@code style} | {@code full}. Null = CLI default. */
        public String preset;
        /** Hard timeout for the CLI process. */
        public long timeoutSeconds = 60;

        public Config(String cliPath) {
            this.cliPath = cliPath;
        }
    }

    private final Config config;

    public AbapMcpClient(Config config) {
        if (config == null || config.cliPath == null || config.cliPath.isEmpty()) {
            throw new IllegalArgumentException("AbapMcpClient requires a Config with cliPath set");
        }
        this.config = config;
    }

    /**
     * Lint a snippet of ABAP source.
     *
     * @param source       the raw ABAP source text (e.g. from the active editor)
     * @param abapGitName  abapGit-style file name, e.g. {@code zcl_demo.clas.abap}.
     *                     If null/blank, {@code zsource.clas.abap} is used.
     * @return parsed findings + exit code
     * @throws AbapMcpException on process failure, timeout, or unparseable output
     */
    public AbapMcpResult lint(String source, String abapGitName) throws AbapMcpException {
        String name = (abapGitName == null || abapGitName.isBlank())
                ? "zsource.clas.abap" : sanitize(abapGitName);

        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("abap-adt-mcp-");
            Path tmpFile = tmpDir.resolve(name);
            Files.writeString(tmpFile, source == null ? "" : source, StandardCharsets.UTF_8);

            List<String> cmd = new ArrayList<>();
            cmd.add(config.nodePath);
            cmd.add(config.cliPath);
            cmd.add("lint");
            cmd.add(tmpFile.toString());
            if (config.abapVersion != null && !config.abapVersion.isBlank()) {
                cmd.add("--abap-version");
                cmd.add(config.abapVersion);
            }
            if (config.preset != null && !config.preset.isBlank()) {
                cmd.add("--preset");
                cmd.add(config.preset);
            }
            cmd.add("--json");

            ProcessExecution exec = run(cmd);
            return parseLint(exec, tmpFile.getFileName().toString());
        } catch (IOException e) {
            throw new AbapMcpException("Failed to run abap-mcp CLI: " + e.getMessage(), e);
        } finally {
            if (tmpDir != null) {
                deleteQuietly(tmpDir);
            }
        }
    }

    // ---- internals (package-visible bits kept minimal) ----

    /** Captured stdout/stderr/exit of one process run. */
    static final class ProcessExecution {
        final int exitCode;
        final String stdout;
        final String stderr;
        ProcessExecution(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private ProcessExecution run(List<String> cmd) throws AbapMcpException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new AbapMcpException(
                    "Could not start process. Is Node installed and is the abap-mcp path correct? "
                            + "(node='" + config.nodePath + "', cli='" + config.cliPath + "')", e);
        }

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread tErr = pump(proc.getErrorStream(), err);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new AbapMcpException("Failed reading abap-mcp output: " + e.getMessage(), e);
        }

        boolean finished;
        try {
            finished = proc.waitFor(config.timeoutSeconds, TimeUnit.SECONDS);
            tErr.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            throw new AbapMcpException("Interrupted while waiting for abap-mcp", e);
        }
        if (!finished) {
            proc.destroyForcibly();
            throw new AbapMcpException("abap-mcp timed out after " + config.timeoutSeconds + "s");
        }
        return new ProcessExecution(proc.exitValue(), out.toString(), err.toString());
    }

    private static Thread pump(java.io.InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sink.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // best-effort capture of stderr
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Parse abap-mcp lint stdout into a result; package-visible for unit tests. */
    static AbapMcpResult parseLint(ProcessExecution exec, String tmpFileName) throws AbapMcpException {
        // Exit code 2 = usage error: the CLI printed a human message, not JSON.
        if (exec.exitCode == 2) {
            String msg = exec.stderr.isBlank() ? exec.stdout : exec.stderr;
            throw new AbapMcpException("abap-mcp usage error: " + msg.trim());
        }
        String json = exec.stdout.trim();
        if (json.isEmpty()) {
            throw new AbapMcpException("abap-mcp produced no output (exit " + exec.exitCode
                    + "). stderr: " + exec.stderr.trim());
        }
        Map<String, Object> root;
        try {
            root = MiniJson.asObject(MiniJson.parse(json));
        } catch (RuntimeException e) {
            throw new AbapMcpException("Could not parse abap-mcp JSON: " + e.getMessage()
                    + "\n--- raw ---\n" + json, e);
        }

        int files = MiniJson.intOr(root, "files", 0);
        List<AbapMcpFinding> findings = new ArrayList<>();
        for (Object o : MiniJson.asArray(root.get("findings"))) {
            Map<String, Object> f = MiniJson.asObject(o);
            findings.add(new AbapMcpFinding(
                    MiniJson.str(f, "rule"),
                    MiniJson.str(f, "message"),
                    MiniJson.str(f, "severity"),
                    MiniJson.str(f, "file"),
                    MiniJson.intOr(f, "line", 1),
                    MiniJson.intOr(f, "column", 1),
                    MiniJson.str(f, "excerpt"),
                    MiniJson.str(f, "docsUrl")));
        }
        return new AbapMcpResult(files, findings, exec.exitCode);
    }

    /** Keep only a safe file name; preserve abapGit double-extension. */
    private static String sanitize(String name) {
        String base = name.replaceAll("[\\\\/]+", "_").trim();
        if (!base.toLowerCase().endsWith(".abap")) {
            base = base + ".clas.abap";
        }
        return base;
    }

    private static void deleteQuietly(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
        } catch (IOException ignored) {
            // temp cleanup is best-effort
        }
    }
}
