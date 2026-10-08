package io.github.praktimarc.mikrotik.facade.internal.command;

import io.github.praktimarc.mikrotik.facade.RouterOsQuery;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.CommandDiagnosticRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable internal representation of one finite RouterOS command.
 */
public final class RouterOsCommand {

    private final String path;
    private final Map<String, String> arguments;
    private final List<String> flags;
    private final Map<String, String> queries;
    private final List<RouterOsQuery> advancedQueries;
    private final RouterOsQuery queryExpression;
    private final List<String> properties;

    private RouterOsCommand(Builder builder) {
        this.path = validatePath(builder.path);
        this.arguments = immutableOrderedCopy(builder.arguments);
        this.flags = List.copyOf(builder.flags);
        this.queries = immutableOrderedCopy(builder.queries);
        this.advancedQueries = List.copyOf(builder.advancedQueries);
        this.queryExpression = combineQueries(this.queries, this.advancedQueries);
        this.properties = List.copyOf(builder.properties);
    }

    /** Creates a builder for a RouterOS command path. */
    public static Builder builder(String path) {
        return new Builder(path);
    }

    /** Returns the command path without arguments. */
    public String path() {
        return path;
    }

    /** Returns immutable arguments in insertion order. */
    public Map<String, String> arguments() {
        return arguments;
    }

    /** Returns immutable valueless command flags in insertion order. */
    public List<String> flags() {
        return flags;
    }

    /**
     * Returns immutable legacy equality queries in insertion order.
     *
     * <p>Advanced query expressions are available through {@link #queryExpression()}.</p>
     */
    public Map<String, String> queries() {
        return queries;
    }

    /** Returns advanced query expressions added directly to the command. */
    public List<RouterOsQuery> advancedQueries() {
        return advancedQueries;
    }

    /** Returns the complete combined query expression, or null when no query exists. */
    public RouterOsQuery queryExpression() {
        return queryExpression;
    }

    /** Returns all distinct query property names without exposing query values. */
    public List<String> queryPropertyNames() {
        return queryExpression == null ? List.of() : queryExpression.propertyNames();
    }

    /**
     * Returns all query values under synthetic keys for error-message redaction.
     *
     * <p>The returned values must never be used for diagnostics.</p>
     */
    public Map<String, String> queryValuesForRedaction() {
        if (queryExpression == null) {
            return Map.of();
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        List<String> rawValues = queryExpression.values();
        for (int index = 0; index < rawValues.size(); index++) {
            values.put("query-value-" + index, rawValues.get(index));
        }
        return Collections.unmodifiableMap(values);
    }

    /** Returns immutable property selection in insertion order. */
    public List<String> properties() {
        return properties;
    }

    /**
     * Serializes the command for the public low-level string parser.
     *
     * @return low-level command string
     */
    public String serialize() {
        StringBuilder command = new StringBuilder(path);
        arguments.forEach((key, value) -> command
                .append(' ')
                .append(key)
                .append('=')
                .append(quote(value)));
        flags.forEach(flag -> command.append(' ').append(flag));
        if (queryExpression != null) {
            command.append(" where ")
                    .append(queryExpression.expression());
        }
        if (!properties.isEmpty()) {
            command.append(" return ");
            for (int i = 0; i < properties.size(); i++) {
                if (i > 0) {
                    command.append(',');
                }
                command.append(properties.get(i));
            }
        }
        return command.toString();
    }

    @Override
    public String toString() {
        return CommandDiagnosticRenderer.structural(this);
    }

    private static RouterOsQuery combineQueries(
            Map<String, String> legacyQueries,
            List<RouterOsQuery> advancedQueries) {
        RouterOsQuery combined = null;
        for (Map.Entry<String, String> entry : legacyQueries.entrySet()) {
            RouterOsQuery query = RouterOsQuery.eq(entry.getKey(), entry.getValue());
            combined = combined == null ? query : combined.and(query);
        }
        for (RouterOsQuery query : advancedQueries) {
            combined = combined == null ? query : combined.and(query);
        }
        return combined;
    }

    private static String validatePath(String path) {
        Objects.requireNonNull(path, "path");
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch) || ch == '=' || ch == '?') {
                throw new IllegalArgumentException("path must contain only the RouterOS command path");
            }
        }
        return trimmed;
    }

    private static String validateName(String name, String label) {
        Objects.requireNonNull(name, label);
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch) || ch == '=' || ch == ',' || ch == '?'
                    || ch == '\'' || ch == '"') {
                throw new IllegalArgumentException(label + " contains unsupported syntax");
            }
        }
        return trimmed;
    }

    private static String quote(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        throw new IllegalArgumentException(
                "RouterOS value contains both quote characters and cannot be represented safely by the low-level parser");
    }

    private static Map<String, String> immutableOrderedCopy(Map<String, String> source) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")));
        return Collections.unmodifiableMap(copy);
    }

    /** Builder whose result is an immutable command snapshot. */
    public static final class Builder {
        private final String path;
        private final LinkedHashMap<String, String> arguments = new LinkedHashMap<>();
        private final List<String> flags = new ArrayList<>();
        private final LinkedHashMap<String, String> queries = new LinkedHashMap<>();
        private final List<RouterOsQuery> advancedQueries = new ArrayList<>();
        private final List<String> properties = new ArrayList<>();

        private Builder(String path) {
            this.path = Objects.requireNonNull(path, "path");
        }

        /** Adds or replaces an argument. */
        public Builder argument(String key, String value) {
            arguments.put(validateName(key, "argument name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /** Adds one valueless command flag, preserving insertion order. */
        public Builder flag(String name) {
            String validated = validateName(name, "flag name");
            if (!flags.contains(validated)) {
                flags.add(validated);
            }
            return this;
        }

        /** Adds or replaces a legacy equality query. */
        public Builder query(String key, String value) {
            queries.put(validateName(key, "query name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Adds an advanced RouterOS query expression and ANDs it with other query terms.
         *
         * <p>Use this form when the same RouterOS property must appear more than once,
         * for example {@code interface=a OR interface=b}.</p>
         */
        public Builder query(RouterOsQuery query) {
            advancedQueries.add(Objects.requireNonNull(query, "query"));
            return this;
        }

        /** Adds a property to the RouterOS return selection. */
        public Builder property(String property) {
            properties.add(validateName(property, "property name"));
            return this;
        }

        /** Builds the immutable snapshot. */
        public RouterOsCommand build() {
            return new RouterOsCommand(this);
        }
    }
}
