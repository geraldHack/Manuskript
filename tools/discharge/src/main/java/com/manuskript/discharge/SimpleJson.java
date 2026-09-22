package com.manuskript.discharge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimaler JSON-Helper ohne externe Dependency. */
final class SimpleJson {

    private SimpleJson() {
    }

    static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String json) {
        Object parsed = parse(json == null ? "" : json.trim());
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append('"').append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static Object parse(String json) {
        if (json.isEmpty()) {
            return Map.of();
        }
        Parser p = new Parser(json);
        Object value = p.parseValue();
        p.skipWs();
        return value;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) {
                return null;
            }
            char c = s.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f' || c == 'n' || c == '-' || Character.isDigit(c)) {
                return parseLiteral();
            }
            throw new IllegalArgumentException("Unerwartetes Zeichen bei " + i);
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (peek('}')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return map;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek(']')) {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(']')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\' && i < s.length()) {
                    char n = s.charAt(i++);
                    out.append(switch (n) {
                        case '"', '\\', '/' -> n;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                yield '?';
                            }
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            yield (char) Integer.parseInt(hex, 16);
                        }
                        default -> n;
                    });
                } else {
                    out.append(c);
                }
            }
            throw new IllegalArgumentException("String nicht geschlossen");
        }

        Object parseLiteral() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                i++;
            }
            String lit = s.substring(start, i);
            return switch (lit) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                case "null" -> null;
                default -> {
                    if (lit.contains(".")) {
                        yield Double.parseDouble(lit);
                    }
                    try {
                        yield Long.parseLong(lit);
                    } catch (NumberFormatException e) {
                        yield Double.parseDouble(lit);
                    }
                }
            };
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("Erwartet '" + c + "' bei " + i);
            }
            i++;
        }
    }
}
