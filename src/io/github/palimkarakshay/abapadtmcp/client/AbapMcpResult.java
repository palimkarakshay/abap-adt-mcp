package io.github.palimkarakshay.abapadtmcp.client;

import java.util.Collections;
import java.util.List;

/**
 * Parsed result of an abap-mcp {@code lint --json} invocation.
 *
 * <p>Carries the structured findings plus the raw exit code so callers can tell
 * apart "ran clean" (0), "had findings" (1) and "usage error" (2) — the contract
 * documented by the abap-mcp CLI.
 */
public final class AbapMcpResult {

    /** Number of files abap-mcp analyzed. */
    public final int files;
    /** The findings (never {@code null}; empty when clean). */
    public final List<AbapMcpFinding> findings;
    /** Raw process exit code: 0 ok, 1 findings, 2 usage error. */
    public final int exitCode;

    public AbapMcpResult(int files, List<AbapMcpFinding> findings, int exitCode) {
        this.files = files;
        this.findings = findings == null ? Collections.emptyList()
                : Collections.unmodifiableList(findings);
        this.exitCode = exitCode;
    }

    /** True when abap-mcp reported a usage error (exit code 2). */
    public boolean isUsageError() {
        return exitCode == 2;
    }

    public long errorCount() {
        return findings.stream().filter(AbapMcpFinding::isError).count();
    }

    public long warningCount() {
        return findings.stream().filter(AbapMcpFinding::isWarning).count();
    }
}
