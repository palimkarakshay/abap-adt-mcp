package io.github.palimkarakshay.abapadtmcp;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator + plugin singleton. Holds the shared preference store
 * (node path, abap-mcp CLI path, preset) that the handlers read.
 */
public class Activator extends AbstractUIPlugin {

    /** Must match Bundle-SymbolicName in MANIFEST.MF. */
    public static final String PLUGIN_ID = "io.github.palimkarakshay.abapadtmcp";

    // Preference keys (see roadmap: a real preference page is the next step).
    public static final String PREF_NODE_PATH = "abapMcp.nodePath";
    public static final String PREF_CLI_PATH  = "abapMcp.cliPath";
    public static final String PREF_PRESET    = "abapMcp.preset";
    public static final String PREF_VERSION   = "abapMcp.abapVersion";

    private static Activator instance;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        seedDefaults();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        instance = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return instance;
    }

    private void seedDefaults() {
        IPreferenceStore store = getPreferenceStore();
        store.setDefault(PREF_NODE_PATH, "node");
        // Sensible default: a globally installed `abap-mcp` puts cli.js here.
        store.setDefault(PREF_CLI_PATH, "");
        store.setDefault(PREF_PRESET, "full");
        store.setDefault(PREF_VERSION, "Cloud");
    }

    /** Allow non-UI code (and tests) to reach the instance-scoped store sanely. */
    public static InstanceScope instanceScope() {
        return InstanceScope.INSTANCE;
    }
}
