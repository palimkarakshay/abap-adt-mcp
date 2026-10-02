package io.github.palimkarakshay.abapadtmcp.client;

/**
 * Standalone, Eclipse-free end-to-end proof of the Java &harr; abap-mcp bridge.
 *
 * <p>Not a JUnit test (the plugin has no JUnit on its bare-JRE verify path). It is a
 * runnable {@code main} that does exactly what {@code AbapMcpLintHandler} will do at
 * runtime, minus the SWT/editor parts: feed a known-dirty ABAP snippet through
 * {@link AbapMcpClient}, then print the parsed findings. If this prints findings, the
 * load-bearing half of the plugin works.
 *
 * <p>Usage:
 * <pre>
 *   javac -d out src/io/github/palimkarakshay/abapadtmcp/client/*.java \
 *                test/io/github/palimkarakshay/abapadtmcp/client/BridgeSmokeTest.java
 *   java  -cp out io.github.palimkarakshay.abapadtmcp.client.BridgeSmokeTest \
 *                 /home/akshay/projects/abap-mcp/dist/cli.js
 * </pre>
 * The single arg is the path to abap-mcp's {@code dist/cli.js}. Optional 2nd arg = node path.
 */
public final class BridgeSmokeTest {

    private static final String SAMPLE =
            "CLASS zcl_demo DEFINITION PUBLIC FINAL CREATE PUBLIC.\n"
          + "  PUBLIC SECTION.\n"
          + "    METHODS run.\n"
          + "ENDCLASS.\n"
          + "CLASS zcl_demo IMPLEMENTATION.\n"
          + "  METHOD run.\n"
          + "    DATA lv_x TYPE i.\n"
          + "    lv_x = 1.\n"
          + "    SELECT * FROM mara INTO TABLE @DATA(lt_mara).\n"
          + "  ENDMETHOD.\n"
          + "ENDCLASS.\n";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: BridgeSmokeTest <path-to-abap-mcp/dist/cli.js> [nodePath]");
            System.exit(2);
        }
        AbapMcpClient.Config cfg = new AbapMcpClient.Config(args[0]);
        if (args.length >= 2) {
            cfg.nodePath = args[1];
        }
        AbapMcpClient client = new AbapMcpClient(cfg);

        try {
            // First a self-check of the JSON parser on a fixed payload, so a parser
            // regression is caught even if abap-mcp is missing.
            assertParser();

            AbapMcpResult result = client.lint(SAMPLE, "zcl_demo.clas.abap");
            System.out.println("=== abap-mcp bridge smoke test ===");
            System.out.println("files analyzed : " + result.files);
            System.out.println("exit code      : " + result.exitCode
                    + " (0=ok, 1=findings, 2=usage)");
            System.out.println("errors         : " + result.errorCount());
            System.out.println("warnings       : " + result.warningCount());
            System.out.println("findings       : " + result.findings.size());
            System.out.println("------------------------------------");
            for (AbapMcpFinding f : result.findings) {
                System.out.println("  " + f);
            }
            System.out.println("------------------------------------");
            if (result.findings.isEmpty()) {
                System.err.println("FAIL: expected findings on the dirty sample, got none.");
                System.exit(1);
            }
            System.out.println("PASS: bridge parsed " + result.findings.size()
                    + " findings end-to-end.");
        } catch (AbapMcpException e) {
            System.err.println("FAIL: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Offline assertion that {@link AbapMcpClient#parseLint} maps the JSON shape correctly. */
    private static void assertParser() {
        String fixture =
                "{\"files\":1,\"findings\":[{\"rule\":\"select_performance\","
              + "\"message\":\"Avoid use of SELECT *\",\"severity\":\"Error\","
              + "\"file\":\"zcl_demo.clas.abap\",\"line\":9,\"column\":5,"
              + "\"excerpt\":\"SELECT * FROM mara INTO TABLE @DATA(lt_mara).\","
              + "\"docsUrl\":\"https://rules.abaplint.org/select_performance/\"}]}";
        try {
            AbapMcpClient.ProcessExecution exec =
                    new AbapMcpClient.ProcessExecution(1, fixture, "");
            AbapMcpResult r = AbapMcpClient.parseLint(exec, "zcl_demo.clas.abap");
            require(r.files == 1, "files == 1");
            require(r.findings.size() == 1, "one finding");
            AbapMcpFinding f = r.findings.get(0);
            require("select_performance".equals(f.rule), "rule parsed");
            require(f.line == 9 && f.column == 5, "line/column parsed");
            require(f.isError(), "severity Error");
            System.out.println("parser self-check: PASS");
        } catch (AbapMcpException e) {
            throw new RuntimeException("parser self-check threw: " + e.getMessage(), e);
        }
    }

    private static void require(boolean cond, String what) {
        if (!cond) {
            throw new AssertionError("parser self-check failed: " + what);
        }
    }

    private BridgeSmokeTest() { }
}
