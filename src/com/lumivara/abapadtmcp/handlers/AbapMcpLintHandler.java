package com.lumivara.abapadtmcp.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.lumivara.abapadtmcp.client.AbapMcpClient;
import com.lumivara.abapadtmcp.client.AbapMcpException;
import com.lumivara.abapadtmcp.client.AbapMcpFinding;
import com.lumivara.abapadtmcp.client.AbapMcpResult;
import com.lumivara.abapadtmcp.markers.AbapMcpMarkers;

/**
 * "Lint with abap-mcp" command handler. Wires the active ABAP editor to the
 * {@link AbapMcpClient} bridge and surfaces results as Eclipse problem markers
 * (with a dialog fallback / summary).
 *
 * <p>All the real work lives in {@link AbapMcpClient}; this handler is thin glue:
 * grab source &rarr; call client &rarr; render. That is the design goal — the Eclipse
 * layer stays replaceable plumbing.
 */
public class AbapMcpLintHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);

        AbapMcpHandlerSupport.EditorSource src = AbapMcpHandlerSupport.activeEditorSource(event);
        if (src == null) {
            MessageDialog.openInformation(shell, "abap-mcp",
                    "Open an ABAP source editor first, then run \"Lint with abap-mcp\".");
            return null;
        }

        AbapMcpClient.Config cfg = AbapMcpHandlerSupport.configFromPrefs();
        if (cfg.cliPath == null || cfg.cliPath.isBlank()) {
            MessageDialog.openWarning(shell, "abap-mcp not configured",
                    "Set the path to abap-mcp's dist/cli.js in "
                    + "Preferences → ABAP → abap-mcp.\n\n"
                    + "(Roadmap: a preferences page; for now seed the "
                    + "\"abapMcp.cliPath\" instance preference.)");
            return null;
        }

        try {
            AbapMcpClient client = new AbapMcpClient(cfg);
            AbapMcpResult result = client.lint(src.text, src.abapGitName);

            // Idiomatic path: problem markers on the backing file.
            if (src.file != null) {
                try {
                    AbapMcpMarkers.apply(src.file, result.findings);
                } catch (Exception markerEx) {
                    // Markers are best-effort; fall through to the dialog summary.
                }
            }

            showSummary(shell, result);
        } catch (AbapMcpException e) {
            MessageDialog.openError(shell, "abap-mcp failed", e.getMessage());
        }
        return null;
    }

    private void showSummary(Shell shell, AbapMcpResult result) {
        if (result.findings.isEmpty()) {
            MessageDialog.openInformation(shell, "abap-mcp",
                    "No findings — clean (" + result.files + " file analyzed).");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(result.findings.size()).append(" finding(s): ")
          .append(result.errorCount()).append(" error, ")
          .append(result.warningCount()).append(" warning.\n")
          .append("See the Problems view / editor gutter for details.\n\n");
        int shown = 0;
        for (AbapMcpFinding f : result.findings) {
            if (shown++ >= 10) {
                sb.append("… and ").append(result.findings.size() - 10).append(" more.");
                break;
            }
            sb.append("• line ").append(f.line).append(": ")
              .append(f.message).append("  [").append(f.rule).append("]\n");
        }
        MessageDialog.openWarning(shell, "abap-mcp findings", sb.toString());
    }
}
