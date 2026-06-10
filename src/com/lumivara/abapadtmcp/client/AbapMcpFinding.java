package com.lumivara.abapadtmcp.client;

/**
 * One lint finding returned by abap-mcp's CLI ({@code lint --json}).
 *
 * <p>Mirrors the JSON object shape emitted under {@code findings[]}:
 * {@code { rule, message, severity, file, line, column, excerpt, docsUrl }}.
 * Plain POJO so it can be used and unit-tested without any Eclipse classes.
 */
public final class AbapMcpFinding {

    /** abaplint rule id, e.g. {@code select_performance}. */
    public final String rule;
    /** Human-readable finding message. */
    public final String message;
    /** abaplint severity: {@code Error} | {@code Warning} | {@code Info}. */
    public final String severity;
    /** Source file name (abapGit-style, e.g. {@code zcl_demo.clas.abap}). */
    public final String file;
    /** 1-based line number. */
    public final int line;
    /** 1-based column number. */
    public final int column;
    /** The offending source line, if provided. May be {@code null}. */
    public final String excerpt;
    /** Link to the rule's documentation, if provided. May be {@code null}. */
    public final String docsUrl;

    public AbapMcpFinding(String rule, String message, String severity, String file,
                          int line, int column, String excerpt, String docsUrl) {
        this.rule = rule;
        this.message = message;
        this.severity = severity;
        this.file = file;
        this.line = line;
        this.column = column;
        this.excerpt = excerpt;
        this.docsUrl = docsUrl;
    }

    /** True for {@code Error} severity (case-insensitive). */
    public boolean isError() {
        return severity != null && severity.equalsIgnoreCase("Error");
    }

    /** True for {@code Warning} severity (case-insensitive). */
    public boolean isWarning() {
        return severity != null && severity.equalsIgnoreCase("Warning");
    }

    @Override
    public String toString() {
        return String.format("%s:%d:%d [%s] %s (%s)",
                file, line, column, severity, message, rule);
    }
}
