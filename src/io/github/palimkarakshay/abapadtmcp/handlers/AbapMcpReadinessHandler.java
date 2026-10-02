package io.github.palimkarakshay.abapadtmcp.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "Cloud Readiness" command handler — scaffolded placeholder.
 *
 * <p>Wired into the menu now so the UX is complete, but the abap-mcp
 * {@code readiness --json} call + a dedicated readiness view are on the roadmap
 * (see README). The bridge for it is a near-clone of {@link AbapMcpLintHandler}'s:
 * the only differences are the CLI subcommand ({@code readiness}) and the richer
 * result shape ({@code verdict}, {@code score}, {@code cloudBlockerCount},
 * {@code releasedApiFindings}). Kept as a stub so the foundation builds and the
 * command shows up, without shipping a half-parsed result.
 */
public class AbapMcpReadinessHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        AbapMcpHandlerSupport.EditorSource src = AbapMcpHandlerSupport.activeEditorSource(event);
        String where = src == null ? "no editor" : src.abapGitName;
        MessageDialog.openInformation(shell, "abap-mcp — Cloud Readiness",
                "Cloud Readiness is on the roadmap.\n\n"
              + "Active source: " + where + "\n\n"
              + "It will call: node <abap-mcp>/dist/cli.js readiness <tmp> --json\n"
              + "and render verdict / score / cloud-blocker count / released-API "
              + "findings in a dedicated view.");
        return null;
    }
}
