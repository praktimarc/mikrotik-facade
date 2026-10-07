package io.github.praktimarc.mikrotik.facade.internal.diagnostic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Produces safe command diagnostics without exposing credential material.
 */
public final class CommandDiagnosticRenderer {

    private CommandDiagnosticRenderer() {
    }

    /**
     * Renders a RouterOS command for logs or exception diagnostics.
     *
     * @param commandPath command path or raw command string
     * @param arguments command arguments
     * @param queries command query properties
     * @return safe diagnostic string
     */
    public static String render(
            String commandPath,
            Map<String, String> arguments,
            Map<String, String> queries) {
        String path = safeCommandPath(commandPath);
        Map<String, String> safeArguments = redactForPath(path, nonNullMap(arguments));
        Map<String, String> safeQueries = redactForPath(path, nonNullMap(queries));
        return "RouterOsCommand{path=" + path
                + ", arguments=" + safeArguments
                + ", queries=" + safeQueries
                + '}';
    }

    /**
     * Extracts a path-only representation from a command or raw command string.
     *
     * <p>Anything after whitespace, an argument marker ({@code =}), or a query
     * marker ({@code ?}) is excluded from the public command path context.</p>
     *
     * @param commandPath command path or raw command string
     * @return safe path-only representation, or null
     */
    public static String safeCommandPath(String commandPath) {
        if (commandPath == null) {
            return null;
        }
        String trimmed = commandPath.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int cut = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch) || ch == '=' || ch == '?') {
                cut = i;
                break;
            }
        }
        return trimmed.substring(0, cut);
    }

    /**
     * Sanitizes a RouterOS message using the exact command properties as an
     * additional source of sensitive values.
     *
     * @param message RouterOS or low-level message
     * @param arguments command arguments
     * @param queries command query properties
     * @return sanitized message, or null
     */
    public static String sanitizeRouterOsMessage(
            String message,
            Map<String, String> arguments,
            Map<String, String> queries) {
        return sanitizeRouterOsMessage(null, message, arguments, queries);
    }

    /**
     * Sanitizes a RouterOS message using command-path-specific secret knowledge.
     *
     * @param commandPath RouterOS command path or raw command string
     * @param message RouterOS or low-level message
     * @param arguments command arguments used only for redaction
     * @param queries command queries used only for redaction
     * @return sanitized message, or null
     */
    public static String sanitizeRouterOsMessage(
            String commandPath,
            String message,
            Map<String, String> arguments,
            Map<String, String> queries) {
        Map<String, String> rawArguments = nonNullMap(arguments);
        Map<String, String> rawQueries = nonNullMap(queries);
        String safe = message;
        if (isSnmpCommunityPath(safeCommandPath(commandPath))) {
            safe = redactNamedValue(safe, rawArguments.get("name"));
            safe = redactNamedValue(safe, rawQueries.get("name"));
        }
        return SecretRedactor.redactText(safe, rawArguments, rawQueries);
    }

    private static Map<String, String> redactForPath(String path, Map<String, String> values) {
        Map<String, String> safe = SecretRedactor.redactMap(values);
        if (!isSnmpCommunityPath(path) || !safe.containsKey("name")) {
            return safe;
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>(safe);
        copy.put("name", SecretRedactor.REDACTED);
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static boolean isSnmpCommunityPath(String path) {
        return path != null && (path.equals("/snmp/community") || path.startsWith("/snmp/community/"));
    }

    private static String redactNamedValue(String text, String value) {
        if (text == null || value == null || value.isEmpty()) {
            return text;
        }
        return text.replace(value, SecretRedactor.REDACTED);
    }

    private static Map<String, String> nonNullMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")));
        return copy;
    }
}
