package com.itdesk;

import java.util.*;

/** Small JSON reader/writer so the server needs no extra libraries. */
final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser p = new Parser(text);
        p.skip();
        Object v = p.value(0);
        p.skip();
        if (p.i != text.length()) throw p.fail("unexpected data after the value");
        return v;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new ApiError(400, "Expected a JSON object.");
        return (Map<String, Object>) v;
    }

    static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof String s) str(sb, s);
        else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) sb.append(v);
        else if (v instanceof Number n) sb.append(Double.isFinite(n.doubleValue()) ? n.toString() : "null");
        else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                str(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else str(sb, v.toString());
    }

    private static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        final String s;
        int i = 0;

        Parser(String s) { this.s = s; }

        ApiError fail(String m) { return new ApiError(400, "Invalid JSON: " + m + "."); }

        void skip() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }

        Object value(int depth) {
            if (depth > 32) throw fail("nested too deeply");
            if (i >= s.length()) throw fail("unexpected end");
            switch (s.charAt(i)) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': lit("true"); return Boolean.TRUE;
                case 'f': lit("false"); return Boolean.FALSE;
                case 'n': lit("null"); return null;
                default: return number();
            }
        }

        void lit(String w) {
            if (!s.startsWith(w, i)) throw fail("bad literal");
            i += w.length();
        }

        Map<String, Object> object(int d) {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            skip();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            for (;;) {
                skip();
                if (i >= s.length() || s.charAt(i) != '"') throw fail("expected a key");
                String k = string();
                skip();
                if (i >= s.length() || s.charAt(i) != ':') throw fail("expected ':'");
                i++;
                skip();
                m.put(k, value(d + 1));
                skip();
                if (i >= s.length()) throw fail("unexpected end");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw fail("expected ',' or '}'");
            }
        }

        List<Object> array(int d) {
            i++;
            List<Object> a = new ArrayList<>();
            skip();
            if (i < s.length() && s.charAt(i) == ']') { i++; return a; }
            for (;;) {
                skip();
                a.add(value(d + 1));
                skip();
                if (i >= s.length()) throw fail("unexpected end");
                char c = s.charAt(i++);
                if (c == ']') return a;
                if (c != ',') throw fail("expected ',' or ']'");
            }
        }

        String string() {
            i++;
            StringBuilder sb = new StringBuilder();
            for (;;) {
                if (i >= s.length()) throw fail("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw fail("bad escape");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw fail("bad unicode escape");
                            try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                            catch (NumberFormatException x) { throw fail("bad unicode escape"); }
                            i += 4;
                        }
                        default -> throw fail("bad escape");
                    }
                } else if (c < 0x20) throw fail("control character in string");
                else sb.append(c);
            }
        }

        Object number() {
            int st = i;
            if (i < s.length() && s.charAt(i) == '-') i++;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(st, i);
            if (t.isEmpty()) throw fail("unexpected character");
            try {
                if (t.matches("-?\\d{1,18}")) return Long.parseLong(t);
                return Double.parseDouble(t);
            } catch (NumberFormatException e) { throw fail("bad number"); }
        }
    }
}
