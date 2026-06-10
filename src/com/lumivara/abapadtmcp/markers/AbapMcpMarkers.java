package com.lumivara.abapadtmcp.markers;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import com.lumivara.abapadtmcp.client.AbapMcpFinding;

/**
 * Maps {@link AbapMcpFinding}s onto Eclipse problem markers so they surface in the
 * editor gutter and the Problems view — the idiomatic Eclipse way to show
 * diagnostics, far better than a modal dialog.
 *
 * <p>This is the only place that touches the Eclipse resources API for markers, so
 * the rest of the plugin stays decoupled. Marker type id mirrors the one declared in
 * {@code plugin.xml}.
 */
public final class AbapMcpMarkers {

    /** Must match the {@code id} of the marker {@code extension} in plugin.xml. */
    public static final String MARKER_TYPE = "com.lumivara.abapadtmcp.abapMcpProblem";

    private AbapMcpMarkers() { }

    /** Remove all abap-mcp markers from a resource (called before re-linting). */
    public static void clear(IResource resource) throws CoreException {
        if (resource != null && resource.exists()) {
            resource.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
        }
    }

    /** Create one marker per finding on the given file. */
    public static void apply(IFile file, List<AbapMcpFinding> findings) throws CoreException {
        clear(file);
        for (AbapMcpFinding f : findings) {
            IMarker marker = file.createMarker(MARKER_TYPE);
            marker.setAttribute(IMarker.MESSAGE, f.message + "  [" + f.rule + "]");
            marker.setAttribute(IMarker.LINE_NUMBER, Math.max(1, f.line));
            marker.setAttribute(IMarker.SEVERITY, toEclipseSeverity(f.severity));
            marker.setAttribute(IMarker.LOCATION, "line " + f.line);
            if (f.docsUrl != null) {
                marker.setAttribute("abapMcpDocsUrl", f.docsUrl);
            }
            marker.setAttribute("abapMcpRule", f.rule == null ? "" : f.rule);
        }
    }

    private static int toEclipseSeverity(String severity) {
        if (severity == null) {
            return IMarker.SEVERITY_INFO;
        }
        switch (severity.toLowerCase()) {
            case "error":   return IMarker.SEVERITY_ERROR;
            case "warning": return IMarker.SEVERITY_WARNING;
            default:        return IMarker.SEVERITY_INFO;
        }
    }
}
