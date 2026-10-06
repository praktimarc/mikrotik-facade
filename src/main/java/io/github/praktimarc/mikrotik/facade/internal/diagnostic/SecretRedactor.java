package io.github.praktimarc.mikrotik.facade.internal.diagnostic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central secret classification and redaction used by facade diagnostics.
 */
public final class SecretRedactor {

    /** Stable replacement token used for every redacted value. */
    public static final String REDACTED = "<redacted>";

    private static final Set<String> SENSITIVE_SUFFIXES = Set.of(
            "password",
            "passwd",
            "passphrase",
            "psk",
            "presharedkey",
            "privatekey",
            "privatekeypassword",
            "secret",
            "community",
            "authresponse",
            "authenticationresponse",
            "authkey",
            "authenticationkey",
            "authenticationpassword",
            "privacykey",
            "privacypassword",
            "encryptionpassword",
            "credential",
            "credentials",
            "accesskey",
            "token",
            "apikey");

    private static final Pattern INLINE_SECRET = Pattern.compile(
            "(?i)(\\b(?:password|passwd|passphrase|psk|pre[-_ ]?shared[-_ ]?key|"
                    + "private[-_ ]?key(?:[-_ ]?password)?|"
                    + "auth(?:entication)?[-_ ]?(?:response|key|password)|"
                    + "privacy[-_ ]?(?:key|password)|encryption[-_ ]?password|"
                    + "snmp[-_ ]?(?:secret|community)|community|credential|credentials|secret|token|"
                    + "access[-_ ]?key|api[-_ ]?key)"
                    + "\\b\\s*(?:=|:)\\s*)"
                    + "(\"[^\"]*\"|'[^']*'|[^\\s,;]+)");

    private SecretRedactor() {
    }

    /**
     * Reports whether a property name represents credential or key material.
     *
     * @param key property name
     * @return {@code true} for a centrally recognized sensitive key
     */
    public static boolean isSensitiveKey(String key) {
        Objects.requireNonNull(key, "key");
        String normalized = normalizeKey(key);
        for (String suffix : SENSITIVE_SUFFIXES) {
            if (normalized.equals(suffix) || normalized.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns either the original value or the stable redaction token.
     *
     * @param key property name
     * @param value property value
     * @return safe diagnostic value
     */
    public static String redactValue(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return isSensitiveKey(key) ? REDACTED : value;
    }

    /**
     * Creates a deterministic insertion-ordered redacted copy of a property map.
     *
     * @param values original properties
     * @return redacted copy
     */
    public static Map<String, String> redactMap(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        LinkedHashMap<String, String> safe = new LinkedHashMap<>();
        values.forEach((key, value) -> safe.put(
                Objects.requireNonNull(key, "key"),
                redactValue(key, Objects.requireNonNull(value, "value"))));
        return Collections.unmodifiableMap(safe);
    }

    /**
     * Sanitizes arbitrary diagnostic text using both known command properties and
     * recognizable inline credential assignments.
     *
     * @param text arbitrary text, possibly from RouterOS
     * @param arguments command arguments whose sensitive values must not be reflected
     * @param queries command queries whose sensitive values must not be reflected
     * @return sanitized text, or {@code null} when input was null
     */
    public static String redactText(
            String text,
            Map<String, String> arguments,
            Map<String, String> queries) {
        if (text == null) {
            return null;
        }
        String safe = text;
        safe = redactKnownSensitiveValues(safe, arguments);
        safe = redactKnownSensitiveValues(safe, queries);

        Matcher matcher = INLINE_SECRET.matcher(safe);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    out,
                    Matcher.quoteReplacement(matcher.group(1) + REDACTED));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String redactKnownSensitiveValues(String text, Map<String, String> values) {
        if (values == null) {
            return text;
        }
        String safe = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "key");
            String value = Objects.requireNonNull(entry.getValue(), "value");
            if (isSensitiveKey(key) && !value.isEmpty()) {
                safe = safe.replace(value, REDACTED);
            }
        }
        return safe;
    }

    private static String normalizeKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                normalized.append(ch);
            }
        }
        return normalized.toString();
    }
}
