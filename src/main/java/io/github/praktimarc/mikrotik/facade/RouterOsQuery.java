package io.github.praktimarc.mikrotik.facade;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable RouterOS API query expression supported by the public low-level string parser.
 *
 * <p>This models server-side RouterOS comparisons and boolean query composition. It does
 * not model regular expressions. RouterOS API regex filtering is not available through
 * the pinned low-level public parser.</p>
 */
public final class RouterOsQuery {

    private enum Kind {
        COMPARISON, AND, OR, NOT
    }

    private enum Comparison {
        EQ("="),
        NOT_EQ("!="),
        LT("<"),
        GT(">");

        private final String token;

        Comparison(String token) {
            this.token = token;
        }
    }

    private final Kind kind;
    private final Comparison comparison;
    private final String property;
    private final String value;
    private final RouterOsQuery left;
    private final RouterOsQuery right;

    private RouterOsQuery(
            Kind kind,
            Comparison comparison,
            String property,
            String value,
            RouterOsQuery left,
            RouterOsQuery right) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.comparison = comparison;
        this.property = property;
        this.value = value;
        this.left = left;
        this.right = right;
    }

    /** Creates an exact equality comparison. */
    public static RouterOsQuery eq(String property, String value) {
        return comparison(Comparison.EQ, property, value);
    }

    /** Creates an exact inequality comparison. */
    public static RouterOsQuery notEq(String property, String value) {
        return comparison(Comparison.NOT_EQ, property, value);
    }

    /** Creates a RouterOS less-than comparison. */
    public static RouterOsQuery lt(String property, String value) {
        return comparison(Comparison.LT, property, value);
    }

    /** Creates a RouterOS greater-than comparison. */
    public static RouterOsQuery gt(String property, String value) {
        return comparison(Comparison.GT, property, value);
    }

    /** Returns a boolean AND of this expression and another expression. */
    public RouterOsQuery and(RouterOsQuery other) {
        return binary(Kind.AND, this, other);
    }

    /** Returns a boolean OR of this expression and another expression. */
    public RouterOsQuery or(RouterOsQuery other) {
        return binary(Kind.OR, this, other);
    }

    /** Returns the boolean negation of this expression. */
    public RouterOsQuery not() {
        return new RouterOsQuery(Kind.NOT, null, null, null, this, null);
    }

    /**
     * Returns the low-level-parser expression syntax without the leading {@code where}.
     *
     * <p>The returned string contains the query values supplied by the caller and therefore
     * must not be used for logging.</p>
     */
    public String expression() {
        return render(false);
    }

    /** Returns the distinct RouterOS property names in deterministic encounter order. */
    public List<String> propertyNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        collectProperties(names);
        return List.copyOf(names);
    }

    /**
     * Returns query values in encounter order.
     *
     * <p>This exists for command-error redaction. Values are caller-provided data and must
     * not be included in diagnostics.</p>
     */
    public List<String> values() {
        ArrayList<String> values = new ArrayList<>();
        collectValues(values);
        return List.copyOf(values);
    }

    @Override
    public String toString() {
        return "RouterOsQuery{propertyNames=" + propertyNames() + '}';
    }

    private static RouterOsQuery comparison(
            Comparison comparison,
            String property,
            String value) {
        return new RouterOsQuery(
                Kind.COMPARISON,
                Objects.requireNonNull(comparison, "comparison"),
                validateName(property),
                Objects.requireNonNull(value, "value"),
                null,
                null);
    }

    private static RouterOsQuery binary(
            Kind kind,
            RouterOsQuery left,
            RouterOsQuery right) {
        if (kind != Kind.AND && kind != Kind.OR) {
            throw new IllegalArgumentException("binary kind must be AND or OR");
        }
        return new RouterOsQuery(
                kind,
                null,
                null,
                null,
                Objects.requireNonNull(left, "left"),
                Objects.requireNonNull(right, "right"));
    }

    private String render(boolean nested) {
        return switch (kind) {
            case COMPARISON -> property + comparison.token + quote(value);
            case NOT -> "not " + parenthesize(left.render(false));
            case AND, OR -> {
                String operator = kind == Kind.AND ? " and " : " or ";
                String expression = renderOperand(left) + operator + renderOperand(right);
                yield nested ? parenthesize(expression) : expression;
            }
        };
    }

    private static String renderOperand(RouterOsQuery query) {
        if (query.kind == Kind.COMPARISON || query.kind == Kind.NOT) {
            return query.render(false);
        }
        return query.render(true);
    }

    private void collectProperties(Set<String> names) {
        switch (kind) {
            case COMPARISON -> names.add(property);
            case NOT -> left.collectProperties(names);
            case AND, OR -> {
                left.collectProperties(names);
                right.collectProperties(names);
            }
        }
    }

    private void collectValues(List<String> values) {
        switch (kind) {
            case COMPARISON -> values.add(value);
            case NOT -> left.collectValues(values);
            case AND, OR -> {
                left.collectValues(values);
                right.collectValues(values);
            }
        }
    }

    private static String parenthesize(String expression) {
        return "(" + expression + ")";
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "property");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("property must not be blank");
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if (Character.isWhitespace(ch)
                    || ch == '=' || ch == '<' || ch == '>' || ch == '!'
                    || ch == ',' || ch == '?' || ch == '\'' || ch == '"'
                    || ch == '(' || ch == ')') {
                throw new IllegalArgumentException("property contains unsupported query syntax");
            }
        }
        return trimmed;
    }

    private static String quote(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        throw new IllegalArgumentException(
                "RouterOS query value contains both quote characters and cannot be represented safely by the pinned low-level parser");
    }
}
