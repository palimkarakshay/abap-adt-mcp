package io.github.palimkarakshay.abapadtmcp.client;

/**
 * Raised when the abap-mcp bridge cannot produce a usable result: process
 * launch/timeout failure, a CLI usage error (exit code 2), or unparseable output.
 * A checked exception so handlers are forced to surface a clear message to the user.
 */
public class AbapMcpException extends Exception {

    private static final long serialVersionUID = 1L;

    public AbapMcpException(String message) {
        super(message);
    }

    public AbapMcpException(String message, Throwable cause) {
        super(message, cause);
    }
}
