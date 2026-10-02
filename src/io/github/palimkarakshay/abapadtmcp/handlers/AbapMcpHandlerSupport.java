package io.github.palimkarakshay.abapadtmcp.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.palimkarakshay.abapadtmcp.Activator;
import io.github.palimkarakshay.abapadtmcp.client.AbapMcpClient;

/**
 * Shared plumbing for the abap-mcp handlers: pull the active editor's source text and
 * build an {@link AbapMcpClient.Config} from the plugin preferences.
 *
 * <p><b>ADT decoupling note.</b> The ABAP source editor that ADT (com.sap.adt.*)
 * contributes is, at bottom, an {@link ITextEditor} over an {@link IDocument}; reading
 * its text needs only the generic Eclipse text-editor API, NOT any ADT-internal class.
 * This is deliberate: ADT's API bundles (e.g. {@code com.sap.adt.tools.core},
 * {@code com.sap.adt.communication}) are not on the public p2 update sites in a stable,
 * documented form, so we do not compile against them. We hook the editor by its
 * context-menu contribution id in plugin.xml instead. If/when a stable ADT model API is
 * adopted (to read the real object name / package), it goes behind this class only.
 */
final class AbapMcpHandlerSupport {

    private AbapMcpHandlerSupport() { }

    /** The editor text + (optional) backing file + a derived abapGit name. */
    static final class EditorSource {
        final String text;
        final IFile file;        // may be null for non-file inputs
        final String abapGitName;
        EditorSource(String text, IFile file, String abapGitName) {
            this.text = text;
            this.file = file;
            this.abapGitName = abapGitName;
        }
    }

    /** Read the active editor's full source. Returns null if no usable text editor is active. */
    static EditorSource activeEditorSource(ExecutionEvent event) {
        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (!(editor instanceof ITextEditor)) {
            return null;
        }
        ITextEditor textEditor = (ITextEditor) editor;
        IDocumentProvider provider = textEditor.getDocumentProvider();
        IEditorInput input = textEditor.getEditorInput();
        IDocument doc = provider == null ? null : provider.getDocument(input);
        if (doc == null) {
            return null;
        }
        String text = doc.get();

        IFile file = (input instanceof IFileEditorInput)
                ? ((IFileEditorInput) input).getFile() : null;
        String name = deriveAbapGitName(input.getName(), text);
        return new EditorSource(text, file, name);
    }

    /**
     * Produce an abapGit-style file name so abap-mcp's engine recognizes the object.
     * Prefer an existing {@code *.abap} editor name; otherwise infer from the source:
     * a class definition &rarr; {@code z<obj>.clas.abap}, else {@code .prog.abap}.
     */
    static String deriveAbapGitName(String editorName, String text) {
        if (editorName != null && editorName.toLowerCase().endsWith(".abap")) {
            return editorName;
        }
        String upper = text == null ? "" : text.toUpperCase();
        boolean isClass = upper.contains("CLASS ") && upper.contains("ENDCLASS");
        String base = "zsource";
        if (editorName != null) {
            String stem = editorName.replaceAll("\\.[^.]*$", "");
            if (!stem.isBlank()) {
                base = stem.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            }
        }
        return base + (isClass ? ".clas.abap" : ".prog.abap");
    }

    /** Build a client config from the plugin preference store. */
    static AbapMcpClient.Config configFromPrefs() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        String cli = store.getString(Activator.PREF_CLI_PATH);
        AbapMcpClient.Config cfg = new AbapMcpClient.Config(cli);
        String node = store.getString(Activator.PREF_NODE_PATH);
        if (node != null && !node.isBlank()) {
            cfg.nodePath = node;
        }
        cfg.preset = store.getString(Activator.PREF_PRESET);
        cfg.abapVersion = store.getString(Activator.PREF_VERSION);
        return cfg;
    }
}
