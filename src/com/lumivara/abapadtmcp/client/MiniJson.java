package com.lumivara.abapadtmcp.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser — just enough to read abap-mcp's CLI output.
 *
 * <p>Rationale: an Eclipse/OSGi plugin should avoid dragging in a third-party JSON
 * library (extra bundle, classpath, version skew). abap-mcp emits well-formed,
 * machine-generated JSON, so a compact recursive-descent reader is sufficient and
 * keeps the bridge self-contained and unit-testable on a bare JRE.
 *
 * <p>Returns Java values: {@code Map<String,Object>} for objects,
 * {@code List<Object>} for arrays, {@code String}, {@code Double}, {@code Boolean},
 * and {@code null}. Not a general-purpose parser (no surrogate-pair edge cases,
 * no comments) — scoped deliberately to this one producer.
 */
public final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    /** Parse a JSON document into Java values. */
    public static Object parse(String json) {
        MiniJson p = new MiniJson(json);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < p.s.length()) {
            throw new JsonException("Trailing content at index " + p.i);
        }
        return v;
    }

    /** Thrown on malformed input. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        JsonException(String m) { super(m); }
    }

    // ---- helpers to read typed fields without unchecked-cast noise at call sites ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        if (!(o instanceof Map)) {
            throw new JsonException("Expected JSON object, got " + typeOf(o));
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        if (o == null) return new ArrayList<>();
        if (!(o instanceof List)) {
            throw new JsonException("Expected JSON array, got " + typeOf(o));
        }
        return (List<Object>) o;
    }

    public static String str(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static int intOr(Map<String, Object> obj, String key, int dflt) {
        Object v = obj.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return (int) Double.parseDouble((String) v); }
            catch (NumberFormatException e) { return dflt; }
        }
        return dflt;
    }

    private static String typeOf(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    // ---- recursive-descent core ----

    private Object value() {
        char c = peek();
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': case 'f': return bool();
            case 'n': literal("null"); return null;
            default:  return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        expect('{');
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            expect(':');
            ws();
            m.put(key, value());
            ws();
            char c = next();
            if (c == '}') break;
            if (c != ',') throw new JsonException("Expected ',' or '}' at " + i);
        }
        return m;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        expect('[');
        ws();
        if (peek() == ']') { i++; return list; }
        while (true) {
            ws();
            list.add(value());
            ws();
            char c = next();
            if (c == ']') break;
            if (c != ',') throw new JsonException("Expected ',' or ']' at " + i);
        }
        return list;
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') break;
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        String hex = s.substring(i, i + 4);
                        i += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default: throw new JsonException("Bad escape \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) throw new JsonException("Invalid token at " + i);
        return Double.parseDouble(s.substring(start, i));
    }

    private Boolean bool() {
        if (peek() == 't') { literal("true"); return Boolean.TRUE; }
        literal("false");
        return Boolean.FALSE;
    }

    private void literal(String lit) {
        if (!s.regionMatches(i, lit, 0, lit.length())) {
            throw new JsonException("Expected '" + lit + "' at " + i);
        }
        i += lit.length();
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        if (i >= s.length()) throw new JsonException("Unexpected end of input");
        return s.charAt(i);
    }

    private char next() {
        if (i >= s.length()) throw new JsonException("Unexpected end of input");
        return s.charAt(i++);
    }

    private void expect(char c) {
        char got = next();
        if (got != c) throw new JsonException("Expected '" + c + "' but got '" + got + "' at " + (i - 1));
    }
}
